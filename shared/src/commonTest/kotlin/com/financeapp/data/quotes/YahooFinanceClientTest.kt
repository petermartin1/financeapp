package com.financeapp.data.quotes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class YahooFinanceClientTest {

    private val base = "https://query1.finance.yahoo.com/v8/finance/chart"

    @Test
    fun `ordinary symbols pass through unchanged`() {
        assertEquals("$base/AAPL", yahooChartUrl("AAPL"))
        assertEquals("$base/BRK.B", yahooChartUrl("BRK.B"))
    }

    @Test
    fun `a slash in the symbol cannot escape its path segment`() {
        val url = yahooChartUrl("AAPL/../../etc")
        val segment = url.removePrefix("$base/")
        assertFalse(segment.contains("/"), "slash must be percent-encoded, got: $url")
    }

    @Test
    fun `query and fragment characters are percent-encoded`() {
        val url = yahooChartUrl("foo?bar#baz")
        val segment = url.removePrefix("$base/")
        assertFalse(segment.contains("?"), "'?' must be encoded, got: $url")
        assertFalse(segment.contains("#"), "'#' must be encoded, got: $url")
    }
}
