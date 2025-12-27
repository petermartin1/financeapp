package com.financeapp.domain.repository

import com.financeapp.data.quotes.HistoricalPrice
import com.financeapp.data.quotes.StockQuote

/**
 * Repository for fetching stock quotes from external APIs
 */
interface QuoteRepository {
    /**
     * Fetch and update prices for all holdings
     * Returns map of symbol -> success/failure result
     */
    suspend fun refreshAllPrices(): Map<String, Result<StockQuote>>

    /**
     * Fetch and update price for a single symbol
     */
    suspend fun refreshPrice(symbol: String): Result<StockQuote>

    /**
     * Fetch historical prices for a symbol
     */
    suspend fun fetchPriceHistory(symbol: String, days: Int): Result<List<HistoricalPrice>>

    /**
     * Get timestamp of last successful refresh
     */
    suspend fun getLastRefreshTime(): Long?

    /**
     * Update last refresh timestamp
     */
    suspend fun setLastRefreshTime(timestamp: Long)
}
