package com.financeapp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvestmentTest {

    private fun holding(costBasis: Long = 100_00, shares: Double = 10.0) =
        Holding(accountId = 1, symbol = "X", name = null, shares = shares, costBasis = costBasis)

    @Test
    fun `marketValue gainLoss and percent are null when price is unknown`() {
        val h = HoldingWithPrice(holding = holding(), currentPrice = null, accountName = "Acct")

        // A missing quote must read as "unknown" (—), never as $0 / -100%.
        assertNull(h.marketValue)
        assertNull(h.gainLoss)
        assertNull(h.gainLossPercent)
    }

    @Test
    fun `marketValue and gainLoss are computed when the price is known`() {
        val h = HoldingWithPrice(holding = holding(costBasis = 100_00, shares = 10.0), currentPrice = 20_00, accountName = "Acct")

        assertEquals(200_00L, h.marketValue) // 10 shares * $20.00
        assertEquals(100_00L, h.gainLoss)    // $200.00 - $100.00 cost basis
        assertEquals(100.0, h.gainLossPercent)
    }
}
