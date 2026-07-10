package com.financeapp.db.schema

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.ReferenceOption

// Accounts (bank accounts, credit cards, investment accounts)
object Accounts : IntIdTable("Account") {
    val name = varchar("name", 255)
    val type = varchar("type", 50) // CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT, CASH
    val institution = varchar("institution", 255).nullable()
    val accountNumber = varchar("account_number", 255).nullable()
    val currency = varchar("currency", 3).default("USD")
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

// Categories for transactions
object Categories : IntIdTable("Category") {
    val name = varchar("name", 255)
    val parentId = reference("parent_id", Categories).nullable()
    val type = varchar("type", 50) // INCOME, EXPENSE, TRANSFER
    val icon = varchar("icon", 50).nullable()
    val color = varchar("color", 20).nullable()
}

// Payees (who you pay or receive from)
object Payees : IntIdTable("Payee") {
    val name = varchar("name", 255).uniqueIndex()
    val defaultCategoryId = reference("default_category_id", Categories).nullable()
}

// Payee aliases (for mapping imported payee names to canonical payees)
object PayeeAliases : IntIdTable("PayeeAlias") {
    val aliasName = varchar("alias_name", 255).uniqueIndex() // lowercase normalized
    val canonicalPayeeId = reference("canonical_payee_id", Payees)
    val matchType = varchar("match_type", 50) // EXACT, FUZZY, MANUAL
    val confidence = double("confidence").nullable() // 0.0-1.0 for fuzzy matches
    val preferredCategoryId = reference("preferred_category_id", Categories).nullable() // Category preference from import
    val createdAt = long("created_at")

    init {
        index(false, aliasName)
        index(false, canonicalPayeeId)
    }
}

// Transactions
object Transactions : IntIdTable("TransactionRecord") {
    val accountId = reference("account_id", Accounts)
    val date = long("date")
    val amount = long("amount") // stored in cents
    val payeeId = reference("payee_id", Payees).nullable()
    val categoryId = reference("category_id", Categories).nullable()
    val memo = text("memo").nullable()
    val checkNumber = varchar("check_number", 50).nullable()
    val importedName = varchar("imported_name", 1024).nullable()
    val isCleared = bool("is_cleared").default(false)
    val isReconciled = bool("is_reconciled").default(false)
    val transferId = reference("transfer_id", Transactions).nullable()
    val importId = varchar("import_id", 255).nullable()
    val transactionType = varchar("transaction_type", 50).nullable()
    val sic = varchar("sic", 50).nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

// Split transaction items
object SplitItems : IntIdTable("SplitItem") {
    val transactionId = reference("transaction_id", Transactions)
    val categoryId = reference("category_id", Categories).nullable()
    val amount = long("amount")
    val memo = text("memo").nullable()
}

// Tags
object Tags : IntIdTable("Tag") {
    val name = varchar("name", 255).uniqueIndex()
    val color = varchar("color", 20).nullable()
}

// Transaction-Tag relationship (many-to-many)
object TransactionTags : org.jetbrains.exposed.v1.core.Table("TransactionTag") {
    val transactionId = reference("transaction_id", Transactions)
    val tagId = reference("tag_id", Tags)
    override val primaryKey = PrimaryKey(transactionId, tagId)
}

// Budgets
object Budgets : IntIdTable("Budget") {
    val categoryId = reference("category_id", Categories)
    val amount = long("amount") // monthly budget in cents
    val year = integer("year")
    val month = integer("month")

    init {
        uniqueIndex(categoryId, year, month)
    }
}

// Investment holdings
object Holdings : IntIdTable("Holding") {
    val accountId = reference("account_id", Accounts)
    val symbol = varchar("symbol", 20)
    val name = varchar("name", 255).nullable()
    val shares = double("shares")
    val costBasis = long("cost_basis") // in cents

    init {
        uniqueIndex(accountId, symbol)
    }
}

// Lots for investment holdings (multiple purchase lots per holding)
object HoldingLots : IntIdTable("HoldingLot") {
    val holdingId = reference("holding_id", Holdings, onDelete = ReferenceOption.CASCADE)
    val acquiredDate = long("acquired_date")
    val purpose = varchar("purpose", 255).nullable()
    val shares = double("shares")
    val costBasis = long("cost_basis")
    val notes = text("notes").nullable()

    init {
        index(false, holdingId, acquiredDate)
    }
}

// Security prices
object SecurityPrices : IntIdTable("SecurityPrice") {
    val symbol = varchar("symbol", 20)
    val date = long("date")
    val price = long("price") // in cents

