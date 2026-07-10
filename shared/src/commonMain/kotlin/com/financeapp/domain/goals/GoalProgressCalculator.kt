package com.financeapp.domain.goals

import com.financeapp.domain.model.GoalProgress
import kotlinx.datetime.TimeZone
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Pure pace/progress math for savings goals. Progress = linked account balance vs target;
 * pacing is a straight line from goal creation to the deadline (full-balance counting, so a
 * pre-funded account simply starts ahead). See the 2026-07-09 savings-goals spec.
 */
object GoalProgressCalculator {

    fun calculate(
        targetCents: Long,
        balanceCents: Long?,
        createdAtMs: Long,
        deadlineMs: Long?,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): GoalProgress {
        if (balanceCents == null) {
            // Unlinked goal: nothing to measure, no pacing.
            return GoalProgress(
                currentCents = 0,
                percent = 0,
                remainingCents = targetCents.coerceAtLeast(0),
                isComplete = false,
                neededPerMonthCents = null,
                onTrack = null
            )
        }
        val current = balanceCents.coerceAtLeast(0)
        if (targetCents <= 0) {
            // Degenerate target (UI forbids it; be safe anyway): already complete.
            return GoalProgress(current, 100, 0, true, deadlineMs?.let { 0L }, deadlineMs?.let { true })
        }

        val percent = ((current * 100) / targetCents).coerceIn(0, 100).toInt()
        val remaining = (targetCents - current).coerceAtLeast(0)
        val isComplete = current >= targetCents

        if (deadlineMs == null) return GoalProgress(current, percent, remaining, isComplete, null, null)
        if (isComplete) return GoalProgress(current, percent, remaining, true, 0, true)
        if (nowMs >= deadlineMs) {
            // Past deadline with money still to save: the whole remainder is due now.
            return GoalProgress(current, percent, remaining, false, remaining, false)
        }

        val nowDate = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(timeZone).date
        val deadlineDate = Instant.fromEpochMilliseconds(deadlineMs).toLocalDateTime(timeZone).date
        val monthsLeft = nowDate.monthsUntil(deadlineDate).coerceAtLeast(1).toLong()
        val neededPerMonth = (remaining + monthsLeft - 1) / monthsLeft   // ceil: never undershoot

        val totalSpan = deadlineMs - createdAtMs
        // Double is fine here: this is a comparison threshold, not stored money.
        val expected = if (totalSpan <= 0) targetCents.toDouble()
        else targetCents.toDouble() * (nowMs - createdAtMs).coerceAtLeast(0) / totalSpan
        return GoalProgress(current, percent, remaining, false, neededPerMonth, current >= expected)
    }
}
