package com.financeapp.security.vault

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class KeyVaultTest {

    // Cheap KDF so the suite stays fast.
    private val cheap = Argon2Params(memoryKiB = 1024, iterations = 1, parallelism = 1)
    private fun vault(store: VaultStore = InMemoryVaultStore()) = KeyVault(store, cheap)

    @Test
    fun `status is NoVault before setup, Unlocked after`() = runTest {
        val kv = vault()
        assertEquals(KeyVault.Status.NoVault, kv.status())
        kv.setUp("correct horse battery staple".toCharArray())
        assertEquals(KeyVault.Status.Unlocked, kv.status())
        assertNotNull(kv.currentDek())
    }

    @Test
    fun `unlock with the correct password returns the same DEK`() = runTest {
        val store = InMemoryVaultStore()
        val dek = vault(store).setUp("correct horse battery staple".toCharArray()).dek

        val kv2 = vault(store)
        assertEquals(KeyVault.Status.Locked, kv2.status())
        assertEquals(dek, kv2.unlock("correct horse battery staple".toCharArray()))
        assertEquals(KeyVault.Status.Unlocked, kv2.status())
    }

    @Test
    fun `unlock with a wrong password returns null and stays locked`() = runTest {
        val store = InMemoryVaultStore()
        vault(store).setUp("correct horse battery staple".toCharArray())

        val kv2 = vault(store)
        assertNull(kv2.unlock("wrong password here".toCharArray()))
        assertEquals(KeyVault.Status.Locked, kv2.status())
        assertNull(kv2.currentDek())
    }

    @Test
    fun `recovery key unlocks and lets the password be reset`() = runTest {
        val store = InMemoryVaultStore()
        val setup = vault(store).setUp("correct horse battery staple".toCharArray())
        val recovery = assertNotNull(setup.recoveryKey)

        val kv2 = vault(store)
        assertEquals(setup.dek, kv2.unlockWithRecovery(recovery.display))

        assertTrue(kv2.resetPasswordWithRecovery(recovery.display, "a brand new passphrase here".toCharArray()))

        val kv3 = vault(store)
        assertNull(kv3.unlock("correct horse battery staple".toCharArray()))
        assertEquals(setup.dek, kv3.unlock("a brand new passphrase here".toCharArray()))
    }

    @Test
    fun `changePassword keeps the same DEK and rejects the old password`() = runTest {
        val store = InMemoryVaultStore()
        val dek = vault(store).setUp("correct horse battery staple".toCharArray()).dek

        val kv2 = vault(store)
        kv2.unlock("correct horse battery staple".toCharArray())
        assertTrue(kv2.changePassword("correct horse battery staple".toCharArray(), "another good passphrase x".toCharArray()))

        val kv3 = vault(store)
        assertNull(kv3.unlock("correct horse battery staple".toCharArray()))
        assertEquals(dek, kv3.unlock("another good passphrase x".toCharArray()))
    }

    @Test
    fun `adopting an existing key uses it verbatim as the DEK`() = runTest {
        val store = InMemoryVaultStore()
        val legacyKey = "kZ3legacyBase64KeyString=="

        val kv = vault(store)
        val result = kv.adoptExistingKeyAsDek(legacyKey, "correct horse battery staple".toCharArray())
        assertEquals(legacyKey, result.dek)

        val kv2 = vault(store)
        assertEquals(legacyKey, kv2.unlock("correct horse battery staple".toCharArray()))
    }

    @Test
    fun `a corrupted vault version fails closed on status`() = runTest {
        val store = InMemoryVaultStore()
        vault(store).setUp("correct horse battery staple".toCharArray())
        val bumped = store.read()!!.copy(version = 999)
        store.write(bumped)

        assertFailsWith<VaultException> { vault(store).status() }
    }
}
