package com.financeapp.ui

import com.financeapp.data.repository.AppLockRepositoryImpl
import com.financeapp.data.repository.PreferencesStore
import com.financeapp.security.vault.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelLockoutTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class MapPrefs : PreferencesStore {
        val m = mutableMapOf<String, String>()
        override suspend fun getString(key: String) = m[key]
        override suspend fun putString(key: String, value: String) { m[key] = value }
        override suspend fun remove(key: String) { m.remove(key) }
    }

    private fun noMigration(store: InMemoryVaultStore, p: Argon2Params) =
        LegacyKeyMigration(KeyVault(store, p), legacyKeyProvider = { null }, onMigrated = {})

    @Test
    fun `repeated wrong passwords trip the persisted lockout`() = runTest {
        val cheap = Argon2Params(1024, 1, 1)
        val store = InMemoryVaultStore()
        KeyVault(store, cheap).setUp("correct horse battery staple".toCharArray())

        val prefs = MapPrefs()
        val lock = AppLockRepositoryImpl(prefs) { 0L }
        val vm = VaultViewModel(KeyVault(store, cheap), noMigration(store, cheap), lock) { 0L }

        repeat(5) { vm.attemptUnlock("wrong wrong wrong".toCharArray()) }

        assertNotNull(lock.getLockoutState().lockedUntilEpochMs, "should be locked out after 5 failures")
    }

    @Test
    fun `a correct password resets the failure counter`() = runTest {
        val cheap = Argon2Params(1024, 1, 1)
        val store = InMemoryVaultStore()
        KeyVault(store, cheap).setUp("correct horse battery staple".toCharArray())

        val prefs = MapPrefs()
        val lock = AppLockRepositoryImpl(prefs) { 0L }
        val vm = VaultViewModel(KeyVault(store, cheap), noMigration(store, cheap), lock) { 0L }

        vm.attemptUnlock("wrong wrong wrong".toCharArray())
        assertTrue(vm.attemptUnlock("correct horse battery staple".toCharArray()))
        assertEquals(0, lock.getLockoutState().failedAttempts)
    }
}
