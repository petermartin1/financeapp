package com.financeapp.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppLockRepositoryTest {

    private val store = FakePreferencesStore()
    private var now = 1_000_000L
    private val correctPin = "135790"

    private fun newRepo() = AppLockRepositoryImpl(store) { now }

    @Test
    fun `verifyPin accepts the correct pin and clears failed attempts`() = runTest {
        val repo = newRepo()
        repo.setPin(correctPin)
        repo.recordFailedAttempt()

        assertTrue(repo.verifyPin(correctPin))
        assertEquals(0, repo.getLockoutState().failedAttempts)
        assertNull(repo.getLockoutState().lockedUntilEpochMs)
    }

    @Test
    fun `verifyPin rejects a wrong pin and increments failed attempts`() = runTest {
        val repo = newRepo()
        repo.setPin(correctPin)

        assertFalse(repo.verifyPin("000000"))
        assertEquals(1, repo.getLockoutState().failedAttempts)
    }

    @Test
    fun `failed attempts persist across repository instances`() = runTest {
        newRepo().apply { setPin(correctPin) }

        newRepo().verifyPin("000000")
        newRepo().verifyPin("000000")

        // A fresh instance (simulating an app restart) must see the prior failures
        // rather than resetting the counter in memory — that was the lockout bypass.
        assertEquals(2, newRepo().getLockoutState().failedAttempts)
    }

    @Test
    fun `lockout triggers at the threshold and blocks even the correct pin across a restart`() = runTest {
        newRepo().apply { setPin(correctPin) }

        repeat(5) { newRepo().verifyPin("000000") }

        // A brand-new instance (process restart) must still be locked out.
        val restarted = newRepo()
        assertTrue(restarted.getLockoutState().isLockedOut(now))
        assertFalse(restarted.verifyPin(correctPin)) // correct pin is refused while locked
    }

    @Test
    fun `lockout expires after its duration and the correct pin then unlocks`() = runTest {
        val repo = newRepo()
        repo.setPin(correctPin)
        repeat(5) { repo.verifyPin("000000") }

        val lockedUntil = repo.getLockoutState().lockedUntilEpochMs!!
        now = lockedUntil + 1

        assertTrue(repo.verifyPin(correctPin))
        assertEquals(0, repo.getLockoutState().failedAttempts)
    }

    @Test
    fun `lockout duration grows with continued failures`() = runTest {
        val repo = newRepo()
        repo.setPin(correctPin)

        repeat(5) { repo.verifyPin("000000") }
        val firstLockMs = repo.getLockoutState().lockedUntilEpochMs!! - now

        // Wait out the first lockout, then fail once more.
        now = repo.getLockoutState().lockedUntilEpochMs!! + 1
        repo.verifyPin("000000")
        val secondLockMs = repo.getLockoutState().lockedUntilEpochMs!! - now

        assertTrue(secondLockMs > firstLockMs, "expected $secondLockMs > $firstLockMs")
    }

    @Test
    fun `clearLock removes the pin and any lockout state`() = runTest {
        val repo = newRepo()
        repo.setPin(correctPin)
        repeat(5) { repo.verifyPin("000000") }

        repo.clearLock()

        assertFalse(repo.isLockSetUp())
        assertEquals(0, repo.getLockoutState().failedAttempts)
        assertNull(repo.getLockoutState().lockedUntilEpochMs)
    }

    private class FakePreferencesStore : PreferencesStore {
        private val map = mutableMapOf<String, String>()
        override suspend fun getString(key: String): String? = map[key]
        override suspend fun putString(key: String, value: String) { map[key] = value }
        override suspend fun remove(key: String) { map.remove(key) }
    }
}
