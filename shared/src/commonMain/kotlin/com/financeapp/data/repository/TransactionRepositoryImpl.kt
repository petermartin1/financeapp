package com.financeapp.data.repository

import com.financeapp.db.schema.Accounts
import com.financeapp.db.schema.Categories
import com.financeapp.db.schema.Payees
import com.financeapp.db.schema.SplitItems
import com.financeapp.db.schema.TransactionTags
import com.financeapp.db.schema.Transactions
import com.financeapp.domain.model.SplitItem
import com.financeapp.domain.model.Transaction
import com.financeapp.domain.model.TransactionWithDetails
import com.financeapp.domain.reporting.expandSpendingLines
import com.financeapp.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class TransactionRepositoryImpl(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TransactionRepository {

    // Trigger for reactive transaction updates
    private val transactionRefreshTrigger = MutableStateFlow(0L)

    override fun notifyTransactionsChanged() {
        transactionRefreshTrigger.value += 1
    }

    override fun getTransactionsByAccount(accountId: Long): Flow<List<Transaction>> =
        transactionRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    Transactions
                        .selectAll().where { Transactions.accountId eq accountId.toInt() }
                        .orderBy(Transactions.date to SortOrder.DESC, Transactions.id to SortOrder.DESC)
                        .map { it.toDomain() }
                }
            }
        }

    override fun getTransactionsWithDetailsByAccount(accountId: Long): Flow<List<TransactionWithDetails>> =
        transactionRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    // Fetch everything in a single transaction for speed
                    val transactions = Transactions
                        .selectAll().where { Transactions.accountId eq accountId.toInt() }
                        .orderBy(Transactions.date to SortOrder.DESC, Transactions.id to SortOrder.DESC)
                        .map { it.toDomain() }

                    // Fetch lookup tables once
                    val payees = Payees.selectAll().associate { it[Payees.id].value.toLong() to it[Payees.name] }
                    val categories = Categories.selectAll().associate { it[Categories.id].value.toLong() to it[Categories.name] }
                    val accountName = Accounts.selectAll().where { Accounts.id eq accountId.toInt() }
                        .singleOrNull()
                        ?.get(Accounts.name) ?: ""

                    // Map to details
                    transactions.map { txn ->
                        TransactionWithDetails(
                            transaction = txn,
                            payeeName = txn.payeeId?.let { payees[it] },
                            categoryName = txn.categoryId?.let { categories[it] },
                            accountName = accountName
                        )
                    }
                }
            }
        }

    override fun getAllTransactionsWithDetails(): Flow<List<TransactionWithDetails>> =
        transactionRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    // Fetch all transactions
                    val transactions = Transactions
                        .selectAll()
                        .orderBy(Transactions.date to SortOrder.DESC, Transactions.id to SortOrder.DESC)
                        .map { it.toDomain() }

                    // Fetch lookup tables once
                    val payees = Payees.selectAll().associate { it[Payees.id].value.toLong() to it[Payees.name] }
                    val categories = Categories.selectAll().associate { it[Categories.id].value.toLong() to it[Categories.name] }
                    val accounts = Accounts.selectAll().associate { it[Accounts.id].value.toLong() to it[Accounts.name] }

                    // Map to details
                    transactions.map { txn ->
                        TransactionWithDetails(
                            transaction = txn,
                            payeeName = txn.payeeId?.let { payees[it] },
                            categoryName = txn.categoryId?.let { categories[it] },
                            accountName = accounts[txn.accountId] ?: ""
                        )
                    }
                }
            }
        }

    override fun getTransactionsByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<Transaction>> = transactionRefreshTrigger.map { _ ->
        val tz = TimeZone.currentSystemDefault()
        val startMillis = startDate.atStartOfDayIn(tz).toEpochMilliseconds()
        // Use proper date arithmetic to handle DST (23/25 hour days)
        val endMillis = endDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds() - 1

        withContext(ioDispatcher) {
            transaction(database) {
                Transactions
                    .selectAll().where { (Transactions.date greaterEq startMillis) and (Transactions.date lessEq endMillis) }
                    .orderBy(Transactions.date to SortOrder.DESC, Transactions.id to SortOrder.DESC)
                    .map { it.toDomain() }
            }
        }
    }

    override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        transactionRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    Transactions
                        .selectAll().where { Transactions.categoryId eq categoryId.toInt() }
                        .orderBy(Transactions.date to SortOrder.DESC)
                        .map { it.toDomain() }
                }
            }
        }

    override suspend fun getTransactionById(id: Long): Transaction? = withContext(ioDispatcher) {
        transaction(database) {
            Transactions.selectAll().where { Transactions.id eq id.toInt() }
                .singleOrNull()
                ?.toDomain()
        }
    }

    override suspend fun insertTransaction(transaction: Transaction): Long = withContext(ioDispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        val dateMillis = transaction.date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

        val id = transaction(database) {
            Transactions.insert {
                it[accountId] = transaction.accountId.toInt()
                it[date] = dateMillis
                it[amount] = transaction.amount
                it[payeeId] = transaction.payeeId?.toInt()
                it[categoryId] = transaction.categoryId?.toInt()
                it[memo] = transaction.memo
                it[checkNumber] = transaction.checkNumber
                it[isCleared] = transaction.isCleared
                it[isReconciled] = transaction.isReconciled
                it[transferId] = transaction.transferId?.toInt()
                it[importId] = transaction.importId
                it[transactionType] = transaction.transactionType
                it[sic] = transaction.sic
                it[createdAt] = now
                it[updatedAt] = now
            }[Transactions.id].value.toLong()
        }
        notifyTransactionsChanged()
        id
    }

    override suspend fun batchInsertTransactions(transactions: List<Transaction>): List<Long> = withContext(ioDispatcher) {
        if (transactions.isEmpty()) return@withContext emptyList()

        val now = Clock.System.now().toEpochMilliseconds()

        val ids = transaction(database) {
            transactions.map { transaction ->
                try {
                    val dateMillis = transaction.date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

                    Transactions.insert {
                        it[accountId] = transaction.accountId.toInt()
                        it[date] = dateMillis
                        it[amount] = transaction.amount
                        it[payeeId] = transaction.payeeId?.toInt()
                        it[categoryId] = transaction.categoryId?.toInt()
                        it[memo] = transaction.memo
                        it[checkNumber] = transaction.checkNumber
                        it[isCleared] = transaction.isCleared
                        it[isReconciled] = transaction.isReconciled
                        it[transferId] = transaction.transferId?.toInt()
                        it[importId] = transaction.importId
                        it[transactionType] = transaction.transactionType
                        it[sic] = transaction.sic
                        it[createdAt] = now
                        it[updatedAt] = now
                    }[Transactions.id].value.toLong()
                } catch (e: Exception) {
                    println("Failed to insert transaction with importId=${transaction.importId}: ${e.message}")
                    throw e
                }
            }
        }
        notifyTransactionsChanged()
        ids
    }

    override suspend fun updateTransaction(transaction: Transaction): Unit = withContext(ioDispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        val dateMillis = transaction.date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

        org.jetbrains.exposed.v1.jdbc.transactions.transaction(database) {
            // R21: the edit dialog may hold a category that was deleted elsewhere while it was
            // open. Drop a categoryId that no longer references a live category so the rest of
            // the user's edit still saves instead of failing with an FK violation.
            val safeCategoryId = transaction.categoryId?.takeIf { id ->
                Categories.selectAll().where { Categories.id eq id.toInt() }.any()
            }

            Transactions.update({ Transactions.id eq transaction.id.toInt() }) {
                it[accountId] = transaction.accountId.toInt()
                it[date] = dateMillis
                it[amount] = transaction.amount
                it[payeeId] = transaction.payeeId?.toInt()
                it[categoryId] = safeCategoryId?.toInt()
                it[memo] = transaction.memo
                it[checkNumber] = transaction.checkNumber
                it[isCleared] = transaction.isCleared
                it[isReconciled] = transaction.isReconciled
                it[transferId] = transaction.transferId?.toInt()
                it[importId] = transaction.importId
                it[transactionType] = transaction.transactionType
                it[sic] = transaction.sic
                it[updatedAt] = now
            }
        }
        notifyTransactionsChanged()
    }

    override suspend fun deleteTransaction(id: Long): Unit = withContext(ioDispatcher) {
        org.jetbrains.exposed.v1.jdbc.transactions.transaction(database) {
            val txn = Transactions.selectAll()
                .where { Transactions.id eq id.toInt() }
                .singleOrNull()

            val counterpartId = txn?.get(Transactions.transferId)?.value?.toLong()

            if (counterpartId != null) {
                // Break the bidirectional link first so neither delete trips an FK.
                Transactions.update({ Transactions.id eq id.toInt() }) {
                    it[Transactions.transferId] = null
                }
                Transactions.update({ Transactions.id eq counterpartId.toInt() }) {
                    it[Transactions.transferId] = null
                }

                TransactionTags.deleteWhere { TransactionTags.transactionId eq counterpartId.toInt() }
                SplitItems.deleteWhere { SplitItems.transactionId eq counterpartId.toInt() }
                Transactions.deleteWhere { Transactions.id eq counterpartId.toInt() }
            }

            TransactionTags.deleteWhere { TransactionTags.transactionId eq id.toInt() }
            SplitItems.deleteWhere { SplitItems.transactionId eq id.toInt() }
            Transactions.deleteWhere { Transactions.id eq id.toInt() }
        }
        notifyTransactionsChanged()
    }

    override suspend fun getRecentTransactions(limit: Int): List<TransactionWithDetails> = withContext(ioDispatcher) {
        transaction(database) {
            // Get recent transactions
            val transactions = Transactions
                .selectAll()
                .orderBy(Transactions.date to SortOrder.DESC, Transactions.id to SortOrder.DESC)
                .limit(limit)
                .map { it.toDomain() }

            // Fetch lookup tables
            val payees = Payees.selectAll().associate { it[Payees.id].value.toLong() to it[Payees.name] }
            val categories = Categories.selectAll().associate { it[Categories.id].value.toLong() to it[Categories.name] }
            val accounts = Accounts.selectAll().associate { it[Accounts.id].value.toLong() to it[Accounts.name] }

            transactions.map { txn ->
                val payeeName = txn.payeeId?.let { payees[it] }
                val categoryName = txn.categoryId?.let { categories[it] }
                val accountName = accounts[txn.accountId] ?: ""

                TransactionWithDetails(
                    transaction = txn,
                    payeeName = payeeName,
                    categoryName = categoryName,
                    accountName = accountName
                )
            }
        }
    }

    override suspend fun getTransactionByImportId(importId: String): Transaction? = withContext(ioDispatcher) {
        transaction(database) {
            Transactions
                .selectAll().where { Transactions.importId eq importId }
                .singleOrNull()
                ?.toDomain()
        }
    }

    override suspend fun getExistingImportIds(accountId: Long, importIds: List<String>): Set<String> = withContext(ioDispatcher) {
        if (importIds.isEmpty()) return@withContext emptySet()

        transaction(database) {
            Transactions
                .select(Transactions.importId)
                .where {
                    (Transactions.accountId eq accountId.toInt()) and
                        (Transactions.importId inList importIds)
                }
                .mapNotNull { it[Transactions.importId] }
                .toSet()
        }
    }

    override suspend fun getSplitsByTransactionIds(transactionIds: List<Long>): Map<Long, List<SplitItem>> =
        withContext(ioDispatcher) {
            if (transactionIds.isEmpty()) return@withContext emptyMap()
            transaction(database) { splitsByTransactionIds(transactionIds) }
        }

    override suspend fun getSpendingByCategory(): Map<String, Long> = withContext(ioDispatcher) {
        transaction(database) {
            // Get current month's date range
            val now = Clock.System.now()
            val currentDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
            val startOfMonth = LocalDate(currentDate.year, currentDate.monthNumber, 1)
            val startMillis = startOfMonth.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            val endMillis = now.toEpochMilliseconds()

            // Get expense categories only (not income or transfer categories)
            val expenseCategories = Categories
                .selectAll()
                .where { Categories.type eq "EXPENSE" }
                .associate { it[Categories.id].value.toLong() to it[Categories.name] }

            // Load this month's non-transfer transactions, then expand any split transactions into
            // their per-category lines so spending reflects how the money was actually allocated.
            val transactions = Transactions
                .selectAll().where {
                    (Transactions.date greaterEq startMillis) and
                    (Transactions.date lessEq endMillis) and
                    (Transactions.transferId.isNull())
                }
                .map { it.toDomain() }

            val splitsByTransactionId = splitsByTransactionIds(transactions.map { it.id })

            // Keep only expense-category outflows (negative). Uncategorized and income/transfer-typed
            // lines are excluded here, matching the dashboard's long-standing behavior.
            expandSpendingLines(transactions, splitsByTransactionId)
                .filter { it.amount < 0 && it.categoryId != null && expenseCategories.containsKey(it.categoryId) }
                .groupBy { it.categoryId }
                .mapNotNull { (categoryId, lines) ->
                    val categoryName = categoryId?.let { expenseCategories[it] } ?: return@mapNotNull null
                    categoryName to kotlin.math.abs(lines.sumOf { it.amount })
                }
                .toMap()
        }
    }

    // Split lookup used inside an existing transaction(database) block (no dispatcher/transaction
    // wrapping, unlike the public getSplitsByTransactionIds).
    private fun splitsByTransactionIds(transactionIds: List<Long>): Map<Long, List<SplitItem>> {
        if (transactionIds.isEmpty()) return emptyMap()
        return SplitItems
            .selectAll().where { SplitItems.transactionId inList transactionIds.map { it.toInt() } }
            .map {
                SplitItem(
                    id = it[SplitItems.id].value.toLong(),
                    transactionId = it[SplitItems.transactionId].value.toLong(),
                    categoryId = it[SplitItems.categoryId]?.value?.toLong(),
                    amount = it[SplitItems.amount],
                    memo = it[SplitItems.memo]
                )
            }
            .groupBy { it.transactionId }
    }

    override suspend fun markTransactionReconciled(id: Long, isReconciled: Boolean): Unit = withContext(ioDispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        transaction(database) {
            Transactions.update({ Transactions.id eq id.toInt() }) {
                it[Transactions.isReconciled] = isReconciled
                // Reconciling implies cleared, otherwise getClearedBalance (which sums
                // isCleared rows) would exclude reconciled transactions (N8). Un-reconciling
                // leaves the cleared flag untouched.
                if (isReconciled) it[Transactions.isCleared] = true
                it[Transactions.updatedAt] = now
            }
        }
        notifyTransactionsChanged()
    }

    override suspend fun createTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        date: LocalDate,
        memo: String?,
        fromAccountName: String,
        toAccountName: String
    ): Pair<Long, Long> = withContext(ioDispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        val dateMillis = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val transferMemo = memo ?: "Transfer"

        val (outgoingId, incomingId) = transaction(database) {
            // Create outgoing transaction (negative amount from source)
            val outId = Transactions.insert {
                it[accountId] = fromAccountId.toInt()
                it[Transactions.date] = dateMillis
                it[Transactions.amount] = -amount
                it[payeeId] = null
                it[categoryId] = null
                it[Transactions.memo] = "$transferMemo to $toAccountName"
                it[checkNumber] = null
                it[isCleared] = false
                it[isReconciled] = false
                it[transferId] = null  // Will update after creating incoming
                it[importId] = null
                it[transactionType] = null
                it[sic] = null
                it[createdAt] = now
                it[updatedAt] = now
            }[Transactions.id].value.toLong()

            // Create incoming transaction (positive amount to destination)
            val inId = Transactions.insert {
                it[accountId] = toAccountId.toInt()
                it[Transactions.date] = dateMillis
                it[Transactions.amount] = amount
                it[payeeId] = null
                it[categoryId] = null
                it[Transactions.memo] = "$transferMemo from $fromAccountName"
                it[checkNumber] = null
                it[isCleared] = false
                it[isReconciled] = false
                it[transferId] = outId.toInt()
                it[importId] = null
                it[transactionType] = null
                it[sic] = null
                it[createdAt] = now
                it[updatedAt] = now
            }[Transactions.id].value.toLong()

            // Link outgoing to incoming
            Transactions.update({ Transactions.id eq outId.toInt() }) {
                it[transferId] = inId.toInt()
            }

            Pair(outId, inId)
        }

        notifyTransactionsChanged()
        Pair(outgoingId, incomingId)
    }

    private fun ResultRow.toDomain(): Transaction {
        val tz = TimeZone.currentSystemDefault()
        val localDate = Instant.fromEpochMilliseconds(this[Transactions.date])
            .toLocalDateTime(tz).date

        return Transaction(
            id = this[Transactions.id].value.toLong(),
            accountId = this[Transactions.accountId].value.toLong(),
            date = localDate,
            amount = this[Transactions.amount],
            payeeId = this[Transactions.payeeId]?.value?.toLong(),
            categoryId = this[Transactions.categoryId]?.value?.toLong(),
            memo = this[Transactions.memo],
            checkNumber = this[Transactions.checkNumber],
            isCleared = this[Transactions.isCleared],
            isReconciled = this[Transactions.isReconciled],
            transferId = this[Transactions.transferId]?.value?.toLong(),
            importId = this[Transactions.importId],
            transactionType = this[Transactions.transactionType],
            sic = this[Transactions.sic],
            createdAt = Instant.fromEpochMilliseconds(this[Transactions.createdAt]),
            updatedAt = Instant.fromEpochMilliseconds(this[Transactions.updatedAt])
        )
    }
}
