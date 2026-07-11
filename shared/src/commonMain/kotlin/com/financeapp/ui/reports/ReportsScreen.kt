package com.financeapp.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.*
import com.financeapp.domain.reporting.SpendingDetailLine
import com.financeapp.ui.components.charts.*
import com.financeapp.ui.transactions.EditTransactionDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Report type selector
            ScrollableTabRow(
                selectedTabIndex = ReportType.entries.indexOf(uiState.selectedType),
                modifier = Modifier.fillMaxWidth()
            ) {
                ReportType.entries.forEach { type ->
                    Tab(
                        selected = uiState.selectedType == type,
                        onClick = { viewModel.setReportType(type) },
                        text = { Text(type.displayName) }
                    )
                }
            }

            // Period selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = uiState.selectedPeriod == period,
                        onClick = { viewModel.setPeriod(period) },
                        label = { Text(period.displayName) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (uiState.selectedType) {
                    ReportType.SPENDING_BY_CATEGORY -> SpendingByCategoryReport(
                        report = uiState.spendingReport,
                        selectedCategoryKey = uiState.selectedSpendingCategoryId,
                        viewModel = viewModel
                    )
                    ReportType.INCOME_VS_EXPENSES -> IncomeVsExpensesReport(
                        report = uiState.incomeExpenseReport,
                        monthNames = monthNames
                    )
                    ReportType.NET_WORTH -> NetWorthReportView(
                        report = uiState.netWorthReport
                    )
                }
            }
        }
    }
}

@Composable
private fun SpendingByCategoryReport(
    report: SpendingReport,
    selectedCategoryKey: Long?,
    viewModel: ReportsViewModel
) {
    if (report.categorySpending.isEmpty()) {
        EmptyReportMessage("No spending data for this period")
        return
    }

    val colors = listOf(
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFFC107),
        Color(0xFFFF5722), Color(0xFF9C27B0), Color(0xFF00BCD4),
        Color(0xFFE91E63), Color(0xFF8BC34A), Color(0xFF3F51B5),
        Color(0xFFFF9800)
    )

    var transactionToEdit by remember { mutableStateOf<TransactionWithDetails?>(null) }
    var editTagIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    val selectedIndex = report.categorySpending
        .indexOfFirst { it.categoryId == selectedCategoryKey }
        .takeIf { it >= 0 }
    val selectedSpending = selectedIndex?.let { report.categorySpending[it] }
    val selectedLines = selectedCategoryKey?.let { report.detailLinesByCategory[it] }.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pie chart
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Spending Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    CategorySpendingPieChart(
                        categorySpending = report.categorySpending,
                        selectedIndex = selectedIndex,
                        onSliceClick = { index ->
                            viewModel.selectSpendingCategory(report.categorySpending[index].categoryId)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Total: ${formatCurrency(report.totalSpent)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Drill-down panel for the selected slice
        if (selectedSpending != null) {
            item {
                DrillDownHeader(
                    spending = selectedSpending,
                    lineCount = selectedLines.size,
                    onClear = { viewModel.clearSpendingSelection() }
                )
            }
            // No item keys: two splits of one transaction can share id+category, so positional
            // identity is the only always-unique choice here.
            items(selectedLines) { line ->
                SpendingDetailRow(
                    line = line,
                    onClick = {
                        coroutineScope.launch {
                            editTagIds = viewModel.getTagsForTransaction(line.source.transaction.id)
                            transactionToEdit = line.source
                        }
                    }
                )
            }
        }

        // Category list
        items(report.categorySpending.take(10)) { item ->
            val colorIndex = report.categorySpending.indexOf(item) % colors.size
            CategorySpendingItem(
                item = item,
                color = colors[colorIndex],
                selected = item.categoryId == selectedCategoryKey,
                onClick = { viewModel.selectSpendingCategory(item.categoryId) }
            )
        }
    }

    // Edit transaction dialog (same reuse pattern as GlobalSearchDialog)
    transactionToEdit?.let { txn ->
        EditTransactionDialog(
            transaction = txn,
            onDismiss = { transactionToEdit = null },
            onSave = { categoryId, memo, date, isCleared, tagIds ->
                viewModel.editTransaction(txn.transaction, categoryId, memo, date, isCleared, tagIds)
                transactionToEdit = null
            },
            onDelete = {
                viewModel.deleteTransaction(txn.transaction.id)
                transactionToEdit = null
            },
            initialTagIds = editTagIds
        )
    }
}

@Composable
private fun CategorySpendingPieChart(
    categorySpending: List<CategorySpending>,
    selectedIndex: Int?,
    onSliceClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val chartData = categorySpending.mapIndexed { index, item ->
        PieChartData(
            label = item.categoryName,
            value = item.amount.toFloat() / 100f,
            color = ChartColors.CategoryPalette[index % ChartColors.CategoryPalette.size]
        )
    }

    PieChart(
        data = chartData,
        modifier = modifier,
        showLegend = true,
        showLabels = false,
        onSliceClick = onSliceClick,
        selectedIndex = selectedIndex
    )
}

@Composable
private fun DrillDownHeader(
    spending: CategorySpending,
    lineCount: Int,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                spending.categoryName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${formatCurrency(spending.amount)} · $lineCount transaction${if (lineCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, contentDescription = "Clear selection")
        }
    }
}

