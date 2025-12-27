package com.financeapp.ui.categories

import com.financeapp.test.*
import com.financeapp.data.repository.*
import com.financeapp.domain.model.CategoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
class CategoriesViewModelTest {

    private lateinit var database: Database
    private lateinit var categoryRepository: CategoryRepositoryImpl
    private lateinit var viewModel: CategoriesViewModel
    private val testDispatcher = StandardTestDispatcher()
    // Helper function to wait for a specific state condition
    private suspend fun waitForState(
        timeout: kotlin.time.Duration = 10.seconds,
        predicate: (CategoriesUiState) -> Boolean
    ): CategoriesUiState {
        var result: CategoriesUiState? = null
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
        categoryRepository = CategoryRepositoryImpl(database, testDispatcher)

        viewModel = CategoriesViewModel(categoryRepository)
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
        assertTrue(state.categories.isEmpty())

    }

    @Test
    fun `loadCategories updates state with empty list initially`() = runTest(timeout = 10.seconds) {
        val state = waitForState(timeout = 10.seconds) { !it.isLoading }
        assertFalse(state.isLoading)
        assertTrue(state.categories.isEmpty())

    }

    // ============================================
    // Category Loading Tests
    // ============================================

    @Test
    fun `loadCategories loads all categories`() = runTest(timeout = 10.seconds) {
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Salary", type = CategoryType.INCOME)
        )
        categoryRepository.notifyCategoriesChanged()

