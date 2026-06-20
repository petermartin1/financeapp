package com.financeapp.ui

import com.financeapp.security.vault.KeyVault
import com.financeapp.security.vault.LegacyKeyMigration
import com.financeapp.security.vault.PasswordStrength
import com.financeapp.security.vault.RecoveryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VaultGate { Loading, Setup, Migrate, Locked, Unlocked }

class VaultViewModel(
    private val keyVault: KeyVault,
    private val migration: LegacyKeyMigration
) {
    private val scope = supervisedViewModelScope()

    private val _gate = MutableStateFlow(VaultGate.Loading)
    val gate: StateFlow<VaultGate> = _gate.asStateFlow()

    private val _recoveryToShow = MutableStateFlow<RecoveryKey?>(null)
    val recoveryToShow: StateFlow<RecoveryKey?> = _recoveryToShow.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { refresh() }

    private fun refresh() {
        scope.launch {
            _gate.value = when {
                keyVault.status() == KeyVault.Status.Unlocked -> VaultGate.Unlocked
                migration.needsMigration() -> VaultGate.Migrate
                keyVault.status() == KeyVault.Status.NoVault -> VaultGate.Setup
                else -> VaultGate.Locked
            }
        }
    }

    fun checkStrength(password: CharArray): PasswordStrength.Result = PasswordStrength.evaluate(password)

    fun setUp(password: CharArray) {
        scope.launch {
            val result = keyVault.setUp(password)
            _recoveryToShow.value = result.recoveryKey
            _gate.value = VaultGate.Unlocked
        }
    }

    fun migrate(password: CharArray) {
        scope.launch {
            val result = migration.migrate(password)
            _recoveryToShow.value = result.recoveryKey
            _gate.value = VaultGate.Unlocked
        }
    }

    fun unlock(password: CharArray) {
        scope.launch {
            if (keyVault.unlock(password) != null) {
                _error.value = null
                _gate.value = VaultGate.Unlocked
            } else {
                _error.value = "Incorrect password."
            }
        }
    }

    fun unlockWithRecovery(code: String, newPassword: CharArray) {
        scope.launch {
            if (keyVault.resetPasswordWithRecovery(code, newPassword)) {
                _gate.value = VaultGate.Unlocked
            } else {
                _error.value = "That recovery key was not recognized."
            }
        }
    }

    fun dismissRecovery() { _recoveryToShow.value = null }

    fun lock() {
        keyVault.lock()
        _gate.value = VaultGate.Locked
    }
}
