package com.financeapp.ui.scheduled

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.Account
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.ScheduledTransactionWithDetails
import com.financeapp.domain.model.TransactionFrequency
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.financeapp.ui.components.parseDecimalToCents
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledScreen(
    viewModel: ScheduledViewModel,
    accounts: List<Account>,
    categories: List<Category>,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled Transactions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.enterDueTransactions() }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Enter Due")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
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
            // Entered notification
            uiState.lastEnteredCount?.let { count ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, null)
                        Text("$count transaction(s) entered", modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearEnteredCount() }) {
                            Icon(Icons.Default.Close, "Dismiss")
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.scheduledTransactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No scheduled transactions",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Add recurring bills or income",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Scheduled Transaction")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.scheduledTransactions) { item ->
                        ScheduledTransactionCard(
                            item = item,
                            onSkip = { viewModel.skipNextOccurrence(item.scheduled.id) },
                            onDelete = { viewModel.deleteScheduledTransaction(item.scheduled.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddScheduledDialog(
            accounts = accounts,
            categories = categories,
            onDismiss = { showAddDialog = false },
            onConfirm = { accountId, payeeId, categoryId, amount, memo, frequency, nextDate ->
                viewModel.addScheduledTransaction(
                    accountId, payeeId, categoryId, amount, memo, frequency, nextDate, null
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ScheduledTransactionCard(
    item: ScheduledTransactionWithDetails,
    onSkip: () -> Unit,
    onDelete: () -> Unit
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val isDue = item.scheduled.nextDate <= today

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isDue) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.payeeName ?: "No Payee",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        item.accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    formatCurrency(item.scheduled.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.scheduled.amount >= 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Next: ${item.scheduled.nextDate}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        item.scheduled.frequency.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onSkip) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            "Skip",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScheduledDialog(
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long?, Long?, Long, String?, TransactionFrequency, LocalDate) -> Unit
) {
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var amountText by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var selectedFrequency by remember { mutableStateOf(TransactionFrequency.MONTHLY) }
    var isExpense by remember { mutableStateOf(true) }

    val nextDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Scheduled Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Account dropdown
                var accountExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = it }
                ) {
                    OutlinedTextField(
                        value = accounts.find { it.id == selectedAccountId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = accountExpanded,
                        onDismissRequest = { accountExpanded = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedAccountId = account.id
                                    accountExpanded = false
                                }
                            )
                        }
                    }
                }

                // Amount
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        prefix = { Text("$") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = isExpense,
                        onClick = { isExpense = !isExpense },
                        label = { Text(if (isExpense) "Expense" else "Income") }
                    )
                }

                // Frequency dropdown
                var freqExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = freqExpanded,
                    onExpandedChange = { freqExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedFrequency.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = freqExpanded,
                        onDismissRequest = { freqExpanded = false }
                    ) {
                        TransactionFrequency.entries.forEach { freq ->
                            DropdownMenuItem(
                                text = { Text(freq.displayName) },
                                onClick = {
                                    selectedFrequency = freq
                                    freqExpanded = false
                                }
                            )
                        }
                    }
                }

                // Memo
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("Memo (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = parseDecimalToCents(amountText) ?: 0L
                    val signedAmount = if (isExpense) -amount else amount
                    onConfirm(
                        selectedAccountId,
                        null, // payeeId - could add payee selection
                        selectedCategoryId,
                        signedAmount,
                        memo.takeIf { it.isNotBlank() },
                        selectedFrequency,
                        nextDate
                    )
                },
                enabled = amountText.isNotBlank() && selectedAccountId != 0L
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

private fun formatCurrency(cents: Long): String {
    val dollars = kotlin.math.abs(cents) / 100
    val centsPart = kotlin.math.abs(cents) % 100
    val sign = if (cents < 0) "-" else ""
    return "$sign$$dollars.${centsPart.toString().padStart(2, '0')}"
}
