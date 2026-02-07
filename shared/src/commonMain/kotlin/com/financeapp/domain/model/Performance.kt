package com.financeapp.domain.model

/**
 * Portfolio snapshot at a specific point in time
 */
data class PortfolioSnapshot(
    val id: Long = 0,
    val date: Long,
    val totalValue: Long,
    val totalCostBasis: Long,
    val totalGainLoss: Long,
    val snapshotType: SnapshotType = SnapshotType.DAILY
)

/**
 * Type of snapshot
 */
enum class SnapshotType {
    DAILY, WEEKLY, MONTHLY
}

/**
 * Performance metrics for a given time range
 */
data class PerformanceMetrics(
    val timeRange: TimeRange,
    val startValue: Long,
    val endValue: Long,
    val totalReturn: Long,
    val totalReturnPercent: Double,
    val timeWeightedReturn: Double, // Annualized return (CAGR)
    val highWaterMark: Long,
    val lowWaterMark: Long,
    val volatility: Double
)

/**
 * Time range for performance analysis
 */
enum class TimeRange(val days: Int, val label: String) {
    ONE_WEEK(7, "1W"),
    ONE_MONTH(30, "1M"),
    THREE_MONTHS(90, "3M"),
    SIX_MONTHS(180, "6M"),
    ONE_YEAR(365, "1Y"),
    ALL_TIME(36500, "ALL") // ~100 years; avoids Int.MAX_VALUE overflow in arithmetic
}

/**
 * Chart data for performance visualization
 */
data class PerformanceChartData(
    val timeRange: TimeRange,
    val dataPoints: List<PerformanceDataPoint>
)

/**
 * Single data point in performance chart
 */
data class PerformanceDataPoint(
    val date: Long,
    val value: Long,
    val gainLoss: Long,
    val gainLossPercent: Double
)

/**
 * Snapshot of a single holding at a specific point in time
 */
data class HoldingSnapshot(
    val id: Long = 0,
    val holdingId: Long,
    val date: Long,
    val quantity: Double, // Number of shares (consistent with Holding.shares)
    val price: Long, // In cents
    val value: Long, // In cents
    val costBasis: Long, // In cents
    val gainLoss: Long, // In cents
    val snapshotType: SnapshotType = SnapshotType.DAILY
)

/**
 * Current performance data for a specific holding
 */
data class HoldingPerformance(
    val holdingId: Long,
    val symbol: String,
    val name: String,
    val quantity: Double, // Number of shares (consistent with Holding.shares)
    val costBasis: Long, // Total cost basis in cents
    val currentPrice: Long, // Current price in cents
    val currentValue: Long, // Current total value in cents
    val gainLoss: Long, // Total gain/loss in cents
    val gainLossPercent: Double, // Percentage gain/loss
    val dayChange: Long, // Today's change in cents
    val dayChangePercent: Double, // Today's change percentage
    val allocation: Double // Percentage of total portfolio
)

/**
 * Dividend event for a holding
 */
data class DividendEvent(
    val id: Long = 0,
    val holdingId: Long,
    val symbol: String,
    val paymentDate: Long,
    val amount: Long, // Total dividend in cents
    val perShare: Long, // Dividend per share in cents
    val shares: Double, // Number of shares at time of dividend
    val isReinvested: Boolean = false
)

/**
 * Performance summary for display
 */
data class PerformanceSummary(
    val totalValue: Long,
    val totalCostBasis: Long,
    val totalGainLoss: Long,
    val totalGainLossPercent: Double,
    val dayChange: Long,
    val dayChangePercent: Double,
    val bestPerformer: HoldingPerformance?,
    val worstPerformer: HoldingPerformance?,
    val topHoldings: List<HoldingPerformance>
)
