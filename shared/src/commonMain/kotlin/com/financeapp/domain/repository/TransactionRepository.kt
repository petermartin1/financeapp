package com.financeapp.domain.repository

import com.financeapp.domain.model.SplitItem
import com.financeapp.domain.model.Transaction
import com.financeapp.domain.model.TransactionWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface TransactionRepository {
    fun getTransactionsByAccount(accountId: Long): Flow<List<Transaction>>
    fun getTransactionsWithDetailsByAccount(accountId: Long): Flow<List<TransactionWithDetails>>
    fun getAllTransactionsWithDetails(): Flow<List<TransactionWithDetails>>
    fun getTransactionsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>>
    fun getTransactionsWithDetailsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TransactionWithDetails>>
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>
    suspend fun getTransactionById(id: Long): Transaction?
    suspend fun insertTransaction(transaction: Transaction): Long
    suspend fun batchInsertTransactions(transactions: List<Transaction>): List<Long>
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: Long)
    suspend fun getRecentTransactions(limit: Int): List<TransactionWithDetails>
    suspend fun getTransactionByImportId(importId: String): Transaction?
    suspend fun getExistingImportIds(accountId: Long, importIds: List<String>): Set<String>

    /**
     * Returns the split items for the given transaction ids, keyed by transaction id. Transactions
     * with no splits are omitted from the map. Used to make category spending reflect splits.
     */
    suspend fun getSplitsByTransactionIds(transactionIds: List<Long>): Map<Long, List<SplitItem>>

    suspend fun getSpendingByCategory(): Map<String, Long>
    suspend fun markTransactionReconciled(id: Long, isReconciled: Boolean)

    /**
     * Create a transfer between two accounts atomically.
     * Creates both sides of the transfer in a single transaction.
     *
     * @param fromAccountId Source account
     * @param toAccountId Destination account
     * @param amount Amount in cents (positive value)
     * @param date Transaction date
     * @param memo Optional memo
     * @param fromAccountName Name of source account (for memo)
     * @param toAccountName Name of destination account (for memo)
     * @return Pair of (outgoingId, incomingId)
     */
    suspend fun createTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        date: LocalDate,
        memo: String?,
        fromAccountName: String,
        toAccountName: String
    ): Pair<Long, Long>

    /**
     * Notify that transactions have changed, triggering UI refresh
     */
    fun notifyTransactionsChanged()
}
