package com.financeapp.ui.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.*
import com.financeapp.ui.components.charts.SimpleLineChart
import com.financeapp.ui.theme.income
import com.financeapp.ui.theme.expense
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun PerformanceTabContent(
    viewModel: PerformanceTabViewModel,
    modifier: Modifier = Modifier
) {
    val performanceSummary by viewModel.performanceSummary.collectAsState()
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()
    val performanceMetrics by viewModel.performanceMetrics.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val allHoldingPerformance by viewModel.allHoldingPerformance.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lastRefreshTime by viewModel.lastRefreshTime.collectAsState()
    val error by viewModel.error.collectAsState()

    if (isLoading && performanceSummary == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (performanceSummary == null) {
        // No data yet - show empty state
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "No Performance Data",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Add holdings and create a snapshot to start tracking performance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { viewModel.createSnapshot() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Snapshot")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Refresh banner
            item {
                RefreshBanner(
                    isRefreshing = isRefreshing,
                    lastRefreshTime = lastRefreshTime,
                    onRefresh = { viewModel.refreshPrices() }
                )
            }

            // Performance Summary Card
            item {
                PerformanceSummaryCard(performanceSummary!!)
            }

            // Time Range Selector + Metrics
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Performance Metrics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Time Range Selector
                        TimeRangeSelector(
                            selectedRange = selectedTimeRange,
                            onRangeSelected = { viewModel.selectTimeRange(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        performanceMetrics?.let { metrics ->
                            PerformanceMetricsDisplay(metrics)
                        } ?: run {
                            Text(
                                text = "No data for selected time range",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }

            // Performance Chart
            chartData?.let { data ->
                if (data.dataPoints.isNotEmpty()) {
                    item {
                        PerformanceChartCard(data)
                    }
                }
            }

            // Top/Bottom Performers
            if (allHoldingPerformance.isNotEmpty()) {
                item {
                    TopBottomPerformersCard(
                        topPerformer = performanceSummary!!.bestPerformer,
                        bottomPerformer = performanceSummary!!.worstPerformer
                    )
                }

                // Holdings List
                item {
                    HoldingsPerformanceCard(allHoldingPerformance)
                }
            }

            // Error message
            error?.let { errorMessage ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RefreshBanner(
    isRefreshing: Boolean,
    lastRefreshTime: Long?,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column {
                    Text(
                        text = if (isRefreshing) "Refreshing prices..." else "Prices",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    lastRefreshTime?.let { time ->
                        Text(
                            text = "Last updated: ${formatRefreshTime(time)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onRefresh,
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
fun PerformanceSummaryCard(summary: PerformanceSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Portfolio Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Total Value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(summary.totalValue),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCurrency(summary.totalGainLoss),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (summary.totalGainLoss >= 0) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${if (summary.totalGainLossPercent >= 0) "+" else ""}${"%.2f".format(summary.totalGainLossPercent)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (summary.totalGainLoss >= 0) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Details Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryMetric("Cost Basis", formatCurrency(summary.totalCostBasis), Modifier.weight(1f))
                SummaryMetric("Day Change", formatCurrency(summary.dayChange), Modifier.weight(1f), summary.dayChange >= 0)
                SummaryMetric(
                    "Day %",
                    "${if (summary.dayChangePercent >= 0) "+" else ""}${"%.2f".format(summary.dayChangePercent)}%",
                    Modifier.weight(1f),
                    summary.dayChange >= 0
                )
            }
        }
    }
}

@Composable
fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier, isPositive: Boolean? = null) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = when (isPositive) {
                true -> MaterialTheme.colorScheme.income
                false -> MaterialTheme.colorScheme.expense
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun TimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimeRange.entries.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.label) }
            )
        }
    }
}

@Composable
fun PerformanceMetricsDisplay(metrics: PerformanceMetrics) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricRow("Total Return", formatCurrency(metrics.totalReturn), "${if (metrics.totalReturnPercent >= 0) "+" else ""}${"%.2f".format(metrics.totalReturnPercent)}%")
        MetricRow("Annualized Return", "", "${"%.2f".format(metrics.timeWeightedReturn)}%")
        MetricRow("High Water Mark", formatCurrency(metrics.highWaterMark), "")
        MetricRow("Low Water Mark", formatCurrency(metrics.lowWaterMark), "")
        MetricRow("Volatility", "", "${"%.2f".format(metrics.volatility)}%")
    }
}

@Composable
fun MetricRow(label: String, value: String, secondaryValue: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            if (secondaryValue.isNotEmpty()) {
                Text(
                    text = secondaryValue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PerformanceChartCard(data: PerformanceChartData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Performance Chart",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (data.dataPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No data for selected time range",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Convert data points to chart format
                val labels = data.dataPoints.map { point ->
                    formatChartDate(point.date)
                }
                val values = data.dataPoints.map { point ->
                    (point.value / 100.0).toFloat()
                }

                SimpleLineChart(
                    labels = labels,
                    values = values,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    fillArea = true,
                    showGrid = true
                )
            }
        }
    }
}

@Composable
fun TopBottomPerformersCard(
    topPerformer: HoldingPerformance?,
    bottomPerformer: HoldingPerformance?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Top & Bottom Performers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            topPerformer?.let { holding ->
                PerformerItem(
                    label = "Best Performer",
                    holding = holding,
                    icon = Icons.Default.Star,
                    isPositive = true
                )
            }

            if (topPerformer != null && bottomPerformer != null) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            bottomPerformer?.let { holding ->
                PerformerItem(
                    label = "Worst Performer",
                    holding = holding,
                    icon = Icons.Default.Warning,
                    isPositive = false
                )
            }
        }
    }
}

@Composable
fun PerformerItem(
    label: String,
    holding: HoldingPerformance,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isPositive) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${holding.symbol} - ${holding.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatCurrency(holding.gainLoss),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (holding.gainLoss >= 0) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense
            )
            Text(
                text = "${if (holding.gainLossPercent >= 0) "+" else ""}${"%.2f".format(holding.gainLossPercent)}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (holding.gainLoss >= 0) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense
            )
        }
    }
}

@Composable
fun HoldingsPerformanceCard(holdings: List<HoldingPerformance>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "All Holdings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            val sortedHoldings = holdings.sortedByDescending { it.allocation }
            sortedHoldings.forEachIndexed { index, holding ->
                HoldingPerformanceItem(holding)
                if (index < sortedHoldings.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun HoldingPerformanceItem(holding: HoldingPerformance) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = holding.symbol,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${formatShares(holding.quantity)} shares @ ${formatCurrency(holding.currentPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${"%.1f".format(holding.allocation)}% of portfolio",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatCurrency(holding.currentValue),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatCurrency(holding.gainLoss),
                style = MaterialTheme.typography.bodySmall,
                color = if (holding.gainLoss >= 0) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense
            )
            Text(
                text = "${if (holding.gainLossPercent >= 0) "+" else ""}${"%.2f".format(holding.gainLossPercent)}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (holding.gainLoss >= 0) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense
            )
        }
    }
}

private fun formatCurrency(cents: Long): String {
    val absCents = kotlin.math.abs(cents)
    val wholeDollars = absCents / 100
    val centsPart = absCents % 100
    val sign = if (cents < 0) "-" else ""
    return "${sign}${'$'}${String.format("%,d", wholeDollars)}.${centsPart.toString().padStart(2, '0')}"
}

private fun formatShares(shares: Double): String {
    return "%,.4f".format(shares)
}

private fun formatRefreshTime(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}

private fun formatChartDate(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.monthNumber}/${dateTime.dayOfMonth}"
}
