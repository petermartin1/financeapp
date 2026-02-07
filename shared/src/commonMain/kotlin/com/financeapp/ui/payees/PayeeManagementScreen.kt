package com.financeapp.ui.payees

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.PayeeWithStats
import com.financeapp.domain.model.TransactionWithDetails
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayeeManagementScreen(
    viewModel: PayeeManagementViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val transactionsState by viewModel.transactionsUiState.collectAsState()
    var selectedPayeeId by remember { mutableStateOf<Long?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val selectedPayee = uiState.payees.find { it.payee.id == selectedPayeeId }

    LaunchedEffect(selectedPayeeId) {
        viewModel.selectPayee(selectedPayeeId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payee Management") },
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
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search payees...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredPayees = viewModel.getFilteredPayees()

                if (filteredPayees.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No payees match your search" else "No payees yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val isNarrow = maxWidth < 900.dp
                        val hasSelection = selectedPayeeId != null
                        if (isNarrow) {
                            if (hasSelection) {
                                Column(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    PayeeList(
                                        payees = filteredPayees,
                                        selectedPayeeId = selectedPayeeId,
                                        categoryNameFor = { viewModel.getCategoryName(it) },
                                        onSelect = { selectedPayeeId = it.payee.id },
                                        onEdit = {
                                            selectedPayeeId = it.payee.id
                                            showEditDialog = true
                                        },
                                        onMerge = {
                                            selectedPayeeId = it.payee.id
                                            showMergeDialog = true
                                        },
                                        onDelete = {
                                            selectedPayeeId = it.payee.id
                                            showDeleteDialog = true
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    )
                                    HorizontalDivider()
                                    PayeeTransactionsPanel(
                                        payee = selectedPayee,
                                        transactions = transactionsState.transactions,
                                        onClose = { selectedPayeeId = null },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    )
                                }
                            } else {
                                PayeeList(
                                    payees = filteredPayees,
                                    selectedPayeeId = selectedPayeeId,
                                    categoryNameFor = { viewModel.getCategoryName(it) },
                                    onSelect = { selectedPayeeId = it.payee.id },
                                    onEdit = {
                                        selectedPayeeId = it.payee.id
                                        showEditDialog = true
                                    },
                                    onMerge = {
                                        selectedPayeeId = it.payee.id
                                        showMergeDialog = true
                                    },
                                    onDelete = {
                                        selectedPayeeId = it.payee.id
                                        showDeleteDialog = true
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            if (hasSelection) {
                                Row(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    PayeeList(
                                        payees = filteredPayees,
                                        selectedPayeeId = selectedPayeeId,
                                        categoryNameFor = { viewModel.getCategoryName(it) },
                                        onSelect = { selectedPayeeId = it.payee.id },
                                        onEdit = {
                                            selectedPayeeId = it.payee.id
                                            showEditDialog = true
                                        },
                                        onMerge = {
                                            selectedPayeeId = it.payee.id
                                            showMergeDialog = true
                                        },
                                        onDelete = {
                                            selectedPayeeId = it.payee.id
                                            showDeleteDialog = true
                                        },
                                        modifier = Modifier
                                            .weight(0.6f)
                                            .fillMaxHeight()
                                    )
                                    VerticalDivider()
                                    PayeeTransactionsPanel(
                                        payee = selectedPayee,
                                        transactions = transactionsState.transactions,
                                        onClose = { selectedPayeeId = null },
                                        modifier = Modifier
                                            .weight(0.4f)
                                            .fillMaxHeight()
                                    )
                                }
                            } else {
                                PayeeList(
                                    payees = filteredPayees,
                                    selectedPayeeId = selectedPayeeId,
                                    categoryNameFor = { viewModel.getCategoryName(it) },
                                    onSelect = { selectedPayeeId = it.payee.id },
                                    onEdit = {
                                        selectedPayeeId = it.payee.id
                                        showEditDialog = true
                                    },
                                    onMerge = {
                                        selectedPayeeId = it.payee.id
                                        showMergeDialog = true
                                    },
                                    onDelete = {
                                        selectedPayeeId = it.payee.id
                                        showDeleteDialog = true
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit dialog
    if (showEditDialog && selectedPayee != null) {
        EditPayeeDialog(
            payee = selectedPayee!!,
            categories = uiState.categories,
            onDismiss = { showEditDialog = false },
            onConfirm = { newName, categoryId ->
                viewModel.renamePayee(selectedPayee!!.payee.id, newName)
                viewModel.setDefaultCategory(selectedPayee!!.payee.id, categoryId)
                showEditDialog = false
            }
        )
    }

    // Merge dialog
    if (showMergeDialog && selectedPayee != null) {
        MergePayeeDialog(
            sourcePayee = selectedPayee!!,
            allPayees = uiState.payees.filter { it.payee.id != selectedPayee!!.payee.id },
            onDismiss = { showMergeDialog = false },
            onConfirm = { targetId ->
                viewModel.mergePayees(selectedPayee!!.payee.id, targetId)
                selectedPayeeId = null
                showMergeDialog = false
            }
        )
    }

    // Delete confirmation
    if (showDeleteDialog && selectedPayee != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Payee") },
            text = {
                Text("Are you sure you want to delete \"${selectedPayee!!.payee.name}\"? This will not delete associated transactions.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePayee(selectedPayee!!.payee.id)
                        selectedPayeeId = null
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PayeeList(
    payees: List<PayeeWithStats>,
    selectedPayeeId: Long?,
    categoryNameFor: (Long?) -> String,
    onSelect: (PayeeWithStats) -> Unit,
    onEdit: (PayeeWithStats) -> Unit,
    onMerge: (PayeeWithStats) -> Unit,
    onDelete: (PayeeWithStats) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(payees) { payeeWithStats ->
            PayeeItem(
                payeeWithStats = payeeWithStats,
                categoryName = categoryNameFor(payeeWithStats.payee.defaultCategoryId),
                isSelected = payeeWithStats.payee.id == selectedPayeeId,
                onSelect = { onSelect(payeeWithStats) },
                onEdit = { onEdit(payeeWithStats) },
                onMerge = { onMerge(payeeWithStats) },
                onDelete = { onDelete(payeeWithStats) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PayeeItem(
    payeeWithStats: PayeeWithStats,
    categoryName: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(payeeWithStats.payee.name) },
        supportingContent = {
            Column {
                Text("${payeeWithStats.transactionCount} transactions")
                if (payeeWithStats.payee.defaultCategoryId != null) {
                    Text(
                        "Auto-category: $categoryName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
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
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Merge into...") },
                        onClick = {
                            showMenu = false
                            onMerge()
                        },
                        leadingIcon = { Icon(Icons.Default.Call, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.combinedClickable(
            onClick = { onSelect() },
            onDoubleClick = { onEdit() }
        )
    )
    HorizontalDivider()
}

@Composable
private fun PayeeTransactionsPanel(
    payee: PayeeWithStats?,
    transactions: List<TransactionWithDetails>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = payee?.payee?.name ?: "Payee Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (payee != null) {
                        Text(
                            text = "${transactions.size} transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (payee != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
            HorizontalDivider()

            when {
                payee == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select a payee to view transactions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                transactions.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No non-split, non-transfer transactions for this payee",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(transactions) { transaction ->
                            PayeeTransactionRow(transaction = transaction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayeeTransactionRow(transaction: TransactionWithDetails) {
    val memo = transaction.transaction.memo?.takeIf { it.isNotBlank() }
    val categoryLabel = transaction.categoryName ?: "Uncategorized"
    val primaryLabel = memo ?: transaction.categoryName ?: "Transaction"
    val secondaryParts = mutableListOf<String>()

    secondaryParts.add(transaction.accountName)
    if (primaryLabel != categoryLabel) {
        secondaryParts.add(categoryLabel)
    }
    secondaryParts.add(formatDate(transaction.transaction.date))

    val amountColor = if (transaction.transaction.amount >= 0) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    ListItem(
        headlineContent = {
            Text(
                text = primaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = secondaryParts.joinToString(" - "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Text(
                text = formatCurrency(transaction.transaction.amount),
                color = amountColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPayeeDialog(
    payee: PayeeWithStats,
    categories: List<com.financeapp.domain.model.Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?) -> Unit
) {
    var name by remember { mutableStateOf(payee.payee.name) }
    var selectedCategoryId by remember { mutableStateOf(payee.payee.defaultCategoryId) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Payee") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = categories.find { it.id == selectedCategoryId }?.name ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Default Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedCategoryId = null
                                categoryExpanded = false
                            }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategoryId = category.id
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    "Setting a default category will auto-categorize new transactions from this payee.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, selectedCategoryId) },
                enabled = name.isNotBlank()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergePayeeDialog(
    sourcePayee: PayeeWithStats,
    allPayees: List<PayeeWithStats>,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTargetId by remember { mutableStateOf<Long?>(null) }

    val filteredPayees = if (searchQuery.isBlank()) {
        allPayees
    } else {
        allPayees.filter { it.payee.name.lowercase().contains(searchQuery.lowercase()) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge \"${sourcePayee.payee.name}\" into...") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(300.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search target payee...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    "All ${sourcePayee.transactionCount} transactions will be moved to the selected payee.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredPayees) { payee ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetId = payee.payee.id }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTargetId == payee.payee.id,
                                onClick = { selectedTargetId = payee.payee.id }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(payee.payee.name)
                                Text(
                                    "${payee.transactionCount} transactions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedTargetId?.let { onConfirm(it) } },
                enabled = selectedTargetId != null
            ) {
                Text("Merge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDate(date: LocalDate): String {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${months[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"
}

private fun formatCurrency(cents: Long): String {
    val absCents = kotlin.math.abs(cents)
    val wholeDollars = absCents / 100
    val centsPart = absCents % 100
    val sign = if (cents < 0) "-" else ""
    return "$sign\$$wholeDollars.${centsPart.toString().padStart(2, '0')}"
}
