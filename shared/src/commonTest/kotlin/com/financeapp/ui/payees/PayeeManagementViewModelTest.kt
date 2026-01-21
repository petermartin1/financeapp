package com.financeapp.ui.payees

import com.financeapp.test.*
import com.financeapp.data.repository.*
import com.financeapp.domain.matching.PayeeMatcher
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
class PayeeManagementViewModelTest {

    private lateinit var database: Database
    private lateinit var payeeRepository: PayeeRepositoryImpl
    private lateinit var categoryRepository: CategoryRepositoryImpl
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var accountRepository: AccountRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var payeeMatchingRepository: PayeeMatchingRepositoryImpl
    private lateinit var viewModel: PayeeManagementViewModel
    private val testDispatcher = StandardTestDispatcher()
    // Helper function to wait for a specific state condition
    private suspend fun waitForState(
        timeout: kotlin.time.Duration = 10.seconds,
        predicate: (PayeeManagementUiState) -> Boolean
    ): PayeeManagementUiState {
        var result: PayeeManagementUiState? = null
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
        payeeRepository = PayeeRepositoryImpl(database, testDispatcher)
        categoryRepository = CategoryRepositoryImpl(database, testDispatcher)
        transactionRepository = TransactionRepositoryImpl(database, testDispatcher)
        accountRepository = AccountRepositoryImpl(database, testDispatcher)
        tagRepository = TagRepositoryImpl(database, testDispatcher)
        payeeMatchingRepository = PayeeMatchingRepositoryImpl(database, PayeeMatcher(), testDispatcher)

        viewModel = PayeeManagementViewModel(
            payeeRepository,
            categoryRepository,
            transactionRepository,
            tagRepository,
            payeeMatchingRepository
        )
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
        assertTrue(state.payees.isEmpty())
        assertTrue(state.categories.isEmpty())
        assertEquals("", state.searchQuery)

    }

    @Test
    fun `loadData updates state with payees and categories`() = runTest(timeout = 10.seconds) {
        payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Amazon"))
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Shopping", type = CategoryType.EXPENSE)
        )
        payeeRepository.notifyPayeesChanged()
        categoryRepository.notifyCategoriesChanged()

