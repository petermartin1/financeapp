package com.financeapp.data.repository

import com.financeapp.db.schema.Accounts
import com.financeapp.db.schema.ReconciliationSessions
import com.financeapp.db.schema.Transactions
import com.financeapp.domain.model.Account
import com.financeapp.domain.model.AccountType
import com.financeapp.domain.model.AccountWithBalance
import com.financeapp.domain.repository.AccountRepository
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
            // Delete all transactions for this account first
            Transactions.deleteWhere { accountId eq id.toInt() }
            // Then delete the account
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
