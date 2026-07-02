package com.financeapp.test

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import com.financeapp.db.schema.*

/**
 * Create an in-memory H2 database for testing
 *
 * This creates a fresh database with all tables for each test.
 * The database is in-memory only and will be discarded after tests complete.
 */
fun createTestDatabase(): Database {
    val db = Database.connect(
        url = "jdbc:h2:mem:test_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        driver = "org.h2.Driver"
    )

    // Create all tables
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
            DividendEvents,
            DetectedSubscriptions
        )
    }

    return db
}

/**
 * Clear all data from test database
 *
 * This drops and recreates all tables, effectively resetting the database.
 * Use this in @AfterTest or between tests to ensure clean state.
 */
fun Database.clearAllTables() {
    transaction(this) {
        // Drop tables in reverse order to handle foreign key constraints
        SchemaUtils.drop(
            DetectedSubscriptions,
            DividendEvents,
            HoldingSnapshots,
            PortfolioSnapshots,
            ReconciliationSessions,
            ConnectedAccounts,
            BankConnections,
            TransactionTemplates,
            ScheduledTransactions,
            SecurityPrices,
            HoldingLots,
            Holdings,
            Budgets,
            TransactionTags,
            Tags,
            SplitItems,
            Transactions,
            PayeeAliases,
            Payees,
            Categories,
            Accounts
        )

        // Recreate tables
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
            DividendEvents,
            DetectedSubscriptions
        )
    }
}

/**
 * Delete all rows from a single table by its SQL name (e.g. "TransactionRecord"), with
 * referential integrity temporarily disabled so FK-referenced rows can be wiped in one shot.
 */
fun Database.clearTable(tableName: String) = transaction(this) {
    // Unquoted so H2 (PostgreSQL mode) resolves the folded, unquoted identifier the schema created.
    exec("SET REFERENTIAL_INTEGRITY FALSE")
    exec("DELETE FROM $tableName")
    exec("SET REFERENTIAL_INTEGRITY TRUE")
}

/**
 * Execute a block within a database transaction
 *
 * This is useful for test setup that needs to insert data directly.
 */
fun <T> Database.runInTransaction(block: () -> T): T {
    return transaction(this) {
        block()
    }
}
