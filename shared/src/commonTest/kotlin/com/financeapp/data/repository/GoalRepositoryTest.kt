package com.financeapp.data.repository

import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.GoalRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.test.TestDataFactory
import com.financeapp.test.clearAllTables
import com.financeapp.test.createTestDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GoalRepositoryTest {
    private lateinit var database: Database
    private lateinit var goals: GoalRepository
    private lateinit var accounts: AccountRepository
    private lateinit var transactions: TransactionRepository
    private var accountId: Long = 0
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        accounts = AccountRepositoryImpl(database, dispatcher)
        transactions = TransactionRepositoryImpl(database, dispatcher)
        goals = GoalRepositoryImpl(database, accounts, dispatcher)
        accountId = runBlocking { accounts.insertAccount(TestDataFactory.createTestAccount(name = "Vacation Savings")) }
    }

    @AfterTest
    fun teardown() = database.clearAllTables()

    @Test
    fun `created goal round-trips with account name and zero progress`() = runTest {
        goals.createGoal("Hawaii", 500_000, accountId, deadlineMs = null)
        val list = goals.getGoalsWithProgress().first()
        assertEquals(1, list.size)
        val g = list.single()
        assertEquals("Hawaii", g.goal.name)
        assertEquals(500_000, g.goal.targetAmountCents)
        assertEquals(accountId, g.goal.accountId)
        assertEquals("Vacation Savings", g.accountName)
        assertEquals(0, g.progress.currentCents)
        assertEquals(0, g.progress.percent)
        assertFalse(g.goal.archived)
    }

    @Test
    fun `progress reflects the linked account balance`() = runTest {
        goals.createGoal("Hawaii", 100_000, accountId, deadlineMs = null)
        transactions.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = 25_000)
        )
        accounts.notifyBalancesChanged()
        val g = goals.getGoalsWithProgress().first().single()
        assertEquals(25_000, g.progress.currentCents)
        assertEquals(25, g.progress.percent)
        assertEquals(75_000, g.progress.remainingCents)
    }

    @Test
    fun `createGoal rejects blank name and non-positive target`() = runTest {
        assertFailsWith<IllegalArgumentException> { goals.createGoal("  ", 100, accountId, null) }
        assertFailsWith<IllegalArgumentException> { goals.createGoal("X", 0, accountId, null) }
        assertFailsWith<IllegalArgumentException> { goals.createGoal("X", -5, accountId, null) }
    }

    @Test
    fun `updateGoal changes fields and returns false for missing id`() = runTest {
        val id = goals.createGoal("Hawaii", 100_000, accountId, deadlineMs = null)
        assertTrue(goals.updateGoal(id, "Maui", 200_000, accountId, deadlineMs = 1_800_000_000_000))
        val g = goals.getGoalsWithProgress().first().single()
        assertEquals("Maui", g.goal.name)
        assertEquals(200_000, g.goal.targetAmountCents)
        assertEquals(1_800_000_000_000, g.goal.deadlineMs)
        assertFalse(goals.updateGoal(9999, "Nope", 100, accountId, null))
    }

    @Test
    fun `setArchived flips the flag and delete removes the row`() = runTest {
        val id = goals.createGoal("Hawaii", 100_000, accountId, deadlineMs = null)
        assertTrue(goals.setArchived(id, true))
        assertTrue(goals.getGoalsWithProgress().first().single().goal.archived)
        assertTrue(goals.deleteGoal(id))
        assertTrue(goals.getGoalsWithProgress().first().isEmpty())
        assertFalse(goals.deleteGoal(id), "second delete is a no-op returning false")
        assertFalse(goals.setArchived(id, false), "archiving a missing id returns false")
    }

    @Test
    fun `deleting the linked account unlinks the goal instead of failing or deleting it`() = runTest {
        val doomedAccountId = accounts.insertAccount(TestDataFactory.createTestAccount(name = "Doomed"))
        val id = goals.createGoal("Orphan-to-be", 100_000, doomedAccountId, deadlineMs = null)

        accounts.deleteAccount(doomedAccountId)   // FK enforcement is ON: must not throw
        goals.notifyGoalsChanged()

        val g = goals.getGoalsWithProgress().first().single { it.goal.id == id }
        assertNull(g.goal.accountId, "goal must be unlinked, not deleted")
        assertNull(g.accountName)
        assertEquals(0, g.progress.currentCents)
        assertNull(g.progress.onTrack)
    }
}
