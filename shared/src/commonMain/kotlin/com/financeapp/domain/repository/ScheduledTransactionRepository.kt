package com.financeapp.domain.repository

import com.financeapp.domain.model.ScheduledTransaction
import com.financeapp.domain.model.ScheduledTransactionWithDetails
import kotlinx.coroutines.flow.Flow

interface ScheduledTransactionRepository {
    fun getAllScheduledTransactions(): Flow<List<ScheduledTransactionWithDetails>>

    suspend fun getScheduledTransactionById(id: Long): ScheduledTransaction?

    suspend fun getDueScheduledTransactions(currentDateMillis: Long): List<ScheduledTransaction>

    suspend fun insertScheduledTransaction(scheduledTransaction: ScheduledTransaction): Long

    suspend fun updateScheduledTransactionNextDate(id: Long, nextDateMillis: Long)

    suspend fun updateScheduledTransactionActive(id: Long, isActive: Boolean)

    suspend fun deleteScheduledTransaction(id: Long)

    // Notification method for reactive updates
    fun notifyScheduledTransactionsChanged()
}
