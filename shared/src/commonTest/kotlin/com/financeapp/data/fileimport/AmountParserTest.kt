package com.financeapp.data.fileimport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmountParserTest {

    // --- US / existing formats (must keep working) ---

    @Test fun `plain decimal`() = assertEquals(12345L, AmountParser.parseToCents("123.45"))
    @Test fun `negative`() = assertEquals(-12345L, AmountParser.parseToCents("-123.45"))
    @Test fun `accounting parentheses`() = assertEquals(-12345L, AmountParser.parseToCents("(123.45)"))
    @Test fun `currency symbol and us thousands`() = assertEquals(123456L, AmountParser.parseToCents("$1,234.56"))
    @Test fun `whole dollars`() = assertEquals(123400L, AmountParser.parseToCents("1234"))
    @Test fun `leading decimal point`() = assertEquals(45L, AmountParser.parseToCents(".45"))
    @Test fun `us thousands without decimal`() = assertEquals(123400L, AmountParser.parseToCents("1,234"))
    @Test fun `us thousands groups`() = assertEquals(123456789L, AmountParser.parseToCents("1,234,567.89"))
    @Test fun `single decimal digit pads`() = assertEquals(150L, AmountParser.parseToCents("1.5"))
    @Test fun `more than two decimals round half up`() = assertEquals(1235L, AmountParser.parseToCents("12.345"))

    // --- European formats (R10) ---

    @Test fun `european decimal comma with dot thousands`() =
        assertEquals(123456L, AmountParser.parseToCents("1.234,56"))

    @Test fun `european decimal comma no thousands`() =
        assertEquals(123456L, AmountParser.parseToCents("1234,56"))

    @Test fun `european single decimal digit`() =
        assertEquals(150L, AmountParser.parseToCents("1,5"))

    @Test fun `european thousands groups with decimal comma`() =
        assertEquals(123456789L, AmountParser.parseToCents("1.234.567,89"))

    @Test fun `european with currency symbol`() =
        assertEquals(123456L, AmountParser.parseToCents("€1.234,56"))

    @Test fun `french space thousands with decimal comma`() =
        assertEquals(123456L, AmountParser.parseToCents("1 234,56"))

    // --- Trailing minus (accounting / mainframe exports) ---

    @Test fun `trailing minus is negative`() =
        assertEquals(-12345L, AmountParser.parseToCents("123.45-"))

    @Test fun `european trailing minus is negative`() =
        assertEquals(-12345L, AmountParser.parseToCents("123,45-"))

    // --- Invalid ---

    @Test fun `blank is null`() = assertNull(AmountParser.parseToCents("   "))
    @Test fun `letters are null`() = assertNull(AmountParser.parseToCents("abc"))
}
