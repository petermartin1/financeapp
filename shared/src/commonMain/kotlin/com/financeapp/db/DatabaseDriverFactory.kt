package com.financeapp.db

import org.jetbrains.exposed.sql.Database

expect class DatabaseDriverFactory(encryptionKey: String) {
    fun createDriver(): Database
}
