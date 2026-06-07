package com.financeapp.ui.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.AssetAllocation
import com.financeapp.domain.model.HoldingLot
import com.financeapp.domain.model.HoldingWithPrice
import com.financeapp.domain.model.LotAnalytics
import com.financeapp.ui.components.forms.DatePickerField
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.financeapp.ui.components.parseDecimalToCents
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentScreen(
    viewModel: InvestmentViewModel,
    performanceViewModel: PerformanceTabViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedHolding by remember { mutableStateOf<HoldingWithPrice?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPriceDialog by remember { mutableStateOf(false) }
    var showLotsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Investments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshAllPrices() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Prices")
                        }
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Holding")
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
            // Portfolio Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Portfolio Value",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = formatCurrency(uiState.portfolio.totalMarketValue),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Cost Basis",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatCurrency(uiState.portfolio.totalCostBasis),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Total Gain/Loss",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${formatCurrency(uiState.portfolio.totalGainLoss)} (${String.format("%.2f", uiState.portfolio.totalGainLossPercent)}%)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.portfolio.totalGainLoss >= 0)
                                    Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                    }
                }
            }

            // Tabs
            TabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = { Text("Holdings") }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = { Text("Allocation") }
                )
                Tab(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.setSelectedTab(2) },
                    text = { Text("Performance") }
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (uiState.selectedTab) {
                    0 -> HoldingsList(
                        holdings = uiState.portfolio.holdings,
                        onEdit = { holding ->
                            selectedHolding = holding
                            showEditDialog = true
                        },
                        onUpdatePrice = { holding ->
                            selectedHolding = holding
                            showPriceDialog = true
                        }
                    )
                    1 -> AllocationChart(uiState.assetAllocation)
                    2 -> PerformanceTabContent(performanceViewModel)
                }
            }
        }
    }

    if (showAddDialog) {
        AddHoldingDialog(
            accounts = uiState.investmentAccounts,
            onDismiss = { showAddDialog = false },
            onConfirm = { accountId, symbol, name, shares, costBasis, acquiredDate, purpose, notes ->
                viewModel.addHolding(accountId, symbol, name, shares, costBasis, acquiredDate, purpose, notes)
                showAddDialog = false
            }
        )
    }

    if (showEditDialog && selectedHolding != null) {
        EditHoldingDialog(
            holding = selectedHolding!!,
            onDismiss = { showEditDialog = false },
            onConfirm = { updated ->
                viewModel.updateHolding(updated)
                showEditDialog = false
            },
            onDelete = {
                viewModel.deleteHolding(selectedHolding!!.holding.id)
                showEditDialog = false
            },
            onManageLots = {
                showEditDialog = false
                showLotsDialog = true
            }
        )
    }

    if (showPriceDialog && selectedHolding != null) {
        UpdatePriceDialog(
            symbol = selectedHolding!!.holding.symbol,
            currentPrice = selectedHolding!!.currentPrice,
            onDismiss = { showPriceDialog = false },
            onConfirm = { price ->
                viewModel.updatePrice(selectedHolding!!.holding.symbol, price)
                showPriceDialog = false
            }
        )
    }

    if (showLotsDialog && selectedHolding != null) {
        val lotsFlow = remember(selectedHolding!!.holding.id) {
            viewModel.observeLots(selectedHolding!!.holding.id)
        }
        val lots by lotsFlow.collectAsState(initial = emptyList())
        val analytics = remember(lots, selectedHolding!!.currentPrice) {
            buildLotAnalytics(lots, selectedHolding!!.currentPrice)
        }
        ManageLotsDialog(
            holding = selectedHolding!!.holding,
            analytics = analytics,
            onDismiss = { showLotsDialog = false },
            onAddLot = { date, purpose, shares, costBasis, notes ->
                viewModel.addLot(selectedHolding!!.holding.id, date, purpose, shares, costBasis, notes)
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
private fun HoldingsList(
    holdings: List<HoldingWithPrice>,
    onEdit: (HoldingWithPrice) -> Unit,
    onUpdatePrice: (HoldingWithPrice) -> Unit
) {
    if (holdings.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No holdings yet. Tap + to add one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn {
            items(holdings) { holding ->
                HoldingItem(
                    holding = holding,
                    onEdit = { onEdit(holding) },
                    onUpdatePrice = { onUpdatePrice(holding) }
                )
            }
        }
    }
}

@Composable
private fun HoldingItem(
    holding: HoldingWithPrice,
    onEdit: () -> Unit,
    onUpdatePrice: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                text = holding.holding.symbol,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Column {
                Text(holding.holding.name ?: "")
                Text(
                    "${String.format("%.4f", holding.holding.shares)} shares @ ${formatCurrency(holding.currentPrice ?: 0)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    holding.accountName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = holding.marketValue?.let { formatCurrency(it) } ?: "—",
                    fontWeight = FontWeight.Bold
                )
                val gainLoss = holding.gainLoss
                val gainLossPercent = holding.gainLossPercent
                Text(
                    text = if (gainLoss != null && gainLossPercent != null) {
                        "${formatCurrency(gainLoss)} (${String.format("%.2f", gainLossPercent)}%)"
                    } else "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        gainLoss == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        gainLoss >= 0 -> Color(0xFF4CAF50)
                        else -> Color(0xFFF44336)
                    }
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Update Price") },
                            onClick = {
                                showMenu = false
                                onUpdatePrice()
                            }
                        )
                    }
                }
            }
        }
    )
    HorizontalDivider()
}

