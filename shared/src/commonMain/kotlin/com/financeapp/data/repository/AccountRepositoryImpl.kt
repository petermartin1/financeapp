package com.financeapp.data.repository

import com.financeapp.db.schema.Accounts
import com.financeapp.db.schema.ConnectedAccounts
import com.financeapp.db.schema.Holdings
import com.financeapp.db.schema.HoldingLots
import com.financeapp.db.schema.ReconciliationSessions
import com.financeapp.db.schema.ScheduledTransactions
import com.financeapp.db.schema.SplitItems
import com.financeapp.db.schema.TransactionTags
import com.financeapp.db.schema.TransactionTemplates
import com.financeapp.db.schema.Transactions
import com.financeapp.domain.model.Account
import com.financeapp.domain.model.AccountType
import com.financeapp.domain.model.AccountWithBalance
import com.financeapp.domain.repository.AccountRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class AccountRepositoryImpl(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AccountRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val accountsFlow = MutableStateFlow<List<Account>>(emptyList())
    private val balanceRefreshTrigger = MutableStateFlow(0L)

    init {
        scope.launch {
            refreshAccounts()
        }
    }

    private suspend fun refreshAccounts() {
        val accounts = withContext(ioDispatcher) {
            transaction(database) {
                Accounts.selectAll().where { Accounts.isActive eq true }
                    .orderBy(Accounts.name to SortOrder.ASC)
                    .map { it.toDomain() }
            }
        }
        accountsFlow.value = accounts
    }

    override fun getAllAccounts(): Flow<List<Account>> = accountsFlow

    override fun getAccountsWithBalances(): Flow<List<AccountWithBalance>> = flow {
        // Combine both account changes AND balance refresh trigger
        kotlinx.coroutines.flow.combine(
            getAllAccounts(),
            balanceRefreshTrigger
        ) { accounts, _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    accounts.map { account ->
                        // Calculate balance for this account
                        val balance = Transactions
                            .select(Transactions.amount.sum())
                            .where { Transactions.accountId eq account.id.toInt() }
                            .singleOrNull()
                            ?.get(Transactions.amount.sum()) ?: 0L

                        // Calculate cleared balance
                        val clearedBalance = Transactions
                            .select(Transactions.amount.sum())
                            .where {
                                (Transactions.accountId eq account.id.toInt()) and
                                (Transactions.isCleared eq true)
                            }
                            .singleOrNull()
                            ?.get(Transactions.amount.sum()) ?: 0L

                        AccountWithBalance(
                            account = account,
                            balance = balance,
                            clearedBalance = clearedBalance
                        )
                    }
                }
            }
        }.collect { accountsWithBalances ->
            emit(accountsWithBalances)
        }
    }

    override suspend fun getAccountById(id: Long): Account? = withContext(ioDispatcher) {
        transaction(database) {
            Accounts.selectAll().where { Accounts.id eq id.toInt() }
                .singleOrNull()
                ?.toDomain()
        }
    }

    override suspend fun insertAccount(account: Account): Long = withContext(ioDispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = transaction(database) {
            Accounts.insert {
                it[name] = account.name
                it[type] = account.type.name
                it[institution] = account.institution
                it[accountNumber] = account.accountNumber
                it[currency] = account.currency
                it[isActive] = account.isActive
                it[createdAt] = now
                it[updatedAt] = now
            }[Accounts.id].value.toLong()
        }
        refreshAccounts()
        notifyBalancesChanged()
        id
    }

    override suspend fun updateAccount(account: Account): Unit = withContext(ioDispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        transaction(database) {
            Accounts.update({ Accounts.id eq account.id.toInt() }) {
                it[name] = account.name
                it[type] = account.type.name
                it[institution] = account.institution
                it[accountNumber] = account.accountNumber
                it[currency] = account.currency
                it[isActive] = account.isActive
                it[updatedAt] = now
            }
        }
        refreshAccounts()
        notifyBalancesChanged()
    }

    override suspend fun deleteAccount(id: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            // Get all transaction IDs in this account
            val transactionIds = Transactions
                .select(Transactions.id)
                .where { Transactions.accountId eq id.toInt() }
                .map { it[Transactions.id].value }

            // Clear transferId on counterpart transactions in OTHER accounts
            // (transactions that point to transactions being deleted)
            if (transactionIds.isNotEmpty()) {
                Transactions.update({
                    Transactions.transferId inList transactionIds
                }) {
                    it[transferId] = null
                }
            }

            // Delete transaction tags and split items for transactions in this account
            for (txnId in transactionIds) {
                TransactionTags.deleteWhere { TransactionTags.transactionId eq txnId }
                SplitItems.deleteWhere { SplitItems.transactionId eq txnId }
            }

            // Delete all transactions for this account
            Transactions.deleteWhere { Transactions.accountId eq id.toInt() }

            // Delete holding lots for holdings in this account
            val holdingIds = Holdings
                .select(Holdings.id)
                .where { Holdings.accountId eq id.toInt() }
                .map { it[Holdings.id].value }

            for (holdingId in holdingIds) {
                HoldingLots.deleteWhere { HoldingLots.holdingId eq holdingId }
            }

            // Delete holdings for this account
            Holdings.deleteWhere { Holdings.accountId eq id.toInt() }

            // Delete scheduled transactions for this account
            ScheduledTransactions.deleteWhere { ScheduledTransactions.accountId eq id.toInt() }

            // Nullify account references in templates (don't delete templates entirely)
            TransactionTemplates.update({ TransactionTemplates.accountId eq id.toInt() }) {
                it[accountId] = null
            }

            // Delete reconciliation sessions for this account
            ReconciliationSessions.deleteWhere { ReconciliationSessions.accountId eq id.toInt() }

            // Delete connected accounts for this account
            ConnectedAccounts.deleteWhere { ConnectedAccounts.localAccountId eq id.toInt() }

            // Finally delete the account
            Accounts.deleteWhere { Accounts.id eq id.toInt() }
        }
        refreshAccounts()
        notifyBalancesChanged()
    }

    override suspend fun getAccountBalance(id: Long): Long = withContext(ioDispatcher) {
        transaction(database) {
            Transactions
                .select(Transactions.amount.sum())
                .where { Transactions.accountId eq id.toInt() }
                .singleOrNull()
                ?.get(Transactions.amount.sum()) ?: 0L
        }
    }

    override suspend fun getClearedBalance(id: Long): Long = withContext(ioDispatcher) {
        transaction(database) {
            Transactions
                .select(Transactions.amount.sum())
                .where {
                    (Transactions.accountId eq id.toInt()) and (Transactions.isCleared eq true)
                }
                .singleOrNull()
                ?.get(Transactions.amount.sum()) ?: 0L
        }
    }

    override suspend fun getReconciledBalance(id: Long): Long = withContext(ioDispatcher) {
        transaction(database) {
            Transactions
                .select(Transactions.amount.sum())
                .where {
                    (Transactions.accountId eq id.toInt()) and (Transactions.isReconciled eq true)
                }
                .singleOrNull()
                ?.get(Transactions.amount.sum()) ?: 0L
        }
    }

    override suspend fun insertReconciliation(accountId: Long, statementDate: LocalDate, statementBalance: Long, isCompleted: Boolean): Long = withContext(ioDispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        val statementDateMillis = statementDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val completedAtMillis = if (isCompleted) now else null

        transaction(database) {
            ReconciliationSessions.insert {
                it[ReconciliationSessions.accountId] = accountId.toInt()
                it[ReconciliationSessions.statementDate] = statementDateMillis
                it[ReconciliationSessions.statementBalance] = statementBalance
                it[ReconciliationSessions.isCompleted] = isCompleted
                it[ReconciliationSessions.completedAt] = completedAtMillis
                it[ReconciliationSessions.createdAt] = now
            }[ReconciliationSessions.id].value.toLong()
        }
    }

    override fun notifyBalancesChanged() {
        balanceRefreshTrigger.value += 1
    }

    fun cleanup() {
        scope.cancel()
    }

    private fun ResultRow.toDomain(): Account {
        return Account(
            id = this[Accounts.id].value.toLong(),
            name = this[Accounts.name],
            type = AccountType.valueOf(this[Accounts.type]),
            institution = this[Accounts.institution],
            accountNumber = this[Accounts.accountNumber],
            currency = this[Accounts.currency],
            isActive = this[Accounts.isActive],
            createdAt = Instant.fromEpochMilliseconds(this[Accounts.createdAt]),
            updatedAt = Instant.fromEpochMilliseconds(this[Accounts.updatedAt])
        )
    }
}
