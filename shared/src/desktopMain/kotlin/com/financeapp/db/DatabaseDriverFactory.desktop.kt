package com.financeapp.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.financeapp.db.schema.*
import java.io.File
import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

actual class DatabaseDriverFactory actual constructor(private val encryptionKey: String) {
    private val databasePath = File(System.getProperty("user.home"), ".financeapp/finance")
    private val configPath = File(System.getProperty("user.home"), ".financeapp/finance.config")

    actual fun createDriver(): Database {
        // Ensure directory exists
        databasePath.parentFile?.mkdirs()

        // Load or create salt configuration
        val config = loadOrCreateConfig()

        // Derive encryption key from password + salt
        val derivedKey = deriveEncryptionKey(encryptionKey, config.salt)

        // Connect to H2 with AES encryption
        val url = "jdbc:h2:${databasePath.absolutePath};CIPHER=AES"

        // H2 encryption format: "username filepassword"
        // Both are set to the derived key for simplicity
        val db = Database.connect(
            url = url,
            driver = "org.h2.Driver",
            user = "sa",
            password = "$derivedKey $derivedKey"
        )

        // Create schema if needed
        transaction(db) {
            SchemaUtils.create(
                Accounts,
                Categories,
                Payees,
                PayeeAliases,
                Transactions,
                SplitItems,
                Tags,
                TransactionTags,
                Budgets,
                Holdings,
                HoldingLots,
                SecurityPrices,
                ScheduledTransactions,
                TransactionTemplates,
                BankConnections,
                ConnectedAccounts,
                ReconciliationSessions,
                PortfolioSnapshots,
                HoldingSnapshots,
                DividendEvents
            )
        }

        // Create indexes for better performance
        // Note: Exposed automatically creates indexes for foreign key columns
        // We only need to create indexes for non-FK columns that are frequently queried
        // When using quoted identifiers in H2, they become case-sensitive, so we use the exact column names
        // as defined in the Exposed column definitions (the string parameter, not the property name)
        transaction(db) {
            try {
                exec("CREATE INDEX IF NOT EXISTS idx_transaction_date ON ${Transactions.tableName}(date)")
                exec("CREATE INDEX IF NOT EXISTS idx_transaction_cleared ON ${Transactions.tableName}(is_cleared)")
                exec("CREATE INDEX IF NOT EXISTS idx_budget_month_year ON ${Budgets.tableName}(month, year)")
                exec("CREATE INDEX IF NOT EXISTS idx_scheduled_date_active ON ${ScheduledTransactions.tableName}(next_date, is_active)")
            } catch (e: Exception) {
                println("Warning: Could not create some indexes: ${e.message}")
            }
        }

        return db
    }

    private fun loadOrCreateConfig(): DatabaseConfig {
        return if (configPath.exists()) {
            // Load existing configuration
            val props = Properties()
            FileInputStream(configPath).use { props.load(it) }

            DatabaseConfig(
                salt = Base64.getDecoder().decode(props.getProperty("salt")),
                iterations = props.getProperty("iterations", "100000").toInt(),
                algorithm = props.getProperty("algorithm", "PBKDF2WithHmacSHA256")
            )
        } else {
            // Generate new configuration
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)

            val config = DatabaseConfig(
                salt = salt,
                iterations = 100000,
                algorithm = "PBKDF2WithHmacSHA256"
            )

            // Save configuration
            val props = Properties()
            props.setProperty("salt", Base64.getEncoder().encodeToString(salt))
            props.setProperty("iterations", config.iterations.toString())
            props.setProperty("algorithm", config.algorithm)
            props.setProperty("created", java.time.Instant.now().toString())

            FileOutputStream(configPath).use { props.store(it, "Database Configuration - Do not delete!") }

            config
        }
    }

    private fun deriveEncryptionKey(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, 100000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(keyBytes)
    }

    private data class DatabaseConfig(
        val salt: ByteArray,
        val iterations: Int,
        val algorithm: String
    )
}
