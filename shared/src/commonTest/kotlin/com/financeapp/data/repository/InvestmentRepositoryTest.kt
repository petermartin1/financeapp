package com.financeapp.data.repository

import app.cash.turbine.test
import com.financeapp.db.schema.Accounts
import com.financeapp.domain.model.AccountType
import com.financeapp.domain.model.Holding
import com.financeapp.domain.model.HoldingLot
import com.financeapp.test.clearAllTables
import com.financeapp.test.createTestDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentRepositoryTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: InvestmentRepositoryImpl
    private lateinit var database: org.jetbrains.exposed.sql.Database

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        repository = InvestmentRepositoryImpl(database, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        database.clearAllTables()
    }

    @Test
    fun `inserting lots updates holding totals`() = runTest {
        val accountId = insertAccount()
        val holdingId = repository.insertHolding(
            Holding(
                accountId = accountId,
                symbol = "VOO",
                name = "S&P 500",
                shares = 0.0,
                costBasis = 0
            )
        )

        val now = Clock.System.now().toEpochMilliseconds()

        repository.insertHoldingLot(
            HoldingLot(
                holdingId = holdingId,
                acquiredDate = now,
                purpose = "Long term",
                shares = 5.5,
                costBasis = 150_00
            )
        )

        repository.insertHoldingLot(
            HoldingLot(
                holdingId = holdingId,
                acquiredDate = now,
                purpose = "Dip buy",
                shares = 2.5,
                costBasis = 70_00
            )
        )

        val holding = repository.getHoldingById(holdingId)
        assertEquals(8.0, holding?.shares ?: 0.0, 0.0001)
        assertEquals(220_00, holding?.costBasis ?: 0)
    }

    @Test
    fun `getLots emits migrated lot for legacy holdings`() = runTest {
        val accountId = insertAccount()
        val holdingId = repository.insertHolding(
            Holding(
                accountId = accountId,
                symbol = "AAPL",
                name = "Apple",
                shares = 3.0,
                costBasis = 300_00
            )
        )

        repository.getLots(holdingId).test {
            val lots = awaitItem()
            assertEquals(1, lots.size)
            val lot = lots.first()
            assertEquals(3.0, lot.shares, 0.0001)
            assertEquals(300_00, lot.costBasis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a lot recalculates holding totals`() = runTest {
        val accountId = insertAccount()
        val holdingId = repository.insertHolding(
            Holding(
                accountId = accountId,
                symbol = "TSLA",
                name = "Tesla",
                shares = 0.0,
                costBasis = 0
            )
        )
        val now = Clock.System.now().toEpochMilliseconds()
        val lotId = repository.insertHoldingLot(
            HoldingLot(
                holdingId = holdingId,
                acquiredDate = now,
                purpose = "Initial",
                shares = 4.0,
                costBasis = 100_00
            )
        )

        repository.deleteHoldingLot(lotId)

        val holding = repository.getHoldingById(holdingId)
        kotlin.test.assertNotNull(holding)
        assertEquals(0.0, holding.shares, 0.0001)
        assertEquals(0, holding.costBasis)
    }

    private fun insertAccount(): Long = transaction(database) {
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
}
