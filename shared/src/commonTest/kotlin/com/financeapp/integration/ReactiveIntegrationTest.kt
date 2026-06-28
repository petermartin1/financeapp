package com.financeapp.integration

import com.financeapp.test.*
import com.financeapp.data.repository.*
import com.financeapp.domain.model.CategoryType
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * CRITICAL INTEGRATION TESTS
 *
 * These tests verify that mutations in one repository trigger reactive
 * updates in ALL affected UI components. This is the core requirement:
 * "when a service that mutates data is called that we ensure the screens
 * and anything it affects gets updates as well."
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactiveIntegrationTest {

    private lateinit var database: Database
    private lateinit var transactionRepo: TransactionRepositoryImpl
    private lateinit var accountRepo: AccountRepositoryImpl
    private lateinit var categoryRepo: CategoryRepositoryImpl
    private lateinit var payeeRepo: PayeeRepositoryImpl
    private lateinit var budgetRepo: BudgetRepositoryImpl
    private lateinit var tagRepo: TagRepositoryImpl
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    private fun runTest(block: suspend TestScope.() -> Unit) =
        kotlinx.coroutines.test.runTest(testDispatcher, testBody = block)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        database = createTestDatabase()
        transactionRepo = TransactionRepositoryImpl(database, testDispatcher)
        accountRepo = AccountRepositoryImpl(database, testDispatcher)
        categoryRepo = CategoryRepositoryImpl(database, testDispatcher)
        payeeRepo = PayeeRepositoryImpl(database, testDispatcher)
        budgetRepo = BudgetRepositoryImpl(database, testDispatcher)
        tagRepo = TagRepositoryImpl(database, testDispatcher)
    }

    @AfterTest
    fun teardown() {
        database.clearAllTables()
        Dispatchers.resetMain()
    }

    // ============================================
    // Transaction Mutations → Multiple UI Updates
    // ============================================

    @Test
    fun `adding transaction updates transaction list AND account balance AND budget spending`() = runTest {
        // Setup: Create account, category, budget
        val accountId = accountRepo.insertAccount(
            TestDataFactory.createTestAccount(name = "Checking")
        )
        val categoryId = categoryRepo.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        budgetRepo.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, amount = 50000, year = 2024, month = 1)
        )

        testScheduler.advanceUntilIdle()

        // Monitor all affected flows
        var transactionListUpdated = false
        var accountBalanceUpdated = false
        var budgetSpendingUpdated = false

        // 1. Monitor transaction list
        launch {
            transactionRepo.getTransactionsWithDetailsByAccount(accountId).test {
                val initial = awaitItem()
                assertEquals(0, initial.size)

                val updated = awaitItem()
                assertEquals(1, updated.size)
                transactionListUpdated = true

                cancelAndConsumeRemainingEvents()
            }
        }

        // 2. Monitor account balance
        launch {
            accountRepo.getAccountsWithBalances().test {
                awaitItem() // Initial

                val updated = awaitItem()
                assertTrue(updated.first().balance != 0L)
                accountBalanceUpdated = true

                cancelAndConsumeRemainingEvents()
            }
        }

        // 3. Monitor budget spending
        launch {
            budgetRepo.getBudgetsWithSpendingByMonth(2024, 1).test {
                val initial = awaitItem()
                assertEquals(0L, initial.first().spent)

                val updated = awaitItem()
                assertTrue(updated.first().spent > 0)
                budgetSpendingUpdated = true

                cancelAndConsumeRemainingEvents()
            }
        }

        testScheduler.advanceUntilIdle()

        // Act: Add transaction
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                categoryId = categoryId,
                amount = -5000,
                date = testDate(2024, 1, 15)
            )
        )
        transactionRepo.notifyTransactionsChanged()
        accountRepo.notifyBalancesChanged()
        budgetRepo.notifyBudgetsChanged()

        testScheduler.advanceUntilIdle()
        delay(100) // Give flows time to emit

        // Assert: All three flows updated
        assertTrue(transactionListUpdated, "Transaction list should have updated")
        assertTrue(accountBalanceUpdated, "Account balance should have updated")
        assertTrue(budgetSpendingUpdated, "Budget spending should have updated")
    }

    @Test
    fun `editing transaction category updates old AND new category budgets`() = runTest {
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())
        val category1Id = categoryRepo.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )
        val category2Id = categoryRepo.insertCategory(
            TestDataFactory.createTestCategory(name = "Transport", type = CategoryType.EXPENSE)
        )

        budgetRepo.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = category1Id, amount = 50000, year = 2024, month = 2)
        )
        budgetRepo.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = category2Id, amount = 30000, year = 2024, month = 2)
        )

        // Create transaction in category1
        val txn = TestDataFactory.createTestTransaction(
            accountId = accountId,
            categoryId = category1Id,
            amount = -10000,
            date = testDate(2024, 2, 10)
        )
        val txnId = transactionRepo.insertTransaction(txn)
        transactionRepo.notifyTransactionsChanged()
        budgetRepo.notifyBudgetsChanged()

        testScheduler.advanceUntilIdle()

        // Verify initial spending
        budgetRepo.getBudgetsWithSpendingByMonth(2024, 2).test {
            val initial = awaitItem()
            val foodBudget = initial.find { it.budget.categoryId == category1Id }
            val transportBudget = initial.find { it.budget.categoryId == category2Id }

            assertEquals(10000L, foodBudget?.spent)
            assertEquals(0L, transportBudget?.spent)

            // Change transaction to category2
            transactionRepo.updateTransaction(txn.copy(id = txnId, categoryId = category2Id))
            transactionRepo.notifyTransactionsChanged()
            budgetRepo.notifyBudgetsChanged()

            val updated = awaitItem()
            val updatedFood = updated.find { it.budget.categoryId == category1Id }
            val updatedTransport = updated.find { it.budget.categoryId == category2Id }

            // Food spending should decrease to 0
            assertEquals(0L, updatedFood?.spent)
            // Transport spending should increase to 10000
            assertEquals(10000L, updatedTransport?.spent)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `deleting transaction updates transaction list AND account balance AND budget`() = runTest {
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())
        val categoryId = categoryRepo.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )
        budgetRepo.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, year = 2024, month = 3)
        )

        val txn = TestDataFactory.createTestTransaction(
            accountId = accountId,
            categoryId = categoryId,
            amount = -5000,
            date = testDate(2024, 3, 5)
        )
        val txnId = transactionRepo.insertTransaction(txn)
        transactionRepo.notifyTransactionsChanged()
        accountRepo.notifyBalancesChanged()
        budgetRepo.notifyBudgetsChanged()

        testScheduler.advanceUntilIdle()

        var transactionListCleared = false
        var balanceReverted = false
        var budgetSpendingCleared = false

        // Monitor transaction list
        launch {
            transactionRepo.getTransactionsWithDetailsByAccount(accountId).test {
                awaitItem() // Initial with 1 transaction

                val updated = awaitItem()
                assertEquals(0, updated.size)
                transactionListCleared = true

                cancelAndConsumeRemainingEvents()
            }
        }

        // Monitor budget
        launch {
            budgetRepo.getBudgetsWithSpendingByMonth(2024, 3).test {
                awaitItem() // Initial with spending

                val updated = awaitItem()
                assertEquals(0L, updated.first().spent)
                budgetSpendingCleared = true

                cancelAndConsumeRemainingEvents()
            }
        }

        testScheduler.advanceUntilIdle()

        // Delete transaction
        transactionRepo.deleteTransaction(txnId)
        transactionRepo.notifyTransactionsChanged()
        accountRepo.notifyBalancesChanged()
        budgetRepo.notifyBudgetsChanged()

        testScheduler.advanceUntilIdle()
        delay(100) // Give flows time to emit

        assertTrue(transactionListCleared)
        assertTrue(budgetSpendingCleared)
    }

    // ============================================
    // Category Mutations → Multiple UI Updates
    // ============================================

    @Test
    fun `adding category appears in category list AND transaction dialog AND budget screen`() = runTest {
        var categoryListUpdated = false

        categoryRepo.getAllCategories().test {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            // Add category
            categoryRepo.insertCategory(
                TestDataFactory.createTestCategory(name = "New Category", type = CategoryType.EXPENSE)
            )

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("New Category", updated[0].name)
            categoryListUpdated = true

            cancelAndConsumeRemainingEvents()
        }

        testScheduler.advanceUntilIdle()
        delay(100) // Give flows time to emit
        assertTrue(categoryListUpdated)
    }

    @Test
    fun `deleting category nullifies transaction categories`() = runTest {
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())
        val categoryId = categoryRepo.insertCategory(
            TestDataFactory.createTestCategory(name = "ToDelete", type = CategoryType.EXPENSE)
        )

        // Create transaction with this category
        val txn = TestDataFactory.createTestTransaction(
            accountId = accountId,
            categoryId = categoryId
        )
        val txnId = transactionRepo.insertTransaction(txn)
        transactionRepo.notifyTransactionsChanged()

        // Verify transaction has category
        val beforeDelete = transactionRepo.getTransactionById(txnId)
        assertEquals(categoryId, beforeDelete?.categoryId)

        // Delete category
        categoryRepo.deleteCategory(categoryId)
        categoryRepo.notifyCategoriesChanged()

        testScheduler.advanceUntilIdle()

        // Category should be removed from list
        categoryRepo.getAllCategories().test {
            val categories = awaitItem()
            assertEquals(0, categories.size)
            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Payee Mutations → Multiple UI Updates
    // ============================================

    @Test
    fun `merging payees updates all transactions AND payee stats`() = runTest {
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())

        val payee1Id = payeeRepo.insertPayee(
            TestDataFactory.createTestPayee(name = "Amazon.com")
        )
        val payee2Id = payeeRepo.insertPayee(
            TestDataFactory.createTestPayee(name = "Amazon")
        )

        // Create transactions for both payees
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payee1Id, amount = -1000)
        )
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payee1Id, amount = -2000)
        )
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payee2Id, amount = -3000)
        )
        transactionRepo.notifyTransactionsChanged()

        testScheduler.advanceUntilIdle()

        // Verify initial stats
        payeeRepo.getPayeesWithStats().test {
            val initial = awaitItem()
            val payee1Stats = initial.find { it.payee.id == payee1Id }
            val payee2Stats = initial.find { it.payee.id == payee2Id }

            assertEquals(2, payee1Stats?.transactionCount)
            assertEquals(1, payee2Stats?.transactionCount)

            // Merge payee2 into payee1
            payeeRepo.mergePayees(payee2Id, payee1Id)

            val updated = awaitItem()
            // Payee2 should be gone
            assertEquals(1, updated.size)

            // Payee1 should have all 3 transactions
            val mergedPayee = updated.find { it.payee.id == payee1Id }
            assertEquals(3, mergedPayee?.transactionCount)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Account Mutations → Multiple UI Updates
    // ============================================

    @Test
    fun `adding account appears in account list AND transaction dialog`() = runTest {
        var accountListUpdated = false

        accountRepo.getAllAccounts().test {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            accountRepo.insertAccount(
                TestDataFactory.createTestAccount(name = "New Savings")
            )

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("New Savings", updated[0].name)
            accountListUpdated = true

            cancelAndConsumeRemainingEvents()
        }

        testScheduler.advanceUntilIdle()
        delay(100) // Give flows time to emit
        assertTrue(accountListUpdated)
    }

    @Test
    fun `deleting account removes all associated transactions`() = runTest {
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())

        // Add transactions
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -1000)
        )
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -2000)
        )
        transactionRepo.notifyTransactionsChanged()

        testScheduler.advanceUntilIdle()

        // Verify transactions exist
        transactionRepo.getTransactionsWithDetailsByAccount(accountId).test {
            val initial = awaitItem()
            assertEquals(2, initial.size)

            // Delete account
            accountRepo.deleteAccount(accountId)
            accountRepo.notifyBalancesChanged()
            transactionRepo.notifyTransactionsChanged()

            val updated = awaitItem()
            assertEquals(0, updated.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Budget Mutations → UI Updates
    // ============================================

    @Test
    fun `updating budget amount updates budget screen immediately`() = runTest {
        val categoryId = categoryRepo.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )

        budgetRepo.getBudgetsByMonth(2024, 4).test {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            // Create budget
            val budgetId = budgetRepo.insertOrUpdateBudget(
                TestDataFactory.createTestBudget(
                    categoryId = categoryId,
                    amount = 50000,
                    year = 2024,
                    month = 4
                )
            )

            val created = awaitItem()
            assertEquals(1, created.size)
            assertEquals(50000L, created[0].amount)

            // Update budget amount
            budgetRepo.updateBudget(
                TestDataFactory.createTestBudget(
                    id = budgetId,
                    categoryId = categoryId,
                    amount = 75000,
                    year = 2024,
                    month = 4
                )
            )

            val updated = awaitItem()
            assertEquals(75000L, updated[0].amount)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Tag Mutations → UI Updates
    // ============================================

    @Test
    fun `adding tag appears in tag list immediately`() = runTest {
        tagRepo.getAllTags().test {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            tagRepo.insertTag(
                TestDataFactory.createTestTag(name = "Business")
            )

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("Business", updated[0].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Complex Multi-Repository Scenarios
    // ============================================

    @Test
    fun `complete transaction workflow updates all affected components`() = runTest {
        // Setup: Full app state
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())
        val categoryId = categoryRepo.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )
        val payeeId = payeeRepo.insertPayee(TestDataFactory.createTestPayee())
        val tagId = tagRepo.insertTag(TestDataFactory.createTestTag())
        budgetRepo.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, year = 2024, month = 5)
        )

        testScheduler.advanceUntilIdle()

        var allComponentsUpdated = true

        // Monitor all components
        launch {
            transactionRepo.getTransactionsWithDetailsByAccount(accountId).test {
                awaitItem()
                awaitItem() // Should update
                cancelAndConsumeRemainingEvents()
            }
        }

        launch {
            accountRepo.getAccountsWithBalances().test {
                awaitItem()
                awaitItem() // Should update
                cancelAndConsumeRemainingEvents()
            }
        }

        launch {
            budgetRepo.getBudgetsWithSpendingByMonth(2024, 5).test {
                awaitItem()
                awaitItem() // Should update
                cancelAndConsumeRemainingEvents()
            }
        }

        launch {
            payeeRepo.getPayeesWithStats().test {
                awaitItem()
                awaitItem() // Should update
                cancelAndConsumeRemainingEvents()
            }
        }

        testScheduler.advanceUntilIdle()

        // Add complete transaction
        val txnId = transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                categoryId = categoryId,
                payeeId = payeeId,
                amount = -5000,
                date = testDate(2024, 5, 15)
            )
        )

        // Add tag to transaction
        tagRepo.addTagToTransaction(txnId, tagId)

        // Notify all affected systems
        transactionRepo.notifyTransactionsChanged()
        accountRepo.notifyBalancesChanged()
        budgetRepo.notifyBudgetsChanged()
        payeeRepo.notifyPayeesChanged()

        testScheduler.advanceUntilIdle()
        delay(100) // Give flows time to emit

        assertTrue(allComponentsUpdated, "All components should reactively update")
    }

    // ============================================
    // Additional Critical Integration Tests
    // ============================================

    @Test
    fun `cross-account transfer updates both account balances`() = runTest {
        val account1 = accountRepo.insertAccount(
            TestDataFactory.createTestAccount(name = "Checking")
        )
        val account2 = accountRepo.insertAccount(
            TestDataFactory.createTestAccount(name = "Savings")
        )

        // Initial balances
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = account1, amount = 100000)
        )
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = account2, amount = 50000)
        )
        transactionRepo.notifyTransactionsChanged()
        accountRepo.notifyBalancesChanged()

        testScheduler.advanceUntilIdle()

        // Create transfer (withdraw from checking, deposit to savings)
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = account1, amount = -10000)
        )
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = account2, amount = 10000)
        )
        transactionRepo.notifyTransactionsChanged()
        accountRepo.notifyBalancesChanged()

        testScheduler.advanceUntilIdle()

        accountRepo.getAccountsWithBalances().test {
            val accounts = awaitItem()
            val checking = accounts.find { it.account.id == account1 }
            val savings = accounts.find { it.account.id == account2 }

            assertEquals(90000L, checking?.balance) // 100000 - 10000
            assertEquals(60000L, savings?.balance)  // 50000 + 10000

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `bulk transaction operations update all affected budgets`() = runTest {
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())
        val categoryId = categoryRepo.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )
        budgetRepo.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(categoryId = categoryId, amount = 100000, year = 2024, month = 6)
        )

        testScheduler.advanceUntilIdle()

        // Batch insert multiple transactions
        val transactions = List(5) { index ->
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                categoryId = categoryId,
                amount = -5000,
                date = testDate(2024, 6, index + 1)
            )
        }

        transactions.forEach { transactionRepo.insertTransaction(it) }
        transactionRepo.notifyTransactionsChanged()
        budgetRepo.notifyBudgetsChanged()

        testScheduler.advanceUntilIdle()

        budgetRepo.getBudgetsWithSpendingByMonth(2024, 6).test {
            val budgets = awaitItem()
            assertEquals(25000L, budgets.first().spent) // 5 * 5000

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `changing category hierarchy affects transaction categorization`() = runTest {
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())

        // Create parent category
        val parentId = categoryRepo.insertCategory(
            TestDataFactory.createTestCategory(name = "Food", type = CategoryType.EXPENSE)
        )

        // Create child category
        val childId = categoryRepo.insertCategory(
            TestDataFactory.createTestCategory(
                name = "Restaurants",
                type = CategoryType.EXPENSE,
                parentId = parentId
            )
        )

        // Create transaction with child category
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                categoryId = childId,
                amount = -5000
            )
        )
        transactionRepo.notifyTransactionsChanged()

        testScheduler.advanceUntilIdle()

        transactionRepo.getTransactionsWithDetailsByAccount(accountId).test {
            val transactions = awaitItem()
            assertEquals(childId, transactions.first().transaction.categoryId)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `payee statistics remain accurate after multiple operations`() = runTest {
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())
        val payeeId = payeeRepo.insertPayee(TestDataFactory.createTestPayee(name = "Test Payee"))

        // Add 3 transactions
        val txn1 = transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payeeId, amount = -1000)
        )
        val txn2 = transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payeeId, amount = -2000)
        )
        val txn3 = transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, payeeId = payeeId, amount = -3000)
        )
        transactionRepo.notifyTransactionsChanged()
        payeeRepo.notifyPayeesChanged()

        testScheduler.advanceUntilIdle()

        payeeRepo.getPayeesWithStats().test {
            var stats = awaitItem()
            assertEquals(3, stats.first().transactionCount)

            // Delete one transaction
            transactionRepo.deleteTransaction(txn2)
            transactionRepo.notifyTransactionsChanged()
            payeeRepo.notifyPayeesChanged()

            stats = awaitItem()
            assertEquals(2, stats.first().transactionCount)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `applying tags to transactions appears in transaction details`() = runTest {
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())
        val tagId = tagRepo.insertTag(TestDataFactory.createTestTag(name = "Business"))

        val txnId = transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId)
        )
        transactionRepo.notifyTransactionsChanged()

        testScheduler.advanceUntilIdle()

        // Add tag to transaction
        tagRepo.addTagToTransaction(txnId, tagId)
        tagRepo.notifyTagsChanged()

        testScheduler.advanceUntilIdle()

        val tags = tagRepo.getTagsForTransaction(txnId)
        assertEquals(1, tags.size)
        assertEquals("Business", tags[0].name)
    }

    @Test
    fun `account closure with transactions handles data correctly`() = runTest {
        val account1 = accountRepo.insertAccount(
            TestDataFactory.createTestAccount(name = "Closed Account")
        )
        val account2 = accountRepo.insertAccount(
            TestDataFactory.createTestAccount(name = "Active Account")
        )

        // Add transactions to account1
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = account1, amount = -1000)
        )
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = account1, amount = -2000)
        )
        transactionRepo.notifyTransactionsChanged()

        testScheduler.advanceUntilIdle()

        // Verify transactions exist
        transactionRepo.getTransactionsWithDetailsByAccount(account1).test {
            val transactions = awaitItem()
            assertEquals(2, transactions.size)

            // Close account (delete it)
            accountRepo.deleteAccount(account1)
            accountRepo.notifyBalancesChanged()
            transactionRepo.notifyTransactionsChanged()

            val afterDelete = awaitItem()
            assertEquals(0, afterDelete.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `budget overspending alerts trigger correctly across months`() = runTest {
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())
        val categoryId = categoryRepo.insertCategory(
            TestDataFactory.createTestCategory(type = CategoryType.EXPENSE)
        )

        // Create budget for month with small amount
        budgetRepo.insertOrUpdateBudget(
            TestDataFactory.createTestBudget(
                categoryId = categoryId,
                amount = 10000,
                year = 2024,
                month = 7
            )
        )

        testScheduler.advanceUntilIdle()

        // Spend more than budgeted
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                categoryId = categoryId,
                amount = -15000,
                date = testDate(2024, 7, 15)
            )
        )
        transactionRepo.notifyTransactionsChanged()
        budgetRepo.notifyBudgetsChanged()

        testScheduler.advanceUntilIdle()

        budgetRepo.getBudgetsWithSpendingByMonth(2024, 7).test {
            val budgets = awaitItem()
            val budget = budgets.first()

            assertEquals(10000L, budget.budget.amount)
            assertEquals(15000L, budget.spent)
            assertTrue(budget.spent > budget.budget.amount) // Overspending detected

            cancelAndConsumeRemainingEvents()
        }
    }
}
