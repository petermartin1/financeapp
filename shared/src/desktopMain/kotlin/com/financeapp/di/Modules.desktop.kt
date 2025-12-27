package com.financeapp.di

import com.financeapp.data.repository.DesktopPreferencesStore
import com.financeapp.data.repository.PreferencesStore
import com.financeapp.db.DatabaseDriverFactory
import com.financeapp.security.BiometricAuth
import com.financeapp.security.EncryptionKeyManager
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { EncryptionKeyManager() }
    single { DatabaseDriverFactory(get<EncryptionKeyManager>().getOrCreateKey()) }
    single<PreferencesStore> { DesktopPreferencesStore() }
    single { BiometricAuth() }
}