    init {
        uniqueIndex(symbol, date)
    }
}

// Scheduled/recurring transactions
object ScheduledTransactions : IntIdTable("ScheduledTransaction") {
    val accountId = reference("account_id", Accounts)
    val payeeId = reference("payee_id", Payees).nullable()
    val categoryId = reference("category_id", Categories).nullable()
    val amount = long("amount")
    val memo = text("memo").nullable()
    val frequency = varchar("frequency", 50) // DAILY, WEEKLY, BIWEEKLY, MONTHLY, YEARLY
    val nextDate = long("next_date")
    val endDate = long("end_date").nullable()
    val isActive = bool("is_active").default(true)
    // Intended day-of-month anchor (1-31) for MONTHLY/YEARLY schedules, so a "31st" schedule keeps
    // landing on month-end instead of permanently drifting to the 28th. Nullable for legacy rows.
    val dayOfMonth = integer("day_of_month").nullable()
}

// Transaction templates (for quick entry)
object TransactionTemplates : IntIdTable("TransactionTemplate") {
    val name = varchar("name", 255)
    val accountId = reference("account_id", Accounts).nullable()
    val payeeId = reference("payee_id", Payees).nullable()
    val categoryId = reference("category_id", Categories).nullable()
    val amount = long("amount").nullable()
    val memo = text("memo").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

// Bank connections for OFX Direct Connect
// Note: Passwords are stored securely in platform-specific keychain/encrypted storage, not in database
object BankConnections : IntIdTable("BankConnection") {
    val bankName = varchar("bank_name", 255)
    val userId = varchar("user_id", 255)
    val lastSynced = long("last_synced").nullable()
    val createdAt = long("created_at")
}

object ConnectedAccounts : IntIdTable("ConnectedAccount") {
    val connectionId = reference("connection_id", BankConnections)
    val localAccountId = reference("local_account_id", Accounts)
    val remoteAccountId = varchar("remote_account_id", 255)
    val accountType = varchar("account_type", 50)

    init {
        uniqueIndex(connectionId, remoteAccountId)
    }
}

// Reconciliation sessions
object ReconciliationSessions : IntIdTable("ReconciliationSession") {
    val accountId = reference("account_id", Accounts)
    val statementDate = long("statement_date")
    val statementBalance = long("statement_balance")
    val isCompleted = bool("is_completed").default(false)
    val completedAt = long("completed_at").nullable()
    val createdAt = long("created_at")
}

// Portfolio snapshots for performance tracking
object PortfolioSnapshots : IntIdTable("PortfolioSnapshot") {
    val date = long("date") // timestamp (daily snapshot at market close)
    val totalValue = long("total_value") // in cents
    val totalCostBasis = long("total_cost_basis") // in cents
    val totalGainLoss = long("total_gain_loss") // in cents
    val snapshotType = varchar("snapshot_type", 20).default("DAILY") // DAILY, WEEKLY, MONTHLY

    init {
        uniqueIndex(date, snapshotType)
    }
}

// Individual holding snapshots (detailed tracking)
object HoldingSnapshots : IntIdTable("HoldingSnapshot") {
    val portfolioSnapshotId = reference("portfolio_snapshot_id", PortfolioSnapshots)
    val holdingId = reference("holding_id", Holdings).nullable() // nullable for migration compatibility
    val symbol = varchar("symbol", 20)
    val shares = double("shares")
    val costBasis = long("cost_basis") // in cents
    val marketValue = long("market_value") // in cents
    val price = long("price") // in cents
}

// Dividend events
object DividendEvents : IntIdTable("DividendEvent") {
    val holdingId = reference("holding_id", Holdings)
    val symbol = varchar("symbol", 20)
    val paymentDate = long("payment_date")
    val amount = long("amount") // total dividend in cents
    val perShare = long("per_share") // dividend per share in cents
    val shares = double("shares") // number of shares at time of dividend
    val isReinvested = bool("is_reinvested").default(false)
}

// Detected subscriptions (recurring charges surfaced for awareness). See
// docs/superpowers/specs/2026-07-01-subscription-detection-design.md.
object DetectedSubscriptions : IntIdTable("DetectedSubscription") {
    val payeeId = reference("payee_id", Payees).nullable()
    val matchKey = varchar("match_key", 512).uniqueIndex()
    val cadence = varchar("cadence", 50)              // WEEKLY, BIWEEKLY, MONTHLY, YEARLY
    val status = varchar("status", 20).default("CANDIDATE") // CANDIDATE, CONFIRMED, DISMISSED
    val medianAmount = long("median_amount")          // absolute cents
    val minAmount = long("min_amount")
    val maxAmount = long("max_amount")
    val isVariable = bool("is_variable").default(false)
    val occurrenceCount = integer("occurrence_count")
    val firstSeen = long("first_seen")                // epoch millis
    val lastSeen = long("last_seen")
    val nextExpectedDate = long("next_expected_date")
    val confidence = integer("confidence")            // 0-100
    val isActive = bool("is_active").default(true)
    val origin = varchar("origin", 20).default("DETECTED")   // DETECTED, MANUAL
    val scheduledTransactionId =                        // set by the action bridge; null otherwise
        reference("scheduled_transaction_id", ScheduledTransactions).nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}

// Savings goals: target amount + optional deadline, progress = linked account's balance. The
// account link is nullable ONLY so account deletion can unlink (never silently delete) goals.
// See docs/superpowers/specs/2026-07-09-savings-goals-design.md.
object SavingsGoals : IntIdTable("SavingsGoal") {
    val name = varchar("name", 100)
    val targetAmount = long("target_amount")                     // cents, > 0
    val accountId = reference("account_id", Accounts).nullable()
    val deadline = long("deadline").nullable()                   // epoch millis
    val createdAt = long("created_at")                           // epoch millis
    val archived = bool("archived").default(false)
}
