package com.financeapp.data.repository

import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.ScheduledTransactionRepository
import com.financeapp.test.TestDataFactory
import com.financeapp.test.createTestDatabase
import com.financeapp.test.clearAllTables
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledTransactionRepositoryTest {
    private lateinit var database: Database
    private lateinit var repository: ScheduledTransactionRepository
    private lateinit var accountRepository: AccountRepository
    private var testAccountId: Long = 0
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        repository = ScheduledTransactionRepositoryImpl(database, testDispatcher)
        accountRepository = AccountRepositoryImpl(database, testDispatcher)
        testAccountId = runBlocking {
            accountRepository.insertAccount(TestDataFactory.createTestAccount())
        }
    }

    @AfterTest
    fun teardown() {
        database.clearAllTables()
    }

    @Test
    fun `insert defaults the day-of-month anchor from the next date`() = runTest {
        val id = repository.insertScheduledTransaction(
            TestDataFactory.createTestScheduledTransaction(
                accountId = testAccountId,
                payeeId = null,
                categoryId = null,
                frequency = TransactionFrequency.MONTHLY,
                nextDate = LocalDate(2026, 1, 31)
            )
        )

        val loaded = repository.getScheduledTransactionById(id)
        assertNotNull(loaded)
        assertEquals(31, loaded.dayOfMonth)
    }

    @Test
    fun `insert preserves an explicit day-of-month anchor`() = runTest {
        val id = repository.insertScheduledTransaction(
            TestDataFactory.createTestScheduledTransaction(
                accountId = testAccountId,
                payeeId = null,
                categoryId = null,
                frequency = TransactionFrequency.MONTHLY,
                nextDate = LocalDate(2026, 2, 28)
            ).copy(dayOfMonth = 31)
        )

        val loaded = repository.getScheduledTransactionById(id)
        assertNotNull(loaded)
        assertEquals(31, loaded.dayOfMonth)
    }
}
