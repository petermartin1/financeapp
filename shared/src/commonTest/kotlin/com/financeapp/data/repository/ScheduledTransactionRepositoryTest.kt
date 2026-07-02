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
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @Test
    fun `deleting a scheduled transaction nulls the subscription bridge link`() = runBlocking {
        val scheduledId = repository.insertScheduledTransaction(
            TestDataFactory.createTestScheduledTransaction(
                accountId = testAccountId,
                payeeId = null,
                categoryId = null
            )
        )
        transaction(database) {
            com.financeapp.db.schema.DetectedSubscriptions.insert {
                it[matchKey] = "payee:1"; it[cadence] = "MONTHLY"; it[status] = "CONFIRMED"
                it[medianAmount] = 1599; it[minAmount] = 1599; it[maxAmount] = 1599
                it[isVariable] = false; it[occurrenceCount] = 3
                it[firstSeen] = 1L; it[lastSeen] = 2L; it[nextExpectedDate] = 3L
                it[confidence] = 80; it[isActive] = true
                it[scheduledTransactionId] = scheduledId.toInt()
                it[createdAt] = 1L; it[updatedAt] = 1L
            }
        }

        repository.deleteScheduledTransaction(scheduledId) // must not throw despite FK

        transaction(database) {
            val row = com.financeapp.db.schema.DetectedSubscriptions.selectAll().single()
            assertNull(row[com.financeapp.db.schema.DetectedSubscriptions.scheduledTransactionId])
        }
    }
}
