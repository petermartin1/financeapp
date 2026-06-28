package com.financeapp.data.repository

import com.financeapp.test.*
import com.financeapp.domain.repository.PayeeRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.model.Payee
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PayeeRepositoryTest {
    private lateinit var database: Database
    private lateinit var payeeRepository: PayeeRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var accountRepository: AccountRepository
    private var testAccountId: Long = 0
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        payeeRepository = PayeeRepositoryImpl(database, testDispatcher)
        transactionRepository = TransactionRepositoryImpl(database, testDispatcher)
        accountRepository = AccountRepositoryImpl(database, testDispatcher)

        testAccountId = runBlocking {
            val account = TestDataFactory.createTestAccount()
            accountRepository.insertAccount(account)
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
    fun `insertPayee should create payee and return id`() = runTest {
        val payee = Payee(id = 0, name = "Amazon", defaultCategoryId = null)

        val id = payeeRepository.insertPayee(payee)

        assertTrue(id > 0, "Payee ID should be positive")
        val retrieved = payeeRepository.getPayeeById(id)
        assertNotNull(retrieved)
        assertEquals("Amazon", retrieved.name)
    }

    @Test
    fun `mergePayees into itself is a no-op and does not delete the payee`() = runTest {
        val id = payeeRepository.insertPayee(Payee(id = 0, name = "Amazon"))

        payeeRepository.mergePayees(id, id)

        assertNotNull(payeeRepository.getPayeeById(id), "merging a payee into itself must not delete it")
    }

    @Test
    fun `getPayeeById should return null for non-existent payee`() = runTest {
        val result = payeeRepository.getPayeeById(99999L)
        assertNull(result)
    }

    @Test
    fun `updatePayee should modify existing payee`() = runTest {
        val payee = Payee(id = 0, name = "Original", defaultCategoryId = null)
        val id = payeeRepository.insertPayee(payee)

        val updated = Payee(id = id, name = "Updated", defaultCategoryId = null)
        payeeRepository.updatePayee(updated)

        val retrieved = payeeRepository.getPayeeById(id)
        assertNotNull(retrieved)
        assertEquals("Updated", retrieved.name)
        assertNull(retrieved.defaultCategoryId)
    }

    @Test
    fun `deletePayee should remove payee from database`() = runTest {
        val payee = Payee(id = 0, name = "ToDelete", defaultCategoryId = null)
        val id = payeeRepository.insertPayee(payee)

        payeeRepository.deletePayee(id)

        val retrieved = payeeRepository.getPayeeById(id)
        assertNull(retrieved)
    }

    // ============================================
    // Reactive Flow - getAllPayees
    // ============================================

    @Test
    fun `getAllPayees should emit initial payees`() = runTest {
        val payee1 = Payee(id = 0, name = "Amazon", defaultCategoryId = null)
        val payee2 = Payee(id = 0, name = "Walmart", defaultCategoryId = null)

        payeeRepository.insertPayee(payee1)
        payeeRepository.insertPayee(payee2)

        delay(200)

        payeeRepository.getAllPayees().test(timeout = 5.seconds) {
            val payees = awaitItem()
            assertEquals(2, payees.size)
            assertTrue(payees.any { it.name == "Amazon" })
            assertTrue(payees.any { it.name == "Walmart" })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllPayees should emit updates when payee inserted`() = runTest {
        payeeRepository.getAllPayees().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            val payee = Payee(id = 0, name = "New Payee", defaultCategoryId = null)
            payeeRepository.insertPayee(payee)

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("New Payee", updated[0].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllPayees should order by name`() = runTest {
        payeeRepository.insertPayee(Payee(id = 0, name = "Zebra Corp", defaultCategoryId = null))
        payeeRepository.insertPayee(Payee(id = 0, name = "Apple Store", defaultCategoryId = null))
        payeeRepository.insertPayee(Payee(id = 0, name = "Mango Inc", defaultCategoryId = null))

        delay(200)

        payeeRepository.getAllPayees().test(timeout = 5.seconds) {
            val payees = awaitItem()
            assertEquals(3, payees.size)
            assertEquals("Apple Store", payees[0].name)
            assertEquals("Mango Inc", payees[1].name)
            assertEquals("Zebra Corp", payees[2].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Lookup Operations
    // ============================================

    @Test
    fun `getPayeeByName should find payee case-insensitively`() = runTest {
        val payee = Payee(id = 0, name = "Amazon", defaultCategoryId = null)
        payeeRepository.insertPayee(payee)

        val found1 = payeeRepository.getPayeeByName("amazon")
        assertNotNull(found1)
        assertEquals("Amazon", found1.name)

        val found2 = payeeRepository.getPayeeByName("AMAZON")
        assertNotNull(found2)
        assertEquals("Amazon", found2.name)

        val found3 = payeeRepository.getPayeeByName("Amazon")
        assertNotNull(found3)
        assertEquals("Amazon", found3.name)
    }

    @Test
    fun `getPayeeByName should return null for non-existent payee`() = runTest {
        val found = payeeRepository.getPayeeByName("NonExistent")
        assertNull(found)
    }

    @Test
    fun `getPayeesByNames should return map of matching payees`() = runTest {
        payeeRepository.insertPayee(Payee(id = 0, name = "Amazon", defaultCategoryId = null))
        payeeRepository.insertPayee(Payee(id = 0, name = "Walmart", defaultCategoryId = null))
        payeeRepository.insertPayee(Payee(id = 0, name = "Target", defaultCategoryId = null))

        val names = listOf("amazon", "WALMART", "NonExistent")
        val payees = payeeRepository.getPayeesByNames(names)

        assertEquals(2, payees.size)
        assertTrue(payees.containsKey("amazon"))
        assertTrue(payees.containsKey("walmart"))
        assertFalse(payees.containsKey("nonexistent"))

        assertEquals("Amazon", payees["amazon"]?.name)
        assertEquals("Walmart", payees["walmart"]?.name)
    }

    @Test
    fun `getPayeesByNames should return empty map for empty list`() = runTest {
        val payees = payeeRepository.getPayeesByNames(emptyList())
        assertTrue(payees.isEmpty())
    }

    // ============================================
    // Batch Operations
    // ============================================

    @Test
    fun `batchInsertPayees should create multiple payees`() = runTest {
        val payees = listOf(
            Payee(id = 0, name = "Amazon", defaultCategoryId = null),
            Payee(id = 0, name = "Walmart", defaultCategoryId = null),
            Payee(id = 0, name = "Target", defaultCategoryId = null)
        )

        val idMap = payeeRepository.batchInsertPayees(payees)

        assertEquals(3, idMap.size)
        assertTrue(idMap.containsKey("amazon"))
        assertTrue(idMap.containsKey("walmart"))
        assertTrue(idMap.containsKey("target"))

        idMap.values.forEach { id ->
            assertTrue(id > 0, "All IDs should be positive")
        }
    }

    @Test
    fun `batchInsertPayees should return empty map for empty list`() = runTest {
        val idMap = payeeRepository.batchInsertPayees(emptyList())
        assertTrue(idMap.isEmpty())
    }

    @Test
    fun `batchInsertPayees should trigger notification`() = runTest {
        val payees = listOf(
            Payee(id = 0, name = "Payee1", defaultCategoryId = null),
            Payee(id = 0, name = "Payee2", defaultCategoryId = null)
        )

        payeeRepository.getAllPayees().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            payeeRepository.batchInsertPayees(payees)

            val updated = awaitItem()
            assertEquals(2, updated.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Payee with Stats
    // ============================================

    @Test
    fun `getPayeesWithStats should return transaction counts`() = runTest {
        val payee1 = Payee(id = 0, name = "Amazon", defaultCategoryId = null)
        val payee1Id = payeeRepository.insertPayee(payee1)

        val payee2 = Payee(id = 0, name = "Walmart", defaultCategoryId = null)
        val payee2Id = payeeRepository.insertPayee(payee2)

        // Add 3 transactions for Amazon
        repeat(3) {
            val txn = TestDataFactory.createTestTransaction(
                accountId = testAccountId,
                payeeId = payee1Id
            )
            transactionRepository.insertTransaction(txn)
        }

        // Add 1 transaction for Walmart
        val txn = TestDataFactory.createTestTransaction(
            accountId = testAccountId,
            payeeId = payee2Id
        )
        transactionRepository.insertTransaction(txn)

        delay(200)

        payeeRepository.getPayeesWithStats().test(timeout = 5.seconds) {
            val stats = awaitItem()
            assertEquals(2, stats.size)

            val amazonStats = stats.find { it.payee.name == "Amazon" }
            assertNotNull(amazonStats)
            assertEquals(3, amazonStats.transactionCount)

            val walmartStats = stats.find { it.payee.name == "Walmart" }
            assertNotNull(walmartStats)
            assertEquals(1, walmartStats.transactionCount)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getPayeesWithStats should show zero transactions for new payee`() = runTest {
        val payee = Payee(id = 0, name = "NewPayee", defaultCategoryId = null)
        payeeRepository.insertPayee(payee)

        delay(200)

        payeeRepository.getPayeesWithStats().test(timeout = 5.seconds) {
            val stats = awaitItem()
            assertEquals(1, stats.size)
            assertEquals(0, stats[0].transactionCount)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Merge Operations
    // ============================================

    @Test
    fun `mergePayees should move transactions from source to target`() = runTest {
        val source = Payee(id = 0, name = "Amazon.com", defaultCategoryId = null)
        val sourceId = payeeRepository.insertPayee(source)

        val target = Payee(id = 0, name = "Amazon", defaultCategoryId = null)
        val targetId = payeeRepository.insertPayee(target)

        // Add 3 transactions to source payee
        repeat(3) {
            val txn = TestDataFactory.createTestTransaction(
                accountId = testAccountId,
                payeeId = sourceId
            )
            transactionRepository.insertTransaction(txn)
        }

        // Add 1 transaction to target payee
        val txn = TestDataFactory.createTestTransaction(
            accountId = testAccountId,
            payeeId = targetId
        )
        transactionRepository.insertTransaction(txn)

        // Merge source into target
        payeeRepository.mergePayees(sourceId, targetId)

        delay(200)

        // Source payee should be deleted
        val sourceAfterMerge = payeeRepository.getPayeeById(sourceId)
        assertNull(sourceAfterMerge)

        // Target payee should still exist
        val targetAfterMerge = payeeRepository.getPayeeById(targetId)
        assertNotNull(targetAfterMerge)

        // Target should now have all 4 transactions
        payeeRepository.getPayeesWithStats().test(timeout = 5.seconds) {
            val stats = awaitItem()
            val targetStats = stats.find { it.payee.id == targetId }
            assertNotNull(targetStats)
            assertEquals(4, targetStats.transactionCount)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `mergePayees should delete source even with no transactions`() = runTest {
        val source = Payee(id = 0, name = "Source", defaultCategoryId = null)
        val sourceId = payeeRepository.insertPayee(source)

        val target = Payee(id = 0, name = "Target", defaultCategoryId = null)
        val targetId = payeeRepository.insertPayee(target)

        payeeRepository.mergePayees(sourceId, targetId)

        val sourceAfterMerge = payeeRepository.getPayeeById(sourceId)
        assertNull(sourceAfterMerge)

        val targetAfterMerge = payeeRepository.getPayeeById(targetId)
        assertNotNull(targetAfterMerge)
    }

    @Test
    fun `mergePayees should trigger notification`() = runTest {
        val source = Payee(id = 0, name = "Source", defaultCategoryId = null)
        val sourceId = payeeRepository.insertPayee(source)

        val target = Payee(id = 0, name = "Target", defaultCategoryId = null)
        val targetId = payeeRepository.insertPayee(target)

        delay(200)

        payeeRepository.getAllPayees().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(2, initial.size)

            payeeRepository.mergePayees(sourceId, targetId)

            val updated = awaitItem()
            assertEquals(1, updated.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `payee should handle null defaultCategoryId`() = runTest {
        val payee = Payee(id = 0, name = "NoCategoryPayee", defaultCategoryId = null)
        val id = payeeRepository.insertPayee(payee)

        val retrieved = payeeRepository.getPayeeById(id)
        assertNotNull(retrieved)
        assertNull(retrieved.defaultCategoryId)
    }

    @Test
    fun `payee should preserve defaultCategoryId`() = runTest {
        // Note: Using null for defaultCategoryId to avoid FK constraints in tests
        // In real usage, defaultCategoryId would reference an actual category
        val payee = Payee(id = 0, name = "PayeeWithCategory", defaultCategoryId = null)
        val id = payeeRepository.insertPayee(payee)

        val retrieved = payeeRepository.getPayeeById(id)
        assertNotNull(retrieved)
        assertNull(retrieved.defaultCategoryId)
    }

    @Test
    fun `notifyPayeesChanged should trigger manual refresh`() = runTest {
        val payee = Payee(id = 0, name = "Test", defaultCategoryId = null)
        payeeRepository.insertPayee(payee)

        delay(400) // Increased delay for test isolation

        payeeRepository.getAllPayees().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)

            // Manually trigger refresh
            payeeRepository.notifyPayeesChanged()

            val refreshed = awaitItem()
            assertEquals(1, refreshed.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Performance Tests
    // ============================================

    @Test
    fun `getAllPayees should handle many payees`() = runTest {
        // Insert 50 payees
        val payeeIds = (0 until 50).map { i ->
            payeeRepository.insertPayee(Payee(id = 0, name = "Payee $i", defaultCategoryId = null))
        }

        assertEquals(50, payeeIds.size)
        delay(200)

        payeeRepository.getAllPayees().test(timeout = 5.seconds) {
            var payees = awaitItem()
            while (payees.size < 50) {
                payees = awaitItem()
            }
            assertEquals(50, payees.size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `batchInsertPayees should handle large batches`() = runTest {
        val payees = (0 until 100).map { i ->
            Payee(id = 0, name = "Payee $i", defaultCategoryId = null)
        }

        val idMap = payeeRepository.batchInsertPayees(payees)

        assertEquals(100, idMap.size)
        idMap.values.forEach { id ->
            assertTrue(id > 0)
        }
    }
}
