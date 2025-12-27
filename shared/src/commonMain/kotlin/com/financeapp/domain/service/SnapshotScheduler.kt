package com.financeapp.domain.service

import com.financeapp.domain.model.SnapshotType
import com.financeapp.domain.repository.PerformanceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

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
        if (_isCreatingSnapshot.value) {
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
     * Calculate delay until a specific hour of the day
     */
    private fun calculateDelayUntilTime(hourOfDay: Int): Long {
        val now = Clock.System.now().toEpochMilliseconds()
        val currentHourMillis = now % (24 * 60 * 60 * 1000L)
        val targetHourMillis = hourOfDay * 60 * 60 * 1000L

        var delay = targetHourMillis - currentHourMillis
        if (delay <= 0) {
            // Target time has passed today, schedule for tomorrow
            delay += 24 * 60 * 60 * 1000L
        }

        return delay
    }

    /**
     * Calculate delay until a specific day of week and hour
     * Simplified version - in production you'd use a proper date/time library
     */
    private fun calculateDelayUntilWeeklyTime(dayOfWeek: Int, hourOfDay: Int): Long {
        // Simplified: just schedule for 7 days from now
        // In production, calculate actual day of week
        return 7 * 24 * 60 * 60 * 1000L
    }

    /**
     * Calculate delay until a specific day of month and hour
     * Simplified version - in production you'd use a proper date/time library
     */
    private fun calculateDelayUntilMonthlyTime(dayOfMonth: Int, hourOfDay: Int): Long {
        // Simplified: just schedule for 30 days from now
        // In production, calculate actual day of month
        return 30 * 24 * 60 * 60 * 1000L
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
