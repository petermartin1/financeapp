package com.financeapp.domain.repository

import com.financeapp.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository for investment performance tracking and analysis
 */
interface PerformanceRepository {

    // Portfolio Snapshots
    /**
     * Create a snapshot of the current portfolio
     */
    suspend fun createPortfolioSnapshot(snapshotType: SnapshotType = SnapshotType.DAILY): Long

    /**
     * Get portfolio snapshots within a date range
     */
    fun getPortfolioSnapshots(startDate: Long, endDate: Long): Flow<List<PortfolioSnapshot>>

    /**
     * Get the most recent portfolio snapshot
     */
    suspend fun getLatestPortfolioSnapshot(): PortfolioSnapshot?

    /**
     * Delete old snapshots (for cleanup)
     */
    suspend fun deleteSnapshotsBefore(date: Long)

    // Holding Snapshots
    /**
     * Create snapshots for all current holdings
     */
    suspend fun createHoldingSnapshots(snapshotType: SnapshotType = SnapshotType.DAILY)

    /**
     * Get snapshots for a specific holding within a date range
     */
    fun getHoldingSnapshots(holdingId: Long, startDate: Long, endDate: Long): Flow<List<HoldingSnapshot>>

    /**
     * Get all holding snapshots for a specific date
     */
    suspend fun getHoldingSnapshotsForDate(date: Long): List<HoldingSnapshot>

    // Performance Metrics
    /**
     * Calculate performance metrics for a given time range
     */
    suspend fun calculatePerformanceMetrics(timeRange: TimeRange): PerformanceMetrics?

    /**
     * Get current performance for all holdings
     */
    fun getAllHoldingPerformance(): Flow<List<HoldingPerformance>>

    /**
     * Get performance for a specific holding
     */
    suspend fun getHoldingPerformance(holdingId: Long): HoldingPerformance?

    /**
     * Get overall performance summary
     */
    suspend fun getPerformanceSummary(): PerformanceSummary

    // Chart Data
    /**
     * Get performance chart data for a time range
     */
    suspend fun getPerformanceChartData(timeRange: TimeRange): PerformanceChartData

    /**
     * Get holding performance chart data for a time range
     */
    suspend fun getHoldingChartData(holdingId: Long, timeRange: TimeRange): PerformanceChartData

    // Dividends
    /**
     * Record a dividend payment
     */
    suspend fun recordDividend(dividend: DividendEvent): Long

    /**
     * Get all dividends for a holding
     */
    fun getHoldingDividends(holdingId: Long): Flow<List<DividendEvent>>

    /**
     * Get all dividends within a date range
     */
    fun getDividends(startDate: Long, endDate: Long): Flow<List<DividendEvent>>

    /**
     * Get total dividends received for a holding
     */
    suspend fun getTotalDividends(holdingId: Long): Long
}
