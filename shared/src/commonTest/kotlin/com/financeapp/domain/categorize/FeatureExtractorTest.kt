package com.financeapp.domain.categorize

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureExtractorTest {

    private val extractor = FeatureExtractor()

    @Test
    fun `lowercases and tokenizes merchant words`() {
        val tokens = extractor.extract("Blue Bottle Coffee", sic = null, amountCents = -500)
        assertTrue("w:blue" in tokens)
        assertTrue("w:bottle" in tokens)
        assertTrue("w:coffee" in tokens)
    }

    @Test
    fun `collapses digits and store numbers to a placeholder token`() {
        val a = extractor.extract("SHELL 1234", sic = null, amountCents = -4000)
        val b = extractor.extract("SHELL 9876", sic = null, amountCents = -4000)
        // The store number must not become its own distinguishing word token.
        assertFalse(a.any { it == "w:1234" }, "raw store number should not survive as a word token")
        assertTrue("w:shell" in a)
        // Two different store numbers for the same merchant collapse to the same word features.
        val wordsA = a.filter { it.startsWith("w:") }.toSet()
        val wordsB = b.filter { it.startsWith("w:") }.toSet()
        assertEquals(wordsA, wordsB, "store numbers should collapse so the merchant features match")
        assertTrue(a.contains(FeatureExtractor.NUMBER_TOKEN))
    }

    @Test
    fun `emits character trigrams of the normalized name`() {
        val tokens = extractor.extract("Shell", sic = null, amountCents = -4000)
        assertTrue("c:she" in tokens)
        assertTrue("c:hel" in tokens)
        assertTrue("c:ell" in tokens)
    }

    @Test
    fun `emits a sic token only when a sic code is present`() {
        val withSic = extractor.extract("Whatever", sic = "5814", amountCents = -500)
        assertTrue("sic:5814" in withSic)

        val withoutSic = extractor.extract("Whatever", sic = null, amountCents = -500)
        assertFalse(withoutSic.any { it.startsWith("sic:") })

        val blankSic = extractor.extract("Whatever", sic = "  ", amountCents = -500)
        assertFalse(blankSic.any { it.startsWith("sic:") })
    }

    @Test
    fun `emits an amount-sign token`() {
        val debit = extractor.extract("Store", sic = null, amountCents = -1500)
        assertTrue("sign:debit" in debit)

        val credit = extractor.extract("Paycheck", sic = null, amountCents = 200000)
        assertTrue("sign:credit" in credit)
    }

    @Test
    fun `strips punctuation from word tokens`() {
        val tokens = extractor.extract("AT&T*Mobile", sic = null, amountCents = -8000)
        assertTrue("w:at" in tokens)
        assertTrue("w:t" in tokens || "w:att" in tokens)
        assertFalse(tokens.any { it.contains("*") || it.contains("&") })
    }

    @Test
    fun `returns no word or trigram tokens for an empty name but still tags the sign`() {
        val tokens = extractor.extract("   ", sic = null, amountCents = -100)
        assertFalse(tokens.any { it.startsWith("w:") })
        assertFalse(tokens.any { it.startsWith("c:") })
        assertTrue("sign:debit" in tokens)
    }
}
