package com.financeapp.ui.scheduled

import com.financeapp.domain.model.ScheduledTransaction
import com.financeapp.domain.model.TransactionFrequency
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduledEntryPlannerTest {

    private fun monthly(nextDate: LocalDate, endDate: LocalDate? = null) = ScheduledTransaction(
        id = 7,
        accountId = 1,
        payeeId = null,
        categoryId = null,
        amount = -5000,
        memo = "Rent",
        frequency = TransactionFrequency.MONTHLY,
        nextDate = nextDate,
        endDate = endDate,
        isActive = true
    )

    private fun importIdFor(scheduledId: Long, date: LocalDate): String =
        scheduledOccurrenceImportId(scheduledId, date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())

    @Test
    fun `catches up every missed occurrence up to today`() {
        val plan = computeDueEntries(
            scheduled = monthly(nextDate = LocalDate(2026, 3, 14)),
            today = LocalDate(2026, 6, 14),
            existingImportIds = emptySet()
        )

        assertEquals(
            listOf(LocalDate(2026, 3, 14), LocalDate(2026, 4, 14), LocalDate(2026, 5, 14), LocalDate(2026, 6, 14)),
            plan.occurrences.map { it.date }
        )
        assertFalse(plan.deactivate)
        assertEquals(LocalDate(2026, 7, 14).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), plan.newNextDateMillis)
    }

    @Test
    fun `skips occurrences already posted (idempotent re-run after a crash)`() {
        val scheduled = monthly(nextDate = LocalDate(2026, 3, 14))
        val alreadyPosted = setOf(
            importIdFor(scheduled.id, LocalDate(2026, 3, 14)),
            importIdFor(scheduled.id, LocalDate(2026, 4, 14))
        )

        val plan = computeDueEntries(scheduled, today = LocalDate(2026, 6, 14), existingImportIds = alreadyPosted)

        // Only the not-yet-posted occurrences come back, but the schedule still advances past all.
        assertEquals(listOf(LocalDate(2026, 5, 14), LocalDate(2026, 6, 14)), plan.occurrences.map { it.date })
        assertEquals(LocalDate(2026, 7, 14).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), plan.newNextDateMillis)
    }

    @Test
    fun `stops at the end date and deactivates`() {
        val plan = computeDueEntries(
            scheduled = monthly(nextDate = LocalDate(2026, 3, 14), endDate = LocalDate(2026, 5, 14)),
            today = LocalDate(2026, 6, 14),
            existingImportIds = emptySet()
        )

        assertEquals(
            listOf(LocalDate(2026, 3, 14), LocalDate(2026, 4, 14), LocalDate(2026, 5, 14)),
            plan.occurrences.map { it.date }
        )
        assertTrue(plan.deactivate)
    }

    @Test
    fun `nothing due when the next date is in the future`() {
        val plan = computeDueEntries(
            scheduled = monthly(nextDate = LocalDate(2026, 6, 15)),
            today = LocalDate(2026, 6, 14),
            existingImportIds = emptySet()
        )

        assertTrue(plan.occurrences.isEmpty())
        assertFalse(plan.deactivate)
    }
}
