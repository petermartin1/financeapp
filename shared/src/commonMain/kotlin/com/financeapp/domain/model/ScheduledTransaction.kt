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
    val isActive: Boolean = true
)

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
