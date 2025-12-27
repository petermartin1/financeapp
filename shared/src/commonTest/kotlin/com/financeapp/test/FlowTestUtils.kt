package com.financeapp.test

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Test that a Flow emits expected values reactively after a mutation
 *
 * Example:
 * ```
 * repository.getAllCategories().testReactivity(
 *     expectedInitial = emptyList(),
 *     mutation = { repository.insertCategory(testCategory) },
 *     expectedAfterMutation = listOf(testCategory)
 * )
 * ```
 */
suspend fun <T> Flow<T>.testReactivity(
    timeout: Duration = 5.seconds,
    expectedInitial: T,
    mutation: suspend () -> Unit,
    expectedAfterMutation: T
) {
    test(timeout = timeout) {
        // Collect initial emission
        val initial = awaitItem()
        require(initial == expectedInitial) {
            "Expected initial: $expectedInitial, got: $initial"
        }

        // Perform mutation
        mutation()

        // Collect reactive update
        val updated = awaitItem()
        require(updated == expectedAfterMutation) {
            "Expected after mutation: $expectedAfterMutation, got: $updated"
        }

        cancelAndConsumeRemainingEvents()
    }
}

/**
 * Test that a Flow emits a sequence of values
 *
 * Example:
 * ```
 * repository.getAllCategories().testEmissions(
 *     expectedValues = listOf(emptyList(), listOf(cat1), listOf(cat1, cat2))
 * )
 * ```
 */
suspend fun <T> Flow<T>.testEmissions(
    timeout: Duration = 5.seconds,
    expectedValues: List<T>
) {
    test(timeout = timeout) {
        expectedValues.forEach { expected ->
            val actual = awaitItem()
            require(actual == expected) {
                "Expected: $expected, got: $actual"
            }
        }
        cancelAndConsumeRemainingEvents()
    }
}

/**
 * Test that a Flow eventually emits a value matching a predicate
 *
 * Example:
 * ```
 * repository.getTransactions().testEventuallyEmits(
 *     predicate = { it.isNotEmpty() }
 * )
 * ```
 */
suspend fun <T> Flow<T>.testEventuallyEmits(
    timeout: Duration = 5.seconds,
    predicate: (T) -> Boolean
) {
    test(timeout = timeout) {
        var item: T
        do {
            item = awaitItem()
        } while (!predicate(item))

        // Found matching item
        cancelAndConsumeRemainingEvents()
    }
}

/**
 * Test that a Flow emits at least N items
 *
 * Example:
 * ```
 * repository.getTransactions().testEmitsAtLeast(3)
 * ```
 */
suspend fun <T> Flow<T>.testEmitsAtLeast(
    count: Int,
    timeout: Duration = 5.seconds
): List<T> {
    val items = mutableListOf<T>()
    test(timeout = timeout) {
        repeat(count) {
            items.add(awaitItem())
        }
        cancelAndConsumeRemainingEvents()
    }
    return items
}
