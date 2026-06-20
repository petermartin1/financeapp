package com.financeapp.security.vault

/**
 * Migrates an install whose DB key lives in the OS keystore to a password-sealed vault by
 * adopting the existing key verbatim as the DEK (so the on-disk DB still opens). After a
 * successful migration, onMigrated() runs so the caller can delete the keystore entry.
 */
class LegacyKeyMigration(
    private val keyVault: KeyVault,
    private val legacyKeyProvider: () -> String?,
    private val onMigrated: () -> Unit
) {
    suspend fun needsMigration(): Boolean =
        keyVault.status() == KeyVault.Status.NoVault && legacyKeyProvider() != null

    suspend fun migrate(password: CharArray, generateRecovery: Boolean = true): KeyVault.SetupResult {
        val legacyKey = legacyKeyProvider()
            ?: throw VaultException("No legacy key to migrate")
        val result = keyVault.adoptExistingKeyAsDek(legacyKey, password, generateRecovery)
        onMigrated()
        return result
    }
}
