package com.financeapp.ui.components

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyTextTest {

    /** Runs [block] with the JVM default locale temporarily set to [locale]. */
    private inline fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `formatCurrency uses consistent separators regardless of default locale`() {
        // Germany groups with '.' and uses ',' as the decimal separator — the exact case that
        // previously produced mixed separators like "$1.234,56" / "$1.234.56".
        withLocale(Locale.GERMANY) {
            assertEquals("$1,234.56", formatCurrency(123_456))
            assertEquals("-$1,234.56", formatCurrency(-123_456))
            assertEquals("+$1,234.56", formatCurrency(123_456, showSign = true))
            assertEquals("$0.05", formatCurrency(5))
            assertEquals("$1,000,000.00", formatCurrency(100_000_000))
        }
    }

    @Test
    fun `formatCurrency uses the symbol for the given currency code`() {
        withLocale(Locale.GERMANY) {
            assertEquals("€1,234.56", formatCurrency(123_456, currencyCode = "EUR"))
            assertEquals("£1,234.56", formatCurrency(123_456, currencyCode = "GBP"))
            assertEquals("-¥1,234.56", formatCurrency(-123_456, currencyCode = "JPY"))
            assertEquals("$1,234.56", formatCurrency(123_456, currencyCode = "USD"))
            // Unknown codes fall back to a code-prefixed form.
            assertEquals("SEK 1,234.56", formatCurrency(123_456, currencyCode = "SEK"))
        }
    }

    @Test
    fun `formatPercent is locale independent and signed`() {
        withLocale(Locale.GERMANY) {
            assertEquals("+12.3%", formatPercent(12.34, decimals = 1))
            assertEquals("-4.50%", formatPercent(-4.5, decimals = 2))
            assertEquals("0.0%", formatPercent(0.0, decimals = 1))
            assertEquals("12.3%", formatPercent(12.34, decimals = 1, showSign = false))
        }
    }
}
