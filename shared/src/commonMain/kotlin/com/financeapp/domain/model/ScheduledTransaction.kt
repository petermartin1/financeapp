package com.financeapp.domain.model

import kotlinx.datetime.LocalDate

data class ScheduledTransaction(
    val id: Long = 0,
    val accountId: Long,
    val payeeId: Long?,
    val categoryId: Long?,
    val amount: Long,
    val memo: String?,
    val frequency: TransactionFrequency,
    val nextDate: LocalDate,
    val endDate: LocalDate?,
    val isActive: Boolean = true,
    /**
     * The intended day-of-month anchor for MONTHLY/YEARLY schedules (1-31). Persisted separately
     * from [nextDate] so a schedule set for, say, the 31st keeps landing on month-end instead of
     * permanently drifting to the 28th once it passes a short month. Null for legacy rows and for
     * frequencies where day-of-month is irrelevant; callers fall back to [nextDate]'s day.
     */
    val dayOfMonth: Int? = null
)

/**
 * @property days Approximate number of days for display/estimation only.
 *   Do NOT use for actual date arithmetic - use proper calendar APIs instead
 *   (e.g., LocalDate.plus(1, DateTimeUnit.MONTH) for MONTHLY).
 */
enum class TransactionFrequency(val displayName: String, val days: Int) {
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
    BIWEEKLY("Every 2 Weeks", 14),
    MONTHLY("Monthly", 30),
    YEARLY("Yearly", 365)
}

data class ScheduledTransactionWithDetails(
    val scheduled: ScheduledTransaction,
    val accountName: String,
    val payeeName: String?,
    val categoryName: String?
)
