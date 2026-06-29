package com.financeapp.domain.categorize

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CategoryModelStoreTest {

    private fun samples() = listOf(
        TrainingSample("Starbucks", null, -600, 10L),
        TrainingSample("Shell", null, -5000, 20L)
    )

    @Test
    fun `trains lazily and caches the result across calls`() = runTest {
        var calls = 0
        val store = CategoryModelStore(trainingData = { calls++; samples() })

        val first = store.model()
        val second = store.model()

        assertEquals(1, calls, "training data should be fetched only once while the cache is warm")
        assertSame(first, second, "the same model instance should be returned from cache")
        assertTrue(!first.isEmpty)
    }

    @Test
    fun `invalidation forces a retrain on the next access`() = runTest {
        var calls = 0
        val store = CategoryModelStore(trainingData = { calls++; samples() })

        store.model()
        store.invalidate()
        store.model()

        assertEquals(2, calls, "after invalidation the next access should retrain")
    }

    @Test
    fun `abstains gracefully when there is no training data`() = runTest {
        val store = CategoryModelStore(trainingData = { emptyList() })
        assertTrue(store.model().isEmpty)
    }
}
