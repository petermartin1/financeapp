package com.financeapp.domain.reporting

import com.financeapp.domain.model.SplitItem
import com.financeapp.domain.model.Transaction
import com.financeapp.domain.model.TransactionWithDetails
import com.financeapp.test.TestDataFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpendingDetailLinesTest {

    private fun withDetails(txn: Transaction, accountName: String = "Checking") =
        TransactionWithDetails(
            transaction = txn,
            payeeName = null,
            categoryName = null,
            accountName = accountName
        )

    @Test
    fun `an unsplit transaction yields one line keeping its source`() {
        val txn = withDetails(TestDataFactory.createTestTransaction(id = 1, categoryId = 5, amount = -3000))

        val lines = expandSpendingDetailLines(listOf(txn), emptyMap())

        assertEquals(1, lines.size)
        assertEquals(txn, lines[0].source)
        assertEquals(5L, lines[0].categoryId)
        assertEquals(-3000L, lines[0].lineAmountCents)
        assertFalse(lines[0].isSplitPortion)
    }

    @Test
    fun `transfers are excluded`() {
        val transfer = withDetails(
            TestDataFactory.createTestTransaction(id = 1, categoryId = 5, amount = -3000, transferId = 2)
        )

        assertEquals(emptyList(), expandSpendingDetailLines(listOf(transfer), emptyMap()))
    }

    @Test
    fun `a split transaction yields a flagged line per split sharing the parent source`() {
        val parent = withDetails(TestDataFactory.createTestTransaction(id = 1, categoryId = 99, amount = -10000))
        val splits = mapOf(
            1L to listOf(
                SplitItem(transactionId = 1, categoryId = 5, amount = -6000),
                SplitItem(transactionId = 1, categoryId = 7, amount = -4000)
            )
        )

        val lines = expandSpendingDetailLines(listOf(parent), splits)

        assertEquals(2, lines.size)
        assertTrue(lines.all { it.source == parent && it.isSplitPortion })
        assertEquals(listOf(5L to -6000L, 7L to -4000L), lines.map { it.categoryId to it.lineAmountCents })
    }

    @Test
    fun `an empty split list falls back to the parent line`() {
        val txn = withDetails(TestDataFactory.createTestTransaction(id = 1, categoryId = 5, amount = -3000))

        val lines = expandSpendingDetailLines(listOf(txn), mapOf(1L to emptyList()))

        assertEquals(1, lines.size)
        assertFalse(lines[0].isSplitPortion)
    }

    @Test
    fun `projection onto (categoryId, amount) equals expandSpendingLines for the same input`() {
        // Pins the detail expansion against drifting from the aggregation expansion.
        val txns = listOf(
            TestDataFactory.createTestTransaction(id = 1, categoryId = 5, amount = -3000),
            TestDataFactory.createTestTransaction(id = 2, categoryId = 9, amount = -10000),
            TestDataFactory.createTestTransaction(id = 3, categoryId = 4, amount = -700, transferId = 8),
            TestDataFactory.createTestTransaction(id = 4, categoryId = null, amount = -1234)
        )
        val splits = mapOf(
            2L to listOf(
                SplitItem(transactionId = 2, categoryId = 5, amount = -6000),
                SplitItem(transactionId = 2, categoryId = null, amount = -4000)
            )
        )

        val detailProjection = expandSpendingDetailLines(txns.map { withDetails(it) }, splits)
            .map { SpendingLine(it.categoryId, it.lineAmountCents) }

        assertEquals(expandSpendingLines(txns, splits), detailProjection)
    }
}
