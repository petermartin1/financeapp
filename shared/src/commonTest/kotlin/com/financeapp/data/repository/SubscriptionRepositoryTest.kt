package com.financeapp.data.repository

import com.financeapp.domain.model.*
import com.financeapp.domain.repository.SubscriptionRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.PayeeRepository
import com.financeapp.domain.repository.ScheduledTransactionRepository
import com.financeapp.domain.subscriptions.SubscriptionDetector
import com.financeapp.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionRepositoryTest {
    private lateinit var database: Database
    private lateinit var subscriptions: SubscriptionRepository
    private lateinit var transactions: TransactionRepository
    private lateinit var accounts: AccountRepository
    private lateinit var payees: PayeeRepository
    private lateinit var scheduled: ScheduledTransactionRepository
    private var accountId: Long = 0
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        transactions = TransactionRepositoryImpl(database, dispatcher)
        accounts = AccountRepositoryImpl(database, dispatcher)
        payees = PayeeRepositoryImpl(database, dispatcher)
        scheduled = ScheduledTransactionRepositoryImpl(database, dispatcher)
        subscriptions = SubscriptionRepositoryImpl(database, transactions, scheduled, SubscriptionDetector(), dispatcher)
        accountId = runBlocking { accounts.insertAccount(TestDataFactory.createTestAccount()) }
    }

    @AfterTest
    fun teardown() = database.clearAllTables()

    private fun insertMonthly(payeeId: Long?, name: String, amountCents: Long, months: List<Int>) = runBlocking {
        months.forEach { m ->
            transactions.insertTransaction(
                TestDataFactory.createTestTransaction(
                    accountId = accountId,
                    payeeId = payeeId,
                    amount = amountCents,
                    date = LocalDate(2026, m, 12),
                    importedName = name
                )
            )
        }
    }

    @Test
    fun `rescan creates candidate rows for recurring charges`() = runTest {
        insertMonthly(null, "Netflix", -1599, listOf(1, 2, 3, 4))
        subscriptions.rescan()
        val list = subscriptions.getSubscriptions().first()
        assertEquals(1, list.size)
        assertEquals(SubscriptionStatus.CANDIDATE, list.single().status)
        assertEquals(TransactionFrequency.MONTHLY, list.single().cadence)
    }

    @Test
    fun `confirmed status survives a rescan and stats update`() = runTest {
        insertMonthly(null, "Netflix", -1599, listOf(1, 2, 3))
        subscriptions.rescan()
        val id = subscriptions.getSubscriptions().first().single().id
        subscriptions.confirm(id)

        insertMonthly(null, "Netflix", -1599, listOf(4)) // a new occurrence
        subscriptions.rescan()

        val after = subscriptions.getSubscriptions().first().single()
        assertEquals(SubscriptionStatus.CONFIRMED, after.status, "confirm must be sticky")
        assertEquals(4, after.occurrenceCount, "stats must update on rescan")
    }

    @Test
    fun `dismissed stays dismissed after rescan`() = runTest {
        insertMonthly(null, "Netflix", -1599, listOf(1, 2, 3))
        subscriptions.rescan()
        val id = subscriptions.getSubscriptions().first().single().id
        subscriptions.dismiss(id)
        subscriptions.rescan()
        assertEquals(SubscriptionStatus.DISMISSED, subscriptions.getSubscriptions().first().single().status)
    }

    @Test
    fun `group that no longer qualifies is marked inactive not deleted`() = runTest {
        insertMonthly(null, "Netflix", -1599, listOf(1, 2, 3))
        subscriptions.rescan()
        assertTrue(subscriptions.getSubscriptions().first().single().isActive)

        // Wipe transactions so the group no longer qualifies, then rescan.
        database.clearTable("TransactionRecord")
        subscriptions.rescan()

        val row = subscriptions.getSubscriptions().first().single()
        assertFalse(row.isActive, "cancelled subscription should remain, marked inactive")
    }

    @Test
    fun `markPayeeAsSubscription creates a confirmed manual row that survives rescan`() = runTest {
        val payeeId = runBlocking { payees.insertPayee(Payee(id = 0, name = "Gym", defaultCategoryId = null)) }
        insertMonthly(payeeId, "Gym", -3000, listOf(1, 2))   // only two charges: below the auto bar
        subscriptions.markPayeeAsSubscription(payeeId)

        val row = subscriptions.getSubscriptions().first().single()
        assertEquals(SubscriptionStatus.CONFIRMED, row.status)
        assertEquals(SubscriptionOrigin.MANUAL, row.origin)

        subscriptions.rescan()   // would not detect a 2-charge group; must not revert/deactivate it
        val after = subscriptions.getSubscriptions().first().single()
        assertEquals(SubscriptionStatus.CONFIRMED, after.status, "manual add must be sticky")
        assertTrue(after.isActive, "manual row must not be auto-deactivated")
    }

    @Test
    fun `markPayeeAsSubscription is idempotent`() = runTest {
        val payeeId = runBlocking { payees.insertPayee(Payee(id = 0, name = "Gym", defaultCategoryId = null)) }
        insertMonthly(payeeId, "Gym", -3000, listOf(1, 2))
        subscriptions.markPayeeAsSubscription(payeeId)
        subscriptions.markPayeeAsSubscription(payeeId)
        assertEquals(1, subscriptions.getSubscriptions().first().size)
    }

    @Test
    fun `createScheduledFromSubscription links one schedule and does not double-create`() = runTest {
        val payeeId = runBlocking { payees.insertPayee(Payee(id = 0, name = "Netflix", defaultCategoryId = null)) }
        insertMonthly(payeeId, "Netflix", -1599, listOf(1, 2, 3))
        subscriptions.rescan()
        val id = subscriptions.getSubscriptions().first().single().id

        subscriptions.createScheduledFromSubscription(id)
        subscriptions.createScheduledFromSubscription(id)   // second call must no-op

        assertEquals(1, runBlocking { scheduled.getAllScheduledTransactions().first().size })
        assertNotNull(subscriptions.getSubscriptions().first().single().scheduledTransactionId)
    }
}
