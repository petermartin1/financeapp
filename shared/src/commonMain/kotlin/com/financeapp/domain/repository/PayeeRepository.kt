package com.financeapp.domain.repository

import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.PayeeWithStats
import kotlinx.coroutines.flow.Flow

interface PayeeRepository {
    fun getAllPayees(): Flow<List<Payee>>
    fun getPayeesWithStats(): Flow<List<PayeeWithStats>>
    suspend fun getPayeeById(id: Long): Payee?
    suspend fun getPayeeByName(name: String): Payee?
    suspend fun getPayeesByNames(names: List<String>): Map<String, Payee>
    suspend fun insertPayee(payee: Payee): Long
    suspend fun batchInsertPayees(payees: List<Payee>): Map<String, Long>
    suspend fun updatePayee(payee: Payee)
    suspend fun deletePayee(id: Long)
    suspend fun mergePayees(sourceId: Long, targetId: Long)

    /**
     * Notify that payees have changed, triggering UI refresh
     */
    fun notifyPayeesChanged()
}
