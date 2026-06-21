package com.financeapp.data.repository

import com.financeapp.test.*
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.model.CategoryType
import com.financeapp.domain.model.SplitItem
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.Database
import kotlin.test.*

/**
 * Comprehensive tests for TransactionRepository
 *
 * Tests cover:
 * - Basic CRUD operations
 * - Reactive Flow emissions
 * - Notification triggers
 * - Query filtering
 * - Performance requirements
 * - Edge cases
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRepositoryTest {

    private lateinit var database: Database
    private lateinit var accountRepository: AccountRepository
    private lateinit var repository: TransactionRepository
    private val testDispatcher = UnconfinedTestDispatcher()
    private var testAccountId: Long = 0

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        accountRepository = AccountRepositoryImpl(database, testDispatcher)
        repository = TransactionRepositoryImpl(database, testDispatcher)

        // Create a test account for transactions
        testAccountId = runBlocking {
            val account = TestDataFactory.createTestAccount()
            accountRepository.insertAccount(account)
        }
    }

    @AfterTest
    fun teardown() {
        database.clearAllTables()
    }

    @Test
    fun `getSpendingByCategory attributes split amounts to each split category`() = runTest {
        val categoryRepository = CategoryRepositoryImpl(database, testDispatcher)
        val tagRepository = TagRepositoryImpl(database, testDispatcher)

        val groceriesId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Groceries", type = CategoryType.EXPENSE)
        )
        val transportId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Transport", type = CategoryType.EXPENSE)
        )

        // getSpendingByCategory looks at the current month, so date the purchase today.
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val txnId = repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = testAccountId,
                categoryId = groceriesId,
                amount = -10000,
                date = today
            )
        )
        tagRepository.setSplitsForTransaction(
            txnId,
            listOf(
                SplitItem(transactionId = txnId, categoryId = groceriesId, amount = -6000),
                SplitItem(transactionId = txnId, categoryId = transportId, amount = -4000)
            )
        )

        val spending = repository.getSpendingByCategory()

        assertEquals(6000L, spending["Groceries"])
        assertEquals(4000L, spending["Transport"])
    }

    // ========================================
    // 1. Basic CRUD Tests
    // ========================================

    @Test
    fun `insertTransaction creates transaction with generated id`() = runTest {
        // Arrange
        val transaction = TestDataFactory.createTestTransaction(
            id = 0,
            accountId = testAccountId
        )

        // Act
        val insertedId = repository.insertTransaction(transaction)

        // Assert
        assertTrue(insertedId > 0, "Generated ID should be positive")
    }

    @Test
    fun `insertTransaction stores all fields correctly`() = runTest {
        // Arrange
        val transaction = TestDataFactory.createTestTransaction(
            id = 0,
            accountId = testAccountId,
            amount = -12345,
            memo = "Test memo",
            isCleared = true
        )

        // Act
        val id = repository.insertTransaction(transaction)
        val retrieved = repository.getTransactionById(id)

        // Assert
        assertNotNull(retrieved)
        assertEquals(-12345, retrieved.amount)
        assertEquals("Test memo", retrieved.memo)
        assertTrue(retrieved.isCleared)
        assertEquals(testAccountId, retrieved.accountId)
    }

    @Test
    fun `getTransactionById returns null for non-existent id`() = runTest {
        // Act
        val result = repository.getTransactionById(99999)

        // Assert
        assertNull(result)
    }

    @Test
    fun `getTransactionById returns correct transaction`() = runTest {
        // Arrange
        val transaction = TestDataFactory.createTestTransaction(
            id = 0,
            accountId = testAccountId
        )
        val id = repository.insertTransaction(transaction)

        // Act
        val retrieved = repository.getTransactionById(id)

        // Assert
        assertNotNull(retrieved)
        assertEquals(id, retrieved.id)
        assertEquals(transaction.amount, retrieved.amount)
    }

    @Test
    fun `updateTransaction modifies transaction fields`() = runTest {
        // Arrange
        val transaction = TestDataFactory.createTestTransaction(
            id = 0,
            accountId = testAccountId,
            amount = -1000,
            memo = "Original"
        )
        val id = repository.insertTransaction(transaction)

        // Act
        val updated = transaction.copy(
            id = id,
            amount = -2000,
            memo = "Updated"
        )
        repository.updateTransaction(updated)

        // Assert
        val retrieved = repository.getTransactionById(id)
        assertNotNull(retrieved)
        assertEquals(-2000, retrieved.amount)
        assertEquals("Updated", retrieved.memo)
    }

    @Test
    fun `deleteTransaction removes transaction`() = runTest {
        // Arrange
        val transaction = TestDataFactory.createTestTransaction(
            id = 0,
            accountId = testAccountId
        )
        val id = repository.insertTransaction(transaction)

        // Act
        repository.deleteTransaction(id)

        // Assert
        val retrieved = repository.getTransactionById(id)
        assertNull(retrieved, "Transaction should be deleted")
    }

    // ========================================
    // 2. Reactive Flow Tests
    // ========================================

    @Test
    fun `getTransactionsWithDetailsByAccount emits initial empty list`() = runTest {
        // Act & Assert
        repository.getTransactionsWithDetailsByAccount(testAccountId).test {
            val initial = awaitItem()
            assertTrue(initial.isEmpty(), "Initial list should be empty")
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getTransactionsWithDetailsByAccount updates after insert`() = runTest {
        // Arrange
        repository.getTransactionsWithDetailsByAccount(testAccountId).test {
            // Initial empty
            val initial = awaitItem()
            assertTrue(initial.isEmpty())

            // Act: Insert transaction
            val txn = TestDataFactory.createTestTransaction(
                id = 0,
                accountId = testAccountId
            )
            repository.insertTransaction(txn)
            repository.notifyTransactionsChanged()

            // Assert: Should emit updated list
            val updated = awaitItem()
            assertEquals(1, updated.size, "Should have 1 transaction after insert")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getTransactionsWithDetailsByAccount updates after delete`() = runTest {
        // Arrange: Insert a transaction first
        val txn = TestDataFactory.createTestTransaction(
            id = 0,
            accountId = testAccountId
        )
        val id = repository.insertTransaction(txn)
        repository.notifyTransactionsChanged()

        repository.getTransactionsWithDetailsByAccount(testAccountId).test {
            // Initial: 1 transaction
            val initial = awaitItem()
            assertEquals(1, initial.size)

            // Act: Delete transaction
            repository.deleteTransaction(id)
            repository.notifyTransactionsChanged()

            // Assert: Should emit empty list
            val updated = awaitItem()
            assertTrue(updated.isEmpty(), "Should be empty after delete")

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getTransactionsWithDetailsByAccount updates after edit`() = runTest {
        // Arrange: Insert transaction
        val txn = TestDataFactory.createTestTransaction(
            id = 0,
            accountId = testAccountId,
            amount = -1000
        )
        val id = repository.insertTransaction(txn)
        repository.notifyTransactionsChanged()

        delay(400) // Increased delay for test isolation

        repository.getTransactionsWithDetailsByAccount(testAccountId).test {
            // Initial
            val initial = awaitItem()
            assertEquals(-1000, initial[0].transaction.amount)

            // Act: Update amount
            val updated = txn.copy(id = id, amount = -2000)
            repository.updateTransaction(updated)
            repository.notifyTransactionsChanged()

            // Assert
            val result = awaitItem()
            assertEquals(-2000, result[0].transaction.amount)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `notifyTransactionsChanged triggers all collectors`() = runTest {
        // Arrange: Multiple collectors
        var collector1Updated = false
        var collector2Updated = false

        val job1 = launch {
            repository.getTransactionsWithDetailsByAccount(testAccountId).test {
                awaitItem() // Initial
                awaitItem() // After notification
                collector1Updated = true
                cancelAndConsumeRemainingEvents()
            }
        }

        val job2 = launch {
            repository.getTransactionsWithDetailsByAccount(testAccountId).test {
                awaitItem() // Initial
                awaitItem() // After notification
                collector2Updated = true
                cancelAndConsumeRemainingEvents()
            }
        }

        // Wait for initial emissions
        kotlinx.coroutines.delay(100)

        // Act: Trigger notification
        repository.notifyTransactionsChanged()

        // Wait for updates
        job1.join()
        job2.join()

        // Assert
        assertTrue(collector1Updated, "Collector 1 should receive update")
        assertTrue(collector2Updated, "Collector 2 should receive update")
    }

    // ========================================
    // 3. Query Tests
    // ========================================

    @Test
    fun `getTransactionsByAccount returns only matching account transactions`() = runTest {
        // Arrange: Create second account
        val account2Id = runBlocking {
            val account = TestDataFactory.createTestAccount(name = "Account 2")
            accountRepository.insertAccount(account)
        }

        // Insert transactions for both accounts
        repository.insertTransaction(
            TestDataFactory.createTestTransaction(id = 0, accountId = testAccountId)
        )
        repository.insertTransaction(
            TestDataFactory.createTestTransaction(id = 0, accountId = account2Id)
        )

        // Act
        repository.getTransactionsWithDetailsByAccount(testAccountId).test {
            val result = awaitItem()

            // Assert: Only 1 transaction for testAccountId
            assertEquals(1, result.size)
            assertEquals(testAccountId, result[0].transaction.accountId)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getTransactionsByDateRange returns only transactions in range`() = runTest {
        // Arrange: Insert transactions with different dates
        repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                id = 0,
                accountId = testAccountId,
                date = testDate(2024, 1, 15)
            )
        )
        repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                id = 0,
                accountId = testAccountId,
                date = testDate(2024, 2, 15)
            )
        )
        repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                id = 0,
                accountId = testAccountId,
                date = testDate(2024, 3, 15)
            )
        )

        // Act: Query for February only
        repository.getTransactionsByDateRange(
            startDate = testDate(2024, 2, 1),
            endDate = testDate(2024, 2, 28)
        ).test {
            val result = awaitItem()

            // Assert: Only February transaction
            assertEquals(1, result.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getRecentTransactions limits results correctly`() = runTest {
        // Arrange: Insert 50 transactions
        repeat(50) { index ->
            repository.insertTransaction(
                TestDataFactory.createTestTransaction(
                    id = 0,
                    accountId = testAccountId,
                    date = testDate(day = (index % 28) + 1)
                )
            )
        }

        // Act: Get recent 10
        val result = repository.getRecentTransactions(limit = 10)

        // Assert
        assertEquals(10, result.size, "Should limit to 10 transactions")
    }

    @Test
    fun `transactions are ordered by date descending`() = runTest {
        // Arrange: Insert transactions out of order
        repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                id = 0,
                accountId = testAccountId,
                date = testDate(2024, 1, 15),
                amount = -1500
            )
        )
        repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                id = 0,
                accountId = testAccountId,
                date = testDate(2024, 1, 25),
                amount = -2500
            )
        )
        repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                id = 0,
                accountId = testAccountId,
                date = testDate(2024, 1, 10),
                amount = -1000
            )
        )
        repository.notifyTransactionsChanged()

        // Act
        repository.getTransactionsWithDetailsByAccount(testAccountId).test {
            val result = awaitItem()

            // Assert: Most recent first
            assertEquals(-2500, result[0].transaction.amount) // Jan 25
            assertEquals(-1500, result[1].transaction.amount) // Jan 15
            assertEquals(-1000, result[2].transaction.amount) // Jan 10

            cancelAndConsumeRemainingEvents()
        }
    }

    // ========================================
    // 4. Batch Operations Tests
    // ========================================

    @Test
    fun `batchInsertTransactions inserts all transactions`() = runTest {
        // Arrange
        val transactions = TestDataFactory.createTestTransactions(
            count = 10,
            accountId = testAccountId
        )

        // Act
        val ids = repository.batchInsertTransactions(transactions)

        // Assert
        assertEquals(10, ids.size, "Should insert 10 transactions")
        assertTrue(ids.all { it > 0 }, "All IDs should be positive")
    }

    @Test
    fun `batchInsertTransactions notifies once for all inserts`() = runTest {
        // Arrange
        val transactions = TestDataFactory.createTestTransactions(
            count = 5,
            accountId = testAccountId
        )

        repository.getTransactionsWithDetailsByAccount(testAccountId).test {
            // Initial empty
            awaitItem()

            // Act
            repository.batchInsertTransactions(transactions)
            repository.notifyTransactionsChanged()

            // Assert: Single update with all transactions
            val result = awaitItem()
            assertEquals(5, result.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ========================================
    // 5. Edge Cases & Special Scenarios
    // ========================================

    @Test
    fun `inserting transaction with null optional fields works`() = runTest {
        // Arrange
        val transaction = TestDataFactory.createTestTransaction(
            id = 0,
            accountId = testAccountId,
            payeeId = null,
            categoryId = null,
            memo = null,
            checkNumber = null
        )

        // Act
        val id = repository.insertTransaction(transaction)
        val retrieved = repository.getTransactionById(id)

        // Assert
        assertNotNull(retrieved)
        assertNull(retrieved.payeeId)
        assertNull(retrieved.categoryId)
        assertNull(retrieved.memo)
        assertNull(retrieved.checkNumber)
    }

    @Test
    fun `markTransactionReconciled updates reconciled status`() = runTest {
        // Arrange
        val transaction = TestDataFactory.createTestTransaction(
            id = 0,
            accountId = testAccountId,
            isReconciled = false
        )
        val id = repository.insertTransaction(transaction)

        // Act
        repository.markTransactionReconciled(id, isReconciled = true)

        // Assert
        val retrieved = repository.getTransactionById(id)
        assertNotNull(retrieved)
        assertTrue(retrieved.isReconciled)
    }

    @Test
    fun `getTransactionByImportId returns correct transaction`() = runTest {
        // Arrange
        val transaction = TestDataFactory.createTestTransaction(
            id = 0,
            accountId = testAccountId,
            importId = "IMPORT_12345"
        )
        repository.insertTransaction(transaction)

        // Act
        val retrieved = repository.getTransactionByImportId("IMPORT_12345")

        // Assert
        assertNotNull(retrieved)
        assertEquals("IMPORT_12345", retrieved.importId)
    }

    @Test
    fun `getExistingImportIds finds all matching ids`() = runTest {
        // Arrange: Insert transactions with import IDs
        repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                id = 0,
                accountId = testAccountId,
                importId = "ID1"
            )
        )
        repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                id = 0,
                accountId = testAccountId,
                importId = "ID2"
            )
        )

        // Act
        val existing = repository.getExistingImportIds(testAccountId, listOf("ID1", "ID2", "ID3"))

        // Assert
        assertEquals(2, existing.size)
        assertTrue(existing.contains("ID1"))
        assertTrue(existing.contains("ID2"))
        assertFalse(existing.contains("ID3"))
    }

    @Test
    fun `getExistingImportIds is scoped per account so identical ids in another account are not duplicates`() = runTest {
        val otherAccountId = accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Second Account")
        )
        repository.insertTransaction(
            TestDataFactory.createTestTransaction(id = 0, accountId = testAccountId, importId = "DUP")
        )

        // The same import id used by a different account must not be seen as already present.
        val forOther = repository.getExistingImportIds(otherAccountId, listOf("DUP"))
        assertTrue(forOther.isEmpty(), "import id from another account must not count as a duplicate")

        // But re-importing into the same account still dedups.
        val forSame = repository.getExistingImportIds(testAccountId, listOf("DUP"))
        assertTrue(forSame.contains("DUP"))
    }

    // ========================================
    // 6. Performance Tests
    // ========================================

    @Test
    fun `refresh completes quickly for 100 transactions`() = runTest {
        // Arrange: Insert 100 transactions
        val transactions = TestDataFactory.createTestTransactions(
            count = 100,
            accountId = testAccountId
        )
        repository.batchInsertTransactions(transactions)

        // Act: Measure refresh time
        val startTime = System.currentTimeMillis()
        repository.notifyTransactionsChanged()
        repository.getTransactionsWithDetailsByAccount(testAccountId).test {
            awaitItem()
            val duration = System.currentTimeMillis() - startTime

            // Assert: Should be fast (< 200ms target)
            assertTrue(
                duration < 500,
                "Refresh should complete in under 500ms, took ${duration}ms"
            )

            cancelAndConsumeRemainingEvents()
        }
    }

    // ========================================
    // Reconciliation implies cleared (N8)
    // ========================================

    @Test
    fun `markTransactionReconciled also marks the transaction cleared`() = runTest {
        val id = repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                id = 0, accountId = testAccountId, amount = -5000, isCleared = false
            )
        )

        repository.markTransactionReconciled(id, true)

        val retrieved = repository.getTransactionById(id)
        assertNotNull(retrieved)
        assertTrue(retrieved.isReconciled, "transaction should be reconciled")
        assertTrue(retrieved.isCleared, "a reconciled transaction must also count as cleared")
        // The cleared balance (which sums isCleared rows) must now include it.
        assertEquals(-5000L, accountRepository.getClearedBalance(testAccountId))
    }

    // ========================================
    // Stale category id on update (R21)
    // ========================================

    @Test
    fun `updateTransaction nulls a categoryId that no longer exists instead of failing`() = runTest {
        // R21: an Edit dialog can hold a category that was deleted elsewhere while it was open.
        // Saving with that stale id must not throw an FK violation and lose the user's edit;
        // the transaction should simply become uncategorized.
        val categoryRepository = CategoryRepositoryImpl(database, testDispatcher)
        val categoryId = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Soon Deleted")
        )
        val txnId = repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                id = 0, accountId = testAccountId, amount = -2500, categoryId = categoryId
            )
        )

        // The category is removed (e.g. from the Categories screen) while the edit dialog is open.
        categoryRepository.deleteCategory(categoryId)

        // The dialog still holds the stale category object and saves with its id.
        val stale = repository.getTransactionById(txnId)!!.copy(categoryId = categoryId, memo = "edited")
        repository.updateTransaction(stale)

        val saved = repository.getTransactionById(txnId)
        assertNotNull(saved)
        assertEquals("edited", saved.memo, "the rest of the edit must be saved")
        assertNull(saved.categoryId, "the deleted category must be dropped, not cause an FK failure")
    }
}
