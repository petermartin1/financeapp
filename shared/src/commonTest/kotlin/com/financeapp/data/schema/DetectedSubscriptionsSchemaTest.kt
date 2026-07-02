package com.financeapp.data.schema

import com.financeapp.db.schema.DetectedSubscriptions
import com.financeapp.test.createTestDatabase
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class DetectedSubscriptionsSchemaTest {
    @Test
    fun `can insert and read a detected subscription row`() {
        val db = createTestDatabase()
        transaction(db) {
            DetectedSubscriptions.insertAndGetId {
                it[matchKey] = "payee:1"
                it[cadence] = "MONTHLY"
                it[status] = "CANDIDATE"
                it[medianAmount] = 1599
                it[minAmount] = 1599
                it[maxAmount] = 1599
                it[isVariable] = false
                it[occurrenceCount] = 4
                it[firstSeen] = 1000L
                it[lastSeen] = 2000L
                it[nextExpectedDate] = 3000L
                it[confidence] = 80
                it[isActive] = true
                it[createdAt] = 1L
                it[updatedAt] = 1L
            }
            assertEquals(1, DetectedSubscriptions.selectAll().count().toInt())
        }
    }
}
