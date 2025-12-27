package com.financeapp.ui.tags

import com.financeapp.test.*
import com.financeapp.data.repository.*
import com.financeapp.domain.model.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.exposed.sql.Database
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel tests using real repositories (integration-style testing)
 * More valuable than mocked tests as they verify actual data flow
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModelTest {

    private lateinit var database: Database
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var viewModel: TagsViewModel
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)

    private fun runTest(
        timeout: kotlin.time.Duration,
        block: suspend TestScope.() -> Unit
    ) = kotlinx.coroutines.test.runTest(testDispatcher, timeout = timeout, testBody = block)
    // Helper function to wait for a specific state condition
    private suspend fun waitForState(
        timeout: kotlin.time.Duration = 10.seconds,
        predicate: (TagsUiState) -> Boolean
    ): TagsUiState {
        var result: TagsUiState? = null
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


    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        database = createTestDatabase()
        tagRepository = TagRepositoryImpl(database, testDispatcher)

        viewModel = TagsViewModel(tagRepository)
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
        assertTrue(state.tags.isEmpty())

    }

    @Test
    fun `loadTags updates state with empty list initially`() = runTest(timeout = 10.seconds) {
        val state = waitForState(timeout = 10.seconds) { !it.isLoading }
        assertFalse(state.isLoading)
        assertTrue(state.tags.isEmpty())

    }

    // ============================================
    // Tag Loading Tests
    // ============================================

    @Test
    fun `loadTags loads all tags`() = runTest(timeout = 10.seconds) {
        tagRepository.insertTag(Tag(id = 0, name = "Work", color = "#FF5722"))
        tagRepository.insertTag(Tag(id = 0, name = "Personal", color = "#4CAF50"))
        tagRepository.notifyTagsChanged()

        val state = waitForState(timeout = 10.seconds) { it.tags.size == 2 }
        assertEquals(2, state.tags.size)
        assertTrue(state.tags.any { it.name == "Work" })
        assertTrue(state.tags.any { it.name == "Personal" })

    }

    @Test
    fun `loadTags orders tags by name`() = runTest(timeout = 10.seconds) {
        tagRepository.insertTag(Tag(id = 0, name = "Zebra", color = null))
        tagRepository.insertTag(Tag(id = 0, name = "Apple", color = null))
        tagRepository.insertTag(Tag(id = 0, name = "Mango", color = null))
        tagRepository.notifyTagsChanged()

        val state = waitForState(timeout = 10.seconds) { it.tags.size == 3 }
        assertEquals(3, state.tags.size)
        assertEquals("Apple", state.tags[0].name)
        assertEquals("Mango", state.tags[1].name)
        assertEquals("Zebra", state.tags[2].name)

    }

    // ============================================
    // Tag CRUD Tests
    // ============================================

    @Test
    fun `addTag creates new tag`() = runTest(timeout = 10.seconds) {
        val initialState = waitForState(timeout = 10.seconds) { it.tags.size == 0 }
        assertEquals(0, initialState.tags.size)

        viewModel.addTag(name = "Business", color = "#2196F3")
        val state = waitForState(timeout = 10.seconds) { it.tags.size == 1 }
        assertEquals(1, state.tags.size)
        assertEquals("Business", state.tags[0].name)
        assertEquals("#2196F3", state.tags[0].color)

    }

    @Test
    fun `addTag with null color`() = runTest(timeout = 10.seconds) {
        viewModel.addTag(name = "Uncolored", color = null)
        val state = waitForState(timeout = 10.seconds) { it.tags.size == 1 }
        assertEquals(1, state.tags.size)
        assertEquals("Uncolored", state.tags[0].name)
        assertNull(state.tags[0].color)

    }

    @Test
    fun `updateTag modifies tag`() = runTest(timeout = 10.seconds) {
        val tagId = tagRepository.insertTag(Tag(id = 0, name = "Original", color = "#000000"))
        tagRepository.notifyTagsChanged()

        val initialState = waitForState(timeout = 10.seconds) { it.tags.isNotEmpty() }
        val originalTag = initialState.tags[0]
        assertEquals("Original", originalTag.name)

        viewModel.updateTag(originalTag.copy(name = "Updated", color = "#FFFFFF"))
        val state = waitForState(timeout = 10.seconds) { it.tags.isNotEmpty() && it.tags[0].name == "Updated" }

        assertEquals("Updated", state.tags[0].name)
        assertEquals("#FFFFFF", state.tags[0].color)

    }

    @Test
    fun `deleteTag removes tag`() = runTest(timeout = 10.seconds) {
        val tagId = tagRepository.insertTag(Tag(id = 0, name = "To Delete", color = null))
        tagRepository.notifyTagsChanged()

        val initialState = waitForState(timeout = 10.seconds) { it.tags.size == 1 }
        assertEquals(1, initialState.tags.size)

        viewModel.deleteTag(tagId)
        val state = waitForState(timeout = 10.seconds) { it.tags.isEmpty() }
        assertEquals(0, state.tags.size)

    }

    // ============================================
    // Reactive Updates Tests
    // ============================================

    @Test
    fun `state updates when tag added externally`() = runTest(timeout = 10.seconds) {
        val initialState = waitForState(timeout = 10.seconds) { it.tags.size == 0 }
        assertEquals(0, initialState.tags.size)

        // Add tag externally
        tagRepository.insertTag(Tag(id = 0, name = "External Tag", color = "#FF9800"))
        tagRepository.notifyTagsChanged()

        val state = waitForState(timeout = 10.seconds) { it.tags.size == 1 }
        assertEquals(1, state.tags.size)
        assertEquals("External Tag", state.tags[0].name)

    }

    @Test
    fun `state updates when tag deleted externally`() = runTest(timeout = 10.seconds) {
        val tagId = tagRepository.insertTag(Tag(id = 0, name = "Test", color = null))
        tagRepository.notifyTagsChanged()

        val initialState = waitForState(timeout = 10.seconds) { it.tags.size == 1 }
        assertEquals(1, initialState.tags.size)

        // Delete externally
        tagRepository.deleteTag(tagId)
        tagRepository.notifyTagsChanged()

        val state = waitForState(timeout = 10.seconds) { it.tags.size == 0 }
        assertEquals(0, state.tags.size)

    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `handles no tags gracefully`() = runTest(timeout = 10.seconds) {
        val state = waitForState(timeout = 10.seconds) { !it.isLoading }
        assertTrue(state.tags.isEmpty())
        assertFalse(state.isLoading)

    }

    @Test
    fun `handles multiple tags correctly`() = runTest(timeout = 10.seconds) {
        // Add 5 tags
        repeat(5) { index ->
            tagRepository.insertTag(Tag(id = 0, name = "Tag $index", color = "#00000$index"))
        }
        tagRepository.notifyTagsChanged()

        val state = waitForState(timeout = 10.seconds) { it.tags.size == 5 }
        assertEquals(5, state.tags.size)

    }
}
