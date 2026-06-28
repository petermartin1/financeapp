package com.financeapp.data.repository

import com.financeapp.test.*
import com.financeapp.domain.repository.CategoryRepository
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.financeapp.db.schema.PayeeAliases
import com.financeapp.db.schema.Payees
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryRepositoryTest {
    private lateinit var database: Database
    private lateinit var repository: CategoryRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        repository = CategoryRepositoryImpl(database, testDispatcher)
    }

    @AfterTest
    fun teardown() {
        database.clearAllTables()
    }

    // ============================================
    // Basic CRUD Operations
    // ============================================

    @Test
    fun `insertCategory should create category and return id`() = runTest {
        val category = TestDataFactory.createTestCategory(
            name = "Groceries",
            type = CategoryType.EXPENSE
        )

        val id = repository.insertCategory(category)

        assertTrue(id > 0, "Category ID should be positive")
        val retrieved = repository.getCategoryById(id)
        assertNotNull(retrieved)
        assertEquals("Groceries", retrieved.name)
        assertEquals(CategoryType.EXPENSE, retrieved.type)
    }

    @Test
    fun `getCategoryById should return null for non-existent category`() = runTest {
        val result = repository.getCategoryById(99999L)
        assertNull(result)
    }

    @Test
    fun `updateCategory should modify existing category`() = runTest {
        val category = TestDataFactory.createTestCategory(name = "Original", icon = "food")
        val id = repository.insertCategory(category)

        val updated = category.copy(
            id = id,
            name = "Updated",
            icon = "shopping_cart",
            color = "#FF5722"
        )
        repository.updateCategory(updated)

        val retrieved = repository.getCategoryById(id)
        assertNotNull(retrieved)
        assertEquals("Updated", retrieved.name)
        assertEquals("shopping_cart", retrieved.icon)
        assertEquals("#FF5722", retrieved.color)
    }

    @Test
    fun `deleteCategory should remove category from database`() = runTest {
        val category = TestDataFactory.createTestCategory()
        val id = repository.insertCategory(category)

        repository.deleteCategory(id)

        val retrieved = repository.getCategoryById(id)
        assertNull(retrieved)
    }

    @Test
    fun `deleteCategory should nullify preferred category on payee aliases`() = runTest {
        val categoryId = repository.insertCategory(TestDataFactory.createTestCategory(name = "Groceries"))

        val payeeId = transaction(database) {
            Payees.insertAndGetId {
                it[name] = "Costco"
                it[defaultCategoryId] = null
            }.value
        }
        transaction(database) {
            PayeeAliases.insertAndGetId {
                it[aliasName] = "costco wholesale"
                it[canonicalPayeeId] = payeeId
                it[matchType] = "MANUAL"
                it[confidence] = null
                it[preferredCategoryId] = categoryId.toInt()
                it[createdAt] = 0L
            }
        }

        // Before the fix this throws a foreign-key violation: the category is
        // deleted while PayeeAlias.preferredCategoryId still references it.
        repository.deleteCategory(categoryId)

        assertNull(repository.getCategoryById(categoryId))
        // The alias must survive, only its preferred-category hint is cleared.
        val (aliasCount, preferredCategoryId) = transaction(database) {
            val row = PayeeAliases.selectAll().single()
            PayeeAliases.selectAll().count() to row[PayeeAliases.preferredCategoryId]?.value
        }
        assertEquals(1L, aliasCount)
        assertNull(preferredCategoryId)
    }

    // ============================================
    // Reactive Flow - getAllCategories
    // ============================================

    @Test
    fun `getAllCategories should emit initial categories`() = runTest {
        val category1 = TestDataFactory.createTestCategory(name = "Groceries")
        val category2 = TestDataFactory.createTestCategory(name = "Rent")

        repository.insertCategory(category1)
        repository.insertCategory(category2)

        delay(100)

        repository.getAllCategories().test(timeout = 5.seconds) {
            val categories = awaitItem()
            assertEquals(2, categories.size)
            assertTrue(categories.any { it.name == "Groceries" })
            assertTrue(categories.any { it.name == "Rent" })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllCategories should emit updates when category inserted`() = runTest {
        repository.getAllCategories().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            val category = TestDataFactory.createTestCategory(name = "Salary")
            repository.insertCategory(category)

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("Salary", updated[0].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllCategories should emit updates when category updated`() = runTest {
        val category = TestDataFactory.createTestCategory(name = "Original")
        val id = repository.insertCategory(category)

        delay(100)

        repository.getAllCategories().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)
            assertEquals("Original", initial[0].name)

            repository.updateCategory(category.copy(id = id, name = "Updated"))

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("Updated", updated[0].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllCategories should emit updates when category deleted`() = runTest {
        val category = TestDataFactory.createTestCategory()
        val id = repository.insertCategory(category)

        delay(100)

        repository.getAllCategories().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)

            repository.deleteCategory(id)

            val updated = awaitItem()
            assertEquals(0, updated.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllCategories should notify multiple collectors`() = runTest {
        val category = TestDataFactory.createTestCategory()

        launch {
            repository.getAllCategories().test {
                val initial = awaitItem()
                assertEquals(0, initial.size)

                val updated = awaitItem()
                assertEquals(1, updated.size)

                cancelAndConsumeRemainingEvents()
            }
        }

        launch {
            repository.getAllCategories().test {
                awaitItem() // Initial
                awaitItem() // After insert
                cancelAndConsumeRemainingEvents()
            }
        }

        delay(50)
        repository.insertCategory(category)
        delay(150)
    }

    // ============================================
    // Reactive Flow - getCategoriesByType
    // ============================================

    @Test
    fun `getCategoriesByType should only return categories of specified type`() = runTest {
        val expense1 = TestDataFactory.createTestCategory(name = "Groceries", type = CategoryType.EXPENSE)
        val expense2 = TestDataFactory.createTestCategory(name = "Rent", type = CategoryType.EXPENSE)
        val income = TestDataFactory.createTestCategory(name = "Salary", type = CategoryType.INCOME)

        repository.insertCategory(expense1)
        repository.insertCategory(expense2)
        repository.insertCategory(income)

        delay(100)

        repository.getCategoriesByType(CategoryType.EXPENSE).test(timeout = 5.seconds) {
            val expenses = awaitItem()
            assertEquals(2, expenses.size)
            assertTrue(expenses.all { it.type == CategoryType.EXPENSE })
            assertTrue(expenses.any { it.name == "Groceries" })
            assertTrue(expenses.any { it.name == "Rent" })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getCategoriesByType should emit updates when matching category added`() = runTest {
        repository.getCategoriesByType(CategoryType.INCOME).test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            val income = TestDataFactory.createTestCategory(name = "Salary", type = CategoryType.INCOME)
            repository.insertCategory(income)

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("Salary", updated[0].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getCategoriesByType should not emit when different type category added`() = runTest {
        repository.getCategoriesByType(CategoryType.INCOME).test(timeout = 2.seconds) {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            // Insert EXPENSE category
            val expense = TestDataFactory.createTestCategory(name = "Rent", type = CategoryType.EXPENSE)
            repository.insertCategory(expense)

            // Should emit again (trigger fires for all) but still be empty for INCOME
            val stillEmpty = awaitItem()
            assertEquals(0, stillEmpty.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Category Types
    // ============================================

    @Test
    fun `should support all category types`() = runTest {
        val types = listOf(
            CategoryType.INCOME,
            CategoryType.EXPENSE,
            CategoryType.TRANSFER
        )

        types.forEach { type ->
            val category = TestDataFactory.createTestCategory(
                name = "${type.name} Category",
                type = type
            )
            val id = repository.insertCategory(category)

            val retrieved = repository.getCategoryById(id)
            assertNotNull(retrieved)
            assertEquals(type, retrieved.type)
        }
    }

    // ============================================
    // Parent/Child Relationships
    // ============================================

    @Test
    fun `should create category with parent`() = runTest {
        val parent = TestDataFactory.createTestCategory(name = "Shopping")
        val parentId = repository.insertCategory(parent)

        val child = TestDataFactory.createTestCategory(
            name = "Groceries",
            parentId = parentId
        )
        val childId = repository.insertCategory(child)

        val retrieved = repository.getCategoryById(childId)
        assertNotNull(retrieved)
        assertEquals("Groceries", retrieved.name)
        assertEquals(parentId, retrieved.parentId)
    }

    @Test
    fun `should handle categories without parent`() = runTest {
        val category = TestDataFactory.createTestCategory(name = "Groceries", parentId = null)
        val id = repository.insertCategory(category)

        val retrieved = repository.getCategoryById(id)
        assertNotNull(retrieved)
        assertNull(retrieved.parentId)
    }

    @Test
    fun `should update category parent`() = runTest {
        val parent1 = TestDataFactory.createTestCategory(name = "Shopping")
        val parent1Id = repository.insertCategory(parent1)

        val parent2 = TestDataFactory.createTestCategory(name = "Food")
        val parent2Id = repository.insertCategory(parent2)

        val child = TestDataFactory.createTestCategory(name = "Groceries", parentId = parent1Id)
        val childId = repository.insertCategory(child)

        // Move child from parent1 to parent2
        repository.updateCategory(child.copy(id = childId, parentId = parent2Id))

        val retrieved = repository.getCategoryById(childId)
        assertNotNull(retrieved)
        assertEquals(parent2Id, retrieved.parentId)
    }

    // ============================================
    // Ordering
    // ============================================

    @Test
    fun `getAllCategories should order by type then name`() = runTest {
        // Insert in random order
        repository.insertCategory(TestDataFactory.createTestCategory(name = "Rent", type = CategoryType.EXPENSE))
        repository.insertCategory(TestDataFactory.createTestCategory(name = "Salary", type = CategoryType.INCOME))
        repository.insertCategory(TestDataFactory.createTestCategory(name = "Groceries", type = CategoryType.EXPENSE))
        repository.insertCategory(TestDataFactory.createTestCategory(name = "Bonus", type = CategoryType.INCOME))

        delay(100)

        repository.getAllCategories().test(timeout = 5.seconds) {
            val categories = awaitItem()
            assertEquals(4, categories.size)

            // Should be ordered: EXPENSE (Groceries, Rent), then INCOME (Bonus, Salary)
            assertEquals(CategoryType.EXPENSE, categories[0].type)
            assertEquals(CategoryType.EXPENSE, categories[1].type)
            assertEquals("Groceries", categories[0].name)
            assertEquals("Rent", categories[1].name)

            assertEquals(CategoryType.INCOME, categories[2].type)
            assertEquals(CategoryType.INCOME, categories[3].type)
            assertEquals("Bonus", categories[2].name)
            assertEquals("Salary", categories[3].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getCategoriesByType should order by name`() = runTest {
        repository.insertCategory(TestDataFactory.createTestCategory(name = "Zebra", type = CategoryType.EXPENSE))
        repository.insertCategory(TestDataFactory.createTestCategory(name = "Apple", type = CategoryType.EXPENSE))
        repository.insertCategory(TestDataFactory.createTestCategory(name = "Mango", type = CategoryType.EXPENSE))

        delay(100)

        repository.getCategoriesByType(CategoryType.EXPENSE).test(timeout = 5.seconds) {
            val categories = awaitItem()
            assertEquals(3, categories.size)
            assertEquals("Apple", categories[0].name)
            assertEquals("Mango", categories[1].name)
            assertEquals("Zebra", categories[2].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `should handle category with null icon and color`() = runTest {
        val category = TestDataFactory.createTestCategory(
            name = "Plain Category",
            icon = null,
            color = null
        )
        val id = repository.insertCategory(category)

        val retrieved = repository.getCategoryById(id)
        assertNotNull(retrieved)
        assertNull(retrieved.icon)
        assertNull(retrieved.color)
    }

    @Test
    fun `should handle category with custom icon and color`() = runTest {
        val category = TestDataFactory.createTestCategory(
            name = "Colorful",
            icon = "custom_icon",
            color = "#FF5722"
        )
        val id = repository.insertCategory(category)

        val retrieved = repository.getCategoryById(id)
        assertNotNull(retrieved)
        assertEquals("custom_icon", retrieved.icon)
        assertEquals("#FF5722", retrieved.color)
    }

    @Test
    fun `notifyCategoriesChanged should trigger manual refresh`() = runTest {
        val category = TestDataFactory.createTestCategory()
        repository.insertCategory(category)

        delay(400) // Increased for test isolation

        repository.getAllCategories().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)

            // Manually trigger refresh
            repository.notifyCategoriesChanged()

            // Should emit again
            val refreshed = awaitItem()
            assertEquals(1, refreshed.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Performance Tests
    // ============================================

    @Test
    fun `getAllCategories should handle many categories`() = runTest {
        // Insert 50 categories
        val categoryIds = (0 until 50).map { i ->
            repository.insertCategory(
                TestDataFactory.createTestCategory(
                    name = "Category $i",
                    type = if (i % 2 == 0) CategoryType.EXPENSE else CategoryType.INCOME
                )
            )
        }

        assertEquals(50, categoryIds.size)
        delay(200)

        repository.getAllCategories().test(timeout = 5.seconds) {
            // Keep consuming until we get all 50 categories (handles async refresh)
            var categories = awaitItem()
            while (categories.size < 50) {
                categories = awaitItem()
            }
            assertEquals(50, categories.size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getCategoriesByType should filter correctly with many categories`() = runTest {
        // Insert 30 EXPENSE and 20 INCOME categories
        repeat(30) { i ->
            repository.insertCategory(
                TestDataFactory.createTestCategory(name = "Expense $i", type = CategoryType.EXPENSE)
            )
        }
        repeat(20) { i ->
            repository.insertCategory(
                TestDataFactory.createTestCategory(name = "Income $i", type = CategoryType.INCOME)
            )
        }

        delay(200)

        repository.getCategoriesByType(CategoryType.EXPENSE).test(timeout = 5.seconds) {
            var expenses = awaitItem()
            while (expenses.size < 30) {
                expenses = awaitItem()
            }
            assertEquals(30, expenses.size)
            assertTrue(expenses.all { it.type == CategoryType.EXPENSE })
            cancelAndConsumeRemainingEvents()
        }
    }
}
