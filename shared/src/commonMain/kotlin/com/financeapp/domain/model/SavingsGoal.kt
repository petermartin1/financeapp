package com.financeapp.domain.model

/** Static goal config; progress is always derived from the linked account's balance. */
data class SavingsGoal(
    val id: Long,
    val name: String,
    val targetAmountCents: Long,
    val accountId: Long?,      // null only after the linked account was deleted
    val deadlineMs: Long?,
    val createdAtMs: Long,
    val archived: Boolean
)

data class GoalProgress(
    val currentCents: Long,          // clamped >= 0
    val percent: Int,                // 0..100
    val remainingCents: Long,        // >= 0
    val isComplete: Boolean,
    val neededPerMonthCents: Long?,  // null when no deadline or unlinked
    val onTrack: Boolean?            // null when no deadline or unlinked
)

data class GoalWithProgress(
    val goal: SavingsGoal,
    val progress: GoalProgress,
    val accountName: String?         // null => "needs an account"
)
