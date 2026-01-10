package com.financeapp.ui.investments

import com.financeapp.domain.model.*
import com.financeapp.domain.repository.InvestmentRepository
import com.financeapp.domain.repository.PerformanceRepository
import com.financeapp.domain.service.PriceRefreshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * ViewModel for holding detail screen showing performance metrics
 */
class HoldingDetailViewModel(
    val holdingId: Long,
    private val investmentRepository: InvestmentRepository,
    private val performanceRepository: PerformanceRepository,
    private val priceRefreshService: PriceRefreshService
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _holdingPerformance = MutableStateFlow<HoldingPerformance?>(null)
    val holdingPerformance: StateFlow<HoldingPerformance?> = _holdingPerformance.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.ONE_MONTH)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _chartData = MutableStateFlow<PerformanceChartData?>(null)
    val chartData: StateFlow<PerformanceChartData?> = _chartData.asStateFlow()

    private val _dividends = MutableStateFlow<List<DividendEvent>>(emptyList())
    val dividends: StateFlow<List<DividendEvent>> = _dividends.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lots = MutableStateFlow<List<HoldingLot>>(emptyList())
    val lots: StateFlow<List<HoldingLot>> = _lots.asStateFlow()

    val lotAnalytics: StateFlow<List<LotAnalytics>> =
        combine(_lots, _holdingPerformance) { lots, performance ->
            val currentPrice = performance?.currentPrice
            lots.map { lot ->
                val marketValue = currentPrice?.let { (lot.shares * it).toLong() }
                val gainLoss = marketValue?.minus(lot.costBasis)
                val percent = if (gainLoss != null && lot.costBasis != 0L) {
                    (gainLoss.toDouble() / lot.costBasis) * 100
                } else null
                LotAnalytics(
                    lot = lot,
                    marketValue = marketValue,
                    gainLoss = gainLoss,
                    gainLossPercent = percent
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val isRefreshing: StateFlow<Boolean> = priceRefreshService.isRefreshing

    init {
        loadHoldingDetails()
        loadDividends()
        observeLots()
    }

    fun loadHoldingDetails() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Load holding performance
                val performance = performanceRepository.getHoldingPerformance(holdingId)
                _holdingPerformance.value = performance

                // Load chart data for selected time range
                loadChartData()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load holding details"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectTimeRange(timeRange: TimeRange) {
        if (_selectedTimeRange.value != timeRange) {
            _selectedTimeRange.value = timeRange
            loadChartData()
        }
    }

    private fun loadChartData() {
        viewModelScope.launch {
            try {
                val data = performanceRepository.getHoldingChartData(
                    holdingId,
                    _selectedTimeRange.value
                )
                _chartData.value = data
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load chart data"
            }
        }
    }

    private fun loadDividends() {
        viewModelScope.launch {
            performanceRepository.getHoldingDividends(holdingId)
                .collect { dividendList ->
                    _dividends.value = dividendList
                }
        }
    }

    private fun observeLots() {
        viewModelScope.launch {
            investmentRepository.getLots(holdingId)
                .collect { _lots.value = it }
        }
    }

    fun refreshPrice() {
        viewModelScope.launch {
            val symbol = _holdingPerformance.value?.symbol ?: return@launch

            priceRefreshService.refreshSymbol(symbol)
                .onSuccess {
                    loadHoldingDetails()
                }
                .onFailure { exception ->
                    _error.value = exception.message ?: "Failed to refresh price"
                }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun addLot(
        date: LocalDate,
        purpose: String?,
        shares: Double,
        costBasis: Long,
        notes: String?
    ) {
        viewModelScope.launch {
            val epoch = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            val lot = HoldingLot(
                holdingId = holdingId,
                acquiredDate = epoch,
                purpose = purpose?.ifBlank { null },
                shares = shares,
                costBasis = costBasis,
                notes = notes?.ifBlank { null }
            )
            investmentRepository.insertHoldingLot(lot)
        }
    }

    fun updateLot(
        lot: HoldingLot,
        date: LocalDate,
        purpose: String?,
        shares: Double,
        costBasis: Long,
        notes: String?
    ) {
        viewModelScope.launch {
            val updated = lot.copy(
                acquiredDate = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
                purpose = purpose?.ifBlank { null },
                shares = shares,
                costBasis = costBasis,
                notes = notes?.ifBlank { null }
            )
            investmentRepository.updateHoldingLot(updated)
        }
    }

    fun deleteLot(lotId: Long) {
        viewModelScope.launch {
            investmentRepository.deleteHoldingLot(lotId)
        }
    }

    fun onDispose() {
        viewModelScope.launch {
            // Cancel all coroutines
        }
    }
}
