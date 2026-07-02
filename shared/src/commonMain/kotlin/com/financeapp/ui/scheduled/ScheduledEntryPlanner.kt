package com.financeapp.ui.scheduled

import com.financeapp.domain.model.ScheduledTransaction
import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.recurrence.nextRecurrenceDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/** A single occurrence to post when catching up a due scheduled transaction. */
internal data class DueOccurrence(val date: LocalDate, val importId: String)

/**
 * Result of planning the catch-up for one scheduled transaction: the occurrences that still
 * need to be posted, and how the scheduled row should advance afterwards.
 */
internal data class DueEntryPlan(
    val occurrences: List<DueOccurrence>,
    val newNextDateMillis: Long,
    val deactivate: Boolean
)

/**
 * Deterministic import id for a scheduled-transaction occurrence. Because it depends only on the
 * scheduled id and the occurrence date, re-running the catch-up after a crash recognises
 * already-posted occurrences and skips them instead of double-posting (N4).
 */
internal fun scheduledOccurrenceImportId(scheduledId: Long, dateMillis: Long): String =
    "SCHEDULED_${scheduledId}_$dateMillis"

/**
 * Plans the missed-occurrence catch-up for [scheduled] up to and including [today]. Occurrences
 * whose import id is already in [existingImportIds] are omitted (idempotency), while [newNextDateMillis]
 * still advances past every occurrence so the schedule moves forward exactly once.
 */
internal fun computeDueEntries(
    scheduled: ScheduledTransaction,
    today: LocalDate,
    existingImportIds: Set<String>
): DueEntryPlan {
    val occurrences = mutableListOf<DueOccurrence>()

    // The day-of-month the schedule should land on. Use the persisted anchor when present,
    // otherwise fall back to the current next date's day (legacy rows). This keeps month-end
    // schedules from drifting to the 28th after they pass a short month.
    val anchorDay = scheduled.dayOfMonth ?: scheduled.nextDate.dayOfMonth

    var currentDate = scheduled.nextDate
    while (currentDate <= today && (scheduled.endDate == null || currentDate <= scheduled.endDate)) {
        val dateMillis = currentDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val importId = scheduledOccurrenceImportId(scheduled.id, dateMillis)
        if (importId !in existingImportIds) {
            occurrences.add(DueOccurrence(currentDate, importId))
        }
        currentDate = nextScheduledDate(currentDate, scheduled.frequency, anchorDay)
    }

    val newNextDateMillis = currentDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val endDateMillis = scheduled.endDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
    val deactivate = endDateMillis != null && newNextDateMillis > endDateMillis

    return DueEntryPlan(occurrences, newNextDateMillis, deactivate)
}

/**
 * Advances [current] by one period. For MONTHLY/YEARLY the result is anchored on [anchorDay]
 * (the schedule's intended day-of-month), clamped to the target month's length so a "31st"
 * schedule lands on the 28th/29th/30th in short months without losing its anchor for later
 * months. [anchorDay] defaults to [current]'s own day, which preserves day/week behaviour for
 * the other frequencies.
 */
internal fun nextScheduledDate(
    current: LocalDate,
    frequency: TransactionFrequency,
    anchorDay: Int = current.dayOfMonth
): LocalDate = nextRecurrenceDate(current, frequency, anchorDay)
