package com.financeapp.ui.transactions

import com.financeapp.test.*
import com.financeapp.data.repository.*
import com.financeapp.domain.model.CategoryType
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel tests using real repositories (integration-style testing)
 * More valuable than mocked tests as they verify actual data flow
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    private lateinit var database: Database
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var accountRepository: AccountRepositoryImpl
    private lateinit var categoryRepository: CategoryRepositoryImpl
    private lateinit var payeeRepository: PayeeRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var viewModel: TransactionsViewModel
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    private fun runTest(block: suspend TestScope.() -> Unit) =
        kotlinx.coroutines.test.runTest(testDispatcher, testBody = block)

    private fun runTest(
        timeout: kotlin.time.Duration,
        block: suspend TestScope.() -> Unit
    ) = kotlinx.coroutines.test.runTest(testDispatcher, timeout = timeout, testBody = block)

    // Helper function to wait for a specific state condition
    private suspend fun waitForState(
        timeout: kotlin.time.Duration = 10.seconds,
        predicate: (TransactionsUiState) -> Boolean
    ): TransactionsUiState {
        var result: TransactionsUiState? = null
        viewModel.uiState.test(timeout = timeout) {
            while (true) {
                testScheduler.runCurrent()
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

    private var testAccountId: Long = 0

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        database = createTestDatabase()
        transactionRepository = TransactionRepositoryImpl(database, testDispatcher)
        accountRepository = AccountRepositoryImpl(database, testDispatcher)
        categoryRepository = CategoryRepositoryImpl(database, testDispatcher)
        payeeRepository = PayeeRepositoryImpl(database, testDispatcher)
        tagRepository = TagRepositoryImpl(database, testDispatcher)

        viewModel = TransactionsViewModel(
            transactionRepository,
            accountRepository,
            categoryRepository,
            payeeRepository,
            tagRepository
        )

        // Create test account
        testAccountId = runBlocking(testDispatcher) {
            accountRepository.insertAccount(
                TestDataFactory.createTestAccount(name = "Test Account")
            )
        }
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
        assertTrue(state.transactions.isEmpty())
    }

    @Test
    fun `loadTransactions updates state with account info`() = runTest(timeout = 5.seconds) {
        viewModel.loadTransactions(testAccountId)
        val state = waitForState(timeout = 5.seconds) { it.accountName == "Test Account" }

        assertEquals("Test Account", state.accountName)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadTransactions loads empty list initially`() = runTest(timeout = 5.seconds) {
        viewModel.loadTransactions(testAccountId)
        val state = waitForState(timeout = 5.seconds) { !it.isLoading }

        assertTrue(state.transactions.isEmpty())
        assertFalse(state.isLoading)
    }

    // ============================================
    // Transaction Loading Tests
    // ============================================

    @Test
    fun `loadTransactions loads all transactions for account`() = runTest(timeout = 5.seconds) {
        // Add transactions
        val txn1 = TestDataFactory.createTestTransaction(accountId = testAccountId, amount = -1000)
        val txn2 = TestDataFactory.createTestTransaction(accountId = testAccountId, amount = -2000)

        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)
        transactionRepository.notifyTransactionsChanged()

        viewModel.loadTransactions(testAccountId)
        val state = waitForState(timeout = 5.seconds) { it.transactions.size == 2 }
        assertEquals(2, state.transactions.size)
    }

    @Test
    fun `loadTransactions updates balance correctly`() = runTest(timeout = 5.seconds) {
        val txn = TestDataFactory.createTestTransaction(accountId = testAccountId, amount = -5000)
        transactionRepository.insertTransaction(txn)
        transactionRepository.notifyTransactionsChanged()

        viewModel.loadTransactions(testAccountId)
        val state = waitForState(timeout = 5.seconds) { it.accountBalance == -5000L }
        assertEquals(-5000L, state.accountBalance)
    }

    // ============================================
    // Search/Filter Tests
    // ============================================

    @Test
    fun `updateSearchQuery filters transactions`() = runTest(timeout = 5.seconds) {
        val category = TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        val catId = categoryRepository.insertCategory(category)

        val txn1 = TestDataFactory.createTestTransaction(
            accountId = testAccountId,
            categoryId = catId,
            amount = -1000,
            memo = "Coffee at Starbucks"
        )
        val txn2 = TestDataFactory.createTestTransaction(
            accountId = testAccountId,
            categoryId = catId,
            amount = -2000,
            memo = "Lunch at Subway"
        )

        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)
        transactionRepository.notifyTransactionsChanged()

        viewModel.loadTransactions(testAccountId)
        waitForState(timeout = 5.seconds) { it.transactions.size == 2 }

        // Search for "Coffee"
        viewModel.updateSearchQuery("Coffee")
        val state = waitForState(timeout = 5.seconds) { it.filteredTransactions.size == 1 }
        assertEquals(1, state.filteredTransactions.size)
        assertTrue(state.filteredTransactions[0].transaction.memo?.contains("Coffee") == true)
    }

    @Test
    fun `clearFilter resets to show all transactions`() = runTest(timeout = 5.seconds) {
        val txn1 = TestDataFactory.createTestTransaction(accountId = testAccountId, amount = -1000)
        val txn2 = TestDataFactory.createTestTransaction(accountId = testAccountId, amount = -2000)

        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)
        transactionRepository.notifyTransactionsChanged()

        viewModel.loadTransactions(testAccountId)
        waitForState(timeout = 5.seconds) { it.transactions.size == 2 }

        // Apply filter
        viewModel.updateSearchQuery("test")
        waitForState(timeout = 5.seconds) { it.isFilterActive }

        // Clear filter
        viewModel.clearFilter()
        val state = waitForState(timeout = 5.seconds) { !it.isFilterActive }
        assertEquals(2, state.filteredTransactions.size)
        assertFalse(state.isFilterActive)
    }

    // ============================================
    // Transaction Actions Tests
    // ============================================

    @Test
    fun `addTransaction creates new transaction`() = runTest(timeout = 5.seconds) {
        viewModel.loadTransactions(testAccountId)

        viewModel.addTransaction(
            amount = -3000,
            payeeName = "Test Payee",
            categoryId = null,
            memo = "Test transaction",
            date = testDate(2024, 1, 15),
            isCleared = false
        )
        val state = waitForState(timeout = 5.seconds) { it.transactions.size == 1 }
        assertEquals(1, state.transactions.size)
    }

    @Test
    fun `deleteTransaction removes transaction`() = runTest(timeout = 5.seconds) {
        val txn = TestDataFactory.createTestTransaction(accountId = testAccountId)
        val id = transactionRepository.insertTransaction(txn)
        transactionRepository.notifyTransactionsChanged()

        viewModel.loadTransactions(testAccountId)
        val initialState = waitForState(timeout = 5.seconds) { it.transactions.size == 1 }
        assertEquals(1, initialState.transactions.size)

        viewModel.deleteTransaction(id)
        val state = waitForState(timeout = 5.seconds) { it.transactions.size == 0 }
        assertEquals(0, state.transactions.size)
    }

    @Test
    fun `toggleCleared updates transaction status`() = runTest(timeout = 5.seconds) {
        val txn = TestDataFactory.createTestTransaction(
            accountId = testAccountId,
            isCleared = false
        )
        val id = transactionRepository.insertTransaction(txn)
        transactionRepository.notifyTransactionsChanged()

        viewModel.loadTransactions(testAccountId)
        val initialState = waitForState(timeout = 5.seconds) { it.transactions.size == 1 }

        val transaction = initialState.transactions[0].transaction
        viewModel.toggleCleared(transaction)

        // Wait for the update to complete
        waitForState(timeout = 5.seconds) { it.transactions.isNotEmpty() && it.transactions[0].transaction.isCleared }

        val updated = transactionRepository.getTransactionById(id)
        assertNotNull(updated)
        assertTrue(updated.isCleared)
    }

    // ============================================
    // Reactive Updates Tests
    // ============================================

    @Test
    fun `state updates when transactions change externally`() = runTest(timeout = 5.seconds) {
        viewModel.loadTransactions(testAccountId)
        val initialState = waitForState(timeout = 5.seconds) { !it.isLoading }
        assertEquals(0, initialState.transactions.size)

        // Add transaction externally (simulating another part of the app)
        val txn = TestDataFactory.createTestTransaction(accountId = testAccountId)
        transactionRepository.insertTransaction(txn)
        transactionRepository.notifyTransactionsChanged()

        val state = waitForState(timeout = 5.seconds) { it.transactions.size == 1 }
        assertEquals(1, state.transactions.size)
    }

    @Test
    fun `balance updates when transaction added`() = runTest(timeout = 5.seconds) {
        viewModel.loadTransactions(testAccountId)
        testDispatcher.scheduler.advanceUntilIdle()
        val initialState = waitForState(timeout = 5.seconds) { !it.isLoading }
        testDispatcher.scheduler.advanceUntilIdle()
        val initialBalance = initialState.accountBalance

        viewModel.addTransaction(
            amount = -1000,
            payeeName = null,
            categoryId = null,
            memo = null,
            date = testDate(2024, 1, 15),
            isCleared = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = waitForState(timeout = 5.seconds) { it.accountBalance == initialBalance - 1000 }
        testDispatcher.scheduler.advanceUntilIdle()
        val newBalance = state.accountBalance
        assertEquals(initialBalance - 1000, newBalance)
    }

    // ============================================
    // Filter Combinations Tests
    // ============================================

    @Test
    @Ignore("Timing issue - needs investigation")
    fun `filter by amount range works correctly`() = runTest(timeout = 5.seconds) {
        val txn1 = TestDataFactory.createTestTransaction(accountId = testAccountId, amount = -1000)
        val txn2 = TestDataFactory.createTestTransaction(accountId = testAccountId, amount = -5000)
        val txn3 = TestDataFactory.createTestTransaction(accountId = testAccountId, amount = -10000)

        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)
        transactionRepository.insertTransaction(txn3)
        transactionRepository.notifyTransactionsChanged()

        viewModel.loadTransactions(testAccountId)
        waitForState(timeout = 5.seconds) { it.transactions.size == 3 }

        // Filter for amounts between $20-$60
        val filter = TransactionFilter(
            minAmount = -6000,
            maxAmount = -2000
        )
        viewModel.updateFilter(filter)
        val state = waitForState(timeout = 5.seconds) { it.filteredTransactions.size == 1 }
        assertEquals(1, state.filteredTransactions.size)
        assertEquals(-5000L, state.filteredTransactions[0].transaction.amount)
    }

    @Test
    fun `filter by cleared status works correctly`() = runTest(timeout = 5.seconds) {
        val txn1 = TestDataFactory.createTestTransaction(
            accountId = testAccountId,
            isCleared = true,
            amount = -1000
        )
        val txn2 = TestDataFactory.createTestTransaction(
            accountId = testAccountId,
            isCleared = false,
            amount = -2000
        )

        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)
        transactionRepository.notifyTransactionsChanged()

        viewModel.loadTransactions(testAccountId)
        waitForState(timeout = 5.seconds) { it.transactions.size == 2 }

        // Show only cleared
        val filter = TransactionFilter(showCleared = true, showUncleared = false)
        viewModel.updateFilter(filter)
        val state = waitForState(timeout = 5.seconds) { it.filteredTransactions.size == 1 }
        assertEquals(1, state.filteredTransactions.size)
        assertTrue(state.filteredTransactions[0].transaction.isCleared)
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `handles non-existent account gracefully`() = runTest(timeout = 5.seconds) {
        viewModel.loadTransactions(99999L)
        val state = waitForState(timeout = 5.seconds) { !it.isLoading }
        assertEquals("", state.accountName)
        assertEquals(0L, state.accountBalance)
    }

    @Test
    fun `handles empty search query`() = runTest(timeout = 5.seconds) {
        val txn = TestDataFactory.createTestTransaction(accountId = testAccountId)
        transactionRepository.insertTransaction(txn)
        transactionRepository.notifyTransactionsChanged()

        viewModel.loadTransactions(testAccountId)
        waitForState(timeout = 5.seconds) { it.transactions.size == 1 }

        viewModel.updateSearchQuery("")
        val state = waitForState(timeout = 5.seconds) { !it.isFilterActive }
        assertEquals(1, state.filteredTransactions.size)
    }
}
