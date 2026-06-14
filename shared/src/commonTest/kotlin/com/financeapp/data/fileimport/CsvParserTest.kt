package com.financeapp.data.fileimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CsvParserTest {

    private val parser = CsvParser()
    private val config = CsvPresets.GENERIC // date col 0, amount col 1, description col 2

    @Test
    fun `parses a quoted field containing an embedded comma`() {
        val content = "Date,Amount,Description\n" +
            "01/15/2024,-50.00,\"Shop, Inc\"\n"

        val txns = parser.parse(content, config).getOrThrow()

        assertEquals(1, txns.size)
        assertEquals("Shop, Inc", txns[0].name)
    }

    @Test
    fun `parses a quoted field that spans multiple physical lines (RFC 4180)`() {
        val content = "Date,Amount,Description\n" +
            "01/15/2024,-50.00,\"Coffee Shop\nMain Street\"\n" +
            "01/16/2024,100.00,Paycheck\n"

        val txns = parser.parse(content, config).getOrThrow()

        assertEquals(2, txns.size)
        assertEquals("Coffee Shop\nMain Street", txns[0].name)
        assertEquals("Paycheck", txns[1].name)
    }

    @Test
    fun `the same transaction gets a stable fitId regardless of its row position`() {
        val fileA = "Date,Amount,Description\n" +
            "01/15/2024,-50.00,Coffee\n"
        val fileB = "Date,Amount,Description\n" +
            "01/10/2024,100.00,Paycheck\n" +
            "01/15/2024,-50.00,Coffee\n"

        val coffeeA = parser.parse(fileA, config).getOrThrow().single { it.name == "Coffee" }
        val coffeeB = parser.parse(fileB, config).getOrThrow().single { it.name == "Coffee" }

        // Across overlapping imports the same logical transaction must dedup to one id,
        // even though it is at row 0 in A and row 1 in B.
        assertEquals(coffeeA.fitId, coffeeB.fitId)
    }

    @Test
    fun `two identical rows in one file get distinct fitIds`() {
        val content = "Date,Amount,Description\n" +
            "01/15/2024,-5.00,Coffee\n" +
            "01/15/2024,-5.00,Coffee\n"

        val txns = parser.parse(content, config).getOrThrow()

        assertEquals(2, txns.size)
        assertNotEquals(txns[0].fitId, txns[1].fitId)
    }

    @Test
    fun `fitId does not depend on unrelated rows in the file`() {
        val withExtra = "Date,Amount,Description\n" +
            "01/01/2024,9.99,Something Else\n" +
            "01/15/2024,-5.00,Coffee\n"
        val withoutExtra = "Date,Amount,Description\n" +
            "01/15/2024,-5.00,Coffee\n"

        val a = parser.parse(withExtra, config).getOrThrow().single { it.name == "Coffee" }
        val b = parser.parse(withoutExtra, config).getOrThrow().single { it.name == "Coffee" }

        assertEquals(a.fitId, b.fitId)
    }

    @Test
    fun `unbalanced data rows are skipped, valid ones parsed`() {
        val content = "Date,Amount,Description\n" +
            "01/15/2024,-50.00,Coffee\n" +
            "garbage\n" +
            "01/16/2024,100.00,Paycheck\n"

        val txns = parser.parse(content, config).getOrThrow()

        assertEquals(2, txns.size)
        assertTrue(txns.any { it.name == "Coffee" })
        assertTrue(txns.any { it.name == "Paycheck" })
    }
}
