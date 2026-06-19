package com.financeapp.security.vault

import javax.crypto.AEADBadTagException
import java.security.SecureRandom
import java.util.Base64

class VaultException(message: String) : Exception(message)

/**
 * Holds the unlocked Data Encryption Key (DEK) in memory and seals it on disk under a
 * password-derived Argon2id key (and an optional recovery key). The DEK is the exact secret
 * string consumed by DatabaseDriverFactory.
 */
class KeyVault(
    private val store: VaultStore,
    private val kdfParams: Argon2Params = Argon2Params.DEFAULT
) {
    enum class Status { NoVault, Locked, Unlocked }

    data class SetupResult(val dek: String, val recoveryKey: RecoveryKey?)

    private var unlockedDek: String? = null

    suspend fun status(): Status {
        if (unlockedDek != null) return Status.Unlocked
        val vault = store.read() ?: return Status.NoVault
        if (vault.version != VaultFile.CURRENT_VERSION) {
            throw VaultException("Unsupported vault version ${vault.version}")
        }
        return Status.Locked
    }

    fun currentDek(): String? = unlockedDek

    suspend fun setUp(password: CharArray, generateRecovery: Boolean = true): SetupResult {
        val dek = Base64.getEncoder().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        return writeVault(dek, password, generateRecovery)
    }

    suspend fun adoptExistingKeyAsDek(
        existingKey: String,
        password: CharArray,
        generateRecovery: Boolean = true
    ): SetupResult = writeVault(existingKey, password, generateRecovery)

    suspend fun unlock(password: CharArray): String? {
        val vault = store.read() ?: return null
        val kek = deriveKek(password, vault)
        return try {
            val dek = AesGcm.decrypt(kek, vault.wrappedDek.password.toGcmBox()).decodeToString()
            unlockedDek = dek
            dek
        } catch (e: AEADBadTagException) {
            null
        } finally {
            kek.fill(0)
        }
    }

    suspend fun unlockWithRecovery(code: String): String? {
        val vault = store.read() ?: return null
        val recoveryBox = vault.wrappedDek.recovery ?: return null
        val key = RecoveryKey.decode(code) ?: return null
        return try {
            val dek = AesGcm.decrypt(key, recoveryBox.toGcmBox()).decodeToString()
            unlockedDek = dek
            dek
        } catch (e: AEADBadTagException) {
            null
        } finally {
            key.fill(0)
        }
    }

    suspend fun changePassword(old: CharArray, new: CharArray): Boolean {
        val dek = unlock(old) ?: return false
        writeVault(dek, new, regenerateRecovery = false, existingVault = store.read())
        return true
    }

    suspend fun resetPasswordWithRecovery(code: String, new: CharArray): Boolean {
        val dek = unlockWithRecovery(code) ?: return false
        writeVault(dek, new, regenerateRecovery = false, existingVault = store.read())
        return true
    }

    fun lock() {
        unlockedDek = null
    }

    // --- internals ---

    private suspend fun writeVault(
        dek: String,
        password: CharArray,
        generateRecovery: Boolean = true,
        regenerateRecovery: Boolean = true,
        existingVault: VaultFile? = null
    ): SetupResult {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val kek = Argon2.deriveKey(password, salt, kdfParams)
        val dekBytes = dek.encodeToByteArray()

        val passwordBox = SerializableBox.of(AesGcm.encrypt(kek, dekBytes))

        // Recovery: regenerate when asked, otherwise preserve any existing recovery wrap.
        var recoveryKey: RecoveryKey? = null
        val recoveryBox: SerializableBox? = when {
            regenerateRecovery && generateRecovery -> {
                val rk = RecoveryKey.generate()
                recoveryKey = rk
                SerializableBox.of(AesGcm.encrypt(rk.bytes, dekBytes))
            }
            !regenerateRecovery -> existingVault?.wrappedDek?.recovery
            else -> null
        }

        kek.fill(0)
        dekBytes.fill(0)

        store.write(
            VaultFile(
                version = VaultFile.CURRENT_VERSION,
                kdf = kdfParams,
                kdfSaltB64 = Base64.getEncoder().encodeToString(salt),
                wrappedDek = WrappedDek(passwordBox, recoveryBox)
            )
        )
        unlockedDek = dek
        return SetupResult(dek, recoveryKey)
    }

    private fun deriveKek(password: CharArray, vault: VaultFile): ByteArray =
        Argon2.deriveKey(password, Base64.getDecoder().decode(vault.kdfSaltB64), vault.kdf)
}
