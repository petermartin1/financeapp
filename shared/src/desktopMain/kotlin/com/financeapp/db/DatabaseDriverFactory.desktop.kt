package com.financeapp.db

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
        val derivedKey = deriveEncryptionKey(encryptionKey, config.salt, config.iterations, config.algorithm)

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

        // Run migrations for existing databases
        transaction(db) {
            try {
                // Migration: Add holding_id column to HoldingSnapshot table (Bug #1 fix)
                exec("ALTER TABLE ${HoldingSnapshots.tableName} ADD COLUMN IF NOT EXISTS holding_id INT REFERENCES ${Holdings.tableName}(id)")
            } catch (e: Exception) {
                println("Warning: Migration may have already been applied: ${e.message}")
            }
        }
        transaction(db) {
            try {
                // Migration: Add preferred_category_id to PayeeAlias for alias category hints
                exec("ALTER TABLE ${PayeeAliases.tableName} ADD COLUMN IF NOT EXISTS preferred_category_id INT REFERENCES ${Categories.tableName}(id)")
            } catch (e: Exception) {
                println("Warning: Migration may have already been applied: ${e.message}")
            }
        }
        transaction(db) {
            try {
                // Migration: Add day_of_month anchor to ScheduledTransaction so MONTHLY/YEARLY
                // schedules keep their original day-of-month instead of drifting to the 28th.
                exec("ALTER TABLE ${ScheduledTransactions.tableName} ADD COLUMN IF NOT EXISTS day_of_month INT")
            } catch (e: Exception) {
                println("Warning: Migration may have already been applied: ${e.message}")
            }
        }

        // Create indexes for better performance
        // Note: Exposed automatically creates indexes for foreign key columns
        // We only need to create indexes for non-FK columns that are frequently queried
        // When using quoted identifiers in H2, they become case-sensitive, so we use the exact column names
        // as defined in the Exposed column definitions (the string parameter, not the property name)
        transaction(db) {
            try {
                exec("CREATE INDEX IF NOT EXISTS idx_transaction_date ON ${Transactions.tableName}(\"${Transactions.date.name}\")")
                exec("CREATE INDEX IF NOT EXISTS idx_transaction_cleared ON ${Transactions.tableName}(is_cleared)")
                exec(
                    "CREATE INDEX IF NOT EXISTS idx_budget_month_year ON ${Budgets.tableName}(\"${Budgets.month.name}\", \"${Budgets.year.name}\")"
                )
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
            setRestrictedPermissions(configPath)

            config
        }
    }

    private fun deriveEncryptionKey(password: String, salt: ByteArray, iterations: Int, algorithm: String): String {
        val safeIterations = if (iterations > 0) iterations else DEFAULT_ITERATIONS
        val safeAlgorithm = if (algorithm.isNotBlank()) algorithm else DEFAULT_ALGORITHM

        return try {
            val spec = PBEKeySpec(password.toCharArray(), salt, safeIterations, DERIVED_KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance(safeAlgorithm)
            val keyBytes = factory.generateSecret(spec).encoded
            Base64.getEncoder().encodeToString(keyBytes)
        } catch (e: Exception) {
            val spec = PBEKeySpec(password.toCharArray(), salt, DEFAULT_ITERATIONS, DERIVED_KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance(DEFAULT_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            Base64.getEncoder().encodeToString(keyBytes)
        }
    }

    private data class DatabaseConfig(
        val salt: ByteArray,
        val iterations: Int,
        val algorithm: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DatabaseConfig) return false
            return salt.contentEquals(other.salt) &&
                    iterations == other.iterations &&
                    algorithm == other.algorithm
        }

        override fun hashCode(): Int {
            var result = salt.contentHashCode()
            result = 31 * result + iterations
            result = 31 * result + algorithm.hashCode()
            return result
        }
    }

    private fun setRestrictedPermissions(file: File) {
        try {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
            file.setExecutable(false, false)
        } catch (e: Exception) {
            // Ignore on systems that don't support POSIX permissions
        }
    }

    private companion object {
        private const val DEFAULT_ITERATIONS = 100000
        private const val DEFAULT_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val DERIVED_KEY_LENGTH_BITS = 256
    }
}
