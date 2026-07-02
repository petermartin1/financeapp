package com.financeapp.domain.service

import com.financeapp.domain.repository.PreferencesRepository
import com.financeapp.domain.repository.SubscriptionRepository

/**
 * Orchestrates subscription detection. [scanAfterImport] re-scans whenever new transactions land;
 * [runInitialScanIfNeeded] scans existing history exactly once (tracked by a preferences flag set
 * only after a committed scan, so a crash mid-scan simply retries next launch).
 */
class SubscriptionScanService(
    private val subscriptionRepository: SubscriptionRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend fun scanAfterImport() {
        subscriptionRepository.rescan()
    }

    suspend fun runInitialScanIfNeeded() {
        if (preferencesRepository.isSubscriptionInitialScanDone()) return
        subscriptionRepository.rescan()               // persist + commit first
        preferencesRepository.markSubscriptionInitialScanDone()  // then set the flag
    }
}
