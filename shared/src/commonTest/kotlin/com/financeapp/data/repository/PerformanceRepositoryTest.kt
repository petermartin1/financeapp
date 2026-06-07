package com.financeapp.data.repository

import com.financeapp.data.quotes.HistoricalPrice
import com.financeapp.data.quotes.StockQuote
import com.financeapp.db.schema.Accounts
import com.financeapp.db.schema.HoldingSnapshots
import com.financeapp.domain.model.AccountType
import com.financeapp.domain.model.Holding
import com.financeapp.domain.model.SnapshotType
import com.financeapp.domain.repository.QuoteRepository
import com.financeapp.test.clearAllTables
import com.financeapp.test.createTestDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
