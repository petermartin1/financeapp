package com.financeapp.ui.goals

import com.financeapp.data.repository.AccountRepositoryImpl
import com.financeapp.data.repository.GoalRepositoryImpl
import com.financeapp.domain.model.AccountType
import com.financeapp.test.TestDataFactory
import com.financeapp.test.clearAllTables
import com.financeapp.test.createTestDatabase
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: Database
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var goals: GoalRepositoryImpl
    private var savingsId: Long = 0
    private var checkingId: Long = 0

    @BeforeTest
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        database = createTestDatabase()
        accounts = AccountRepositoryImpl(database, testDispatcher)
        goals = GoalRepositoryImpl(database, accounts, testDispatcher)
        checkingId = runBlocking {
            accounts.insertAccount(TestDataFactory.createTestAccount(name = "A-Checking", type = AccountType.CHECKING))
        }
        savingsId = runBlocking {
            accounts.insertAccount(TestDataFactory.createTestAccount(name = "Z-Savings", type = AccountType.SAVINGS))
        }
    }

    @AfterTest
    fun teardown() {
        database.clearAllTables()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `archived goals are hidden until toggled`() = runTest(testDispatcher, timeout = 5.seconds) {
        val keptId = runBlocking { goals.createGoal("Active", 100_000, savingsId, null) }
        val archivedId = runBlocking { goals.createGoal("Old", 100_000, savingsId, null) }
        runBlocking { goals.setArchived(archivedId, true) }

        val vm = GoalsViewModel(goals, accounts)
        vm.uiState.test(timeout = 5.seconds) {
            var state = awaitItem()
            while (state.goals.isEmpty()) state = awaitItem()
            assertEquals(listOf(keptId), state.goals.map { it.goal.id }, "archived hidden by default")

            vm.toggleShowArchived()
            var toggled = awaitItem()
            while (toggled.goals.size < 2) toggled = awaitItem()
            assertEquals(setOf(keptId, archivedId), toggled.goals.map { it.goal.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
        vm.cleanup()
    }

    @Test
    fun `account picker lists savings accounts first`() = runTest(testDispatcher, timeout = 5.seconds) {
        val vm = GoalsViewModel(goals, accounts)
        vm.uiState.test(timeout = 5.seconds) {
            var state = awaitItem()
            while (state.accounts.size < 2) state = awaitItem()
            assertEquals(listOf("Z-Savings", "A-Checking"), state.accounts.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
        vm.cleanup()
    }

    @Test
    fun `parseDollarsToCents handles money formats and rejects junk`() {
        assertEquals(123_456, GoalsViewModel.parseDollarsToCents("1,234.56"))
        assertEquals(123_456, GoalsViewModel.parseDollarsToCents("$1234.56"))
        assertEquals(500_000, GoalsViewModel.parseDollarsToCents("5000"))
        assertEquals(50, GoalsViewModel.parseDollarsToCents("0.50"))
        assertEquals(50, GoalsViewModel.parseDollarsToCents("0.5"), "single decimal digit means tens of cents")
        assertNull(GoalsViewModel.parseDollarsToCents(""))
        assertNull(GoalsViewModel.parseDollarsToCents("abc"))
        assertNull(GoalsViewModel.parseDollarsToCents("12.345"), "more than 2 decimals rejected")
        assertNull(GoalsViewModel.parseDollarsToCents("-50"), "negative rejected")
        assertNull(GoalsViewModel.parseDollarsToCents("0"), "zero rejected")
    }
}
