package com.financeapp.data.repository

import com.financeapp.domain.repository.AppLockRepository
import com.financeapp.domain.repository.LockoutState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Clock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AppLockRepositoryImpl(
    private val preferencesStore: PreferencesStore,
    // Injectable clock keeps the time-based lockout deterministically testable.
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : AppLockRepository {

    override suspend fun isLockSetUp(): Boolean = withContext(Dispatchers.IO) {
        preferencesStore.getString(KEY_PIN_HASH) != null
    }

    override suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        val hash = hashPin(pin)
        preferencesStore.putString(KEY_PIN_HASH, hash)
    }

    override suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val state = readLockoutState()
        // Enforce the lockout server-side: while locked, refuse without even checking the
        // pin. The counter is persisted, so this survives restarts (no in-memory bypass).
        if (state.isLockedOut(now())) return@withContext false

        val storedHash = preferencesStore.getString(KEY_PIN_HASH) ?: return@withContext false
        if (matchesStoredPin(pin, storedHash)) {
            resetFailedAttemptsInternal()
            true
        } else {
            recordFailedAttemptInternal(state.failedAttempts)
            false
        }
    }

    override suspend fun clearLock() = withContext(Dispatchers.IO) {
        preferencesStore.remove(KEY_PIN_HASH)
        resetFailedAttemptsInternal()
    }

    override suspend fun getLockoutState(): LockoutState = withContext(Dispatchers.IO) {
        readLockoutState()
    }

    override suspend fun recordFailedAttempt(): LockoutState = withContext(Dispatchers.IO) {
        recordFailedAttemptInternal(readLockoutState().failedAttempts)
    }

    override suspend fun resetFailedAttempts() = withContext(Dispatchers.IO) {
        resetFailedAttemptsInternal()
    }

    private suspend fun readLockoutState(): LockoutState = LockoutState(
        failedAttempts = preferencesStore.getString(KEY_FAILED_ATTEMPTS)?.toIntOrNull() ?: 0,
        lockedUntilEpochMs = preferencesStore.getString(KEY_LOCKED_UNTIL)?.toLongOrNull()
    )

    private suspend fun recordFailedAttemptInternal(currentAttempts: Int): LockoutState {
        val attempts = currentAttempts + 1
        preferencesStore.putString(KEY_FAILED_ATTEMPTS, attempts.toString())

        val lockedUntil = if (attempts >= LOCKOUT_THRESHOLD) now() + lockoutDurationMs(attempts) else null
        if (lockedUntil != null) {
            preferencesStore.putString(KEY_LOCKED_UNTIL, lockedUntil.toString())
        } else {
            preferencesStore.remove(KEY_LOCKED_UNTIL)
        }
        return LockoutState(attempts, lockedUntil)
    }

    private suspend fun resetFailedAttemptsInternal() {
        preferencesStore.remove(KEY_FAILED_ATTEMPTS)
        preferencesStore.remove(KEY_LOCKED_UNTIL)
    }

    /**
     * Exponential backoff once the threshold is hit: BASE * 2^(attempts - threshold),
     * capped at MAX. The first lockout (at the threshold) is BASE.
     */
    private fun lockoutDurationMs(attempts: Int): Long {
        val over = (attempts - LOCKOUT_THRESHOLD).coerceAtLeast(0)
        val exponential = BASE_LOCKOUT_MS.toDouble() * 2.0.pow(over)
        return min(exponential, MAX_LOCKOUT_MS.toDouble()).toLong()
    }

    private suspend fun matchesStoredPin(pin: String, storedHash: String): Boolean {
        val parsedHash = parseHashedPin(storedHash)
        if (parsedHash != null) {
            return verifyHashedPin(pin, parsedHash)
        }

        // Legacy unsalted SHA-256 hash: constant-time compare, then upgrade to the salted hash.
        val legacyHash = hashLegacyPin(pin)
        if (MessageDigest.isEqual(storedHash.toByteArray(), legacyHash.toByteArray())) {
            preferencesStore.putString(KEY_PIN_HASH, hashPin(pin))
            return true
        }
        return false
    }

    private fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val hashBytes = deriveKey(pin, salt, PBKDF2_ITERATIONS, PBKDF2_ALGORITHM)
        val payload = listOf(
            HASH_VERSION,
            PBKDF2_ALGORITHM,
            PBKDF2_ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hashBytes)
        )
        return payload.joinToString(":")
    }

    private fun verifyHashedPin(pin: String, parsedHash: PinHash): Boolean {
        return try {
            val derived = deriveKey(pin, parsedHash.salt, parsedHash.iterations, parsedHash.algorithm)
            MessageDigest.isEqual(derived, parsedHash.hash)
        } catch (e: Exception) {
            false
        }
    }

    private fun parseHashedPin(storedHash: String): PinHash? {
        val parts = storedHash.split(":")
        if (parts.size != 5 || parts[0] != HASH_VERSION) {
            return null
        }
        val iterations = parts[2].toIntOrNull() ?: return null
        if (iterations <= 0) {
            return null
        }
        if (parts[1].isBlank()) {
            return null
        }

        return try {
            PinHash(
                algorithm = parts[1],
                iterations = iterations,
                salt = Base64.getDecoder().decode(parts[3]),
                hash = Base64.getDecoder().decode(parts[4])
            )
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun deriveKey(pin: String, salt: ByteArray, iterations: Int, algorithm: String): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, DERIVED_KEY_LENGTH_BITS)
        try {
            val factory = SecretKeyFactory.getInstance(algorithm)
            return factory.generateSecret(spec).encoded
        } finally {
            // Zero the password CharArray copy held by the spec rather than leaving it to GC (R6).
            spec.clearPassword()
        }
    }

    private fun hashLegacyPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_PIN_HASH = "app_lock_pin_hash"
        private const val KEY_FAILED_ATTEMPTS = "app_lock_failed_attempts"
        private const val KEY_LOCKED_UNTIL = "app_lock_locked_until"
        private const val HASH_VERSION = "v2"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 100000
        private const val SALT_LENGTH_BYTES = 16
        private const val DERIVED_KEY_LENGTH_BITS = 256

        // Brute-force protection: lock after this many consecutive failures, then apply an
        // exponential backoff (BASE doubling each further failure) capped at MAX.
        private const val LOCKOUT_THRESHOLD = 5
        private const val BASE_LOCKOUT_MS = 30_000L          // 30 seconds
        private const val MAX_LOCKOUT_MS = 15 * 60_000L      // 15 minutes
    }

    private data class PinHash(
        val algorithm: String,
        val iterations: Int,
        val salt: ByteArray,
        val hash: ByteArray
    )
}

interface PreferencesStore {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
    suspend fun remove(key: String)
}
