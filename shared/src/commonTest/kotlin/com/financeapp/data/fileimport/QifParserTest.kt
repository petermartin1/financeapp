package com.financeapp.data.fileimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class QifParserTest {

    private val parser = QifParser()

    @Test
    fun `parses transactions with payee, amount and date`() {
        val content = "!Type:Bank\n" +
            "D01/15/2024\nT-50.00\nPCoffee\n^\n" +
            "D01/16/2024\nT100.00\nPPaycheck\n^\n"

        val txns = parser.parse(content).getOrThrow()

        assertEquals(2, txns.size)
        assertEquals("Coffee", txns[0].name)
        assertEquals(-5000L, txns[0].amount)
    }

    @Test
    fun `the same transaction gets a stable fitId regardless of its position`() {
        val fileA = "!Type:Bank\n" +
            "D01/15/2024\nT-50.00\nPCoffee\n^\n"
        val fileB = "!Type:Bank\n" +
            "D01/10/2024\nT100.00\nPPaycheck\n^\n" +
            "D01/15/2024\nT-50.00\nPCoffee\n^\n"

        val coffeeA = parser.parse(fileA).getOrThrow().single { it.name == "Coffee" }
        val coffeeB = parser.parse(fileB).getOrThrow().single { it.name == "Coffee" }

        assertEquals(coffeeA.fitId, coffeeB.fitId)
    }

    @Test
    fun `two identical transactions in one file get distinct fitIds`() {
        val content = "!Type:Bank\n" +
            "D01/15/2024\nT-5.00\nPCoffee\n^\n" +
            "D01/15/2024\nT-5.00\nPCoffee\n^\n"

        val txns = parser.parse(content).getOrThrow()

        assertEquals(2, txns.size)
        assertNotEquals(txns[0].fitId, txns[1].fitId)
    }
}
