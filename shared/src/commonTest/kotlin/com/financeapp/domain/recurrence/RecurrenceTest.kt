package com.financeapp.domain.recurrence

import com.financeapp.domain.model.TransactionFrequency
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceTest {
    @Test
    fun `monthly advances one month keeping day`() {
        assertEquals(
            LocalDate(2026, 2, 15),
            nextRecurrenceDate(LocalDate(2026, 1, 15), TransactionFrequency.MONTHLY)
        )
    }

    @Test
    fun `monthly anchored on 31 clamps to short month but keeps anchor`() {
        // From Jan 31 with anchor 31 -> Feb 28 (clamped), not permanently drifting.
        val feb = nextRecurrenceDate(LocalDate(2026, 1, 31), TransactionFrequency.MONTHLY, anchorDay = 31)
        assertEquals(LocalDate(2026, 2, 28), feb)
        // From Feb 28 with anchor 31 -> Mar 31 (anchor re-applied).
        assertEquals(LocalDate(2026, 3, 31), nextRecurrenceDate(feb, TransactionFrequency.MONTHLY, anchorDay = 31))
    }

    @Test
    fun `weekly and yearly advance correctly`() {
        assertEquals(LocalDate(2026, 1, 22), nextRecurrenceDate(LocalDate(2026, 1, 15), TransactionFrequency.WEEKLY))
        assertEquals(LocalDate(2027, 1, 15), nextRecurrenceDate(LocalDate(2026, 1, 15), TransactionFrequency.YEARLY))
    }
}
