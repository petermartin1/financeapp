package com.financeapp.ui.budget

import com.financeapp.test.*
import com.financeapp.data.repository.*
import com.financeapp.domain.model.CategoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import org.jetbrains.exposed.sql.Database
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel tests using real repositories (integration-style testing)
 * More valuable than mocked tests as they verify actual data flow
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {

    private lateinit var database: Database
    private lateinit var budgetRepository: BudgetRepositoryImpl
    private lateinit var categoryRepository: CategoryRepositoryImpl
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var accountRepository: AccountRepositoryImpl
    private lateinit var viewModel: BudgetViewModel
    private val testDispatcher = StandardTestDispatcher()
    // Helper function to wait for a specific state condition
    private suspend fun waitForState(
        timeout: kotlin.time.Duration = 10.seconds,
        predicate: (BudgetUiState) -> Boolean
    ): BudgetUiState {
        var result: BudgetUiState? = null
        viewModel.uiState.test(timeout = timeout) {
            while (true) {
                val state = awaitItem()
                if (predicate(state)) {
                    result = state
                    break
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
        return result!!
    }


    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        database = createTestDatabase()
        budgetRepository = BudgetRepositoryImpl(database, testDispatcher)
        categoryRepository = CategoryRepositoryImpl(database, testDispatcher)
        transactionRepository = TransactionRepositoryImpl(database, testDispatcher)
        accountRepository = AccountRepositoryImpl(database, testDispatcher)

        viewModel = BudgetViewModel(budgetRepository)
    }

    @AfterTest
    fun teardown() {
        // Cleanup ViewModel to cancel all background coroutines
        viewModel.cleanup()
        // Advance to process cancellation
        testDispatcher.scheduler.advanceUntilIdle()
        // Clear database
        database.clearAllTables()
        // Reset dispatcher
        Dispatchers.resetMain()
    }

    // ============================================
    // Initial State Tests
    // ============================================

    @Test
    fun `initial state shows loading`() {
        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
    }

    @Test
    fun `loadBudgets for specific month updates state`() = runTest(timeout = 10.seconds) {
        viewModel.loadBudgets(2024, 6)
        val state = waitForState(timeout = 10.seconds) { !it.isLoading }
        assertEquals(2024, state.selectedYear)
        assertEquals(6, state.selectedMonth)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadBudgets loads empty list initially`() = runTest(timeout = 10.seconds) {
        viewModel.loadBudgets(2024, 1)
        val state = waitForState(timeout = 10.seconds) { !it.isLoading }
        assertTrue(state.summary.budgets.isEmpty())
        assertEquals(0L, state.summary.totalBudgeted)
        assertEquals(0L, state.summary.totalSpent)
        assertEquals(0L, state.summary.totalRemaining)
    }

    // ============================================
    // Budget Loading Tests
    // ============================================

    @Test
    fun `loadBudgets loads budgets for specific month`() = runTest(timeout = 10.seconds) {
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )

        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(
                categoryId = categoryId,
                amount = 50000,
                year = 2024,
                month = 3
            )
        )
        budgetRepository.notifyBudgetsChanged()

        viewModel.loadBudgets(2024, 3)
        val state = waitForState(timeout = 10.seconds) { it.summary.budgets.size == 1 }
        assertEquals(1, state.summary.budgets.size)
        assertEquals(50000L, state.summary.budgets[0].budget.amount)
    }

    @Test
    fun `loadBudgets calculates totalBudgeted correctly`() = runTest(timeout = 10.seconds) {
        val cat1 = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        val cat2 = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Transport", type = CategoryType.EXPENSE)
        )

        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = cat1, amount = 30000, year = 2024, month = 4)
        )
        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = cat2, amount = 20000, year = 2024, month = 4)
        )
        budgetRepository.notifyBudgetsChanged()

        viewModel.loadBudgets(2024, 4)
        val state = waitForState(timeout = 10.seconds) { it.summary.totalBudgeted == 50000L }
        assertEquals(50000L, state.summary.totalBudgeted)
    }

    @Test
    fun `loadBudgets calculates totalSpent correctly`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )

        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, amount = 50000, year = 2024, month = 5)
        )

        // Add transactions for May 2024
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                categoryId = categoryId,
                amount = -10000,
                date = testDate(2024, 5, 10)
            )
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                categoryId = categoryId,
                amount = -15000,
                date = testDate(2024, 5, 20)
            )
        )
        transactionRepository.notifyTransactionsChanged()
        budgetRepository.notifyBudgetsChanged()

        viewModel.loadBudgets(2024, 5)
        val state = waitForState(timeout = 10.seconds) { it.summary.totalSpent == 25000L }
        assertEquals(25000L, state.summary.totalSpent)
    }

    @Test
    fun `loadBudgets calculates totalRemaining correctly`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )

        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, amount = 50000, year = 2024, month = 6)
        )

        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                categoryId = categoryId,
                amount = -10000,
                date = testDate(2024, 6, 15)
            )
        )
        transactionRepository.notifyTransactionsChanged()
        budgetRepository.notifyBudgetsChanged()

        viewModel.loadBudgets(2024, 6)
        val state = waitForState(timeout = 10.seconds) { it.summary.totalBudgeted == 50000L }
        assertEquals(50000L, state.summary.totalBudgeted)
        assertEquals(10000L, state.summary.totalSpent)
        assertEquals(40000L, state.summary.totalRemaining)
    }

    @Test
    fun `loadBudgets sorts budgets by percentUsed descending`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val cat1 = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        val cat2 = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Transport", type = CategoryType.EXPENSE)
        )
        val cat3 = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Entertainment", type = CategoryType.EXPENSE)
        )

        // Create budgets
        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = cat1, amount = 10000, year = 2024, month = 7)
        )
        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = cat2, amount = 10000, year = 2024, month = 7)
        )
        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = cat3, amount = 10000, year = 2024, month = 7)
        )

        // Add different spending levels
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId, categoryId = cat1, amount = -5000, date = testDate(2024, 7, 1)
            )
        ) // 50% used
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId, categoryId = cat2, amount = -9000, date = testDate(2024, 7, 1)
            )
        ) // 90% used
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId, categoryId = cat3, amount = -2000, date = testDate(2024, 7, 1)
            )
        ) // 20% used

        transactionRepository.notifyTransactionsChanged()
        budgetRepository.notifyBudgetsChanged()

        viewModel.loadBudgets(2024, 7)
        val state = waitForState(timeout = 10.seconds) { !it.isLoading }
        val budgets = state.summary.budgets

        // Should be sorted by percentUsed descending
        assertTrue(budgets[0].percentUsed >= budgets[1].percentUsed)
        assertTrue(budgets[1].percentUsed >= budgets[2].percentUsed)
    }

    // ============================================
    // Month Navigation Tests
    // ============================================

    @Test
    fun `previousMonth decrements month`() = runTest(timeout = 10.seconds) {
        viewModel.loadBudgets(2024, 6)
        waitForState(timeout = 10.seconds) { !it.isLoading && it.selectedMonth == 6 }

        viewModel.previousMonth()
        val state = waitForState(timeout = 10.seconds) { it.selectedYear == 2024 && it.selectedMonth == 5 && !it.isLoading }
        assertEquals(2024, state.selectedYear)
        assertEquals(5, state.selectedMonth)
    }

    @Test
    fun `previousMonth wraps to December and decrements year`() = runTest(timeout = 10.seconds) {
        viewModel.loadBudgets(2024, 1)
        waitForState(timeout = 10.seconds) { !it.isLoading && it.selectedMonth == 1 }

        viewModel.previousMonth()
        val state = waitForState(timeout = 10.seconds) { it.selectedYear == 2023 && it.selectedMonth == 12 && !it.isLoading }
        assertEquals(2023, state.selectedYear)
        assertEquals(12, state.selectedMonth)
    }

    @Test
    fun `nextMonth increments month`() = runTest(timeout = 10.seconds) {
        viewModel.loadBudgets(2024, 6)
        waitForState(timeout = 10.seconds) { !it.isLoading && it.selectedMonth == 6 }

        viewModel.nextMonth()
        val state = waitForState(timeout = 10.seconds) { it.selectedYear == 2024 && it.selectedMonth == 7 && !it.isLoading }
        assertEquals(2024, state.selectedYear)
        assertEquals(7, state.selectedMonth)
    }

    @Test
    fun `nextMonth wraps to January and increments year`() = runTest(timeout = 10.seconds) {
        viewModel.loadBudgets(2024, 12)
        waitForState(timeout = 10.seconds) { !it.isLoading && it.selectedMonth == 12 }

        viewModel.nextMonth()
        val state = waitForState(timeout = 10.seconds) { it.selectedYear == 2025 && it.selectedMonth == 1 && !it.isLoading }
        assertEquals(2025, state.selectedYear)
        assertEquals(1, state.selectedMonth)
    }

    // ============================================
    // Budget CRUD Tests
    // ============================================

    @Test
    fun `addBudget creates new budget for selected month`() = runTest(timeout = 10.seconds) {
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )

        viewModel.loadBudgets(2024, 8)
        val initialState = waitForState(timeout = 10.seconds) { it.summary.budgets.size == 0 }
        assertEquals(0, initialState.summary.budgets.size)

        viewModel.addBudget(categoryId, 40000)
        val state = waitForState(timeout = 10.seconds) { it.summary.budgets.size == 1 }
        assertEquals(1, state.summary.budgets.size)
        assertEquals(40000L, state.summary.budgets[0].budget.amount)
        assertEquals(2024, state.summary.budgets[0].budget.year)
        assertEquals(8, state.summary.budgets[0].budget.month)
    }

    @Test
    fun `updateBudget modifies budget amount`() = runTest(timeout = 10.seconds) {
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )
        val budgetId = budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, amount = 30000, year = 2024, month = 9)
        )
        budgetRepository.notifyBudgetsChanged()

        viewModel.loadBudgets(2024, 9)
        val initialState = waitForState(timeout = 10.seconds) { it.summary.budgets.isNotEmpty() && it.summary.budgets[0].budget.amount == 30000L }
        assertEquals(30000L, initialState.summary.budgets[0].budget.amount)

        viewModel.updateBudget(budgetId, 50000)
        val state = waitForState(timeout = 10.seconds) { it.summary.budgets.isNotEmpty() && it.summary.budgets[0].budget.amount == 50000L }
        assertEquals(50000L, state.summary.budgets[0].budget.amount)
    }

    @Test
    fun `deleteBudget removes budget`() = runTest(timeout = 10.seconds) {
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )
        val budgetId = budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, year = 2024, month = 10)
        )
        budgetRepository.notifyBudgetsChanged()

        viewModel.loadBudgets(2024, 10)
        waitForState(timeout = 10.seconds) { !it.isLoading }

        assertEquals(1, viewModel.uiState.value.summary.budgets.size)

        viewModel.deleteBudget(budgetId)
        val state = waitForState(timeout = 10.seconds) { it.summary.budgets.size == 0 }
        assertEquals(0, state.summary.budgets.size)
    }

    // ============================================
    // Reactive Updates Tests
    // ============================================

    @Test
    fun `state updates when budget spending changes externally`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )

        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, amount = 50000, year = 2024, month = 11)
        )
        budgetRepository.notifyBudgetsChanged()

        viewModel.loadBudgets(2024, 11)
        val initialState = waitForState(timeout = 10.seconds) { it.summary.totalSpent == 0L }
        assertEquals(0L, initialState.summary.totalSpent)

        // Add transaction externally
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                categoryId = categoryId,
                amount = -10000,
                date = testDate(2024, 11, 15)
            )
        )
        transactionRepository.notifyTransactionsChanged()
        budgetRepository.notifyBudgetsChanged()

        val state = waitForState(timeout = 10.seconds) { it.summary.budgets.isNotEmpty() }
        assertEquals(10000L, state.summary.totalSpent)
    }

    @Test
    fun `state updates when budget added externally`() = runTest(timeout = 10.seconds) {
        viewModel.loadBudgets(2024, 12)
        val initialState = waitForState(timeout = 10.seconds) { it.summary.budgets.size == 0 }
        assertEquals(0, initialState.summary.budgets.size)

        // Add budget externally
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )
        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, year = 2024, month = 12)
        )
        budgetRepository.notifyBudgetsChanged()
        val state = waitForState(timeout = 10.seconds) { it.summary.budgets.size == 1 }
        assertEquals(1, state.summary.budgets.size)
    }

    @Test
    fun `state updates when budget deleted externally`() = runTest(timeout = 10.seconds) {
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )
        val budgetId = budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, year = 2025, month = 1)
        )
        budgetRepository.notifyBudgetsChanged()

        viewModel.loadBudgets(2025, 1)
        waitForState(timeout = 10.seconds) { !it.isLoading }

        assertEquals(1, viewModel.uiState.value.summary.budgets.size)

        // Delete externally
        budgetRepository.deleteBudget(budgetId)
        budgetRepository.notifyBudgetsChanged()
        val state = waitForState(timeout = 10.seconds) { it.summary.budgets.size == 0 }
        assertEquals(0, state.summary.budgets.size)
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `handles no budgets for month`() = runTest(timeout = 10.seconds) {
        viewModel.loadBudgets(2025, 2)
        val state = waitForState(timeout = 10.seconds) { !it.isLoading }
        assertTrue(state.summary.budgets.isEmpty())
        assertEquals(0L, state.summary.totalBudgeted)
        assertEquals(0L, state.summary.totalSpent)
        assertEquals(0L, state.summary.totalRemaining)
    }

    @Test
    fun `handles overspending correctly`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )

        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, amount = 10000, year = 2025, month = 3)
        )

        // Spend more than budgeted
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                categoryId = categoryId,
                amount = -15000,
                date = testDate(2025, 3, 10)
            )
        )
        transactionRepository.notifyTransactionsChanged()
        budgetRepository.notifyBudgetsChanged()

        viewModel.loadBudgets(2025, 3)
        val state = waitForState(timeout = 10.seconds) { it.summary.totalBudgeted == 10000L }
        assertEquals(10000L, state.summary.totalBudgeted)
        assertEquals(15000L, state.summary.totalSpent)
        assertEquals(-5000L, state.summary.totalRemaining) // Negative remaining
    }

    @Test
    fun `only includes transactions from selected month`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )

        budgetRepository.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, amount = 50000, year = 2025, month = 4)
        )

        // Add transaction in different months
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId, categoryId = categoryId, amount = -10000, date = testDate(2025, 3, 15)
            )
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId, categoryId = categoryId, amount = -20000, date = testDate(2025, 4, 15)
            )
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId, categoryId = categoryId, amount = -15000, date = testDate(2025, 5, 15)
            )
        )
        transactionRepository.notifyTransactionsChanged()
        budgetRepository.notifyBudgetsChanged()

        viewModel.loadBudgets(2025, 4)
        val state = waitForState(timeout = 10.seconds) { !it.isLoading }
        // Should only count April transaction (20000)
        assertEquals(20000L, state.summary.totalSpent)
    }
}
