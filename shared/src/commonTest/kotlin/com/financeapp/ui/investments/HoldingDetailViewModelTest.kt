package com.financeapp.ui.investments

import app.cash.turbine.test
import com.financeapp.domain.model.DividendEvent
import com.financeapp.domain.model.Holding
import com.financeapp.domain.model.HoldingLot
import com.financeapp.domain.model.HoldingPerformance
import com.financeapp.domain.model.HoldingSnapshot
import com.financeapp.domain.model.HoldingWithPrice
import com.financeapp.domain.model.PerformanceChartData
import com.financeapp.domain.model.PerformanceMetrics
import com.financeapp.domain.model.PerformanceSummary
import com.financeapp.domain.model.PortfolioSnapshot
import com.financeapp.domain.model.SecurityPrice
import com.financeapp.domain.model.SnapshotType
import com.financeapp.domain.model.TimeRange
import com.financeapp.domain.repository.InvestmentRepository
import com.financeapp.domain.repository.PerformanceRepository
import com.financeapp.domain.repository.QuoteRepository
import com.financeapp.domain.service.PriceRefreshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HoldingDetailViewModelTest {
    private lateinit var investmentRepository: FakeInvestmentRepository
    private lateinit var performanceRepository: FakePerformanceRepository
    private lateinit var priceRefreshService: PriceRefreshService

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        investmentRepository = FakeInvestmentRepository()
        performanceRepository = FakePerformanceRepository()
        priceRefreshService = PriceRefreshService(
            FakeQuoteRepository(),
            investmentRepository,
            CoroutineScope(UnconfinedTestDispatcher())
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `adding lot updates state`() = runTest {
        val viewModel = HoldingDetailViewModel(
            holdingId = 1L,
            investmentRepository = investmentRepository,
            performanceRepository = performanceRepository,
            priceRefreshService = priceRefreshService
        )

        viewModel.lots.test {
            awaitItem() // initial empty state
            val date = LocalDate(2024, 1, 1)
            viewModel.addLot(date, "Initial", 2.5, 100_00, null)
            val lots = awaitItem()
            assertEquals(1, lots.size)
            assertEquals("Initial", lots.first().purpose)
            assertEquals(100_00, lots.first().costBasis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update lot propagates to repository`() = runTest {
        val viewModel = HoldingDetailViewModel(
            holdingId = 1L,
            investmentRepository = investmentRepository,
            performanceRepository = performanceRepository,
            priceRefreshService = priceRefreshService
        )

        val date = LocalDate(2024, 1, 1)
        viewModel.addLot(date, "Initial", 2.0, 80_00, null)

        val existingLot = investmentRepository.lotsFlow.value.first()
        viewModel.updateLot(existingLot, date, "Adjusted", 3.0, 120_00, null)

        assertEquals("Adjusted", investmentRepository.lotsFlow.value.first().purpose)
        assertEquals(120_00, investmentRepository.lotsFlow.value.first().costBasis)
    }

    private class FakeInvestmentRepository : InvestmentRepository {
        val lotsFlow = MutableStateFlow<List<HoldingLot>>(emptyList())
        private var lotId = 1L

        override fun getLots(holdingId: Long): Flow<List<HoldingLot>> = lotsFlow

        override suspend fun insertHoldingLot(lot: HoldingLot): Long {
            val newLot = lot.copy(id = lotId++)
            lotsFlow.value = lotsFlow.value + newLot
            return newLot.id
        }

        override suspend fun updateHoldingLot(lot: HoldingLot) {
            lotsFlow.value = lotsFlow.value.map { if (it.id == lot.id) lot else it }
        }

        override suspend fun deleteHoldingLot(id: Long) {
            lotsFlow.value = lotsFlow.value.filterNot { it.id == id }
        }

        override fun getPortfolio(): Flow<List<HoldingWithPrice>> = flowOf(emptyList())
        override fun getHoldingsByAccount(accountId: Long): Flow<List<Holding>> = flowOf(emptyList())
        override suspend fun getHoldingById(id: Long): Holding? = null
        override suspend fun getAllHoldings(): List<Holding> = emptyList()
        override suspend fun insertHolding(holding: Holding): Long = 0L
        override suspend fun updateHolding(holding: Holding) {}
        override suspend fun deleteHolding(id: Long) {}
        override suspend fun getLatestPrice(symbol: String): SecurityPrice? = null
        override suspend fun updatePrice(symbol: String, price: Long, date: Long) {}
        override suspend fun getPriceHistory(symbol: String, limit: Int): List<SecurityPrice> = emptyList()
        override fun notifyHoldingsChanged() {}
        override fun notifyPricesChanged() {}
    }

    private class FakePerformanceRepository : PerformanceRepository {
        override suspend fun getHoldingPerformance(holdingId: Long): HoldingPerformance? =
            HoldingPerformance(
                holdingId = holdingId,
                symbol = "TEST",
                name = "Test",
                quantity = 10000,
                costBasis = 100_00,
                currentPrice = 120_00,
                currentValue = 120_00,
                gainLoss = 20_00,
                gainLossPercent = 20.0,
                dayChange = 100,
                dayChangePercent = 0.5,
                allocation = 100.0
            )

        override suspend fun getHoldingChartData(holdingId: Long, timeRange: TimeRange): PerformanceChartData =
            PerformanceChartData(timeRange, emptyList())

        override fun getHoldingDividends(holdingId: Long): Flow<List<DividendEvent>> = flowOf(emptyList())

        override suspend fun createPortfolioSnapshot(snapshotType: SnapshotType): Long = 0
        override fun getPortfolioSnapshots(startDate: Long, endDate: Long): Flow<List<PortfolioSnapshot>> = flowOf(emptyList())
        override suspend fun getLatestPortfolioSnapshot(): PortfolioSnapshot? = null
        override suspend fun deleteSnapshotsBefore(date: Long) {}
        override suspend fun createHoldingSnapshots(snapshotType: SnapshotType) {}
        override fun getHoldingSnapshots(holdingId: Long, startDate: Long, endDate: Long): Flow<List<HoldingSnapshot>> = flowOf(emptyList())
        override suspend fun getHoldingSnapshotsForDate(date: Long): List<HoldingSnapshot> = emptyList()
        override suspend fun calculatePerformanceMetrics(timeRange: TimeRange): PerformanceMetrics? = null
        override fun getAllHoldingPerformance(): Flow<List<HoldingPerformance>> = flowOf(emptyList())
        override suspend fun getPerformanceSummary(): PerformanceSummary =
            PerformanceSummary(0, 0, 0, 0.0, 0, 0.0, null, null, emptyList())
        override suspend fun getPerformanceChartData(timeRange: TimeRange): PerformanceChartData =
            PerformanceChartData(timeRange, emptyList())
        override suspend fun recordDividend(dividend: DividendEvent): Long = 0
        override fun getDividends(startDate: Long, endDate: Long): Flow<List<DividendEvent>> = flowOf(emptyList())
        override suspend fun getTotalDividends(holdingId: Long): Long = 0
    }

    private class FakeQuoteRepository : QuoteRepository {
        override suspend fun refreshAllPrices(): Map<String, Result<com.financeapp.data.quotes.StockQuote>> = emptyMap()
        override suspend fun refreshPrice(symbol: String): Result<com.financeapp.data.quotes.StockQuote> = Result.failure(IllegalStateException("No quotes"))
        override suspend fun fetchPriceHistory(symbol: String, days: Int): Result<List<com.financeapp.data.quotes.HistoricalPrice>> =
            Result.success(emptyList())

        override suspend fun getLastRefreshTime(): Long? = null
        override suspend fun setLastRefreshTime(timestamp: Long) {}
    }
}
