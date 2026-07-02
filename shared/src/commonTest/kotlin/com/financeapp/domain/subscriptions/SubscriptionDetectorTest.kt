package com.financeapp.domain.subscriptions

import com.financeapp.domain.model.TransactionFrequency
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

private data class Src(
    override val payeeId: Long?,
    override val importedName: String?,
    override val amountCents: Long,
    override val date: LocalDate,
    override val transferId: Long? = null
) : SubscriptionSource

private fun monthly(payeeId: Long, amountCents: Long, months: List<Int>) =
    months.map { m -> Src(payeeId, "Netflix", amountCents, LocalDate(2026, m, 15)) }

class SubscriptionDetectorTest {
    private val detector = SubscriptionDetector()

    @Test
    fun `detects a fixed monthly subscription`() {
        val result = detector.detect(monthly(1, -1599, listOf(1, 2, 3, 4)))
        assertEquals(1, result.size)
        val s = result.single()
        assertEquals(TransactionFrequency.MONTHLY, s.cadence)
        assertEquals(1599, s.medianAmountCents)      // stored as positive cents
        assertTrue(!s.isVariable)
        assertEquals(4, s.occurrenceCount)
        assertEquals(LocalDate(2026, 5, 15), s.nextExpectedDate)
        assertEquals("payee:1", s.matchKey)
    }

    @Test
    fun `flags variable-amount recurring as variable`() {
        val src = listOf(
            Src(2, "Electric Co", -8000, LocalDate(2026, 1, 10)),
            Src(2, "Electric Co", -12000, LocalDate(2026, 2, 10)),
            Src(2, "Electric Co", -6000, LocalDate(2026, 3, 10)),
        )
        val s = detector.detect(src).single()
        assertTrue(s.isVariable, "wide amount spread should be variable")
        assertEquals(TransactionFrequency.MONTHLY, s.cadence)
    }

    @Test
    fun `ignores groups with fewer than three occurrences`() {
        val src = monthly(3, -1000, listOf(1, 2))
        assertTrue(detector.detect(src).isEmpty())
    }

    @Test
    fun `rejects erratic gaps as not a subscription`() {
        val src = listOf(
            Src(4, "Random", -500, LocalDate(2026, 1, 1)),
            Src(4, "Random", -500, LocalDate(2026, 1, 6)),   // 5 days
            Src(4, "Random", -500, LocalDate(2026, 3, 20)),  // 73 days
            Src(4, "Random", -500, LocalDate(2026, 3, 25)),  // 5 days
        )
        assertTrue(detector.detect(src).isEmpty())
    }

    @Test
    fun `excludes transfers and inflows`() {
        val src = listOf(
            Src(5, "Paycheck", 300000, LocalDate(2026, 1, 1)),
            Src(5, "Paycheck", 300000, LocalDate(2026, 2, 1)),
            Src(5, "Paycheck", 300000, LocalDate(2026, 3, 1)),
        ) + monthly(6, -1000, listOf(1, 2, 3)).map { it.copy(transferId = 99) }
        assertTrue(detector.detect(src).isEmpty())
    }

    @Test
    fun `collapses duplicate same-day charges before gap analysis`() {
        val src = monthly(7, -1000, listOf(1, 1, 2, 3)) // Jan appears twice same day
        val s = detector.detect(src).single()
        assertEquals(TransactionFrequency.MONTHLY, s.cadence)
        assertEquals(3, s.occurrenceCount) // three distinct dates
    }

    @Test
    fun `groups un-mapped payees by normalized imported name`() {
        val src = listOf(
            Src(null, "SPOTIFY  USA", -1099, LocalDate(2026, 1, 5)),
            Src(null, "spotify usa", -1099, LocalDate(2026, 2, 5)),
            Src(null, "Spotify USA", -1099, LocalDate(2026, 3, 5)),
        )
        val s = detector.detect(src).single()
        assertEquals("name:spotify usa", s.matchKey)
        assertNull(s.payeeId)
        assertEquals(TransactionFrequency.MONTHLY, s.cadence)
    }

    @Test
    fun `weekly cadence detected`() {
        val src = (0..4).map { Src(8, "Gym", -500, LocalDate(2026, 1, 1).plusDays(it * 7)) }
        assertEquals(TransactionFrequency.WEEKLY, detector.detect(src).single().cadence)
    }
}

private fun LocalDate.plusDays(n: Int) =
    LocalDate.fromEpochDays(this.toEpochDays() + n)