@Composable
private fun SpendingDetailRow(
    line: SpendingDetailLine,
    onClick: () -> Unit
) {
    val txn = line.source.transaction
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    line.source.payeeName ?: txn.importedName ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${txn.date.monthNumber}/${txn.date.dayOfMonth}/${txn.date.year} · ${line.source.accountName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                txn.memo?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatCurrency(kotlin.math.abs(line.lineAmountCents)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (line.isSplitPortion) {
                    Text(
                        "of ${formatCurrency(kotlin.math.abs(txn.amount))} split",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySpendingItem(
    item: CategorySpending,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.categoryName,
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { item.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = color
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatCurrency(item.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${item.percentage.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IncomeVsExpensesReport(
    report: IncomeExpenseReport,
    monthNames: List<String>
) {
    if (report.monthlyTrends.isEmpty()) {
        EmptyReportMessage("No transaction data for this period")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Income:")
                        Text(
                            formatCurrency(report.totalIncome),
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Expenses:")
                        Text(
                            formatCurrency(report.totalExpenses),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Net:", fontWeight = FontWeight.Bold)
                        val net = report.totalIncome - report.totalExpenses
                        Text(
                            formatCurrency(net),
                            fontWeight = FontWeight.Bold,
                            color = if (net >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Bar chart
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Monthly Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    MonthlyTrendsChart(
                        trends = report.monthlyTrends,
                        monthNames = monthNames,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                    )
                }
            }
        }

        // Monthly details
        items(report.monthlyTrends.reversed()) { trend ->
            MonthlyTrendItem(trend, monthNames)
        }
    }
}

@Composable
private fun MonthlyTrendsChart(
    trends: List<MonthlyTrend>,
    monthNames: List<String>,
    modifier: Modifier = Modifier
) {
    val chartData = trends.map { trend ->
        GroupedBarData(
            label = monthNames[trend.month - 1],
            values = listOf(
                BarValue(
                    value = trend.income.toFloat() / 100f,
                    color = ChartColors.FinancePalette[0], // Green for income
                    legend = "Income"
                ),
                BarValue(
                    value = trend.expenses.toFloat() / 100f,
                    color = ChartColors.FinancePalette[1], // Red for expenses
                    legend = "Expenses"
                )
            )
        )
    }

    GroupedBarChart(
        data = chartData,
        modifier = modifier,
        showGrid = true,
        showLabels = true,
        showLegend = true
    )
}

@Composable
private fun MonthlyTrendItem(
    trend: MonthlyTrend,
    monthNames: List<String>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "${monthNames.getOrElse(trend.month - 1) { "?" }} ${trend.year}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Income", style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatCurrency(trend.income),
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column {
                    Text("Expenses", style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatCurrency(trend.expenses),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Net", style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatCurrency(trend.net),
                        color = if (trend.net >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun NetWorthReportView(
    report: NetWorthReport
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Current Net Worth",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    formatCurrency(report.currentNetWorth),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (report.currentNetWorth >= 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyReportMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatCurrency(cents: Long): String {
    val dollars = kotlin.math.abs(cents) / 100
    val centsPart = kotlin.math.abs(cents) % 100
    val sign = if (cents < 0) "-" else ""
    return "$sign$$dollars.${centsPart.toString().padStart(2, '0')}"
}
