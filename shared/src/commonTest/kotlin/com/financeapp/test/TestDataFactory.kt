package com.financeapp.test

import com.financeapp.domain.model.*
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/**
 * Factory for creating test data objects with sensible defaults
 *
 * All factory methods allow overriding specific fields while providing
 * reasonable defaults for the rest. This makes test setup concise and readable.
 *
 * Example usage:
 * ```
 * val account = TestDataFactory.createTestAccount(name = "My Checking")
 * val transaction = TestDataFactory.createTestTransaction(
 *     accountId = account.id,
 *     amount = -5000 // -$50.00 expense
 * )
 * ```
 */
object TestDataFactory {

    /**
     * Create a test Account
     */
    fun createTestAccount(
        id: Long = 0,
        name: String = "Test Checking Account",
        type: AccountType = AccountType.CHECKING,
        institution: String? = "Test Bank",
        accountNumber: String? = "****1234",
        currency: String = "USD",
        isActive: Boolean = true,
        createdAt: kotlin.time.Instant = Clock.System.now(),
        updatedAt: kotlin.time.Instant = Clock.System.now()
    ) = Account(
        id = id,
        name = name,
        type = type,
        institution = institution,
        accountNumber = accountNumber,
        currency = currency,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    /**
     * Create a test Category
     */
    fun createTestCategory(
        id: Long = 0,
        name: String = "Test Category",
        type: CategoryType = CategoryType.EXPENSE,
        parentId: Long? = null,
        icon: String? = null,
        color: String? = null
    ) = Category(
        id = id,
        name = name,
        type = type,
        parentId = parentId,
        icon = icon,
        color = color
    )

    /**
     * Create a test Payee
     */
    fun createTestPayee(
        id: Long = 0,
        name: String = "Test Payee",
        defaultCategoryId: Long? = null
    ) = Payee(
        id = id,
        name = name,
        defaultCategoryId = defaultCategoryId
    )

    /**
     * Create a test Transaction
     *
     * Default is a -$50.00 expense transaction
     * Note: payeeId and categoryId default to null to avoid foreign key constraints
     */
    fun createTestTransaction(
        id: Long = 0,
        accountId: Long = 1,
        date: LocalDate = testDate(),
        amount: Long = -5000, // -$50.00 (expense)
        payeeId: Long? = null,
        categoryId: Long? = null,
        memo: String? = "Test transaction",
        checkNumber: String? = null,
        isCleared: Boolean = false,
        isReconciled: Boolean = false,
        transferId: Long? = null,
        importId: String? = null,
        transactionType: String? = null,
        sic: String? = null,
        createdAt: kotlin.time.Instant = Clock.System.now(),
        updatedAt: kotlin.time.Instant = Clock.System.now()
    ) = Transaction(
        id = id,
        accountId = accountId,
        date = date,
        amount = amount,
        payeeId = payeeId,
        categoryId = categoryId,
        memo = memo,
        checkNumber = checkNumber,
        isCleared = isCleared,
        isReconciled = isReconciled,
        transferId = transferId,
        importId = importId,
        transactionType = transactionType,
        sic = sic,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    /**
     * Create a test Tag
     */
    fun createTestTag(
        id: Long = 0,
        name: String = "Test Tag",
        color: String? = "#FF0000"
    ) = Tag(
        id = id,
        name = name,
        color = color
    )

    /**
     * Create a test Budget
     *
     * Default is $500.00/month for category 1 in January 2024
     */
    fun createTestBudget(
        id: Long = 0,
        categoryId: Long = 1,
        amount: Long = 50000, // $500.00
        year: Int = 2024,
        month: Int = 1
    ) = Budget(
        id = id,
        categoryId = categoryId,
        amount = amount,
        year = year,
        month = month
    )

    /**
     * Create a test ScheduledTransaction
     */
    fun createTestScheduledTransaction(
        id: Long = 0,
        accountId: Long = 1,
        payeeId: Long? = 1,
        categoryId: Long? = 1,
        amount: Long = -10000, // -$100.00
        memo: String? = "Monthly payment",
        frequency: TransactionFrequency = TransactionFrequency.MONTHLY,
        nextDate: LocalDate = testDate(),
        endDate: LocalDate? = null,
        isActive: Boolean = true
    ) = ScheduledTransaction(
        id = id,
        accountId = accountId,
        payeeId = payeeId,
        categoryId = categoryId,
        amount = amount,
        memo = memo,
        frequency = frequency,
        nextDate = nextDate,
        endDate = endDate,
        isActive = isActive
    )

    /**
     * Create a test TransactionWithDetails
     *
     * This creates a complete transaction with all related data populated
     */
    fun createTestTransactionWithDetails(
        transaction: Transaction = createTestTransaction(),
        payeeName: String? = "Test Payee",
        categoryName: String? = "Test Category",
        accountName: String = "Test Account",
        runningBalance: Long? = 100000 // $1,000.00
    ) = TransactionWithDetails(
        transaction = transaction,
        payeeName = payeeName,
        categoryName = categoryName,
        accountName = accountName,
        runningBalance = runningBalance
    )

    /**
     * Create a test AccountWithBalance
     */
    fun createTestAccountWithBalance(
        account: Account = createTestAccount(),
        balance: Long = 100000, // $1,000.00
        clearedBalance: Long = 100000 // $1,000.00
    ) = AccountWithBalance(
        account = account,
        balance = balance,
        clearedBalance = clearedBalance
    )

    /**
     * Create multiple test transactions
     *
     * Useful for bulk data setup in tests
     */
    fun createTestTransactions(
        count: Int,
        accountId: Long = 1,
        baseAmount: Long = -1000, // -$10.00
        amountIncrement: Long = -100 // -$1.00
    ): List<Transaction> {
        return (1..count).map { index ->
            createTestTransaction(
                id = index.toLong(),
                accountId = accountId,
                amount = baseAmount + (amountIncrement * (index - 1)),
                date = testDate(day = index.coerceAtMost(28)),
                memo = "Test transaction #$index"
            )
        }
    }

    /**
     * Create multiple test categories
     */
    fun createTestCategories(
        count: Int,
        type: CategoryType = CategoryType.EXPENSE
    ): List<Category> {
        return (1..count).map { index ->
            createTestCategory(
                id = index.toLong(),
                name = "Test Category $index",
                type = type
            )
        }
    }

    /**
     * Create multiple test payees
     */
    fun createTestPayees(count: Int): List<Payee> {
        return (1..count).map { index ->
            createTestPayee(
                id = index.toLong(),
                name = "Test Payee $index"
            )
        }
    }
}
