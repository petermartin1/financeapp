package com.financeapp.domain.repository

interface AppLockRepository {
    suspend fun isLockSetUp(): Boolean
    suspend fun setPin(pin: String)

    /**
     * Verifies [pin] against the stored hash. Enforces lockout: while a lockout is in
     * effect this returns false without checking the pin. On success the failure counter
     * and lockout are cleared; on failure the counter is incremented and a time-based
     * lockout is applied once the threshold is reached.
     */
    suspend fun verifyPin(pin: String): Boolean
    suspend fun clearLock()

    /** Current persisted lockout state (failure count + lockout expiry). */
    suspend fun getLockoutState(): LockoutState

    /** Records a failed attempt from a non-pin path (e.g. biometrics) and returns the new state. */
    suspend fun recordFailedAttempt(): LockoutState
    suspend fun resetFailedAttempts()
}

/**
 * Persisted brute-force protection state. Persisting this (rather than keeping it in memory)
 * is what prevents the lockout from being bypassed by restarting the app.
 */
data class LockoutState(
    val failedAttempts: Int,
    val lockedUntilEpochMs: Long?
) {
    fun isLockedOut(nowEpochMs: Long): Boolean =
        lockedUntilEpochMs != null && nowEpochMs < lockedUntilEpochMs

    fun remainingLockoutMs(nowEpochMs: Long): Long =
        if (lockedUntilEpochMs != null && nowEpochMs < lockedUntilEpochMs) lockedUntilEpochMs - nowEpochMs else 0L
}
