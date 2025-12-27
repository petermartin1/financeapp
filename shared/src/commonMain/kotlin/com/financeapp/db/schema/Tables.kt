package com.financeapp.db.schema

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.datetime.Instant

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

// Transactions
object Transactions : IntIdTable("TransactionRecord") {
    val accountId = reference("account_id", Accounts)
    val date = long("date")
    val amount = long("amount") // stored in cents
    val payeeId = reference("payee_id", Payees).nullable()
    val categoryId = reference("category_id", Categories).nullable()
    val memo = text("memo").nullable()
    val checkNumber = varchar("check_number", 50).nullable()
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
object TransactionTags : org.jetbrains.exposed.sql.Table("TransactionTag") {
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
