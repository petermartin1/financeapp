package com.financeapp.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

class DesktopPreferencesStore : PreferencesStore {

    private val prefsFile = File(System.getProperty("user.home"), ".financeapp/preferences.properties")
    private val properties = Properties()

    init {
        prefsFile.parentFile?.mkdirs()
        if (prefsFile.exists()) {
            prefsFile.inputStream().use { properties.load(it) }
        }
    }

    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        properties.getProperty(key)
    }

    override suspend fun putString(key: String, value: String) = withContext(Dispatchers.IO) {
        properties.setProperty(key, value)
        prefsFile.outputStream().use { properties.store(it, null) }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        properties.remove(key)
        prefsFile.outputStream().use { properties.store(it, null) }
    }
}
