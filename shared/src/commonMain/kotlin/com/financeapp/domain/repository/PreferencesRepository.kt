package com.financeapp.domain.repository

interface PreferencesRepository {
    suspend fun getThemeMode(): String?
    suspend fun setThemeMode(mode: String)
    suspend fun getDashboardConfig(): String?
    suspend fun setDashboardConfig(config: String)
}
