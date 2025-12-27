package com.financeapp.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration

actual class DatabaseDriverFactory actual constructor(private val encryptionKey: String) {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = FinanceDatabase.Schema,
            name = "finance.db",
            onConfiguration = { config ->
                config.copy(
                    extendedConfig = DatabaseConfiguration.Extended(
                        foreignKeyConstraints = true
                    )
                )
            }
        ).also { driver ->
            // Set encryption key using PRAGMA
            driver.execute(null, "PRAGMA key = '$encryptionKey';", 0)
        }
    }
}
