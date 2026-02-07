package com.financeapp.domain.service

import com.financeapp.domain.model.SnapshotType
import com.financeapp.domain.repository.PerformanceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Service for scheduling and creating portfolio snapshots
 */
class SnapshotScheduler(
    private val performanceRepository: PerformanceRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private var dailySnapshotJob: Job? = null
    private var weeklySnapshotJob: Job? = null
    private var monthlySnapshotJob: Job? = null

    private val _lastSnapshotTime = MutableStateFlow<Long?>(null)
    val lastSnapshotTime: StateFlow<Long?> = _lastSnapshotTime.asStateFlow()

    private val _isCreatingSnapshot = MutableStateFlow(false)
    val isCreatingSnapshot: StateFlow<Boolean> = _isCreatingSnapshot.asStateFlow()
    private val snapshotMutex = Mutex()

    /**
     * Start automatic daily snapshot creation
     * @param hourOfDay Hour of day to create snapshot (0-23, default: 16 for 4 PM market close)
     */
    fun startDailySnapshots(hourOfDay: Int = 16) {
        stopDailySnapshots()

        dailySnapshotJob = scope.launch {
            while (isActive) {
                val delayUntilNextSnapshot = calculateDelayUntilTime(hourOfDay)
                delay(delayUntilNextSnapshot)

                if (isActive) {
                    createSnapshot(SnapshotType.DAILY)
                }
            }
        }
    }

    /**
     * Stop automatic daily snapshots
     */
    fun stopDailySnapshots() {
        dailySnapshotJob?.cancel()
        dailySnapshotJob = null
    }

    /**
     * Start automatic weekly snapshot creation
     * @param dayOfWeek Day of week (1=Monday, 7=Sunday)
     * @param hourOfDay Hour of day (0-23)
     */
    fun startWeeklySnapshots(dayOfWeek: Int = 5, hourOfDay: Int = 16) {
        stopWeeklySnapshots()

        weeklySnapshotJob = scope.launch {
            while (isActive) {
                val delayUntilNextSnapshot = calculateDelayUntilWeeklyTime(dayOfWeek, hourOfDay)
                delay(delayUntilNextSnapshot)

                if (isActive) {
                    createSnapshot(SnapshotType.WEEKLY)
                }
            }
        }
    }

    /**
     * Stop automatic weekly snapshots
     */
    fun stopWeeklySnapshots() {
        weeklySnapshotJob?.cancel()
        weeklySnapshotJob = null
    }

    /**
     * Start automatic monthly snapshot creation
     * @param dayOfMonth Day of month (1-31)
     * @param hourOfDay Hour of day (0-23)
     */
    fun startMonthlySnapshots(dayOfMonth: Int = 1, hourOfDay: Int = 16) {
        stopMonthlySnapshots()

        monthlySnapshotJob = scope.launch {
            while (isActive) {
                val delayUntilNextSnapshot = calculateDelayUntilMonthlyTime(dayOfMonth, hourOfDay)
                delay(delayUntilNextSnapshot)

                if (isActive) {
                    createSnapshot(SnapshotType.MONTHLY)
                }
            }
        }
    }

    /**
     * Stop automatic monthly snapshots
     */
    fun stopMonthlySnapshots() {
        monthlySnapshotJob?.cancel()
        monthlySnapshotJob = null
    }

    /**
     * Manually create a snapshot
     */
    suspend fun createSnapshot(type: SnapshotType = SnapshotType.DAILY): Result<Long> {
        if (!snapshotMutex.tryLock()) {
            return Result.failure(Exception("Snapshot creation already in progress"))
        }

        _isCreatingSnapshot.value = true

        return try {
            val snapshotId = performanceRepository.createPortfolioSnapshot(type)
            val now = Clock.System.now().toEpochMilliseconds()
            _lastSnapshotTime.value = now

            Result.success(snapshotId)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isCreatingSnapshot.value = false
            snapshotMutex.unlock()
        }
    }

    /**
     * Clean up old snapshots to save space
     * @param daysToKeep Number of days of snapshots to keep (default: 365)
     */
    suspend fun cleanupOldSnapshots(daysToKeep: Int = 365): Result<Unit> {
        return try {
            val cutoffDate = Clock.System.now().toEpochMilliseconds() - (daysToKeep * 24 * 60 * 60 * 1000L)
            performanceRepository.deleteSnapshotsBefore(cutoffDate)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculate delay until a specific hour of the day (timezone-aware for DST)
     */
    private fun calculateDelayUntilTime(hourOfDay: Int): Long {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val nowLocal = now.toLocalDateTime(tz)

        // Determine if we need today or tomorrow
        var targetDate = nowLocal.date
        if (nowLocal.hour >= hourOfDay) {
            // Target time has passed today, schedule for tomorrow
            targetDate = targetDate.plus(1, DateTimeUnit.DAY)
        }

        // Calculate target instant using timezone-aware arithmetic
        val targetMillis = targetDate.atStartOfDayIn(tz).toEpochMilliseconds() + (hourOfDay * 60 * 60 * 1000L)
        return targetMillis - now.toEpochMilliseconds()
    }

    /**
     * Calculate delay until a specific day of week and hour
     * @param dayOfWeek 1 = Monday, 7 = Sunday (ISO-8601)
     * @param hourOfDay Hour in 24-hour format (0-23)
     */
    private fun calculateDelayUntilWeeklyTime(dayOfWeek: Int, hourOfDay: Int): Long {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val nowLocal = now.toLocalDateTime(tz)
        val targetDayOfWeek = DayOfWeek.of(dayOfWeek)

        // Calculate days until target day of week
        val currentDayValue = nowLocal.dayOfWeek.value
        var daysUntilTarget = targetDayOfWeek.value - currentDayValue
        if (daysUntilTarget < 0) daysUntilTarget += 7
        // If same day but past the hour, schedule for next week
        if (daysUntilTarget == 0 && nowLocal.hour >= hourOfDay) daysUntilTarget = 7

        // Calculate target instant
        val targetDate = nowLocal.date.plus(daysUntilTarget, DateTimeUnit.DAY)
        val targetMillis = targetDate.atStartOfDayIn(tz).toEpochMilliseconds() + (hourOfDay * 60 * 60 * 1000L)

        return targetMillis - now.toEpochMilliseconds()
    }

    /**
     * Calculate delay until a specific day of month and hour
     * @param dayOfMonth Day of month (1-31)
     * @param hourOfDay Hour in 24-hour format (0-23)
     */
    private fun calculateDelayUntilMonthlyTime(dayOfMonth: Int, hourOfDay: Int): Long {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val nowLocal = now.toLocalDateTime(tz)

        // Determine target month
        var targetYear = nowLocal.year
        var targetMonth = nowLocal.monthNumber

        // Get effective day for current month (use last day of month if requested day > days in month)
        var effectiveDay = minOf(dayOfMonth, lastDayOfMonth(targetYear, targetMonth))

        // If we're past the target day this month (or same day but past hour), go to next month
        if (nowLocal.dayOfMonth > effectiveDay ||
            (nowLocal.dayOfMonth == effectiveDay && nowLocal.hour >= hourOfDay)) {
            targetMonth++
            if (targetMonth > 12) {
                targetMonth = 1
                targetYear++
            }
            // Recalculate effective day for the new target month
            effectiveDay = minOf(dayOfMonth, lastDayOfMonth(targetYear, targetMonth))
        }

        // Calculate target instant
        val targetDate = kotlinx.datetime.LocalDate(targetYear, targetMonth, effectiveDay)
        val targetMillis = targetDate.atStartOfDayIn(tz).toEpochMilliseconds() + (hourOfDay * 60 * 60 * 1000L)

        return targetMillis - now.toEpochMilliseconds()
    }

    /**
     * Get the last day of a given month
     */
    private fun lastDayOfMonth(year: Int, month: Int): Int {
        // Create the first day of the next month, then subtract one day
        val nextMonth = if (month == 12) 1 else month + 1
        val nextYear = if (month == 12) year + 1 else year
        val firstOfNextMonth = kotlinx.datetime.LocalDate(nextYear, nextMonth, 1)
        return firstOfNextMonth.minus(1, DateTimeUnit.DAY).dayOfMonth
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        stopDailySnapshots()
        stopWeeklySnapshots()
        stopMonthlySnapshots()
        scope.cancel()
    }
}
