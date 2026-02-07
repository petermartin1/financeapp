package com.financeapp.data.repository

import com.financeapp.domain.repository.AppLockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AppLockRepositoryImpl(
    private val preferencesStore: PreferencesStore
) : AppLockRepository {

    private var failedAttempts = 0

    override suspend fun isLockSetUp(): Boolean = withContext(Dispatchers.IO) {
        preferencesStore.getString(KEY_PIN_HASH) != null
    }

    override suspend fun setPin(pin: String) = withContext(Dispatchers.IO) {
        val hash = hashPin(pin)
        preferencesStore.putString(KEY_PIN_HASH, hash)
    }

    override suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val storedHash = preferencesStore.getString(KEY_PIN_HASH) ?: return@withContext false
        val parsedHash = parseHashedPin(storedHash)

        if (parsedHash != null) {
            return@withContext verifyHashedPin(pin, parsedHash)
        }

        val legacyHash = hashLegacyPin(pin)
        if (storedHash == legacyHash) {
            preferencesStore.putString(KEY_PIN_HASH, hashPin(pin))
            return@withContext true
        }

        false
    }

    override suspend fun clearLock() = withContext(Dispatchers.IO) {
        preferencesStore.remove(KEY_PIN_HASH)
        failedAttempts = 0
    }

    override fun getFailedAttempts(): Int = failedAttempts

    override fun incrementFailedAttempts() {
        failedAttempts++
    }

    override fun resetFailedAttempts() {
        failedAttempts = 0
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
        val factory = SecretKeyFactory.getInstance(algorithm)
        return factory.generateSecret(spec).encoded
    }

    private fun hashLegacyPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_PIN_HASH = "app_lock_pin_hash"
        private const val HASH_VERSION = "v2"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 100000
        private const val SALT_LENGTH_BYTES = 16
        private const val DERIVED_KEY_LENGTH_BITS = 256
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
