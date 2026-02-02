package com.financeapp.ui.investments

import com.financeapp.domain.model.*
import com.financeapp.domain.repository.PerformanceRepository
import com.financeapp.domain.service.PriceRefreshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Performance tab in InvestmentScreen
 */
class PerformanceTabViewModel(
    private val performanceRepository: PerformanceRepository,
    private val priceRefreshService: PriceRefreshService
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _performanceSummary = MutableStateFlow<PerformanceSummary?>(null)
    val performanceSummary: StateFlow<PerformanceSummary?> = _performanceSummary.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.ONE_MONTH)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _performanceMetrics = MutableStateFlow<PerformanceMetrics?>(null)
    val performanceMetrics: StateFlow<PerformanceMetrics?> = _performanceMetrics.asStateFlow()

    private val _chartData = MutableStateFlow<PerformanceChartData?>(null)
    val chartData: StateFlow<PerformanceChartData?> = _chartData.asStateFlow()

    private val _allHoldingPerformance = MutableStateFlow<List<HoldingPerformance>>(emptyList())
    val allHoldingPerformance: StateFlow<List<HoldingPerformance>> = _allHoldingPerformance.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val isRefreshing: StateFlow<Boolean> = priceRefreshService.isRefreshing
    val lastRefreshTime: StateFlow<Long?> = priceRefreshService.lastRefreshTime
    val lastError: StateFlow<String?> = priceRefreshService.lastError

    init {
        loadPerformanceData()
    }

    fun loadPerformanceData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Load performance summary
                val summary = performanceRepository.getPerformanceSummary()
                _performanceSummary.value = summary

                // Load all holding performances once to avoid long-lived collectors
                _allHoldingPerformance.value = performanceRepository.getAllHoldingPerformance().first()

                // Load performance metrics and chart data sequentially
                loadTimeRangeData()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load performance data"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectTimeRange(timeRange: TimeRange) {
        if (_selectedTimeRange.value != timeRange) {
            _selectedTimeRange.value = timeRange
            viewModelScope.launch {
                loadTimeRangeData()
            }
        }
    }

    /**
     * Loads both performance metrics and chart data sequentially to avoid race conditions.
     */
    private suspend fun loadTimeRangeData() {
        try {
            val metrics = performanceRepository.calculatePerformanceMetrics(_selectedTimeRange.value)
            _performanceMetrics.value = metrics

            val data = performanceRepository.getPerformanceChartData(_selectedTimeRange.value)
            _chartData.value = data
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to load time range data"
        }
    }

    fun refreshPrices() {
        viewModelScope.launch {
            priceRefreshService.refreshPrices()
            // Reload performance data after price refresh
            loadPerformanceData()
        }
    }

    fun createSnapshot() {
        viewModelScope.launch {
            try {
                performanceRepository.createPortfolioSnapshot(SnapshotType.DAILY)
                loadPerformanceData()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create snapshot"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun onDispose() {
        // Cleanup if needed
    }
}
