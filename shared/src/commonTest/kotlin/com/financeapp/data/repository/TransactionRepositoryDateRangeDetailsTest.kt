package com.financeapp.data.repository

import com.financeapp.test.TestDataFactory
import com.financeapp.test.clearAllTables
import com.financeapp.test.createTestDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRepositoryDateRangeDetailsTest {
    private lateinit var database: Database
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var accountRepository: AccountRepositoryImpl
    private lateinit var categoryRepository: CategoryRepositoryImpl
    private lateinit var payeeRepository: PayeeRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        transactionRepository = TransactionRepositoryImpl(database, testDispatcher)
        accountRepository = AccountRepositoryImpl(database, testDispatcher)
        categoryRepository = CategoryRepositoryImpl(database, testDispatcher)
        payeeRepository = PayeeRepositoryImpl(database)
    }

    @AfterTest
    fun teardown() {
        database.clearAllTables()
    }

    @Test
    fun `returns only transactions inside the range with bounds inclusive`() = runTest(context = testDispatcher) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val inRangeDates = listOf(LocalDate(2026, 3, 1), LocalDate(2026, 3, 15), LocalDate(2026, 3, 31))
        val outOfRangeDates = listOf(LocalDate(2026, 2, 28), LocalDate(2026, 4, 1))
        (inRangeDates + outOfRangeDates).forEach { date ->
            transactionRepository.insertTransaction(
                TestDataFactory.createTestTransaction(accountId = accountId, date = date, amount = -1000)
            )
        }

        val result = transactionRepository
            .getTransactionsWithDetailsByDateRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))
            .first()

        assertEquals(inRangeDates.sortedDescending(), result.map { it.transaction.date })
    }

    @Test
    fun `joins payee, category, and account names`() = runTest(context = testDispatcher) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount(name = "My Checking"))
        val categoryId = categoryRepository.insertCategory(TestDataFactory.createTestCategory(name = "Groceries"))
        val payeeId = payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Costco"))
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                date = LocalDate(2026, 3, 10),
                amount = -5000,
                payeeId = payeeId,
                categoryId = categoryId
            )
        )

        val result = transactionRepository
            .getTransactionsWithDetailsByDateRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))
            .first()

        assertEquals(1, result.size)
        assertEquals("Costco", result[0].payeeName)
        assertEquals("Groceries", result[0].categoryName)
        assertEquals("My Checking", result[0].accountName)
    }
}
