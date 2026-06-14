package com.financeapp.domain.repository

import com.financeapp.domain.model.Account
import com.financeapp.domain.model.AccountWithBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface AccountRepository {
    fun getAllAccounts(): Flow<List<Account>>
    fun getAccountsWithBalances(): Flow<List<AccountWithBalance>>
    suspend fun getAccountById(id: Long): Account?
    suspend fun insertAccount(account: Account): Long
    suspend fun updateAccount(account: Account)
    suspend fun deleteAccount(id: Long)
    suspend fun getAccountBalance(id: Long): Long
    suspend fun getClearedBalance(id: Long): Long
    suspend fun getReconciledBalance(id: Long): Long
    suspend fun insertReconciliation(accountId: Long, statementDate: LocalDate, statementBalance: Long, isCompleted: Boolean): Long

    /**
     * Atomically marks [transactionIds] reconciled (and cleared) and records the completed
     * reconciliation session in a single transaction, so a failure can't leave transactions
     * reconciled with no session record (or vice versa). Returns the new session id.
     */
    suspend fun completeReconciliation(
        accountId: Long,
        statementDate: LocalDate,
        statementBalance: Long,
        transactionIds: List<Long>
    ): Long

    fun notifyBalancesChanged()
}
