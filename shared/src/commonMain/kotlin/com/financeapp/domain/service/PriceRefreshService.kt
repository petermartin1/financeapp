package com.financeapp.domain.service

import com.financeapp.domain.repository.InvestmentRepository
import com.financeapp.domain.repository.QuoteRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Service for automatically refreshing stock prices
 */
class PriceRefreshService(
    private val quoteRepository: QuoteRepository,
    private val investmentRepository: InvestmentRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private var refreshJob: Job? = null

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastRefreshTime = MutableStateFlow<Long?>(null)
    val lastRefreshTime: StateFlow<Long?> = _lastRefreshTime.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Start automatic price refresh with the specified interval
     * @param intervalMinutes Refresh interval in minutes (default: 15 minutes)
     */
    fun startAutoRefresh(intervalMinutes: Int = 15) {
        stopAutoRefresh()

        refreshJob = scope.launch {
            while (isActive) {
                refreshPrices()
                delay(intervalMinutes * 60 * 1000L) // Convert to milliseconds
            }
        }
    }

    /**
     * Stop automatic price refresh
     */
    fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /**
     * Manually trigger a price refresh
     */
    suspend fun refreshPrices(): RefreshResult {
        if (_isRefreshing.value) {
            return RefreshResult.AlreadyInProgress
        }

        _isRefreshing.value = true
        _lastError.value = null

        return try {
            val results = quoteRepository.refreshAllPrices()

            val successCount = results.values.count { it.isSuccess }
            val failureCount = results.values.count { it.isFailure }

            if (failureCount > 0) {
                val errorMessages = results.filter { it.value.isFailure }
                    .map { "${it.key}: ${it.value.exceptionOrNull()?.message}" }
                    .take(3) // Only keep first 3 errors
                    .joinToString(", ")
                _lastError.value = errorMessages
            }

            val now = Clock.System.now().toEpochMilliseconds()
            _lastRefreshTime.value = now
            quoteRepository.setLastRefreshTime(now)

            RefreshResult.Success(successCount, failureCount)
        } catch (e: Exception) {
            _lastError.value = e.message
            RefreshResult.Failure(e)
        } finally {
            _isRefreshing.value = false
        }
    }

    /**
     * Refresh price for a single symbol
     */
    suspend fun refreshSymbol(symbol: String): Result<Unit> {
        return try {
            val result = quoteRepository.refreshPrice(symbol)
            result.map { }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get time since last refresh in minutes
     */
    suspend fun getMinutesSinceLastRefresh(): Long? {
        val lastRefresh = quoteRepository.getLastRefreshTime() ?: return null
        val now = Clock.System.now().toEpochMilliseconds()
        return (now - lastRefresh) / (60 * 1000L)
    }

    /**
     * Check if prices need refreshing based on age
     * @param maxAgeMinutes Maximum age in minutes before refresh is needed
     */
    suspend fun needsRefresh(maxAgeMinutes: Int = 15): Boolean {
        val minutesSinceRefresh = getMinutesSinceLastRefresh() ?: return true
        return minutesSinceRefresh >= maxAgeMinutes
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        stopAutoRefresh()
        scope.cancel()
    }
}

/**
 * Result of a price refresh operation
 */
sealed class RefreshResult {
    /**
     * Refresh completed successfully
     * @param successCount Number of symbols successfully refreshed
     * @param failureCount Number of symbols that failed to refresh
     */
    data class Success(val successCount: Int, val failureCount: Int) : RefreshResult()

    /**
     * Refresh failed with an exception
     */
    data class Failure(val exception: Exception) : RefreshResult()

    /**
     * Refresh already in progress
     */
    data object AlreadyInProgress : RefreshResult()
}
