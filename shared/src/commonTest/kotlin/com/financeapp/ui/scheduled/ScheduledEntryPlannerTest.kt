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
    fun `monthly on the 31st keeps landing on month-end instead of drifting to the 28th`() {
        // Anchor is derived from the start date's day (31). Each occurrence clamps to the month's
        // length but the schedule must not get stuck on the 28th after February.
        val plan = computeDueEntries(
            scheduled = monthly(nextDate = LocalDate(2026, 1, 31)),
            today = LocalDate(2026, 4, 30),
            existingImportIds = emptySet()
        )

        assertEquals(
            listOf(
                LocalDate(2026, 1, 31),
                LocalDate(2026, 2, 28),
                LocalDate(2026, 3, 31),
                LocalDate(2026, 4, 30)
            ),
            plan.occurrences.map { it.date }
        )
        assertEquals(LocalDate(2026, 5, 31).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), plan.newNextDateMillis)
    }

    @Test
    fun `persisted day-of-month anchor wins over a drifted next date`() {
        // Simulates a schedule whose stored nextDate has already clamped to Feb 28 across an
        // earlier run, but whose original anchor (31) is preserved in dayOfMonth.
        val scheduled = monthly(nextDate = LocalDate(2026, 2, 28)).copy(dayOfMonth = 31)

        val plan = computeDueEntries(scheduled, today = LocalDate(2026, 4, 30), existingImportIds = emptySet())

        assertEquals(
            listOf(LocalDate(2026, 2, 28), LocalDate(2026, 3, 31), LocalDate(2026, 4, 30)),
            plan.occurrences.map { it.date }
        )
    }

    @Test
    fun `yearly on Feb 29 clamps in common years and recovers in the next leap year`() {
        val scheduled = ScheduledTransaction(
            id = 7,
            accountId = 1,
            payeeId = null,
            categoryId = null,
            amount = -5000,
            memo = "Annual",
            frequency = TransactionFrequency.YEARLY,
            nextDate = LocalDate(2024, 2, 29),
            endDate = null,
            isActive = true
        )

        val plan = computeDueEntries(scheduled, today = LocalDate(2028, 3, 1), existingImportIds = emptySet())

        assertEquals(
            listOf(
                LocalDate(2024, 2, 29),
                LocalDate(2025, 2, 28),
                LocalDate(2026, 2, 28),
                LocalDate(2027, 2, 28),
                LocalDate(2028, 2, 29)
            ),
            plan.occurrences.map { it.date }
        )
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
