package com.financeapp.ui

import com.financeapp.data.seed.DatabaseSeeder
import com.financeapp.domain.repository.PreferencesRepository
import com.financeapp.domain.service.PriceRefreshService
import com.financeapp.domain.service.SnapshotScheduler
import com.financeapp.domain.service.SubscriptionScanService
import com.financeapp.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns post-unlock application bootstrap and theme state. The app lock/unlock gate now lives in
 * [VaultViewModel] (master-password vault); this view model is only ever used once the vault is
 * unlocked, so all DB-backed startup is funnelled through [startPostUnlock].
 */
class AppViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val priceRefreshService: PriceRefreshService,
    private val databaseSeeder: DatabaseSeeder,
    private val snapshotScheduler: SnapshotScheduler,
    private val subscriptionScanService: SubscriptionScanService
) {
    private val scope = supervisedViewModelScope()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private var started = false

    /** Runs DB-backed startup exactly once, after the vault is unlocked. */
    fun startPostUnlock() {
        if (started) return
        started = true
        seedDatabaseIfNeeded()
        loadThemeMode()
        startPriceRefreshService()
        startSnapshotScheduler()
        runInitialSubscriptionScan()
    }

    private fun seedDatabaseIfNeeded() {
        scope.launch {
            databaseSeeder.seedIfEmpty()
        }
    }

    private fun startPriceRefreshService() {
        // Start automatic price refresh every 15 minutes for investment holdings
        priceRefreshService.startAutoRefresh(intervalMinutes = 15)
    }

    private fun startSnapshotScheduler() {
        // Accrue daily portfolio performance history automatically (the scheduler was
        // previously registered but never started, so history never accumulated — N5).
        snapshotScheduler.startDailySnapshots()
    }

    private fun runInitialSubscriptionScan() {
        scope.launch {
            try {
                subscriptionScanService.runInitialScanIfNeeded()
            } catch (e: Exception) {
                println("Warning: initial subscription scan failed: ${e.message}")
            }
        }
    }

    private fun loadThemeMode() {
        scope.launch {
            val savedMode = preferencesRepository.getThemeMode()
            _themeMode.value = ThemeMode.entries.find { it.name == savedMode } ?: ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        scope.launch {
            preferencesRepository.setThemeMode(mode.name)
            _themeMode.value = mode
        }
    }
}
