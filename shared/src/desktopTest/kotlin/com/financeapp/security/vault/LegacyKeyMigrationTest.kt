package com.financeapp.security.vault

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LegacyKeyMigrationTest {

    private val cheap = Argon2Params(memoryKiB = 1024, iterations = 1, parallelism = 1)

    @Test
    fun `needsMigration is true when a legacy key exists but no vault`() = runTest {
        val store = InMemoryVaultStore()
        val migration = LegacyKeyMigration(KeyVault(store, cheap), legacyKeyProvider = { "legacyKey==" }, onMigrated = {})
        assertTrue(migration.needsMigration())
    }

    @Test
    fun `needsMigration is false when a vault already exists`() = runTest {
        val store = InMemoryVaultStore()
        KeyVault(store, cheap).setUp("correct horse battery staple".toCharArray())
        val migration = LegacyKeyMigration(KeyVault(store, cheap), legacyKeyProvider = { "legacyKey==" }, onMigrated = {})
        assertFalse(migration.needsMigration())
    }

    @Test
    fun `migrate adopts the legacy key as the DEK and clears it afterwards`() = runTest {
        val store = InMemoryVaultStore()
        var cleared = false
        val migration = LegacyKeyMigration(
            KeyVault(store, cheap),
            legacyKeyProvider = { "legacyKey==" },
            onMigrated = { cleared = true }
        )

        val result = migration.migrate("correct horse battery staple".toCharArray())
        assertEquals("legacyKey==", result.dek)
        assertTrue(cleared, "onMigrated must run so the keystore key can be deleted")

        // The same legacy key now unlocks via the new vault.
        assertEquals("legacyKey==", KeyVault(store, cheap).unlock("correct horse battery staple".toCharArray()))
    }
}
