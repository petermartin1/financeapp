package com.financeapp.di

import com.financeapp.data.repository.DesktopPreferencesStore
import com.financeapp.data.repository.PreferencesStore
import com.financeapp.db.DatabaseDriverFactory
import com.financeapp.security.BiometricAuth
import com.financeapp.security.EncryptionKeyManager
import com.financeapp.security.vault.DesktopVaultStore
import com.financeapp.security.vault.KeyVault
import com.financeapp.security.vault.LegacyKeyMigration
import com.financeapp.security.vault.VaultStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { EncryptionKeyManager() }
    single<VaultStore> { DesktopVaultStore() }
    single { KeyVault(get<VaultStore>()) }
    single {
        LegacyKeyMigration(
            keyVault = get(),
            legacyKeyProvider = { get<EncryptionKeyManager>().peekExistingKey() },
            onMigrated = { get<EncryptionKeyManager>().deleteKey() }
        )
    }
    // The DB driver is built from the unlocked DEK; resolving it before unlock is a bug.
    single {
        DatabaseDriverFactory(
            get<KeyVault>().currentDek()
                ?: error("Database accessed before the vault was unlocked")
        )
    }
    single<PreferencesStore> { DesktopPreferencesStore() }
    single { BiometricAuth() }
}
