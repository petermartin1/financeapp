package com.financeapp.data.repository

import com.financeapp.domain.repository.AppLockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.security.MessageDigest

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
        val inputHash = hashPin(pin)
        storedHash == inputHash
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
        // In production, use PBKDF2 or Argon2 with salt
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_PIN_HASH = "app_lock_pin_hash"
    }
}

interface PreferencesStore {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
    suspend fun remove(key: String)
}
