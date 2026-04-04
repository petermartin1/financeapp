package com.financeapp.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Run test with main dispatcher set to TestDispatcher
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runTestWithDispatcher(block: suspend TestScope.() -> Unit) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        block()
    } finally {
        Dispatchers.resetMain()
    }
}

/**
 * Create a test LocalDate
 */
fun testDate(year: Int = 2024, month: Int = 1, day: Int = 1): LocalDate {
    return LocalDate(year, month, day)
}

/**
 * Create a test Instant
 */
fun testInstant(epochMillis: Long = 1704067200000): Instant {
    return Instant.fromEpochMilliseconds(epochMillis)
}

/**
 * Get current test instant
 */
fun testNow(): Instant = Clock.System.now()

/**
 * Format amount in cents to dollars for assertions
 */
fun Long.toDollars(): Double = this / 100.0

/**
 * Format dollars to cents
 */
fun Double.toCents(): Long = (this * 100).toLong()
