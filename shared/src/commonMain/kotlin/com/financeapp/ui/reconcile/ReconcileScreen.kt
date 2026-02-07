package com.financeapp.ui.reconcile

import androidx.compose.foundation.clickable
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
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.financeapp.ui.components.parseDecimalToCents
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconcileScreen(
    viewModel: ReconcileViewModel,
    accountName: String,
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reconcile: $accountName") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelReconciliation()
                        onBack()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.completeReconciliation() },
                        enabled = uiState.difference == 0L && uiState.transactions.any { it.isSelected }
                    ) {
                        Text("Finish")
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
            // Summary card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.difference == 0L)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
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
                        Text("Statement Balance:")
                        Text(
                            formatCurrency(uiState.statementBalance),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cleared Balance:")
                        Text(formatCurrency(uiState.clearedBalance))
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Difference:",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatCurrency(uiState.difference),
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.difference == 0L)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Selection buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.selectAll() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select All")
                }
                OutlinedButton(
                    onClick = { viewModel.selectNone() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear All")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Transaction list
            if (uiState.transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No unreconciled transactions",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.transactions) { transaction ->
                        ReconcileTransactionItem(
                            transaction = transaction,
                            onToggle = { viewModel.toggleTransaction(transaction.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReconcileTransactionItem(
    transaction: ReconcileTransaction,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = transaction.isSelected,
                onCheckedChange = { onToggle() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.date.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                transaction.memo?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                }
            }

            Text(
                formatCurrency(transaction.amount),
                fontWeight = FontWeight.Medium,
                color = if (transaction.amount >= 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconcileStartDialog(
    onDismiss: () -> Unit,
    onStart: (LocalDate, Long) -> Unit
) {
    var statementDate by remember { mutableStateOf(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ) }
    var balanceText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start Reconciliation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = statementDate.toString(),
                    onValueChange = { },
                    label = { Text("Statement Date") },
                    readOnly = true,
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, null)
                    }
                )

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Statement Ending Balance") },
                    placeholder = { Text("0.00") },
                    prefix = { Text("$") },
                    singleLine = true
                )

                Text(
                    "Enter the ending balance from your bank statement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val balance = parseDecimalToCents(balanceText) ?: return@Button
                    onStart(statementDate, balance)
                },
                enabled = balanceText.isNotBlank() && parseDecimalToCents(balanceText) != null
            ) {
                Text("Start")
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
