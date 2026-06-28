package com.financeapp.db

import org.jetbrains.exposed.v1.jdbc.Database

expect class DatabaseDriverFactory(encryptionKey: String) {
    fun createDriver(): Database
}
