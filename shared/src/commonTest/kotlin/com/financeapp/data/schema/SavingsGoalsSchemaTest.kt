package com.financeapp.data.schema

import com.financeapp.db.schema.Accounts
import com.financeapp.db.schema.SavingsGoals
import com.financeapp.test.createTestDatabase
import com.financeapp.test.clearAllTables
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

class SavingsGoalsSchemaTest {
    private lateinit var database: Database

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
    }

    @AfterTest
    fun teardown() = database.clearAllTables()

    @Test
    fun `savings goal row round-trips with nullable account and deadline`() {
        transaction(database) {
            val accountId = Accounts.insert {
                it[name] = "Vacation Fund"
                it[type] = "SAVINGS"
                it[createdAt] = 1L
                it[updatedAt] = 1L
            }[Accounts.id]

            SavingsGoals.insert {
                it[name] = "Hawaii"
                it[targetAmount] = 500_000L
                it[SavingsGoals.accountId] = accountId
                it[deadline] = 1_782_000_000_000L
                it[createdAt] = 1_751_000_000_000L
            }
            SavingsGoals.insert {
                it[name] = "Rainy day"
                it[targetAmount] = 100_000L
                it[SavingsGoals.accountId] = null
                it[deadline] = null
                it[createdAt] = 1_751_000_000_000L
            }

            val rows = SavingsGoals.selectAll().orderBy(SavingsGoals.id).toList()
            assertEquals(2, rows.size)
            assertEquals("Hawaii", rows[0][SavingsGoals.name])
            assertEquals(500_000L, rows[0][SavingsGoals.targetAmount])
            assertEquals(accountId.value, rows[0][SavingsGoals.accountId]?.value)
            assertEquals(1_782_000_000_000L, rows[0][SavingsGoals.deadline])
            assertFalse(rows[0][SavingsGoals.archived], "archived must default to false")
            assertNull(rows[1][SavingsGoals.accountId])
            assertNull(rows[1][SavingsGoals.deadline])
        }
    }
}
