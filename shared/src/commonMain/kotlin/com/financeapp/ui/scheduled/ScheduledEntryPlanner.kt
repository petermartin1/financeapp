package com.financeapp.ui.scheduled

import com.financeapp.domain.model.ScheduledTransaction
import com.financeapp.domain.model.TransactionFrequency
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

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

    var currentDate = scheduled.nextDate
    while (currentDate <= today && (scheduled.endDate == null || currentDate <= scheduled.endDate)) {
        val dateMillis = currentDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val importId = scheduledOccurrenceImportId(scheduled.id, dateMillis)
        if (importId !in existingImportIds) {
            occurrences.add(DueOccurrence(currentDate, importId))
        }
        currentDate = nextScheduledDate(currentDate, scheduled.frequency)
    }

    val newNextDateMillis = currentDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val endDateMillis = scheduled.endDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
    val deactivate = endDateMillis != null && newNextDateMillis > endDateMillis

    return DueEntryPlan(occurrences, newNextDateMillis, deactivate)
}

internal fun nextScheduledDate(current: LocalDate, frequency: TransactionFrequency): LocalDate =
    when (frequency) {
        TransactionFrequency.DAILY -> current.plus(1, DateTimeUnit.DAY)
        TransactionFrequency.WEEKLY -> current.plus(7, DateTimeUnit.DAY)
        TransactionFrequency.BIWEEKLY -> current.plus(14, DateTimeUnit.DAY)
        TransactionFrequency.MONTHLY -> current.plus(1, DateTimeUnit.MONTH)
        TransactionFrequency.YEARLY -> current.plus(1, DateTimeUnit.YEAR)
    }
