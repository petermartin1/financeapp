package com.financeapp.data.repository

import com.financeapp.data.quotes.HistoricalPrice
import com.financeapp.data.quotes.StockQuote
import com.financeapp.db.schema.Accounts
import com.financeapp.db.schema.HoldingSnapshots
import com.financeapp.db.schema.PortfolioSnapshots
import com.financeapp.domain.model.AccountType
import com.financeapp.domain.model.DividendEvent
import com.financeapp.domain.model.Holding
import com.financeapp.domain.model.HoldingLot
import com.financeapp.domain.model.SnapshotType
import com.financeapp.domain.model.TimeRange
import com.financeapp.domain.repository.QuoteRepository
import com.financeapp.test.clearAllTables
import com.financeapp.test.createTestDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PerformanceRepositoryTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: Database
    private lateinit var investmentRepository: InvestmentRepositoryImpl
    private lateinit var performanceRepository: PerformanceRepositoryImpl

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        investmentRepository = InvestmentRepositoryImpl(database, testDispatcher)
        performanceRepository = PerformanceRepositoryImpl(database, investmentRepository, FakeQuoteRepository())
    }

    @AfterTest
    fun tearDown() {
        database.clearAllTables()
    }

    @Test
    fun `createPortfolioSnapshot does not persist a zero market value for holdings without a price`() = runTest {
        val accountId = insertInvestmentAccount()
        investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "NOPRICE", name = "No Price Co", shares = 10.0, costBasis = 100_000)
        )

        performanceRepository.createPortfolioSnapshot(SnapshotType.DAILY)

        // A holding with no available price must not be recorded as $0, which would
        // permanently corrupt history with a fake -100% position.
        val snapshotRows = transaction(database) { HoldingSnapshots.selectAll().count() }
        assertEquals(0L, snapshotRows)
    }

    @Test
    fun `createPortfolioSnapshot records holdings that have a price`() = runTest {
        val accountId = insertInvestmentAccount()
        investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "VOO", name = "S&P 500", shares = 10.0, costBasis = 100_000)
        )
        investmentRepository.updatePrice("VOO", price = 200_00, date = 1_000L)

        performanceRepository.createPortfolioSnapshot(SnapshotType.DAILY)

        val marketValues = transaction(database) {
            HoldingSnapshots.selectAll().map { it[HoldingSnapshots.marketValue] }
        }
        assertEquals(1, marketValues.size)
        assertEquals(2_000_00L, marketValues.first()) // 10 shares * $200.00
    }

    // --- R11: reinvested dividends update holding shares & cost basis (DRIP) ---

    @Test
    fun `recordDividend reinvested increases holding shares and cost basis`() = runTest {
        val accountId = insertInvestmentAccount()
        val holdingId = investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "VOO", name = "S&P 500", shares = 10.0, costBasis = 100_000)
        )
        investmentRepository.updatePrice("VOO", price = 50_00, date = 1_000L) // $50.00/share

        // $10.00 dividend reinvested at $50.00 buys 0.2 shares
        performanceRepository.recordDividend(
            DividendEvent(
                holdingId = holdingId, symbol = "VOO", paymentDate = 2_000L,
                amount = 10_00, perShare = 100, shares = 10.0, isReinvested = true
            )
        )

        val updated = investmentRepository.getHoldingById(holdingId)!!
        assertEquals(10.2, updated.shares, 0.0001)
        assertEquals(101_000L, updated.costBasis) // original $1000 + reinvested $10
    }

    @Test
    fun `recordDividend cash payout leaves holding shares and cost basis unchanged`() = runTest {
        val accountId = insertInvestmentAccount()
        val holdingId = investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "VOO", name = "S&P 500", shares = 10.0, costBasis = 100_000)
        )
        investmentRepository.updatePrice("VOO", price = 50_00, date = 1_000L)

        performanceRepository.recordDividend(
            DividendEvent(
                holdingId = holdingId, symbol = "VOO", paymentDate = 2_000L,
                amount = 10_00, perShare = 100, shares = 10.0, isReinvested = false
            )
        )

        val updated = investmentRepository.getHoldingById(holdingId)!!
        assertEquals(10.0, updated.shares, 0.0001)
        assertEquals(100_000L, updated.costBasis)
    }

    @Test
    fun `recordDividend reinvested with no known price records event without corrupting holding`() = runTest {
        val accountId = insertInvestmentAccount()
        val holdingId = investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "VOO", name = "S&P 500", shares = 10.0, costBasis = 100_000)
        )
        // No price recorded for VOO

        performanceRepository.recordDividend(
            DividendEvent(
                holdingId = holdingId, symbol = "VOO", paymentDate = 2_000L,
                amount = 10_00, perShare = 100, shares = 10.0, isReinvested = true
            )
        )

        // Without a price we cannot determine reinvested shares, so the holding must be
        // left untouched rather than fabricating a share count.
        val updated = investmentRepository.getHoldingById(holdingId)!!
        assertEquals(10.0, updated.shares, 0.0001)
        assertEquals(100_000L, updated.costBasis)
        val events = performanceRepository.getHoldingDividends(holdingId).first()
        assertEquals(1, events.size)
    }

    @Test
    fun `recordDividend reinvested keeps lot totals consistent with the holding`() = runTest {
        val accountId = insertInvestmentAccount()
        val holdingId = investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "VOO", name = "S&P 500", shares = 10.0, costBasis = 100_000)
        )
        // Materialize the position as a lot so lots become the source of truth.
        investmentRepository.insertHoldingLot(
            HoldingLot(holdingId = holdingId, acquiredDate = 500L, purpose = null, shares = 10.0, costBasis = 100_000)
        )
        investmentRepository.updatePrice("VOO", price = 50_00, date = 1_000L)

        performanceRepository.recordDividend(
            DividendEvent(
                holdingId = holdingId, symbol = "VOO", paymentDate = 2_000L,
                amount = 10_00, perShare = 100, shares = 10.0, isReinvested = true
            )
        )

        val updated = investmentRepository.getHoldingById(holdingId)!!
        assertEquals(10.2, updated.shares, 0.0001)
        assertEquals(101_000L, updated.costBasis)

        // Lots must still sum to the holding totals, or a later recalculation would
        // silently drop the reinvested shares.
        val lots = investmentRepository.getLots(holdingId).first()
        assertEquals(10.2, lots.sumOf { it.shares }, 0.0001)
        assertEquals(101_000L, lots.sumOf { it.costBasis })
    }

    // --- R12: holding chart gain base is cost basis, not the previous snapshot ---

    @Test
    fun `getHoldingChartData measures gain against cost basis, not the previous snapshot`() = runTest {
        val accountId = insertInvestmentAccount()
        val holdingId = investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "VOO", name = "S&P 500", shares = 10.0, costBasis = 100_000)
        )
        // Both snapshots are already above cost basis, so "previous value" != cost basis.
        insertHoldingSnapshot(holdingId, date = 1_000L, marketValue = 120_000, costBasis = 100_000, price = 120_00)
        insertHoldingSnapshot(holdingId, date = 2_000L, marketValue = 130_000, costBasis = 100_000, price = 130_00)

        val chart = performanceRepository.getHoldingChartData(holdingId, TimeRange.ALL_TIME)

        assertEquals(2, chart.dataPoints.size)
        assertEquals(20_000L, chart.dataPoints[0].gainLoss) // 120,000 - 100,000
        assertEquals(30_000L, chart.dataPoints[1].gainLoss) // 130,000 - 100,000 (not 130k - 120k)
        assertEquals(30.0, chart.dataPoints[1].gainLossPercent, 0.0001)
    }

    // --- Residual `?: 0L`: unpriced holdings must not distort the performance tab ---

    @Test
    fun `getAllHoldingPerformance excludes holdings without a known price`() = runTest {
        val accountId = insertInvestmentAccount()
        investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "VOO", name = "S&P 500", shares = 10.0, costBasis = 100_000)
        )
        investmentRepository.updatePrice("VOO", price = 120_00, date = 1_000L)
        investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "NOPRICE", name = "No Price Co", shares = 5.0, costBasis = 50_000)
        )

        val perfs = performanceRepository.getAllHoldingPerformance().first()

        // The unpriced holding would otherwise show a fake -100% loss and skew allocation.
        assertEquals(1, perfs.size)
        assertEquals("VOO", perfs[0].symbol)
        assertEquals(100.0, perfs[0].allocation, 0.0001)
    }

    @Test
    fun `getHoldingPerformance returns null for a holding without a known price`() = runTest {
        val accountId = insertInvestmentAccount()
        val holdingId = investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "NOPRICE", name = "No Price Co", shares = 5.0, costBasis = 50_000)
        )

        assertNull(performanceRepository.getHoldingPerformance(holdingId))
    }

    @Test
    fun `getPerformanceSummary excludes unpriced holdings from totals`() = runTest {
        val accountId = insertInvestmentAccount()
        investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "VOO", name = "S&P 500", shares = 10.0, costBasis = 100_000)
        )
        investmentRepository.updatePrice("VOO", price = 120_00, date = 1_000L)
        investmentRepository.insertHolding(
            Holding(accountId = accountId, symbol = "NOPRICE", name = "No Price Co", shares = 5.0, costBasis = 50_000)
        )

        val summary = performanceRepository.getPerformanceSummary()

        assertEquals(120_000L, summary.totalValue)
        assertEquals(100_000L, summary.totalCostBasis) // excludes the unpriced holding's cost basis
    }

    private fun insertHoldingSnapshot(
        holdingId: Long,
        date: Long,
        marketValue: Long,
        costBasis: Long,
        price: Long,
        shares: Double = 10.0
    ) = transaction(database) {
        val snapshotId = PortfolioSnapshots.insertAndGetId {
            it[PortfolioSnapshots.date] = date
            it[snapshotType] = SnapshotType.DAILY.name
            it[totalValue] = marketValue
            it[totalCostBasis] = costBasis
            it[totalGainLoss] = marketValue - costBasis
        }
        HoldingSnapshots.insert {
            it[portfolioSnapshotId] = snapshotId
            it[HoldingSnapshots.holdingId] = holdingId.toInt()
            it[symbol] = "VOO"
            it[HoldingSnapshots.shares] = shares
            it[HoldingSnapshots.costBasis] = costBasis
            it[HoldingSnapshots.marketValue] = marketValue
            it[HoldingSnapshots.price] = price
        }
        Unit
    }

    private fun insertInvestmentAccount(): Long = transaction(database) {
        val now = Clock.System.now().toEpochMilliseconds()
        Accounts.insertAndGetId {
            it[name] = "Brokerage"
            it[type] = AccountType.INVESTMENT.name
            it[institution] = "Test"
            it[accountNumber] = "****1234"
            it[currency] = "USD"
            it[isActive] = true
            it[createdAt] = now
            it[updatedAt] = now
        }.value.toLong()
    }

    /**
     * createPortfolioSnapshot only depends on InvestmentRepository; quotes are not used,
     * so this fake satisfies the constructor without any network setup.
     */
    private class FakeQuoteRepository : QuoteRepository {
        override suspend fun refreshAllPrices(): Map<String, Result<StockQuote>> = emptyMap()
        override suspend fun refreshPrice(symbol: String): Result<StockQuote> =
            Result.failure(UnsupportedOperationException("not used in test"))
        override suspend fun fetchPriceHistory(symbol: String, days: Int): Result<List<HistoricalPrice>> =
            Result.failure(UnsupportedOperationException("not used in test"))
        override suspend fun getLastRefreshTime(): Long? = null
        override suspend fun setLastRefreshTime(timestamp: Long) {}
    }
}
