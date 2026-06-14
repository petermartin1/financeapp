package com.financeapp.ui

import com.financeapp.data.seed.DatabaseSeeder
import com.financeapp.domain.model.AppLockState
import com.financeapp.domain.repository.AppLockRepository
import com.financeapp.domain.repository.PreferencesRepository
import com.financeapp.domain.service.PriceRefreshService
import com.financeapp.domain.service.SnapshotScheduler
import com.financeapp.security.BiometricAuth
import com.financeapp.security.BiometricResult
import com.financeapp.security.BiometricType
import com.financeapp.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(
    private val appLockRepository: AppLockRepository,
    private val biometricAuth: BiometricAuth,
    private val preferencesRepository: PreferencesRepository,
    private val priceRefreshService: PriceRefreshService,
    private val databaseSeeder: DatabaseSeeder,
    private val snapshotScheduler: SnapshotScheduler
) {
    companion object {
        // Track if user has unlocked in this session.
        // Using companion object so state survives ViewModel recreation
        // but is lost when app process is killed (desired for security).
        private var hasUnlockedThisSession = false
    }

    private val scope = supervisedViewModelScope()

    private val _lockState = MutableStateFlow(AppLockState())
    val lockState: StateFlow<AppLockState> = _lockState.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        seedDatabaseIfNeeded()
        checkLockSetup()
        loadThemeMode()
        startPriceRefreshService()
        startSnapshotScheduler()
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

    private fun checkLockSetup() {
        scope.launch {
            val isSetUp = appLockRepository.isLockSetUp()
            // Reflect any persisted lockout so a restart can't silently clear it.
            val lockout = appLockRepository.getLockoutState()
            _lockState.value = AppLockState(
                isSetUp = isSetUp,
                isLocked = isSetUp && !hasUnlockedThisSession,
                failedAttempts = lockout.failedAttempts,
                lockedUntilEpochMs = lockout.lockedUntilEpochMs
            )
        }
    }

    fun setupPin(pin: String) {
        scope.launch {
            appLockRepository.setPin(pin)
            hasUnlockedThisSession = true
            _lockState.value = AppLockState(
                isSetUp = true,
                isLocked = false,
                failedAttempts = 0
            )
        }
    }

    fun verifyPin(pin: String) {
        scope.launch {
            // verifyPin enforces the persisted lockout and updates the counter internally.
            val isValid = appLockRepository.verifyPin(pin)
            if (isValid) {
                hasUnlockedThisSession = true
                _lockState.value = _lockState.value.copy(
                    isLocked = false,
                    failedAttempts = 0,
                    lockedUntilEpochMs = null
                )
            } else {
                val lockout = appLockRepository.getLockoutState()
                _lockState.value = _lockState.value.copy(
                    failedAttempts = lockout.failedAttempts,
                    lockedUntilEpochMs = lockout.lockedUntilEpochMs
                )
            }
        }
    }

    fun lock() {
        hasUnlockedThisSession = false
        _lockState.value = _lockState.value.copy(isLocked = true)
    }

    fun isBiometricAvailable(): Boolean {
        return biometricAuth.isAvailable()
    }

    fun getBiometricType(): BiometricType {
        return biometricAuth.getBiometricType()
    }

    fun authenticateWithBiometric() {
        val reason = "Unlock Finance App"
        biometricAuth.authenticate(reason) { result ->
            when (result) {
                BiometricResult.SUCCESS -> {
                    scope.launch {
                        appLockRepository.resetFailedAttempts()
                        hasUnlockedThisSession = true
                        _lockState.value = _lockState.value.copy(
                            isLocked = false,
                            failedAttempts = 0,
                            lockedUntilEpochMs = null
                        )
                    }
                }
                BiometricResult.FAILED -> {
                    scope.launch {
                        val lockout = appLockRepository.recordFailedAttempt()
                        _lockState.value = _lockState.value.copy(
                            failedAttempts = lockout.failedAttempts,
                            lockedUntilEpochMs = lockout.lockedUntilEpochMs
                        )
                    }
                }
                else -> {
                    // Cancelled or not available - do nothing, user can use PIN
                }
            }
        }
    }
}
