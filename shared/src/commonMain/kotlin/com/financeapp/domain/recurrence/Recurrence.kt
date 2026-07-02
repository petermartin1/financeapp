package com.financeapp.domain.recurrence

import com.financeapp.domain.model.TransactionFrequency
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Advances [current] by one period. For MONTHLY/YEARLY the result is anchored on [anchorDay]
 * (the intended day-of-month), clamped to the target month's length so a "31st" recurrence lands
 * on the 28th/29th/30th in short months without losing its anchor for later months.
 */
fun nextRecurrenceDate(
    current: LocalDate,
    frequency: TransactionFrequency,
    anchorDay: Int = current.dayOfMonth
): LocalDate =
    when (frequency) {
        TransactionFrequency.DAILY -> current.plus(1, DateTimeUnit.DAY)
        TransactionFrequency.WEEKLY -> current.plus(7, DateTimeUnit.DAY)
        TransactionFrequency.BIWEEKLY -> current.plus(14, DateTimeUnit.DAY)
        TransactionFrequency.MONTHLY -> current.plus(1, DateTimeUnit.MONTH).withClampedDay(anchorDay)
        TransactionFrequency.YEARLY -> current.plus(1, DateTimeUnit.YEAR).withClampedDay(anchorDay)
    }

/** Returns this date with its day replaced by [day], clamped to the number of days in its month. */
private fun LocalDate.withClampedDay(day: Int): LocalDate {
    val daysInMonth = LocalDate(year, month, 1)
        .plus(1, DateTimeUnit.MONTH)
        .minus(1, DateTimeUnit.DAY)
        .dayOfMonth
    return LocalDate(year, monthNumber, day.coerceIn(1, daysInMonth))
}
