package com.financeapp.data.repository

import com.financeapp.test.*
import com.financeapp.domain.repository.TagRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.model.Tag
import com.financeapp.domain.model.SplitItem
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.Database
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TagRepositoryTest {
    private lateinit var database: Database
    private lateinit var tagRepository: TagRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var accountRepository: AccountRepository
    private var testAccountId: Long = 0
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    private fun runTest(block: suspend TestScope.() -> Unit) =
        kotlinx.coroutines.test.runTest(testDispatcher, testBody = block)

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        tagRepository = TagRepositoryImpl(database, testDispatcher)
        transactionRepository = TransactionRepositoryImpl(database, testDispatcher)
        accountRepository = AccountRepositoryImpl(database, testDispatcher)

        testAccountId = runBlocking(testDispatcher) {
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
    fun `insertTag should create tag and return id`() = runTest {
        val tag = Tag(id = 0, name = "Work", color = "#FF5722")

        val id = tagRepository.insertTag(tag)

        assertTrue(id > 0, "Tag ID should be positive")
        val retrieved = tagRepository.getTagById(id)
        assertNotNull(retrieved)
        assertEquals("Work", retrieved.name)
        assertEquals("#FF5722", retrieved.color)
    }

    @Test
    fun `getTagById should return null for non-existent tag`() = runTest {
        val result = tagRepository.getTagById(99999L)
        assertNull(result)
    }

    @Test
    fun `updateTag should modify existing tag`() = runTest {
        val tag = Tag(id = 0, name = "Original", color = "#000000")
        val id = tagRepository.insertTag(tag)

        val updated = Tag(id = id, name = "Updated", color = "#FFFFFF")
        tagRepository.updateTag(updated)

        val retrieved = tagRepository.getTagById(id)
        assertNotNull(retrieved)
        assertEquals("Updated", retrieved.name)
        assertEquals("#FFFFFF", retrieved.color)
    }

    @Test
    fun `deleteTag should remove tag from database`() = runTest {
        val tag = Tag(id = 0, name = "ToDelete", color = "#000000")
        val id = tagRepository.insertTag(tag)

        tagRepository.deleteTag(id)

        val retrieved = tagRepository.getTagById(id)
        assertNull(retrieved)
    }

    // ============================================
    // Reactive Flow - getAllTags
    // ============================================

    @Test
    fun `getAllTags should emit initial tags`() = runTest {
        val tag1 = Tag(id = 0, name = "Work", color = "#FF5722")
        val tag2 = Tag(id = 0, name = "Personal", color = "#4CAF50")

        tagRepository.insertTag(tag1)
        tagRepository.insertTag(tag2)

        delay(100)

        tagRepository.getAllTags().test(timeout = 5.seconds) {
            val tags = awaitItem()
            assertEquals(2, tags.size)
            assertTrue(tags.any { it.name == "Work" })
            assertTrue(tags.any { it.name == "Personal" })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllTags should emit updates when tag inserted`() = runTest {
        tagRepository.getAllTags().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(0, initial.size)

            val tag = Tag(id = 0, name = "New Tag", color = "#000000")
            tagRepository.insertTag(tag)

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("New Tag", updated[0].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllTags should emit updates when tag updated`() = runTest {
        val tag = Tag(id = 0, name = "Original", color = "#000000")
        val id = tagRepository.insertTag(tag)

        delay(300) // Increased delay for test isolation

        tagRepository.getAllTags().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)
            assertEquals("Original", initial[0].name)

            tagRepository.updateTag(Tag(id = id, name = "Updated", color = "#FFFFFF"))

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("Updated", updated[0].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllTags should emit updates when tag deleted`() = runTest {
        val tag = Tag(id = 0, name = "ToDelete", color = "#000000")
        val id = tagRepository.insertTag(tag)

        delay(100)

        tagRepository.getAllTags().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)

            tagRepository.deleteTag(id)

            val updated = awaitItem()
            assertEquals(0, updated.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllTags should order by name`() = runTest {
        tagRepository.insertTag(Tag(id = 0, name = "Zebra", color = "#000000"))
        tagRepository.insertTag(Tag(id = 0, name = "Apple", color = "#000000"))
        tagRepository.insertTag(Tag(id = 0, name = "Mango", color = "#000000"))

        delay(100)

        tagRepository.getAllTags().test(timeout = 5.seconds) {
            val tags = awaitItem()
            assertEquals(3, tags.size)
            assertEquals("Apple", tags[0].name)
            assertEquals("Mango", tags[1].name)
            assertEquals("Zebra", tags[2].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Transaction-Tag Relationships
    // ============================================

    @Test
    fun `addTagToTransaction should link tag to transaction`() = runTest {
        val tag = Tag(id = 0, name = "Work", color = "#FF5722")
        val tagId = tagRepository.insertTag(tag)

        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId)
        val transactionId = transactionRepository.insertTransaction(transaction)

        tagRepository.addTagToTransaction(transactionId, tagId)

        val tags = tagRepository.getTagsForTransaction(transactionId)
        assertEquals(1, tags.size)
        assertEquals("Work", tags[0].name)
    }

    @Test
    fun `addTagToTransaction should handle multiple tags for same transaction`() = runTest {
        val tag1Id = tagRepository.insertTag(Tag(id = 0, name = "Work", color = "#FF5722"))
        val tag2Id = tagRepository.insertTag(Tag(id = 0, name = "Important", color = "#4CAF50"))

        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId)
        val transactionId = transactionRepository.insertTransaction(transaction)

        tagRepository.addTagToTransaction(transactionId, tag1Id)
        tagRepository.addTagToTransaction(transactionId, tag2Id)

        val tags = tagRepository.getTagsForTransaction(transactionId)
        assertEquals(2, tags.size)
        assertTrue(tags.any { it.name == "Work" })
        assertTrue(tags.any { it.name == "Important" })
    }

    @Test
    fun `addTagToTransaction should handle duplicate adds gracefully`() = runTest {
        val tagId = tagRepository.insertTag(Tag(id = 0, name = "Work", color = "#FF5722"))

        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId)
        val transactionId = transactionRepository.insertTransaction(transaction)

        tagRepository.addTagToTransaction(transactionId, tagId)
        tagRepository.addTagToTransaction(transactionId, tagId) // Duplicate

        val tags = tagRepository.getTagsForTransaction(transactionId)
        assertEquals(1, tags.size) // Should only have 1 tag, not 2
    }

    @Test
    fun `removeTagFromTransaction should unlink tag from transaction`() = runTest {
        val tagId = tagRepository.insertTag(Tag(id = 0, name = "Work", color = "#FF5722"))

        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId)
        val transactionId = transactionRepository.insertTransaction(transaction)

        tagRepository.addTagToTransaction(transactionId, tagId)

        val tagsBefore = tagRepository.getTagsForTransaction(transactionId)
        assertEquals(1, tagsBefore.size)

        tagRepository.removeTagFromTransaction(transactionId, tagId)

        val tagsAfter = tagRepository.getTagsForTransaction(transactionId)
        assertEquals(0, tagsAfter.size)
    }

    @Test
    fun `setTransactionTags should replace all tags for transaction`() = runTest {
        val tag1Id = tagRepository.insertTag(Tag(id = 0, name = "Work", color = "#FF5722"))
        val tag2Id = tagRepository.insertTag(Tag(id = 0, name = "Important", color = "#4CAF50"))
        val tag3Id = tagRepository.insertTag(Tag(id = 0, name = "Urgent", color = "#F44336"))

        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId)
        val transactionId = transactionRepository.insertTransaction(transaction)

        // Set initial tags
        tagRepository.setTransactionTags(transactionId, listOf(tag1Id, tag2Id))

        val tagsBefore = tagRepository.getTagsForTransaction(transactionId)
        assertEquals(2, tagsBefore.size)

        // Replace with different tags
        tagRepository.setTransactionTags(transactionId, listOf(tag3Id))

        val tagsAfter = tagRepository.getTagsForTransaction(transactionId)
        assertEquals(1, tagsAfter.size)
        assertEquals("Urgent", tagsAfter[0].name)
    }

    @Test
    fun `setTransactionTags with empty list should clear all tags`() = runTest {
        val tagId = tagRepository.insertTag(Tag(id = 0, name = "Work", color = "#FF5722"))

        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId)
        val transactionId = transactionRepository.insertTransaction(transaction)

        tagRepository.setTransactionTags(transactionId, listOf(tagId))

        val tagsBefore = tagRepository.getTagsForTransaction(transactionId)
        assertEquals(1, tagsBefore.size)

        tagRepository.setTransactionTags(transactionId, emptyList())

        val tagsAfter = tagRepository.getTagsForTransaction(transactionId)
        assertEquals(0, tagsAfter.size)
    }

    @Test
    fun `getTagsForTransaction should return empty list for transaction with no tags`() = runTest {
        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId)
        val transactionId = transactionRepository.insertTransaction(transaction)

        val tags = tagRepository.getTagsForTransaction(transactionId)
        assertEquals(0, tags.size)
    }

    // ============================================
    // Split Operations
    // ============================================

    @Test
    fun `setSplitsForTransaction should create split items`() = runTest {
        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId, amount = 10000)
        val transactionId = transactionRepository.insertTransaction(transaction)

        val splits = listOf(
            SplitItem(id = 0, transactionId = transactionId, categoryId = null, amount = 6000, memo = "Split 1"),
            SplitItem(id = 0, transactionId = transactionId, categoryId = null, amount = 4000, memo = "Split 2")
        )

        tagRepository.setSplitsForTransaction(transactionId, splits)

        val retrievedSplits = tagRepository.getSplitsForTransaction(transactionId)
        assertEquals(2, retrievedSplits.size)
        assertTrue(retrievedSplits.any { it.amount == 6000L && it.memo == "Split 1" })
        assertTrue(retrievedSplits.any { it.amount == 4000L && it.memo == "Split 2" })
    }

    @Test
    fun `setSplitsForTransaction should replace existing splits`() = runTest {
        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId, amount = 10000)
        val transactionId = transactionRepository.insertTransaction(transaction)

        // Set initial splits
        val initialSplits = listOf(
            SplitItem(id = 0, transactionId = transactionId, categoryId = null, amount = 5000, memo = "Initial")
        )
        tagRepository.setSplitsForTransaction(transactionId, initialSplits)

        val splitsBefore = tagRepository.getSplitsForTransaction(transactionId)
        assertEquals(1, splitsBefore.size)

        // Replace with new splits
        val newSplits = listOf(
            SplitItem(id = 0, transactionId = transactionId, categoryId = null, amount = 6000, memo = "New 1"),
            SplitItem(id = 0, transactionId = transactionId, categoryId = null, amount = 4000, memo = "New 2")
        )
        tagRepository.setSplitsForTransaction(transactionId, newSplits)

        val splitsAfter = tagRepository.getSplitsForTransaction(transactionId)
        assertEquals(2, splitsAfter.size)
        assertTrue(splitsAfter.none { it.memo == "Initial" })
    }

    @Test
    fun `clearSplitsForTransaction should remove all splits`() = runTest {
        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId, amount = 10000)
        val transactionId = transactionRepository.insertTransaction(transaction)

        val splits = listOf(
            SplitItem(id = 0, transactionId = transactionId, categoryId = null, amount = 5000, memo = "Split 1")
        )
        tagRepository.setSplitsForTransaction(transactionId, splits)

        val splitsBefore = tagRepository.getSplitsForTransaction(transactionId)
        assertEquals(1, splitsBefore.size)

        tagRepository.clearSplitsForTransaction(transactionId)

        val splitsAfter = tagRepository.getSplitsForTransaction(transactionId)
        assertEquals(0, splitsAfter.size)
    }

    @Test
    fun `getSplitsForTransaction should return empty list for transaction with no splits`() = runTest {
        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId)
        val transactionId = transactionRepository.insertTransaction(transaction)

        val splits = tagRepository.getSplitsForTransaction(transactionId)
        assertEquals(0, splits.size)
    }

    @Test
    fun `split items should preserve category and amount data`() = runTest {
        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId)
        val transactionId = transactionRepository.insertTransaction(transaction)

        val split = SplitItem(
            id = 0,
            transactionId = transactionId,
            categoryId = null, // Use null to avoid FK constraint
            amount = 12345,
            memo = "Test split"
        )

        tagRepository.setSplitsForTransaction(transactionId, listOf(split))

        val retrieved = tagRepository.getSplitsForTransaction(transactionId)
        assertEquals(1, retrieved.size)
        assertNull(retrieved[0].categoryId)
        assertEquals(12345L, retrieved[0].amount)
        assertEquals("Test split", retrieved[0].memo)
        assertEquals(transactionId, retrieved[0].transactionId)
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `tag should handle null color`() = runTest {
        val tag = Tag(id = 0, name = "No Color", color = null)
        val id = tagRepository.insertTag(tag)

        val retrieved = tagRepository.getTagById(id)
        assertNotNull(retrieved)
        assertNull(retrieved.color)
    }

    @Test
    fun `notifyTagsChanged should trigger manual refresh`() = runTest {
        val tag = Tag(id = 0, name = "Test", color = "#000000")
        tagRepository.insertTag(tag)

        delay(400) // Increased delay for test isolation

        tagRepository.getAllTags().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)

            // Manually trigger refresh
            tagRepository.notifyTagsChanged()

            val refreshed = awaitItem()
            assertEquals(1, refreshed.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Performance Tests
    // ============================================

    @Test
    fun `getAllTags should handle many tags`() = runTest {
        // Insert 50 tags
        val tagIds = (0 until 50).map { i ->
            tagRepository.insertTag(Tag(id = 0, name = "Tag $i", color = "#000000"))
        }

        assertEquals(50, tagIds.size)
        delay(200)

        tagRepository.getAllTags().test(timeout = 5.seconds) {
            var tags = awaitItem()
            while (tags.size < 50) {
                tags = awaitItem()
            }
            assertEquals(50, tags.size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `transaction should handle many tags`() = runTest {
        // Create 10 tags
        val tagIds = (0 until 10).map { i ->
            tagRepository.insertTag(Tag(id = 0, name = "Tag $i", color = "#000000"))
        }

        val transaction = TestDataFactory.createTestTransaction(accountId = testAccountId)
        val transactionId = transactionRepository.insertTransaction(transaction)

        // Add all tags to transaction
        tagRepository.setTransactionTags(transactionId, tagIds)

        val tags = tagRepository.getTagsForTransaction(transactionId)
        assertEquals(10, tags.size)
    }
}
