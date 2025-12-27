package com.financeapp.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.TransactionWithDetails
import com.financeapp.ui.transactions.EditTransactionDialog
import com.financeapp.ui.tags.TagsViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

@Composable
fun GlobalSearchDialog(
    viewModel: SearchViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var transactionToEdit by remember { mutableStateOf<TransactionWithDetails?>(null) }
    var editTagIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(700.dp).heightIn(max = 600.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Search Transactions")
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Search field
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by payee, memo, category, account, or amount...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    singleLine = true
                )

                // Results count
                Text(
                    text = if (viewModel.searchQuery.isBlank()) {
                        "${uiState.allTransactions.size} total transactions"
                    } else {
                        "${uiState.filteredTransactions.size} results found"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Results list
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.filteredTransactions.isEmpty() && viewModel.searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transactions found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.filteredTransactions,
                            key = { it.transaction.id }
                        ) { txn ->
                            TransactionSearchResultCard(
                                transaction = txn,
                                onClick = {
                                    coroutineScope.launch {
                                        editTagIds = viewModel.getTagsForTransaction(txn.transaction.id)
                                        transactionToEdit = txn
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )

    // Edit transaction dialog
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
private fun TransactionSearchResultCard(
    transaction: TransactionWithDetails,
    onClick: () -> Unit
) {
    val date = transaction.transaction.date

    val amount = transaction.transaction.amount / 100.0
    val isNegative = amount < 0

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.payeeName ?: "No payee",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = transaction.accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = String.format("$%.2f", kotlin.math.abs(amount)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isNegative) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$date • ${transaction.categoryName ?: "Uncategorized"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transaction.transaction.memo != null) {
                    Text(
                        text = transaction.transaction.memo!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
