package com.financeapp.security

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Rate limiter with exponential backoff for protecting against brute force attacks
 * and respecting API rate limits.
 *
 * Features:
 * - Exponential backoff after failures (2^attempts * base delay)
 * - Configurable maximum delay and attempts
 * - Per-key tracking (e.g., per bank connection)
 * - Automatic reset after success
 * - Time-based lockout after too many failures
 *
 * Example usage:
 * ```
 * val limiter = RateLimiter()
 * limiter.checkAndWait("bank_connection_123") // Throws if blocked
 * try {
 *     // Make API call
 *     limiter.recordSuccess("bank_connection_123")
 * } catch (e: Exception) {
 *     limiter.recordFailure("bank_connection_123")
 * }
 * ```
 */
class RateLimiter(
    /**
     * Base delay before first retry (default: 1 second)
     */
    private val baseDelay: Duration = 1.seconds,

    /**
     * Maximum delay between retries (default: 60 seconds)
     */
    private val maxDelay: Duration = 60.seconds,

    /**
     * Maximum number of consecutive failures before lockout (default: 5)
     */
    private val maxAttempts: Int = 5,

    /**
     * Duration to lock out after max attempts exceeded (default: 15 minutes)
     */
    private val lockoutDuration: Duration = (15 * 60).seconds,

    /**
     * Duration after which to reset failure count if no activity (default: 1 hour)
     */
    private val resetAfter: Duration = (60 * 60).seconds
) {
    private data class AttemptRecord(
        var consecutiveFailures: Int = 0,
        var lastAttempt: Instant? = null,
        var lockedUntil: Instant? = null
    )

    private val attemptsByKey = mutableMapOf<String, AttemptRecord>()

    /**
     * Check if the key is currently rate limited and wait if needed.
     * Throws RateLimitException if the key is locked out.
     *
     * @param key Unique identifier (e.g., "bank_connection_123")
     * @throws RateLimitException if key is locked out
     */
    suspend fun checkAndWait(key: String) {
        val now = Clock.System.now()
        val record = attemptsByKey.getOrPut(key) { AttemptRecord() }

        // Reset if enough time has passed since last attempt
        record.lastAttempt?.let { lastAttempt ->
            if (now - lastAttempt > resetAfter) {
                record.consecutiveFailures = 0
                record.lockedUntil = null
            }
        }

        // Check if locked out
        record.lockedUntil?.let { lockedUntil ->
            if (now < lockedUntil) {
                val remainingLockout = lockedUntil - now
                throw RateLimitException(
                    "Too many failed attempts. Locked out for ${remainingLockout.inWholeSeconds} more seconds."
                )
            } else {
                // Lockout expired, reset
                record.consecutiveFailures = 0
                record.lockedUntil = null
            }
        }

        // Calculate exponential backoff delay
        if (record.consecutiveFailures > 0) {
            val delaySeconds = calculateBackoffDelay(record.consecutiveFailures)
            delay(delaySeconds.inWholeMilliseconds)
        }

        record.lastAttempt = now
    }

    /**
     * Record a successful attempt, resetting the failure counter.
     *
     * @param key Unique identifier
     */
    fun recordSuccess(key: String) {
        val record = attemptsByKey.getOrPut(key) { AttemptRecord() }
        record.consecutiveFailures = 0
        record.lockedUntil = null
    }

    /**
     * Record a failed attempt, incrementing the failure counter and potentially
     * triggering a lockout.
     *
     * @param key Unique identifier
     */
    fun recordFailure(key: String) {
        val record = attemptsByKey.getOrPut(key) { AttemptRecord() }
        record.consecutiveFailures++

        // Check if we've exceeded max attempts
        if (record.consecutiveFailures >= maxAttempts) {
            record.lockedUntil = Clock.System.now() + lockoutDuration
        }
    }

    /**
     * Check if a key is currently locked out without waiting.
     *
     * @param key Unique identifier
     * @return true if currently locked out
     */
    fun isLockedOut(key: String): Boolean {
        val now = Clock.System.now()
        val record = attemptsByKey[key] ?: return false

        return record.lockedUntil?.let { it > now } ?: false
    }

    /**
     * Get the number of consecutive failures for a key.
     *
     * @param key Unique identifier
     * @return Number of consecutive failures
     */
    fun getFailureCount(key: String): Int {
        return attemptsByKey[key]?.consecutiveFailures ?: 0
    }

    /**
     * Manually reset a key's failure counter and lockout.
     * Useful for admin override or testing.
     *
     * @param key Unique identifier
     */
    fun reset(key: String) {
        attemptsByKey.remove(key)
    }

    /**
     * Reset all keys.
     */
    fun resetAll() {
        attemptsByKey.clear()
    }

    /**
     * Calculate exponential backoff delay with jitter.
     *
     * Formula: min(baseDelay * 2^(attempts-1), maxDelay)
     *
     * @param attempts Number of failed attempts
     * @return Delay duration
     */
    private fun calculateBackoffDelay(attempts: Int): Duration {
        val exponentialDelay = (baseDelay.inWholeMilliseconds * 2.0.pow(attempts - 1)).toLong()
        val clampedDelay = min(exponentialDelay, maxDelay.inWholeMilliseconds)

        // Add jitter (0-20% of delay) to prevent thundering herd
        val jitterRange = (clampedDelay * 0.2).toLong()
        val jitter = (0..jitterRange).random()

        return (clampedDelay + jitter).milliseconds
    }
}

/**
 * Exception thrown when rate limit is exceeded.
 */
class RateLimitException(message: String) : SecurityException(message)
