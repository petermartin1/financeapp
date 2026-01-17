package com.financeapp.ui

import com.financeapp.data.seed.DatabaseSeeder
import com.financeapp.domain.model.AppLockState
import com.financeapp.domain.repository.AppLockRepository
import com.financeapp.domain.repository.PreferencesRepository
import com.financeapp.domain.service.PriceRefreshService
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
    private val databaseSeeder: DatabaseSeeder
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _lockState = MutableStateFlow(AppLockState())
    val lockState: StateFlow<AppLockState> = _lockState.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        seedDatabaseIfNeeded()
        checkLockSetup()
        loadThemeMode()
        startPriceRefreshService()
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
            _lockState.value = AppLockState(
                isSetUp = isSetUp,
                isLocked = true,
                failedAttempts = 0
            )
        }
    }

    fun setupPin(pin: String) {
        scope.launch {
            appLockRepository.setPin(pin)
            _lockState.value = AppLockState(
                isSetUp = true,
                isLocked = false,
                failedAttempts = 0
            )
        }
    }

    fun verifyPin(pin: String) {
        scope.launch {
            val isValid = appLockRepository.verifyPin(pin)
            if (isValid) {
                appLockRepository.resetFailedAttempts()
                _lockState.value = _lockState.value.copy(
                    isLocked = false,
                    failedAttempts = 0
                )
            } else {
                appLockRepository.incrementFailedAttempts()
                _lockState.value = _lockState.value.copy(
                    failedAttempts = appLockRepository.getFailedAttempts()
                )
            }
        }
    }

    fun lock() {
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
                        _lockState.value = _lockState.value.copy(
                            isLocked = false,
                            failedAttempts = 0
                        )
                    }
                }
                BiometricResult.FAILED -> {
                    scope.launch {
                        appLockRepository.incrementFailedAttempts()
                        _lockState.value = _lockState.value.copy(
                            failedAttempts = appLockRepository.getFailedAttempts()
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
