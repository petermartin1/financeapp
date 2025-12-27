package com.financeapp.data.repository

import platform.Foundation.NSUserDefaults

class IosPreferencesStore : PreferencesStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun getString(key: String): String? {
        return defaults.stringForKey(key)
    }

    override suspend fun putString(key: String, value: String) {
        defaults.setObject(value, key)
    }

    override suspend fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