        val state = waitForState(timeout = 10.seconds) { it.categories.size == 2 }
        assertEquals(2, state.categories.size)
        assertTrue(state.categories.any { it.name == "Food" })
        assertTrue(state.categories.any { it.name == "Salary" })

    }

    @Test
    fun `loadCategories loads categories of all types`() = runTest(timeout = 10.seconds) {
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Expense", type = CategoryType.EXPENSE)
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Income", type = CategoryType.INCOME)
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Transfer", type = CategoryType.TRANSFER)
        )
        categoryRepository.notifyCategoriesChanged()

        val state = waitForState(timeout = 10.seconds) { it.categories.size == 3 }
        assertEquals(3, state.categories.size)
        assertTrue(state.categories.any { it.type == CategoryType.EXPENSE })
        assertTrue(state.categories.any { it.type == CategoryType.INCOME })
        assertTrue(state.categories.any { it.type == CategoryType.TRANSFER })

    }

    // ============================================
    // Category CRUD Tests
    // ============================================

    @Test
    fun `addCategory creates new category`() = runTest(timeout = 10.seconds) {
        val initialState = waitForState(timeout = 10.seconds) { it.categories.size == 0 }
        assertEquals(0, initialState.categories.size)

        viewModel.addCategory(
            name = "Groceries",
            type = CategoryType.EXPENSE,
            parentId = null,
            icon = "shopping_cart",
            color = "#4CAF50"
        )
        val state = waitForState(timeout = 10.seconds) { it.categories.size == 1 }
        assertEquals(1, state.categories.size)
        assertEquals("Groceries", state.categories[0].name)
        assertEquals(CategoryType.EXPENSE, state.categories[0].type)
        assertEquals("shopping_cart", state.categories[0].icon)
        assertEquals("#4CAF50", state.categories[0].color)

    }

    @Test
    fun `addCategory with null optional fields`() = runTest(timeout = 10.seconds) {
        viewModel.addCategory(
            name = "Miscellaneous",
            type = CategoryType.EXPENSE,
            parentId = null,
            icon = null,
            color = null
        )
        val state = waitForState(timeout = 10.seconds) { it.categories.size == 1 }
        assertEquals(1, state.categories.size)
        assertEquals("Miscellaneous", state.categories[0].name)
        assertNull(state.categories[0].icon)
        assertNull(state.categories[0].color)
        assertNull(state.categories[0].parentId)

    }

    @Test
    fun `addCategory with parent creates subcategory`() = runTest(timeout = 10.seconds) {
        // Create parent category
        val parentId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        categoryRepository.notifyCategoriesChanged()

        waitForState(timeout = 10.seconds) { !it.isLoading }

        // Create subcategory
        viewModel.addCategory(
            name = "Restaurants",
            type = CategoryType.EXPENSE,
            parentId = parentId,
            icon = null,
            color = null
        )
        val state = waitForState(timeout = 10.seconds) { it.categories.size == 2 }
        val subcategory = state.categories.find { it.name == "Restaurants" }
        assertNotNull(subcategory)
        assertEquals(parentId, subcategory.parentId)

    }

    @Test
    fun `deleteCategory removes category`() = runTest(timeout = 10.seconds) {
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "To Delete")
        )
        categoryRepository.notifyCategoriesChanged()

        val initialState = waitForState(timeout = 10.seconds) { it.categories.size == 1 }
        assertEquals(1, initialState.categories.size)

        viewModel.deleteCategory(categoryId)
        val state = waitForState(timeout = 10.seconds) { it.categories.size == 0 }
        assertEquals(0, state.categories.size)

    }

    // ============================================
    // Category Filtering Tests
    // ============================================

    @Test
    fun `getCategoriesByType returns only expense categories`() = runTest(timeout = 10.seconds) {
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Transport", type = CategoryType.EXPENSE)
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Salary", type = CategoryType.INCOME)
        )
        categoryRepository.notifyCategoriesChanged()

        waitForState(timeout = 10.seconds) { it.categories.size == 3 }

        val expenseCategories = viewModel.getCategoriesByType(CategoryType.EXPENSE)
        assertEquals(2, expenseCategories.size)
        assertTrue(expenseCategories.all { it.type == CategoryType.EXPENSE })
        assertTrue(expenseCategories.any { it.name == "Food" })
        assertTrue(expenseCategories.any { it.name == "Transport" })

    }

    @Test
    fun `getCategoriesByType returns only income categories`() = runTest(timeout = 10.seconds) {
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Salary", type = CategoryType.INCOME)
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Freelance", type = CategoryType.INCOME)
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        categoryRepository.notifyCategoriesChanged()

        waitForState(timeout = 10.seconds) { it.categories.size == 3 }

        val incomeCategories = viewModel.getCategoriesByType(CategoryType.INCOME)
        assertEquals(2, incomeCategories.size)
        assertTrue(incomeCategories.all { it.type == CategoryType.INCOME })

    }

    @Test
    fun `getCategoriesByType returns only transfer categories`() = runTest(timeout = 10.seconds) {
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Account Transfer", type = CategoryType.TRANSFER)
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        categoryRepository.notifyCategoriesChanged()

        waitForState(timeout = 10.seconds) { !it.isLoading }

        val transferCategories = viewModel.getCategoriesByType(CategoryType.TRANSFER)
        assertEquals(1, transferCategories.size)
        assertEquals(CategoryType.TRANSFER, transferCategories[0].type)

    }

    @Test
    fun `getCategoriesByType returns empty list for type with no categories`() = runTest(timeout = 10.seconds) {
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        categoryRepository.notifyCategoriesChanged()

        waitForState(timeout = 10.seconds) { !it.isLoading }

        val incomeCategories = viewModel.getCategoriesByType(CategoryType.INCOME)
        assertTrue(incomeCategories.isEmpty())

    }

    // ============================================
    // Reactive Updates Tests
    // ============================================

    @Test
    fun `state updates when category added externally`() = runTest(timeout = 10.seconds) {
        val initialState = waitForState(timeout = 10.seconds) { it.categories.size == 0 }
        assertEquals(0, initialState.categories.size)

        // Add category externally
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "External Category")
        )
        categoryRepository.notifyCategoriesChanged()

        val state = waitForState(timeout = 10.seconds) { it.categories.size == 1 }
        assertEquals(1, state.categories.size)
        assertEquals("External Category", state.categories[0].name)

    }

    @Test
    fun `state updates when category deleted externally`() = runTest(timeout = 10.seconds) {
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory()
        )
        categoryRepository.notifyCategoriesChanged()

        val initialState = waitForState(timeout = 10.seconds) { it.categories.size == 1 }
        assertEquals(1, initialState.categories.size)

        // Delete externally
        categoryRepository.deleteCategory(categoryId)
        categoryRepository.notifyCategoriesChanged()

        val state = waitForState(timeout = 10.seconds) { it.categories.size == 0 }
        assertEquals(0, state.categories.size)

    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `handles no categories gracefully`() = runTest(timeout = 10.seconds) {
        val state = waitForState(timeout = 10.seconds) { !it.isLoading }
        assertTrue(state.categories.isEmpty())
        assertFalse(state.isLoading)

    }

    @Test
    fun `handles multiple categories of same type`() = runTest(timeout = 10.seconds) {
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Transport", type = CategoryType.EXPENSE)
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Entertainment", type = CategoryType.EXPENSE)
        )
        categoryRepository.notifyCategoriesChanged()

        val state = waitForState(timeout = 10.seconds) { it.categories.size == 3 }
        assertEquals(3, state.categories.size)
        assertTrue(state.categories.all { it.type == CategoryType.EXPENSE })

    }

    @Test
    fun `handles hierarchical categories with parent and children`() = runTest(timeout = 10.seconds) {
        // Create parent
        val parentId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )

        // Create children
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(
                name = "Groceries",
                type = CategoryType.EXPENSE,
                parentId = parentId
            )
        )
        categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(
                name = "Restaurants",
                type = CategoryType.EXPENSE,
                parentId = parentId
            )
        )
        categoryRepository.notifyCategoriesChanged()

        val state = waitForState(timeout = 10.seconds) { it.categories.size == 3 }
        assertEquals(3, state.categories.size)

        val parent = state.categories.find { it.name == "Food" }
        val child1 = state.categories.find { it.name == "Groceries" }
        val child2 = state.categories.find { it.name == "Restaurants" }

        assertNotNull(parent)
        assertNotNull(child1)
        assertNotNull(child2)
        assertNull(parent.parentId)
        assertEquals(parentId, child1.parentId)
        assertEquals(parentId, child2.parentId)

    }
}
