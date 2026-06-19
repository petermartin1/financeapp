package com.financeapp.security.vault

import kotlin.test.*

class RecoveryKeyTest {

    @Test
    fun `generate produces 32 bytes and a grouped display string`() {
        val rk = RecoveryKey.generate()
        assertEquals(32, rk.bytes.size)
        // Crockford Base32 of 32 bytes = 52 symbols, shown in groups of 4 separated by '-'.
        assertEquals(52 + (52 / 4 - 1), rk.display.length)
        assertTrue(rk.display.all { it == '-' || it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" })
    }

    @Test
    fun `display round-trips back to the same bytes`() {
        val rk = RecoveryKey.generate()
        val decoded = RecoveryKey.decode(rk.display)
        assertNotNull(decoded)
        assertContentEquals(rk.bytes, decoded)
    }

    @Test
    fun `decode is tolerant of spaces, lowercase, and Crockford confusables`() {
        val rk = RecoveryKey.generate()
        val messy = rk.display.lowercase().replace("-", " ")
        assertContentEquals(rk.bytes, RecoveryKey.decode(messy))
        // Crockford: I/L map to 1, O maps to 0.
        val confusable = rk.display.replace("1", "I").replace("0", "O")
        assertContentEquals(rk.bytes, RecoveryKey.decode(confusable))
    }

    @Test
    fun `decode returns null for the wrong length`() {
        assertNull(RecoveryKey.decode("ABCD-EFGH"))
    }
}
