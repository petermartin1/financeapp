package com.financeapp.data.repository

import com.financeapp.test.*
import com.financeapp.domain.repository.BudgetRepository
import com.financeapp.domain.repository.CategoryRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.model.Budget
import com.financeapp.domain.model.CategoryType
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.Database
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetRepositoryTest {
    private lateinit var database: Database
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var accountRepository: AccountRepository
    private var testAccountId: Long = 0
    private var testCategoryId: Long = 0
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        budgetRepository = BudgetRepositoryImpl(database, testDispatcher)
        categoryRepository = CategoryRepositoryImpl(database, testDispatcher)
        transactionRepository = TransactionRepositoryImpl(database, testDispatcher)
        accountRepository = AccountRepositoryImpl(database, testDispatcher)

        testAccountId = runBlocking {
            val account = TestDataFactory.createTestAccount()
            accountRepository.insertAccount(account)
        }

        testCategoryId = runBlocking {
            val category = TestDataFactory.createTestCategory(
                name = "Groceries",
                type = CategoryType.EXPENSE
            )
            categoryRepository.insertCategory(category)
        }
    }

    @AfterTest
    fun teardown() {
        database.clearAllTables()
    }

    // ============================================
    // Basic CRUD Operations
    // ============================================

    @Test
    fun `insertOrUpdateBudget should create new budget`() = runTest {
        val budget = Budget(
            id = 0,
            categoryId = testCategoryId,
            amount = 50000, // $500
            year = 2024,
            month = 1
        )

        val id = budgetRepository.insertOrUpdateBudget(budget)

        assertTrue(id > 0, "Budget ID should be positive")
        val retrieved = budgetRepository.getBudgetById(id)
        assertNotNull(retrieved)
        assertEquals(testCategoryId, retrieved.categoryId)
        assertEquals(50000L, retrieved.amount)
        assertEquals(2024, retrieved.year)
        assertEquals(1, retrieved.month)
    }

    @Test
    fun `insertOrUpdateBudget should update existing budget`() = runTest {
        val budget = Budget(
            id = 0,
            categoryId = testCategoryId,
            amount = 50000,
            year = 2024,
            month = 1
        )

        val id1 = budgetRepository.insertOrUpdateBudget(budget)

        // Insert again with same category/year/month but different amount
        val updated = Budget(
            id = 0,
            categoryId = testCategoryId,
            amount = 60000, // Updated amount
            year = 2024,
            month = 1
        )
        val id2 = budgetRepository.insertOrUpdateBudget(updated)

        // Should return same ID (update, not insert)
        assertEquals(id1, id2)

        val retrieved = budgetRepository.getBudgetById(id1)
        assertNotNull(retrieved)
        assertEquals(60000L, retrieved.amount)
    }

    @Test
    fun `getBudgetById should return null for non-existent budget`() = runTest {
        val result = budgetRepository.getBudgetById(99999L)
        assertNull(result)
    }

    @Test
    fun `updateBudget should modify existing budget`() = runTest {
        val budget = Budget(
            id = 0,
            categoryId = testCategoryId,
            amount = 50000,
            year = 2024,
            month = 1
        )
        val id = budgetRepository.insertOrUpdateBudget(budget)

        val updated = budget.copy(id = id, amount = 75000)
        budgetRepository.updateBudget(updated)

        val retrieved = budgetRepository.getBudgetById(id)
        assertNotNull(retrieved)
        assertEquals(75000L, retrieved.amount)
    }

    @Test
    fun `deleteBudget should remove budget from database`() = runTest {
        val budget = Budget(
            id = 0,
            categoryId = testCategoryId,
            amount = 50000,
            year = 2024,
            month = 1
        )
        val id = budgetRepository.insertOrUpdateBudget(budget)

        budgetRepository.deleteBudget(id)

        val retrieved = budgetRepository.getBudgetById(id)
        assertNull(retrieved)
    }

    // ============================================
    // Month-Based Queries
    // ============================================

    @Test
    fun `getBudgetsByMonth should return budgets for specific month`() = runTest {
        // Create budgets for different months
        budgetRepository.insertOrUpdateBudget(
            Budget(0, testCategoryId, 50000, 2024, 1) // January
        )
        budgetRepository.insertOrUpdateBudget(
            Budget(0, testCategoryId, 60000, 2024, 2) // February
        )

        delay(200)

        budgetRepository.getBudgetsByMonth(2024, 1).test(timeout = 5.seconds) {
            val budgets = awaitItem()
            assertEquals(1, budgets.size)
            assertEquals(1, budgets[0].month)
            assertEquals(50000L, budgets[0].amount)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getBudgetsByMonth should return empty for month with no budgets`() = runTest {
        budgetRepository.getBudgetsByMonth(2024, 12).test(timeout = 5.seconds) {
            val budgets = awaitItem()
            assertEquals(0, budgets.size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getBudgetForCategoryAndMonth should find specific budget`() = runTest {
        val budget = Budget(0, testCategoryId, 50000, 2024, 3)
        budgetRepository.insertOrUpdateBudget(budget)

        val found = budgetRepository.getBudgetForCategoryAndMonth(testCategoryId, 2024, 3)
        assertNotNull(found)
        assertEquals(testCategoryId, found.categoryId)
        assertEquals(2024, found.year)
        assertEquals(3, found.month)
    }

    @Test
    fun `getBudgetForCategoryAndMonth should return null when not found`() = runTest {
        val found = budgetRepository.getBudgetForCategoryAndMonth(testCategoryId, 2024, 12)
        assertNull(found)
    }

    // ============================================
    // Reactive Flow - getBudgetsByMonth
    // ============================================

    @Test
    fun `getBudgetsByMonth should emit updates when budget inserted`() = runTest {
        budgetRepository.getBudgetsByMonth(2024, 5).test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            budgetRepository.insertOrUpdateBudget(Budget(0, testCategoryId, 50000, 2024, 5))

            val updated = awaitItem()
            assertEquals(1, updated.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getBudgetsByMonth should emit updates when budget updated`() = runTest {
        val budget = Budget(0, testCategoryId, 50000, 2024, 6)
        val id = budgetRepository.insertOrUpdateBudget(budget)

        delay(200)

        budgetRepository.getBudgetsByMonth(2024, 6).test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)
            assertEquals(50000L, initial[0].amount)

            budgetRepository.updateBudget(budget.copy(id = id, amount = 75000))

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals(75000L, updated[0].amount)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getBudgetsByMonth should emit updates when budget deleted`() = runTest {
        val budget = Budget(0, testCategoryId, 50000, 2024, 7)
        val id = budgetRepository.insertOrUpdateBudget(budget)

        delay(300) // Increased for test isolation

        budgetRepository.getBudgetsByMonth(2024, 7).test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)

            budgetRepository.deleteBudget(id)

            val updated = awaitItem()
            assertEquals(0, updated.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Budgets with Spending
    // ============================================

    @Test
    fun `getBudgetsWithSpendingByMonth should calculate spending correctly`() = runTest {
        // Create budget
        budgetRepository.insertOrUpdateBudget(Budget(0, testCategoryId, 50000, 2024, 3))

        // Add expense transactions in March 2024
        val txn1 = TestDataFactory.createTestTransaction(
            accountId = testAccountId,
            categoryId = testCategoryId,
            amount = -10000, // $100 spent
            date = testDate(2024, 3, 5)
        )
        val txn2 = TestDataFactory.createTestTransaction(
            accountId = testAccountId,
            categoryId = testCategoryId,
            amount = -15000, // $150 spent
            date = testDate(2024, 3, 15)
        )

        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)
        budgetRepository.notifyBudgetsChanged()

        delay(200)

        budgetRepository.getBudgetsWithSpendingByMonth(2024, 3).test(timeout = 5.seconds) {
            val budgets = awaitItem()
            assertEquals(1, budgets.size)

            val budgetWithSpending = budgets[0]
            assertEquals(50000L, budgetWithSpending.budget.amount) // Budget: $500
            assertEquals(25000L, budgetWithSpending.spent) // Spent: $250
            assertEquals(25000L, budgetWithSpending.remaining) // Remaining: $250
            assertEquals(50, budgetWithSpending.percentUsed) // 50%

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getBudgetsWithSpendingByMonth should only count expenses in that month`() = runTest {
        budgetRepository.insertOrUpdateBudget(Budget(0, testCategoryId, 50000, 2024, 4))

        // Transaction in April (should count)
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = testAccountId,
                categoryId = testCategoryId,
                amount = -10000,
                date = testDate(2024, 4, 10)
            )
        )

        // Transaction in May (should NOT count)
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = testAccountId,
                categoryId = testCategoryId,
                amount = -20000,
                date = testDate(2024, 5, 10)
            )
        )

        budgetRepository.notifyBudgetsChanged()
        delay(200)

        budgetRepository.getBudgetsWithSpendingByMonth(2024, 4).test(timeout = 5.seconds) {
            val budgets = awaitItem()
            assertEquals(1, budgets.size)
            assertEquals(10000L, budgets[0].spent) // Only April transaction
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getBudgetsWithSpendingByMonth should only count negative amounts`() = runTest {
        budgetRepository.insertOrUpdateBudget(Budget(0, testCategoryId, 50000, 2024, 8))

        // Expense (negative - should count)
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = testAccountId,
                categoryId = testCategoryId,
                amount = -10000,
                date = testDate(2024, 8, 5)
            )
        )

        // Income (positive - should NOT count)
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = testAccountId,
                categoryId = testCategoryId,
                amount = 20000,
                date = testDate(2024, 8, 10)
            )
        )

        budgetRepository.notifyBudgetsChanged()
        delay(200)

        budgetRepository.getBudgetsWithSpendingByMonth(2024, 8).test(timeout = 5.seconds) {
            val budgets = awaitItem()
            assertEquals(1, budgets.size)
            assertEquals(10000L, budgets[0].spent) // Only expense amount
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getBudgetsWithSpendingByMonth should show zero spending for new budget`() = runTest {
        budgetRepository.insertOrUpdateBudget(Budget(0, testCategoryId, 50000, 2024, 9))

        delay(200)

        budgetRepository.getBudgetsWithSpendingByMonth(2024, 9).test(timeout = 5.seconds) {
            val budgets = awaitItem()
            assertEquals(1, budgets.size)
            assertEquals(0L, budgets[0].spent)
            assertEquals(50000L, budgets[0].remaining)
            assertEquals(0, budgets[0].percentUsed)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getBudgetsWithSpendingByMonth should handle over-budget spending`() = runTest {
        budgetRepository.insertOrUpdateBudget(Budget(0, testCategoryId, 50000, 2024, 10))

        // Spend more than budget
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = testAccountId,
                categoryId = testCategoryId,
                amount = -75000, // Spent $750, budget is $500
                date = testDate(2024, 10, 15)
            )
        )

        budgetRepository.notifyBudgetsChanged()
        delay(200)

        budgetRepository.getBudgetsWithSpendingByMonth(2024, 10).test(timeout = 5.seconds) {
            val budgets = awaitItem()
            assertEquals(1, budgets.size)
            assertEquals(75000L, budgets[0].spent)
            assertEquals(-25000L, budgets[0].remaining) // Negative remaining
            assertEquals(150, budgets[0].percentUsed) // Over 100%
            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Expense Categories
    // ============================================

    @Test
    fun `getExpenseCategories should return only expense categories`() = runTest {
        val expenseCat1 = TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        val expenseCat2 = TestDataFactory.createTestCategory(name = "Transport", type = CategoryType.EXPENSE)
        val incomeCat = TestDataFactory.createTestCategory(name = "Salary", type = CategoryType.INCOME)

        categoryRepository.insertCategory(expenseCat1)
        categoryRepository.insertCategory(expenseCat2)
        categoryRepository.insertCategory(incomeCat)

        val expenseCategories = budgetRepository.getExpenseCategories()

        // Should have 3 expense categories (2 new + 1 from setup)
        assertEquals(3, expenseCategories.size)
        assertTrue(expenseCategories.any { it.second == "Food" })
        assertTrue(expenseCategories.any { it.second == "Transport" })
        assertTrue(expenseCategories.any { it.second == "Groceries" }) // From setup
        assertFalse(expenseCategories.any { it.second == "Salary" })
    }

    // ============================================
    // Multiple Categories
    // ============================================

    @Test
    fun `should handle budgets for multiple categories in same month`() = runTest {
        val category2Id = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Transport", type = CategoryType.EXPENSE)
        )

        budgetRepository.insertOrUpdateBudget(Budget(0, testCategoryId, 50000, 2024, 11))
        budgetRepository.insertOrUpdateBudget(Budget(0, category2Id, 30000, 2024, 11))

        delay(200)

        budgetRepository.getBudgetsByMonth(2024, 11).test(timeout = 5.seconds) {
            val budgets = awaitItem()
            assertEquals(2, budgets.size)
            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `should handle December correctly (month 12)`() = runTest {
        budgetRepository.insertOrUpdateBudget(Budget(0, testCategoryId, 50000, 2024, 12))

        delay(200)

        budgetRepository.getBudgetsByMonth(2024, 12).test(timeout = 5.seconds) {
            val budgets = awaitItem()
            assertEquals(1, budgets.size)
            assertEquals(12, budgets[0].month)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `should handle year boundaries correctly`() = runTest {
        budgetRepository.insertOrUpdateBudget(Budget(0, testCategoryId, 50000, 2023, 12))
        budgetRepository.insertOrUpdateBudget(Budget(0, testCategoryId, 60000, 2024, 1))

        delay(200)

        budgetRepository.getBudgetsByMonth(2024, 1).test(timeout = 5.seconds) {
            val budgets = awaitItem()
            assertEquals(1, budgets.size)
            assertEquals(2024, budgets[0].year)
            assertEquals(60000L, budgets[0].amount)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `notifyBudgetsChanged should trigger manual refresh`() = runTest {
        budgetRepository.insertOrUpdateBudget(Budget(0, testCategoryId, 50000, 2024, 6))

        delay(400) // Increased delay for test isolation

        budgetRepository.getBudgetsByMonth(2024, 6).test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)

            // Manually trigger refresh
            budgetRepository.notifyBudgetsChanged()

            val refreshed = awaitItem()
            assertEquals(1, refreshed.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Performance Tests
    // ============================================

    @Test
    fun `should handle many budgets in same month`() = runTest {
        // Create 12 categories
        val categoryIds = (0 until 12).map { i ->
            categoryRepository.insertCategory(
                TestDataFactory.createTestCategory(name = "Category $i", type = CategoryType.EXPENSE)
            )
        }

        // Create budget for each category in same month
        categoryIds.forEach { catId ->
            budgetRepository.insertOrUpdateBudget(Budget(0, catId, 50000, 2024, 7))
        }

        delay(200)

        budgetRepository.getBudgetsByMonth(2024, 7).test(timeout = 5.seconds) {
            var budgets = awaitItem()
            while (budgets.size < 12) {
                budgets = awaitItem()
            }
            assertEquals(12, budgets.size)
            cancelAndConsumeRemainingEvents()
        }
    }
}
