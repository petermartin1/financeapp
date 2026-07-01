package com.financeapp.ui

import com.financeapp.data.repository.AppLockRepositoryImpl
import com.financeapp.data.repository.PreferencesStore
import com.financeapp.security.EventType
import com.financeapp.security.SecurityAuditLogger
import com.financeapp.security.vault.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

/**
 * Regression tests for the mid-import auto-lock bug: a long, interactive import (payee review)
 * generates no main-window pointer activity, so the 10-minute idle timer would fire and lock the
 * vault out from under the user, bouncing them to the unlock screen and losing their in-progress
 * work. An operation in progress must suspend the idle auto-lock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelAutoLockTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private class MapPrefs : PreferencesStore {
        val m = mutableMapOf<String, String>()
        override suspend fun getString(key: String) = m[key]
        override suspend fun putString(key: String, value: String) { m[key] = value }
        override suspend fun remove(key: String) { m.remove(key) }
    }

    private fun noMigration(store: InMemoryVaultStore, p: Argon2Params) =
        LegacyKeyMigration(KeyVault(store, p), legacyKeyProvider = { null }, onMigrated = {})

    private val password = "correct horse battery staple"

    /** An unlocked VaultViewModel wired to a controllable clock and short idle timeout. */
    private suspend fun unlockedVm(clock: () -> Long, timeoutMs: Long): VaultViewModel {
        val cheap = Argon2Params(1024, 1, 1)
        val store = InMemoryVaultStore()
        KeyVault(store, cheap).setUp(password.toCharArray())
        val lock = AppLockRepositoryImpl(MapPrefs()) { clock() }
        val vm = VaultViewModel(
            KeyVault(store, cheap), noMigration(store, cheap), lock,
            idleTimeoutMs = timeoutMs, now = clock
        )
        assertTrue(vm.attemptUnlock(password.toCharArray()))
        assertEquals(VaultGate.Unlocked, vm.gate.value)
        return vm
    }

    @Test
    fun `idle past the timeout still locks the vault`() = runTest {
        var t = 0L
        val vm = unlockedVm({ t }, timeoutMs = 1000L)
        t = 1000L
        vm.checkAutoLock()
        assertEquals(VaultGate.Locked, vm.gate.value)
    }

    @Test
    fun `an idle auto-lock is recorded in the security audit log`() = runTest {
        SecurityAuditLogger.clear()
        var t = 0L
        val vm = unlockedVm({ t }, timeoutMs = 1000L)
        t = 1000L
        vm.checkAutoLock()
        assertTrue(
            SecurityAuditLogger.getRecentEvents().any { it.type == EventType.SESSION_AUTO_LOCKED },
            "an idle auto-lock should leave a diagnostic trail"
        )
    }

    @Test
    fun `does not auto-lock while an operation is in progress`() = runTest {
        var t = 0L
        val vm = unlockedVm({ t }, timeoutMs = 1000L)
        vm.beginBusy()            // e.g. an import (payee review) started
        t = 10 * 60_000L          // far past the idle timeout, user reviewing payees
        vm.checkAutoLock()
        assertEquals(
            VaultGate.Unlocked, vm.gate.value,
            "an in-progress import must not be interrupted by idle auto-lock"
        )
    }

    @Test
    fun `resumes the idle countdown fresh after the operation ends`() = runTest {
        var t = 0L
        val vm = unlockedVm({ t }, timeoutMs = 1000L)
        vm.beginBusy()
        t = 10_000L
        vm.endBusy()              // import finished at t=10_000
        vm.checkAutoLock()        // immediately after: not past a fresh timeout
        assertEquals(
            VaultGate.Unlocked, vm.gate.value,
            "finishing an operation should reset the idle clock, not lock instantly"
        )
        t = 11_000L               // a fresh idle period elapses
        vm.checkAutoLock()
        assertEquals(VaultGate.Locked, vm.gate.value)
    }

    @Test
    fun `nested operations keep the vault unlocked until all finish`() = runTest {
        var t = 0L
        val vm = unlockedVm({ t }, timeoutMs = 1000L)
        vm.beginBusy()
        vm.beginBusy()
        vm.endBusy()              // one still in progress
        t = 10 * 60_000L
        vm.checkAutoLock()
        assertEquals(VaultGate.Unlocked, vm.gate.value)
        vm.endBusy()              // now all done; clock reset to t
        t += 2000L
        vm.checkAutoLock()
        assertEquals(VaultGate.Locked, vm.gate.value)
    }
}
