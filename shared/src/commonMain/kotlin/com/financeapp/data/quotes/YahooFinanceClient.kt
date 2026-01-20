package com.financeapp.data.quotes

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.math.roundToLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock

/**
 * Yahoo Finance API client for stock quotes
 * Uses Yahoo Finance v8 API (free, no API key required)
 */
class YahooFinanceClient(
    private val httpClient: HttpClient
) {
    companion object {
        private const val BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart"
        private const val REQUEST_TIMEOUT_MS = 10000L
        private const val MAX_SYMBOLS_PER_REQUEST = 10
    }

    /**
     * Fetch current quote for a single symbol
     */
    suspend fun getQuote(symbol: String): Result<StockQuote> {
        return try {
            val response = httpClient.get("$BASE_URL/$symbol") {
                parameter("interval", "1d")
                parameter("range", "1d")
            }

            if (response.status == HttpStatusCode.OK) {
                val yahooResponse: YahooChartResponse = response.body()
                val result = yahooResponse.chart.result.firstOrNull()
                    ?: return Result.failure(Exception("No data for symbol: $symbol"))

                val meta = result.meta
                val quote = StockQuote(
                    symbol = symbol,
                    price = (meta.regularMarketPrice * 100).roundToLong(), // Convert to cents with rounding
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    currency = meta.currency
                )
                Result.success(quote)
            } else {
                Result.failure(Exception("HTTP ${response.status.value}: Failed to fetch $symbol"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to fetch quote for $symbol: ${e.message}", e))
        }
    }

    /**
     * Batch fetch quotes for multiple symbols
     * Splits into batches if needed to avoid rate limits
     */
    suspend fun getQuotes(symbols: List<String>): Map<String, Result<StockQuote>> {
        val results = mutableMapOf<String, Result<StockQuote>>()

        symbols.chunked(MAX_SYMBOLS_PER_REQUEST).forEach { batch ->
            batch.forEach { symbol ->
                results[symbol] = getQuote(symbol)
            }
            // Small delay between batches to avoid rate limiting
            kotlinx.coroutines.delay(100)
        }

        return results
    }

    /**
     * Fetch historical prices for a symbol
     */
    suspend fun getPriceHistory(symbol: String, days: Int): Result<List<HistoricalPrice>> {
        return try {
            val response = httpClient.get("$BASE_URL/$symbol") {
                parameter("interval", "1d")
                parameter("range", "${days}d")
            }

            if (response.status == HttpStatusCode.OK) {
                val yahooResponse: YahooChartResponse = response.body()
                val result = yahooResponse.chart.result.firstOrNull()
                    ?: return Result.failure(Exception("No data for symbol: $symbol"))

                val timestamps = result.timestamp ?: emptyList()
                val quotes = result.indicators?.quote?.firstOrNull()
                val closes = quotes?.close ?: emptyList()

                val history = timestamps.zip(closes).mapNotNull { (timestamp, close) ->
                    close?.let {
                        HistoricalPrice(
                            symbol = symbol,
                            date = timestamp * 1000, // Convert to milliseconds
                            price = (it * 100).roundToLong() // Convert to cents with rounding
                        )
                    }
                }

                Result.success(history)
            } else {
                Result.failure(Exception("HTTP ${response.status.value}: Failed to fetch history for $symbol"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to fetch history for $symbol: ${e.message}", e))
        }
    }
}

/**
 * Current stock quote data
 */
data class StockQuote(
    val symbol: String,
    val price: Long, // in cents
    val timestamp: Long,
    val currency: String = "USD"
)

/**
 * Historical price data point
 */
data class HistoricalPrice(
    val symbol: String,
    val date: Long, // timestamp in milliseconds
    val price: Long  // in cents
)

// Yahoo Finance API response models
@Serializable
private data class YahooChartResponse(
    val chart: YahooChart
)

@Serializable
private data class YahooChart(
    val result: List<YahooChartResult>
)

@Serializable
private data class YahooChartResult(
    val meta: YahooMeta,
    val timestamp: List<Long>? = null,
    val indicators: YahooIndicators? = null
)

@Serializable
private data class YahooMeta(
    @SerialName("regularMarketPrice")
    val regularMarketPrice: Double,
    val currency: String
)

@Serializable
private data class YahooIndicators(
    val quote: List<YahooQuote>? = null
)

@Serializable
private data class YahooQuote(
    val close: List<Double?>? = null
)
