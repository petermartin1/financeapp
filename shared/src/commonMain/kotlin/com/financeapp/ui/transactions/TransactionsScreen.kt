package com.financeapp.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.Account
import com.financeapp.domain.model.TransactionWithDetails
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    accountId: Long,
    viewModel: TransactionsViewModel,
    onBack: () -> Unit,
    onReconcile: (Long, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<TransactionWithDetails?>(null) }
    var editTagIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val searchFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(accountId) {
        viewModel.loadTransactions(accountId)
    }

    // Keyboard shortcut handler
    val keyboardModifier = Modifier.onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            val isCtrlOrCmd = event.isCtrlPressed || event.isMetaPressed
            when {
                // Ctrl/Cmd + N: New transaction
                isCtrlOrCmd && event.key == Key.N -> {
                    showAddDialog = true
                    true
                }
                // Ctrl/Cmd + F: Focus search
                isCtrlOrCmd && event.key == Key.F -> {
                    searchFocusRequester.requestFocus()
                    true
                }
                // Escape: Go back or clear filter
                event.key == Key.Escape -> {
                    if (uiState.isFilterActive) {
                        viewModel.clearFilter()
                    } else {
                        onBack()
                    }
                    true
                }
                else -> false
            }
        } else false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.accountName)
                        Text(
                            text = formatCurrency(uiState.accountBalance),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reconcile") },
                                onClick = {
                                    showMenu = false
                                    onReconcile(accountId, uiState.accountName)
                                }
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier.then(keyboardModifier).focusable()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            SearchBar(
                searchQuery = uiState.filter.searchQuery,
                onSearchChange = { viewModel.updateSearchQuery(it) },
                onFilterClick = { showFilterSheet = true },
                isFilterActive = uiState.isFilterActive,
                onClearFilter = { viewModel.clearFilter() },
                focusRequester = searchFocusRequester
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.transactions.isEmpty()) {
                EmptyTransactionsContent(
                    onAddClick = { showAddDialog = true }
                )
            } else if (uiState.filteredTransactions.isEmpty() && uiState.isFilterActive) {
                // No results for filter
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No matching transactions",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.clearFilter() }) {
                        Text("Clear filters")
                    }
                }
            } else {
                // Show result count when filtering
                if (uiState.isFilterActive) {
                    Text(
                        text = "${uiState.filteredTransactions.size} of ${uiState.transactions.size} transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Calculate running balances (from oldest to newest, then display newest first)
                val transactionsWithBalance = remember(uiState.filteredTransactions, uiState.accountBalance) {
                    val sorted = uiState.filteredTransactions.sortedBy { it.transaction.date }
                    var balance = uiState.accountBalance - sorted.sumOf { it.transaction.amount }
                    sorted.map { txn ->
                        balance += txn.transaction.amount
                        txn.copy(runningBalance = balance)
                    }.reversed()
                }

                TransactionsList(
                    transactions = transactionsWithBalance,
                    onTransactionClick = { txn ->
                        coroutineScope.launch {
                            editTagIds = viewModel.getTagsForTransaction(txn.transaction.id)
                            transactionToEdit = txn
                        }
                    },
                    onToggleCleared = { viewModel.toggleCleared(it.transaction) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddTransactionDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, payee, categoryId, memo, date, isCleared, tagIds ->
                viewModel.addTransaction(amount, payee, categoryId, memo, date, isCleared, tagIds)
                showAddDialog = false
            },
            currentAccountId = accountId,
            accounts = uiState.accounts,
            onTransfer = { amount, toAccountId, memo, date ->
                viewModel.addTransfer(amount, toAccountId, memo, date)
                showAddDialog = false
            }
        )
    }

    if (showFilterSheet) {
        AlertDialog(
            onDismissRequest = { showFilterSheet = false },
            confirmButton = {},
            text = {
                TransactionFilterSheet(
                    currentFilter = uiState.filter,
                    onApply = { filter ->
                        viewModel.updateFilter(filter)
                        showFilterSheet = false
                    },
                    onClear = {
                        viewModel.clearFilter()
                        showFilterSheet = false
                    },
                    onDismiss = { showFilterSheet = false }
                )
            }
        )
    }

    transactionToEdit?.let { txn ->
        EditTransactionDialog(
            transaction = txn,
            onDismiss = { transactionToEdit = null },
            onSave = { categoryId, memo, date, isCleared, tagIds ->
                viewModel.editTransaction(
                    txn.transaction,
                    categoryId,
                    memo,
                    date,
                    isCleared,
                    tagIds
                )
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
private fun SearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    isFilterActive: Boolean,
    onClearFilter: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    // Use local TextFieldValue state to properly manage cursor position
    var textFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(searchQuery)) }

    // Sync with incoming searchQuery from ViewModel, but preserve cursor position
    LaunchedEffect(searchQuery) {
        if (textFieldValue.text != searchQuery) {
            textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                text = searchQuery,
                selection = androidx.compose.ui.text.TextRange(searchQuery.length)
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onSearchChange(newValue.text)
            },
            placeholder = { Text("Search transactions...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (textFieldValue.text.isNotEmpty()) {
                    IconButton(onClick = {
                        textFieldValue = androidx.compose.ui.text.input.TextFieldValue("")
                        onSearchChange("")
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.weight(1f).let { mod ->
                if (focusRequester != null) mod.focusRequester(focusRequester) else mod
            }
        )

        // Filter button with badge
        BadgedBox(
            badge = {
                if (isFilterActive) {
                    Badge()
                }
            }
        ) {
            IconButton(onClick = onFilterClick) {
                Icon(Icons.Default.Menu, contentDescription = "Filter")
            }
        }
    }
}

@Composable
private fun EmptyTransactionsContent(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No transactions yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add your first transaction",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Transaction")
        }
    }
}

@Composable
private fun TransactionsList(
    transactions: List<TransactionWithDetails>,
    onTransactionClick: (TransactionWithDetails) -> Unit,
    onToggleCleared: (TransactionWithDetails) -> Unit,
    modifier: Modifier = Modifier
) {
    // Group by date
    val groupedTransactions = transactions.groupBy { it.transaction.date }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groupedTransactions.forEach { (date, dayTransactions) ->
            item {
                Text(
                    text = formatDate(date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(dayTransactions) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    onClick = { onTransactionClick(transaction) },
                    onToggleCleared = { onToggleCleared(transaction) }
                )
            }
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: TransactionWithDetails,
    onClick: () -> Unit,
    onToggleCleared: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cleared checkbox
            IconButton(
                onClick = onToggleCleared,
                modifier = Modifier.size(32.dp)
            ) {
                if (transaction.transaction.isCleared) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Cleared",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .padding(2.dp)
                    ) {
                        // Empty circle would go here
                    }
                }
            }

            // Payee and category
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = transaction.payeeName
                        ?: transaction.transaction.memo
                        ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                transaction.categoryName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                transaction.transaction.memo?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Show transaction type if not a standard debit/credit
                transaction.transaction.transactionType?.let { type ->
                    if (type !in listOf("DEBIT", "CREDIT")) {
                        Text(
                            text = type,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Amount and running balance
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatCurrency(transaction.transaction.amount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (transaction.transaction.amount >= 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                transaction.runningBalance?.let { balance ->
                    Text(
                        text = formatCurrency(balance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatCurrency(cents: Long): String {
    val dollars = cents / 100.0
    val sign = if (cents < 0) "-" else ""
    return "$sign$${String.format("%.2f", kotlin.math.abs(dollars))}"
}

private fun formatDate(date: LocalDate): String {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${months[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"
}
