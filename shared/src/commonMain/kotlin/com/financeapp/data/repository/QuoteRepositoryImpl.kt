package com.financeapp.data.repository

import com.financeapp.data.quotes.HistoricalPrice
import com.financeapp.data.quotes.StockQuote
import com.financeapp.data.quotes.YahooFinanceClient
import com.financeapp.domain.repository.InvestmentRepository
import com.financeapp.domain.repository.QuoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Implementation of QuoteRepository using Yahoo Finance API
 */
class QuoteRepositoryImpl(
    private val yahooFinanceClient: YahooFinanceClient,
    private val investmentRepository: InvestmentRepository,
    private val preferencesStore: PreferencesStore
) : QuoteRepository {

    companion object {
        private const val PREF_KEY_LAST_REFRESH = "last_quote_refresh"
    }

    override suspend fun refreshAllPrices(): Map<String, Result<StockQuote>> = withContext(Dispatchers.IO) {
        // Get all unique symbols from holdings
        val holdings = investmentRepository.getAllHoldings()
        val symbols = holdings.map { it.symbol }.distinct()

        if (symbols.isEmpty()) {
            return@withContext emptyMap()
        }

        // Fetch quotes
        val quotes = yahooFinanceClient.getQuotes(symbols)

        // Update database for successful fetches
        val now = Clock.System.now().toEpochMilliseconds()
        quotes.forEach { (symbol, result) ->
            result.onSuccess { quote ->
                investmentRepository.updatePrice(symbol, quote.price, now)
            }
        }

        // Update last refresh time if at least one succeeded
        if (quotes.values.any { it.isSuccess }) {
            setLastRefreshTime(now)
        }

        quotes
    }

    override suspend fun refreshPrice(symbol: String): Result<StockQuote> = withContext(Dispatchers.IO) {
        val result = yahooFinanceClient.getQuote(symbol)

        result.onSuccess { quote ->
            val now = Clock.System.now().toEpochMilliseconds()
            investmentRepository.updatePrice(symbol, quote.price, now)
        }

        result
    }

    override suspend fun fetchPriceHistory(symbol: String, days: Int): Result<List<HistoricalPrice>> =
        withContext(Dispatchers.IO) {
            yahooFinanceClient.getPriceHistory(symbol, days)
        }

    override suspend fun getLastRefreshTime(): Long? = withContext(Dispatchers.IO) {
        preferencesStore.getString(PREF_KEY_LAST_REFRESH)?.toLongOrNull()
    }

    override suspend fun setLastRefreshTime(timestamp: Long) = withContext(Dispatchers.IO) {
        preferencesStore.putString(PREF_KEY_LAST_REFRESH, timestamp.toString())
    }
}
