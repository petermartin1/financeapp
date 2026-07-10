package com.financeapp.domain.goals

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.*

class GoalProgressCalculatorTest {
    private val tz = TimeZone.UTC
    private fun ms(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).atStartOfDayIn(tz).toEpochMilliseconds()

    @Test
    fun `no deadline gives percent and remaining but no pacing`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = 25_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = null, nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(25_000, p.currentCents)
        assertEquals(25, p.percent)
        assertEquals(75_000, p.remainingCents)
        assertFalse(p.isComplete)
        assertNull(p.neededPerMonthCents)
        assertNull(p.onTrack)
    }

    @Test
    fun `over-funded goal is complete at 100 percent with zero remaining`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = 150_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2027, 1, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(100, p.percent)
        assertEquals(0, p.remainingCents)
        assertTrue(p.isComplete)
        assertEquals(0, p.neededPerMonthCents)
        assertEquals(true, p.onTrack)
    }

    @Test
    fun `negative balance clamps to zero percent`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = -5_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = null, nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(0, p.currentCents)
        assertEquals(0, p.percent)
        assertEquals(100_000, p.remainingCents)
    }

    @Test
    fun `unlinked goal has zero progress and no pacing even with a deadline`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = null,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2027, 1, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(0, p.currentCents)
        assertEquals(0, p.percent)
        assertEquals(100_000, p.remainingCents)
        assertNull(p.neededPerMonthCents)
        assertNull(p.onTrack)
    }

    @Test
    fun `behind pace at halfway point reports behind and needed per month`() {
        // Jan 1 -> Jan 1 next year, target $1,200. At Jul 1 the straight line expects ~$600.
        val p = GoalProgressCalculator.calculate(
            targetCents = 120_000, balanceCents = 30_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2027, 1, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(false, p.onTrack)
        // 6 calendar months left (Jul 1 -> Jan 1), $900 remaining -> $150/month.
        assertEquals(15_000, p.neededPerMonthCents)
    }

    @Test
    fun `ahead of pace at halfway point reports on track`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 120_000, balanceCents = 70_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2027, 1, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(true, p.onTrack)
    }

    @Test
    fun `past deadline with remaining is behind and needs the full remainder`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = 40_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2026, 6, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(false, p.onTrack)
        assertEquals(60_000, p.neededPerMonthCents)
    }

    @Test
    fun `deadline under one month away clamps to one month`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = 90_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2026, 7, 15), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(10_000, p.neededPerMonthCents, "monthsLeft must clamp to 1")
    }

    @Test
    fun `needed per month rounds up so the plan never undershoots`() {
        // $1,000 remaining over 3 months -> 33,334 cents, not 33,333.
        val p = GoalProgressCalculator.calculate(
            targetCents = 200_000, balanceCents = 100_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2026, 10, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(33_334, p.neededPerMonthCents)
    }

    @Test
    fun `non-positive target is treated as complete`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 0, balanceCents = 500,
            createdAtMs = ms(2026, 1, 1), deadlineMs = null, nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(100, p.percent)
        assertTrue(p.isComplete)
        assertEquals(0, p.remainingCents)
    }
}