        val state = waitForState(timeout = 10.seconds) { it.payees.size == 1 && it.categories.size == 1 }
        assertFalse(state.isLoading)
        assertEquals(1, state.payees.size)
        assertEquals("Amazon", state.payees[0].payee.name)
        assertEquals(1, state.categories.size)
        assertEquals("Shopping", state.categories[0].name)

    }

    // ============================================
    // Data Loading Tests
    // ============================================

    @Test
    fun `loadData loads payees with stats`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val payeeId = payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Starbucks"))

        // Add transactions to create stats
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payeeId, amount = -500)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payeeId, amount = -750)
        )

        transactionRepository.notifyTransactionsChanged()
        payeeRepository.notifyPayeesChanged()

        val state = waitForState(timeout = 10.seconds) {
            it.payees.isNotEmpty() && it.payees.any { p -> p.payee.name == "Starbucks" && p.transactionCount == 2L }
        }
        val payeeStats = state.payees.find { it.payee.name == "Starbucks" }
        assertNotNull(payeeStats)
        assertEquals(2, payeeStats.transactionCount)

    }

    @Test
    fun `loadData loads categories for default category selection`() = runTest(timeout = 10.seconds) {
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Transport", type = CategoryType.EXPENSE)
        )
        categoryRepository.notifyCategoriesChanged()

        val state = waitForState(timeout = 10.seconds) { it.categories.size == 2 }
        assertEquals(2, state.categories.size)
        assertTrue(state.categories.any { it.name == "Food" })
        assertTrue(state.categories.any { it.name == "Transport" })

    }

    // ============================================
    // Search Filtering Tests
    // ============================================

    @Test
    fun `setSearchQuery updates search query in state`() = runTest(timeout = 10.seconds) {
        viewModel.setSearchQuery("amazon")

        val state = waitForState(timeout = 10.seconds) { it.searchQuery == "amazon" }
        assertEquals("amazon", state.searchQuery)

    }

    @Test
    fun `getFilteredPayees returns all when query is blank`() = runTest(timeout = 10.seconds) {
        payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Amazon"))
        payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Walmart"))
        payeeRepository.notifyPayeesChanged()

        waitForState(timeout = 10.seconds) { it.payees.size == 2 }

        viewModel.setSearchQuery("")
        val filtered = viewModel.getFilteredPayees()

        assertEquals(2, filtered.size)

    }

    @Test
    fun `getFilteredPayees filters by name case insensitive`() = runTest(timeout = 10.seconds) {
        payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Amazon"))
        payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Walmart"))
        payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Target"))
        payeeRepository.notifyPayeesChanged()

        waitForState(timeout = 10.seconds) { it.payees.size == 3 }

        viewModel.setSearchQuery("AMA")
        waitForState(timeout = 10.seconds) { it.searchQuery == "AMA" }
        val filtered = viewModel.getFilteredPayees()

        assertEquals(1, filtered.size)
        assertEquals("Amazon", filtered[0].payee.name)

    }

    // ============================================
    // Payee CRUD Tests
    // ============================================

    @Test
    fun `updatePayee modifies payee`() = runTest(timeout = 10.seconds) {
        val payeeId = payeeRepository.insertPayee(
            TestDataFactory.createTestPayee(name = "Original Name")
        )
        payeeRepository.notifyPayeesChanged()

        val initialState = waitForState(timeout = 10.seconds) { it.payees.isNotEmpty() }
        val originalPayee = initialState.payees[0].payee
        assertEquals("Original Name", originalPayee.name)

        viewModel.updatePayee(originalPayee.copy(name = "Updated Name"))
        val state = waitForState(timeout = 10.seconds) { it.payees.isNotEmpty() && it.payees[0].payee.name == "Updated Name" }
        assertEquals("Updated Name", state.payees[0].payee.name)

    }

    @Test
    fun `setDefaultCategory sets payee default category`() = runTest(timeout = 10.seconds) {
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Groceries", type = CategoryType.EXPENSE)
        )
        val payeeId = payeeRepository.insertPayee(
            TestDataFactory.createTestPayee(name = "Safeway", defaultCategoryId = null)
        )

        categoryRepository.notifyCategoriesChanged()
        payeeRepository.notifyPayeesChanged()

        val initialState = waitForState(timeout = 10.seconds) { it.payees.size == 1 && it.categories.size == 1 }

        assertNull(initialState.payees[0].payee.defaultCategoryId)

        viewModel.setDefaultCategory(payeeId, categoryId)
        val state = waitForState(timeout = 10.seconds) { it.payees.isNotEmpty() && it.payees[0].payee.defaultCategoryId == categoryId }
        assertEquals(categoryId, state.payees[0].payee.defaultCategoryId)

    }

    @Test
    fun `renamePayee changes payee name`() = runTest(timeout = 10.seconds) {
        val payeeId = payeeRepository.insertPayee(
            TestDataFactory.createTestPayee(name = "Old Name")
        )
        payeeRepository.notifyPayeesChanged()

        waitForState(timeout = 10.seconds) { !it.isLoading }

        viewModel.renamePayee(payeeId, "New Name")
        val state = waitForState(timeout = 10.seconds) { it.payees.isNotEmpty() && it.payees[0].payee.name == "New Name" }
        assertEquals("New Name", state.payees[0].payee.name)

    }

    @Test
    fun `deletePayee removes payee`() = runTest(timeout = 10.seconds) {
        val payeeId = payeeRepository.insertPayee(
            TestDataFactory.createTestPayee(name = "To Delete")
        )
        payeeRepository.notifyPayeesChanged()

        val initialState = waitForState(timeout = 10.seconds) { it.payees.size == 1 }
        assertEquals(1, initialState.payees.size)

        viewModel.deletePayee(payeeId)
        val state = waitForState(timeout = 10.seconds) { it.payees.size == 0 }
        assertEquals(0, state.payees.size)

    }

    // ============================================
    // Merge Functionality Tests
    // ============================================

    @Test
    fun `mergePayees combines two payees`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())

        val payee1Id = payeeRepository.insertPayee(
            TestDataFactory.createTestPayee(name = "Amazon.com")
        )
        val payee2Id = payeeRepository.insertPayee(
            TestDataFactory.createTestPayee(name = "Amazon")
        )

        // Add transactions to both payees
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payee1Id, amount = -1000)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payee2Id, amount = -2000)
        )

        transactionRepository.notifyTransactionsChanged()
        payeeRepository.notifyPayeesChanged()

        val initialState = waitForState(timeout = 10.seconds) { it.payees.size == 2 }
        assertEquals(2, initialState.payees.size)

        // Merge payee2 into payee1
        viewModel.mergePayees(payee2Id, payee1Id)
        val state = waitForState(timeout = 10.seconds) { it.payees.size == 1 }
        // Should only have 1 payee now
        assertEquals(1, state.payees.size)
        assertEquals(payee1Id, state.payees[0].payee.id)

    }

    @Test
    fun `mergePayees updates transaction count`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())

        val payee1Id = payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Target 1"))
        val payee2Id = payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Target 2"))

        // Payee1: 2 transactions, Payee2: 3 transactions
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payee1Id, amount = -1000)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payee1Id, amount = -1500)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payee2Id, amount = -2000)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payee2Id, amount = -2500)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payee2Id, amount = -3000)
        )

        transactionRepository.notifyTransactionsChanged()
        payeeRepository.notifyPayeesChanged()

        val initialState = waitForState(timeout = 10.seconds) { !it.isLoading && it.payees.size == 2 }
        val payee1Before = initialState.payees.find { it.payee.id == payee1Id }
        assertNotNull(payee1Before)
        assertEquals(2, payee1Before.transactionCount)

        // Merge payee2 into payee1
        viewModel.mergePayees(payee2Id, payee1Id)
        val state = waitForState(timeout = 10.seconds) {
            val p1 = it.payees.find { p -> p.payee.id == payee1Id }
            p1 != null && p1.transactionCount == 5L && it.payees.size == 1
        }
        val mergedPayee = state.payees.find { it.payee.id == payee1Id }
        assertNotNull(mergedPayee)
        // Should have 5 transactions total (2 + 3)
        assertEquals(5, mergedPayee.transactionCount)

    }

    // ============================================
    // Reactive Updates Tests
    // ============================================

    @Test
    fun `state updates when payee stats change from transaction`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val payeeId = payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Coffee Shop"))

        payeeRepository.notifyPayeesChanged()

        waitForState(timeout = 10.seconds) { !it.isLoading }

        assertEquals(0, viewModel.uiState.value.payees[0].transactionCount)

        // Add transaction externally
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payeeId, amount = -500)
        )
        transactionRepository.notifyTransactionsChanged()
        payeeRepository.notifyPayeesChanged()

        val state = waitForState(timeout = 10.seconds) { it.payees.isNotEmpty() && it.payees[0].transactionCount == 1L }
        assertEquals(1, state.payees[0].transactionCount)

    }

    // ============================================
    // Utility Tests
    // ============================================

    @Test
    fun `getCategoryName returns correct category name`() = runTest(timeout = 10.seconds) {
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        categoryRepository.notifyCategoriesChanged()

        waitForState(timeout = 10.seconds) { it.categories.isNotEmpty() }

        val name = viewModel.getCategoryName(categoryId)
        assertEquals("Food", name)

    }

    @Test
    fun `getCategoryName returns None for null`() {
        val name = viewModel.getCategoryName(null)
        assertEquals("None", name)

    }

    @Test
    fun `getCategoryName returns Unknown for invalid ID`() {
        val name = viewModel.getCategoryName(99999L)
        assertEquals("Unknown", name)

    }
}
