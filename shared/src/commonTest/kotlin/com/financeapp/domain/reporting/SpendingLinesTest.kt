package com.financeapp.domain.reporting

import com.financeapp.domain.model.SplitItem
import com.financeapp.test.TestDataFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class SpendingLinesTest {

    @Test
    fun `an unsplit transaction yields one line from its own category and amount`() {
        val txn = TestDataFactory.createTestTransaction(id = 1, categoryId = 5, amount = -3000)

        val lines = expandSpendingLines(listOf(txn), emptyMap())

        assertEquals(listOf(SpendingLine(categoryId = 5, amount = -3000)), lines)
    }

    @Test
    fun `transfers are excluded`() {
        val transfer = TestDataFactory.createTestTransaction(id = 1, categoryId = 5, amount = -3000, transferId = 2)

        val lines = expandSpendingLines(listOf(transfer), emptyMap())

        assertEquals(emptyList(), lines)
    }

    @Test
    fun `a split transaction yields a line per split and ignores the parent category`() {
        val parent = TestDataFactory.createTestTransaction(id = 1, categoryId = 99, amount = -10000)
        val splits = mapOf(
            1L to listOf(
                SplitItem(transactionId = 1, categoryId = 5, amount = -6000),
                SplitItem(transactionId = 1, categoryId = 7, amount = -4000)
            )
        )

        val lines = expandSpendingLines(listOf(parent), splits)

        assertEquals(
            listOf(
                SpendingLine(categoryId = 5, amount = -6000),
                SpendingLine(categoryId = 7, amount = -4000)
            ),
            lines
        )
    }

    @Test
    fun `an empty split list falls back to the parent line`() {
        val txn = TestDataFactory.createTestTransaction(id = 1, categoryId = 5, amount = -3000)

        val lines = expandSpendingLines(listOf(txn), mapOf(1L to emptyList()))

        assertEquals(listOf(SpendingLine(categoryId = 5, amount = -3000)), lines)
    }
}
