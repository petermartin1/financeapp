package com.financeapp.domain.repository

import com.financeapp.domain.model.Holding
import com.financeapp.domain.model.HoldingWithPrice
import com.financeapp.domain.model.SecurityPrice
import kotlinx.coroutines.flow.Flow

interface InvestmentRepository {
    fun getPortfolio(): Flow<List<HoldingWithPrice>>
    fun getHoldingsByAccount(accountId: Long): Flow<List<Holding>>
    suspend fun getHoldingById(id: Long): Holding?
    suspend fun getAllHoldings(): List<Holding>
    suspend fun insertHolding(holding: Holding): Long
    suspend fun updateHolding(holding: Holding)
    suspend fun deleteHolding(id: Long)
    suspend fun getLatestPrice(symbol: String): SecurityPrice?
    suspend fun updatePrice(symbol: String, price: Long, date: Long)
    suspend fun getPriceHistory(symbol: String, limit: Int): List<SecurityPrice>

    // Notification methods for reactive updates
    fun notifyHoldingsChanged()
    fun notifyPricesChanged()
}
