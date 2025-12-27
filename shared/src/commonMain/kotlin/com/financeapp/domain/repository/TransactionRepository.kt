package com.financeapp.domain.repository

import com.financeapp.domain.model.Transaction
import com.financeapp.domain.model.TransactionWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface TransactionRepository {
    fun getTransactionsByAccount(accountId: Long): Flow<List<Transaction>>
    fun getTransactionsWithDetailsByAccount(accountId: Long): Flow<List<TransactionWithDetails>>
    fun getAllTransactionsWithDetails(): Flow<List<TransactionWithDetails>>
    fun getTransactionsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>>
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>
    suspend fun getTransactionById(id: Long): Transaction?
    suspend fun insertTransaction(transaction: Transaction): Long
    suspend fun batchInsertTransactions(transactions: List<Transaction>): List<Long>
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: Long)
    suspend fun getRecentTransactions(limit: Int): List<TransactionWithDetails>
    suspend fun getTransactionByImportId(importId: String): Transaction?
    suspend fun getExistingImportIds(importIds: List<String>): Set<String>
    suspend fun getSpendingByCategory(): Map<String, Long>
    suspend fun markTransactionReconciled(id: Long, isReconciled: Boolean)

    /**
     * Notify that transactions have changed, triggering UI refresh
     */
    fun notifyTransactionsChanged()
}
