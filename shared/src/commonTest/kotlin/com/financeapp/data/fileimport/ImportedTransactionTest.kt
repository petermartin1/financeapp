package com.financeapp.data.fileimport

import com.financeapp.test.testDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Checks must be recognised even when the bank does not tag them (no TRNTYPE=CHECK, no CHECKNUM)
 * and only the NAME/description carries the check, e.g. "CHECK 1234". The detection must not
 * mistake real merchants that merely contain the word "check" (Checkers, CheckFree, check-card
 * purchases) for checks.
 */
class ImportedTransactionTest {

    private fun txn(name: String, checkNumber: String? = null, type: TransactionType = TransactionType.DEBIT) =
        ImportedTransaction(
            fitId = "F",
            date = testDate(),
            amount = -5000,
            name = name,
            memo = null,
            checkNumber = checkNumber,
            type = type
        )

    @Test
    fun `name-only checks are detected as checks`() {
        val checkNames = listOf(
            "CHECK",
            "Check",
            "CHECK 1234",
            "CHECK #1234",
            "CHECK # 1234",
            "Check No. 1234",
            "CHECK NO 1234",
            "CHECK NUMBER 1234",
            "CHK 1234",
            "CHQ 1234",
            "CHEQUE 1234",
            "E-CHECK 1234",
            "ECHECK 1234",
            "1234",
            "#1234"
        )
        for (name in checkNames) {
            assertTrue(txn(name).isCheck, "\"$name\" should be treated as a check")
        }
    }

    @Test
    fun `real merchants containing 'check' are not treated as checks`() {
        val merchants = listOf(
            "CHECKERS",
            "CheckFree",
            "CHECK INTO CASH",
            "CHECK CITY",
            "CHECKCARD 1234 STARBUCKS",
            "CHECKPOINT SYSTEMS",
            "WHOLE FOODS",
            "Unknown"
        )
        for (name in merchants) {
            assertFalse(txn(name).isCheck, "\"$name\" must remain a normal payee, not a check")
        }
    }

    @Test
    fun `explicitly tagged checks are still detected`() {
        assertTrue(txn("ACME", type = TransactionType.CHECK).isCheck)
        assertTrue(txn("ACME", checkNumber = "9").isCheck)
    }

    @Test
    fun `effectiveCheckNumber recovers the number from a check-like name`() {
        assertEquals("1234", txn("CHECK 1234").effectiveCheckNumber)
        assertEquals("1234", txn("CHECK #1234").effectiveCheckNumber)
        assertEquals("1234", txn("1234").effectiveCheckNumber)
        assertEquals(null, txn("CHECK").effectiveCheckNumber, "a check with no number recovers nothing")
    }

    @Test
    fun `effectiveCheckNumber prefers the explicit field and ignores merchant digits`() {
        assertEquals("99", txn("WHOLE FOODS", checkNumber = "99").effectiveCheckNumber)
        assertEquals(null, txn("CHECKCARD 1234 STARBUCKS").effectiveCheckNumber, "merchant digits are not a check number")
    }
}
