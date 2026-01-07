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
import com.financeapp.domain.model.DividendEvent
import com.financeapp.domain.model.Holding
import com.financeapp.domain.model.HoldingLot
import com.financeapp.domain.model.HoldingPerformance
import com.financeapp.domain.model.PerformanceChartData
import com.financeapp.domain.model.TimeRange
import com.financeapp.ui.components.charts.LineChart
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingDetailScreen(
    viewModel: HoldingDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val holdingPerformance by viewModel.holdingPerformance.collectAsState()
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val dividends by viewModel.dividends.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    val lots by viewModel.lots.collectAsState()
    var showLotsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = holdingPerformance?.symbol ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge
                        )
                        holdingPerformance?.let {
                            Text(
                                text = it.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshPrice() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (isLoading && holdingPerformance == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Performance Summary Card
                item {
                    holdingPerformance?.let { performance ->
                        PerformanceSummaryCard(performance)
                    }
                }

                item {
                    LotsSummaryCard(
                        lots = lots,
                        onManageLots = { showLotsDialog = true }
                    )
                }

                // Performance Chart with Time Range Selector
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Performance",
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

                            // Chart
                            chartData?.let { data ->
                                if (data.dataPoints.isNotEmpty()) {
                                    PerformanceLineChart(data)
                                } else {
                                    Text(
                                        text = "No data available for this time range",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 32.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Dividends Section
                if (dividends.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Dividends",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    val totalDividends = dividends.sumOf { it.amount }
                                    Text(
                                        text = formatCurrency(totalDividends),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                dividends.take(5).forEach { dividend ->
                                    DividendItem(dividend)
                                    if (dividend != dividends.last()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

    val currentPerformance = holdingPerformance
    if (showLotsDialog && currentPerformance != null) {
        val dialogHolding = Holding(
            id = viewModel.holdingId,
            accountId = 0,
            symbol = currentPerformance.symbol,
            name = currentPerformance.name,
            shares = currentPerformance.quantity / 10000.0,
            costBasis = currentPerformance.costBasis
        )
        ManageLotsDialog(
            holding = dialogHolding,
            lots = lots,
            onDismiss = { showLotsDialog = false },
            onAddLot = { date, purpose, shares, costBasis, notes ->
                viewModel.addLot(date, purpose, shares, costBasis, notes)
            },
            onUpdateLot = { lot, date, purpose, shares, costBasis, notes ->
                viewModel.updateLot(lot, date, purpose, shares, costBasis, notes)
            },
            onDeleteLot = { lotId ->
                viewModel.deleteLot(lotId)
            }
        )
    }
}

@Composable
private fun PerformanceSummaryCard(performance: HoldingPerformance) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Current Value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(performance.currentValue),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatCurrency(performance.gainLoss),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (performance.gainLoss >= 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${if (performance.gainLossPercent >= 0) "+" else ""}${"%.2f".format(performance.gainLossPercent)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (performance.gainLoss >= 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Details Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricItem("Shares", formatShares(performance.quantity), Modifier.weight(1f))
                MetricItem("Cost Basis", formatCurrency(performance.costBasis), Modifier.weight(1f))
                MetricItem("Price", formatCurrency(performance.currentPrice), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Day Change
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Today's Change",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = formatCurrency(performance.dayChange),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (performance.dayChange >= 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "(${if (performance.dayChangePercent >= 0) "+" else ""}${"%.2f".format(performance.dayChangePercent)}%)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (performance.dayChange >= 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, modifier: Modifier = Modifier) {
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
            fontWeight = FontWeight.Medium
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
private fun PerformanceLineChart(data: PerformanceChartData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        // Simplified chart - in production, use a proper chart library
        Text(
            text = "Chart: ${data.dataPoints.size} data points",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DividendItem(dividend: DividendEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = formatDate(dividend.paymentDate),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatCurrency(dividend.perShare) + " per share",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatCurrency(dividend.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (dividend.isReinvested) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reinvested",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LotsSummaryCard(
    lots: List<HoldingLot>,
    onManageLots: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Lots",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${lots.size} tracked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onManageLots) {
                    Text("Manage")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Shares", style = MaterialTheme.typography.labelSmall)
                    Text(
                        formatHoldingShares(lots.sumOf { it.shares }),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Cost Basis", style = MaterialTheme.typography.labelSmall)
                    Text(
                        formatHoldingCurrency(lots.sumOf { it.costBasis }),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (lots.isEmpty()) {
                Text(
                    text = "No lots recorded. Track purchase lots to improve performance reporting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lots.take(3).forEach { lot ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatLotDate(lot.acquiredDate),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = lot.purpose ?: "General lot",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Shares: ${formatHoldingShares(lot.shares)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = formatHoldingCurrency(lot.costBasis),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (lots.size > 3) {
                        Text(
                            text = "+${lots.size - 3} more lots",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatCurrency(cents: Long): String {
    val dollars = cents / 100.0
    return "${'$'}${"%,.2f".format(kotlin.math.abs(dollars))}"
}

private fun formatShares(quantity: Long): String {
    val shares = quantity / 10000.0
    return "%,.4f".format(shares)
}

private fun formatDate(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.month.name.take(3)} ${date.dayOfMonth}, ${date.year}"
}
