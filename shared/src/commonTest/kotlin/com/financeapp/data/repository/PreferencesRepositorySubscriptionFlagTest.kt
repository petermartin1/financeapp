package com.financeapp.data.repository

import com.financeapp.domain.repository.PreferencesRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferencesRepositorySubscriptionFlagTest {
    private class FakeStore : PreferencesStore {
        private val map = mutableMapOf<String, String>()
        override suspend fun getString(key: String): String? = map[key]
        override suspend fun putString(key: String, value: String) { map[key] = value }
        override suspend fun remove(key: String) { map.remove(key) }
    }

    private val repo: PreferencesRepository = PreferencesRepositoryImpl(FakeStore())

    @Test
    fun `flag defaults false then true after marking`() = runBlocking {
        assertFalse(repo.isSubscriptionInitialScanDone())
        repo.markSubscriptionInitialScanDone()
        assertTrue(repo.isSubscriptionInitialScanDone())
    }
}