@Composable
private fun AllocationChart(allocation: List<AssetAllocation>) {
    if (allocation.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No holdings to display")
        }
        return
    }

    val colors = listOf(
        Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFFC107),
        Color(0xFFFF5722), Color(0xFF9C27B0), Color(0xFF00BCD4),
        Color(0xFF795548), Color(0xFF607D8B)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Pie chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(180.dp)) {
                var startAngle = -90f
                allocation.forEachIndexed { index, item ->
                    val sweep = (item.percentage / 100 * 360).toFloat()
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height)
                    )
                    startAngle += sweep
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legend
        LazyColumn {
            items(allocation.size) { index ->
                val item = allocation[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .padding(end = 8.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(colors[index % colors.size])
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.symbol,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${String.format("%.1f", item.percentage)}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = formatCurrency(item.marketValue),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddHoldingDialog(
    accounts: List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, String?, Double, Long, LocalDate, String?, String?) -> Unit
) {
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.first ?: 0L) }
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var shares by remember { mutableStateOf("") }
    var costBasis by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val defaultDate = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    var acquiredDate by remember { mutableStateOf(defaultDate) }
    var accountExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Holding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accounts.isEmpty()) {
                    Text(
                        "No investment accounts found. Create an investment account first.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = accounts.find { it.first == selectedAccountId }?.second ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false }
                        ) {
                            accounts.forEach { (id, accountName) ->
                                DropdownMenuItem(
                                    text = { Text(accountName) },
                                    onClick = {
                                        selectedAccountId = id
                                        accountExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase() },
                        label = { Text("Symbol *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = shares,
                        onValueChange = { shares = it },
                        label = { Text("Shares *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = costBasis,
                        onValueChange = { costBasis = it },
                        label = { Text("Total Cost Basis *") },
                        prefix = { Text("$") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DatePickerField(
                        selectedDate = acquiredDate,
                        onDateSelected = { acquiredDate = it },
                        label = "Lot Acquired Date",
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = purpose,
                        onValueChange = { purpose = it },
                        label = { Text("Lot Purpose / Notes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Internal Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val sharesValue = shares.toDoubleOrNull() ?: 0.0
                    val costValue = parseDecimalToCents(costBasis) ?: 0L
                    onConfirm(
                        selectedAccountId,
                        symbol,
                        name.ifBlank { null },
                        sharesValue,
                        costValue,
                        acquiredDate,
                        purpose.ifBlank { null },
                        notes.ifBlank { null }
                    )
                },
                enabled = accounts.isNotEmpty() && symbol.isNotBlank() && shares.isNotBlank() && costBasis.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditHoldingDialog(
    holding: HoldingWithPrice,
    onDismiss: () -> Unit,
    onConfirm: (com.financeapp.domain.model.Holding) -> Unit,
    onDelete: () -> Unit,
    onManageLots: () -> Unit
) {
    var name by remember { mutableStateOf(holding.holding.name ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${holding.holding.symbol}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Total Shares: ${formatHoldingShares(holding.holding.shares)}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Total Cost Basis: ${formatCurrency(holding.holding.costBasis)}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Manage individual lots to adjust totals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(onClick = onManageLots) {
                    Icon(Icons.Default.List, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage Lots")
                }

                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Holding")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = holding.holding.copy(
                        name = name.ifBlank { null }
                    )
                    onConfirm(updated)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun UpdatePriceDialog(
    symbol: String,
    currentPrice: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var price by remember {
        mutableStateOf(currentPrice?.let { (it / 100.0).toString() } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Price: $symbol") },
        text = {
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text("Price per Share") },
                prefix = { Text("$") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val priceValue = parseDecimalToCents(price) ?: 0L
                    onConfirm(priceValue)
                },
                enabled = price.isNotBlank()
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatCurrency(cents: Long): String {
    val absCents = kotlin.math.abs(cents)
    val wholeDollars = absCents / 100
    val centsPart = absCents % 100
    return "$${String.format("%,d", wholeDollars)}.${centsPart.toString().padStart(2, '0')}"
}

private fun buildLotAnalytics(lots: List<HoldingLot>, currentPrice: Long?): List<LotAnalytics> {
    return lots.map { lot ->
        val marketValue = currentPrice?.let { kotlin.math.round(lot.shares * it).toLong() }
        val gainLoss = marketValue?.minus(lot.costBasis)
        val percent = if (gainLoss != null && lot.costBasis != 0L) {
            (gainLoss.toDouble() / lot.costBasis) * 100
        } else null
        LotAnalytics(
            lot = lot,
            marketValue = marketValue,
            gainLoss = gainLoss,
            gainLossPercent = percent
        )
    }
}
