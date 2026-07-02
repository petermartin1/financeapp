package com.financeapp.domain.service

import com.financeapp.domain.model.DetectedSubscription
import com.financeapp.domain.repository.SubscriptionRepository
import com.financeapp.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SubscriptionScanServiceTest {
    private class FakeSubs : SubscriptionRepository {
        var rescans = 0
        override fun getSubscriptions(): Flow<List<DetectedSubscription>> = flowOf(emptyList())
        override suspend fun rescan() { rescans++ }
        override suspend fun confirm(id: Long) {}
        override suspend fun dismiss(id: Long) {}
        override suspend fun markPayeeAsSubscription(payeeId: Long) {}
        override suspend fun createScheduledFromSubscription(id: Long) {}
        override fun notifySubscriptionsChanged() {}
    }
    private class FakePrefs : PreferencesRepository {
        var done = false
        var markedAfterRescan: Boolean? = null
        val subs: FakeSubs
        constructor(subs: FakeSubs) { this.subs = subs }
        override suspend fun getThemeMode(): String? = null
        override suspend fun setThemeMode(mode: String) {}
        override suspend fun getDashboardConfig(): String? = null
        override suspend fun setDashboardConfig(config: String) {}
        override suspend fun isSubscriptionInitialScanDone(): Boolean = done
        override suspend fun markSubscriptionInitialScanDone() {
            // capture that the scan ran before the flag was set
            markedAfterRescan = subs.rescans > 0
            done = true
        }
    }

    @Test
    fun `initial scan runs once then is skipped`() = runBlocking {
        val subs = FakeSubs(); val prefs = FakePrefs(subs)
        val svc = SubscriptionScanService(subs, prefs)
        svc.runInitialScanIfNeeded()
        svc.runInitialScanIfNeeded()
        assertEquals(1, subs.rescans)
    }

    @Test
    fun `flag is set only after the scan runs`() = runBlocking {
        val subs = FakeSubs(); val prefs = FakePrefs(subs)
        SubscriptionScanService(subs, prefs).runInitialScanIfNeeded()
        assertEquals(true, prefs.markedAfterRescan)
    }

    @Test
    fun `scanAfterImport always rescans`() = runBlocking {
        val subs = FakeSubs(); val prefs = FakePrefs(subs)
        val svc = SubscriptionScanService(subs, prefs)
        svc.scanAfterImport()
        svc.scanAfterImport()
        assertEquals(2, subs.rescans)
    }

    @Test
    fun `failed scan does not set the flag`() = runBlocking {
        val subs = object : SubscriptionRepository by FakeSubs() {
            override suspend fun rescan() { throw RuntimeException("boom") }
        }
        val prefs = FakePrefs(FakeSubs())
        val svc = SubscriptionScanService(subs, prefs)
        runCatching { svc.runInitialScanIfNeeded() }
        assertFalse(prefs.done, "flag must not be set when the scan fails")
    }
}
