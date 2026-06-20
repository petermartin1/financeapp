package com.financeapp.domain.matching

import com.financeapp.domain.model.Payee
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PayeeMatcherTest {

    private val matcher = PayeeMatcher()

    private fun payees(vararg names: String): List<Payee> =
        names.mapIndexed { i, n -> Payee(id = (i + 1).toLong(), name = n) }

    private fun matchesName(imported: String, existing: List<Payee>, target: String): Boolean =
        matcher.findSimilarPayees(imported, existing).any { it.payee.name == target }

    // --- Regression: OFX names carry a leading processor prefix that shifts the
    // business name off the first token. The stored payee is still fully present,
    // so it must still be suggested. (Broke in b7dd7cf when the first-token gate
    // was applied to the substring branch.) ---

    @Test
    fun `suggests payee contained after a Square processor prefix`() {
        val existing = payees("Blue Bottle")
        assertTrue(
            matchesName("SQ *BLUE BOTTLE COFFEE", existing, "Blue Bottle"),
            "OFX 'SQ *BLUE BOTTLE COFFEE' should suggest existing 'Blue Bottle'"
        )
    }

    @Test
    fun `suggests payee contained after a TST processor prefix`() {
        val existing = payees("Whole Foods")
        assertTrue(
            matchesName("TST* WHOLE FOODS MKT", existing, "Whole Foods"),
            "OFX 'TST* WHOLE FOODS MKT' should suggest existing 'Whole Foods'"
        )
    }

    @Test
    fun `suggests payee contained after a POS DEBIT prefix`() {
        val existing = payees("Trader Joes")
        assertTrue(
            matchesName("POS DEBIT TRADER JOES 456", existing, "Trader Joes"),
            "OFX 'POS DEBIT TRADER JOES 456' should suggest existing 'Trader Joes'"
        )
    }

    // --- Guard rails: matches the first-token gate was designed to prevent.
    // These must STAY rejected after the fix. ---

    @Test
    fun `does not match different businesses sharing only a location suffix`() {
        val existing = payees("Marianos")
        assertFalse(
            matchesName("DOMINOS 2824 ARLINGTON HEI", existing, "Marianos"),
            "Different businesses sharing only a location suffix must not match"
        )
    }

    @Test
    fun `does not match a short payee name that is only a character substring`() {
        val existing = payees("BP")
        assertFalse(
            matchesName("SUBPRIME LOAN SERVICING", existing, "BP"),
            "'BP' must not match 'SUBPRIME LOAN' just because 'bp' is inside 'subprime'"
        )
    }

    @Test
    fun `still makes an exact match`() {
        val existing = payees("Costco")
        assertTrue(matchesName("COSTCO", existing, "Costco"))
    }

    // --- Real case: a bank crams store id + location into the FIRST token with no spaces,
    // then appends a variable trailing suffix. The long shared leading token is the same
    // store and must match, even though the trailing noise tokens differ. ---

    @Test
    fun `matches two lines of the same store with a long shared first token and differing suffix`() {
        val existing = payees("BP#1724100ARLINGTON SIMP AR")
        assertTrue(
            matchesName("BP#1724100ARLINGTONQPS ARLI", existing, "BP#1724100ARLINGTON SIMP AR"),
            "Same BP store (shared 'bp1724100arlington' first token) should match across differing suffixes"
        )
    }

    @Test
    fun `finds the same long-first-token store similar within one file`() {
        val similar = matcher.findSimilarNames(
            "BP#1724100ARLINGTONQPS ARLI",
            listOf("BP#1724100ARLINGTON SIMP AR")
        )
        assertTrue(
            similar.contains("BP#1724100ARLINGTON SIMP AR"),
            "The two BP store lines in one file should be flagged similar, got $similar"
        )
    }

    @Test
    fun `does not match different long first tokens that merely share a short prefix`() {
        // "bp9..." vs "bp1..." diverge right after "bp"; must not be treated as the same store.
        val existing = payees("BP#9988776COLUMBUS OH")
        assertFalse(
            matchesName("BP#1724100ARLINGTONQPS ARLI", existing, "BP#9988776COLUMBUS OH"),
            "Different BP stores (different ids/locations) must not match"
        )
    }
}
