package com.financeapp.data.repository

import com.financeapp.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class PreferencesRepositoryImpl(
    private val preferencesStore: PreferencesStore
) : PreferencesRepository {

    override suspend fun getThemeMode(): String? = withContext(Dispatchers.IO) {
        preferencesStore.getString(KEY_THEME_MODE)
    }

    override suspend fun setThemeMode(mode: String) = withContext(Dispatchers.IO) {
        preferencesStore.putString(KEY_THEME_MODE, mode)
    }

    override suspend fun getDashboardConfig(): String? = withContext(Dispatchers.IO) {
        preferencesStore.getString(KEY_DASHBOARD_CONFIG)
    }

    override suspend fun setDashboardConfig(config: String) = withContext(Dispatchers.IO) {
        preferencesStore.putString(KEY_DASHBOARD_CONFIG, config)
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DASHBOARD_CONFIG = "dashboard_config"
    }
}
