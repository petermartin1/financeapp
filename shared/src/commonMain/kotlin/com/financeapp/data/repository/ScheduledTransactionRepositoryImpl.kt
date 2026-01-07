package com.financeapp.data.repository

import com.financeapp.db.schema.*
import com.financeapp.domain.model.ScheduledTransaction
import com.financeapp.domain.model.ScheduledTransactionWithDetails
import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.repository.ScheduledTransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class ScheduledTransactionRepositoryImpl(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ScheduledTransactionRepository {

    // Trigger for reactive updates
    private val scheduledTransactionRefreshTrigger = MutableStateFlow(0L)

    override fun notifyScheduledTransactionsChanged() {
        scheduledTransactionRefreshTrigger.value += 1
    }

    override fun getAllScheduledTransactions(): Flow<List<ScheduledTransactionWithDetails>> =
        scheduledTransactionRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    ScheduledTransactions
                        .join(Accounts, JoinType.INNER, ScheduledTransactions.accountId, Accounts.id)
                        .selectAll()
                        .map { it.toScheduledTransactionWithDetails() }
                }
            }
        }

    override suspend fun getScheduledTransactionById(id: Long): ScheduledTransaction? = withContext(Dispatchers.IO) {
        transaction(database) {
            ScheduledTransactions
                .selectAll().where { ScheduledTransactions.id eq id.toInt() }
                .singleOrNull()
                ?.toScheduledTransaction()
        }
    }

    override suspend fun getDueScheduledTransactions(currentDateMillis: Long): List<ScheduledTransaction> = withContext(Dispatchers.IO) {
        transaction(database) {
            ScheduledTransactions
                .selectAll().where {
                    (ScheduledTransactions.isActive eq true) and
                    (ScheduledTransactions.nextDate lessEq currentDateMillis)
                }
                .map { it.toScheduledTransaction() }
        }
    }

    override suspend fun insertScheduledTransaction(scheduledTransaction: ScheduledTransaction): Long = withContext(ioDispatcher) {
        val id = transaction(database) {
            ScheduledTransactions.insert {
                it[accountId] = scheduledTransaction.accountId.toInt()
                it[payeeId] = scheduledTransaction.payeeId?.toInt()
                it[categoryId] = scheduledTransaction.categoryId?.toInt()
                it[amount] = scheduledTransaction.amount
                it[memo] = scheduledTransaction.memo
                it[frequency] = scheduledTransaction.frequency.name
                it[nextDate] = scheduledTransaction.nextDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                it[endDate] = scheduledTransaction.endDate?.atStartOfDayIn(TimeZone.currentSystemDefault())?.toEpochMilliseconds()
                it[isActive] = scheduledTransaction.isActive
            }[ScheduledTransactions.id].value.toLong()
        }
        notifyScheduledTransactionsChanged()
        id
    }

    override suspend fun updateScheduledTransactionNextDate(id: Long, nextDateMillis: Long): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            ScheduledTransactions.update({ ScheduledTransactions.id eq id.toInt() }) {
                it[nextDate] = nextDateMillis
            }
        }
        notifyScheduledTransactionsChanged()
    }

    override suspend fun updateScheduledTransactionActive(id: Long, isActive: Boolean): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            ScheduledTransactions.update({ ScheduledTransactions.id eq id.toInt() }) {
                it[ScheduledTransactions.isActive] = isActive
            }
        }
        notifyScheduledTransactionsChanged()
    }

    override suspend fun deleteScheduledTransaction(id: Long): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            ScheduledTransactions.deleteWhere { ScheduledTransactions.id eq id.toInt() }
        }
        notifyScheduledTransactionsChanged()
    }

    private fun ResultRow.toScheduledTransaction(): ScheduledTransaction {
        val nextDateMillis = this[ScheduledTransactions.nextDate]
        val endDateMillis = this[ScheduledTransactions.endDate]

        return ScheduledTransaction(
            id = this[ScheduledTransactions.id].value.toLong(),
            accountId = this[ScheduledTransactions.accountId].value.toLong(),
            payeeId = this[ScheduledTransactions.payeeId]?.value?.toLong(),
            categoryId = this[ScheduledTransactions.categoryId]?.value?.toLong(),
            amount = this[ScheduledTransactions.amount],
            memo = this[ScheduledTransactions.memo],
            frequency = TransactionFrequency.valueOf(this[ScheduledTransactions.frequency]),
            nextDate = Instant.fromEpochMilliseconds(nextDateMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date,
            endDate = endDateMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date },
            isActive = this[ScheduledTransactions.isActive]
        )
    }

    private fun ResultRow.toScheduledTransactionWithDetails(): ScheduledTransactionWithDetails {
        val scheduled = this.toScheduledTransaction()
        val accountName = this[Accounts.name]

        // Load payee name if exists
        val payeeName = scheduled.payeeId?.let { payeeId ->
            transaction(database) {
                Payees
                    .selectAll().where { Payees.id eq payeeId.toInt() }
                    .singleOrNull()
                    ?.get(Payees.name)
            }
        }

        // Load category name if exists
        val categoryName = scheduled.categoryId?.let { categoryId ->
            transaction(database) {
                Categories
                    .selectAll().where { Categories.id eq categoryId.toInt() }
                    .singleOrNull()
                    ?.get(Categories.name)
            }
        }

        return ScheduledTransactionWithDetails(
            scheduled = scheduled,
            accountName = accountName,
            payeeName = payeeName,
            categoryName = categoryName
        )
    }
}
