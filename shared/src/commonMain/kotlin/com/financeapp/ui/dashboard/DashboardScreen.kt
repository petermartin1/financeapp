package com.financeapp.ui.dashboard

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
import com.financeapp.domain.model.DashboardWidgetType
import com.financeapp.domain.model.AccountWithBalance
import com.financeapp.domain.model.TransactionWithDetails
import com.financeapp.ui.animations.AnimatedCurrencyLarge
import com.financeapp.ui.animations.AnimatedCurrency
import com.financeapp.ui.animations.StaggeredListItem
import com.financeapp.ui.animations.pressAnimation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAccountClick: (Long) -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
    }

    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val enabledWidgets = uiState.dashboardConfig.widgets
                .filter { it.enabled }
                .sortedBy { it.order }

            items(enabledWidgets) { widget ->
                when (widget.type) {
                    DashboardWidgetType.NET_WORTH -> {
                        NetWorthWidget(totalBalance = uiState.totalBalance)
                    }
                    DashboardWidgetType.ACCOUNTS_SUMMARY -> {
                        AccountsSummaryWidget(
                            accounts = uiState.accounts,
                            onAccountClick = onAccountClick
                        )
                    }
                    DashboardWidgetType.RECENT_TRANSACTIONS -> {
                        RecentTransactionsWidget(
                            transactions = uiState.recentTransactions
                        )
                    }
                    DashboardWidgetType.BUDGET_PROGRESS -> {
                        BudgetProgressWidget(
                            spent = uiState.monthlyBudgetSpent,
                            total = uiState.monthlyBudgetTotal
                        )
                    }
                    DashboardWidgetType.SPENDING_BY_CATEGORY -> {
                        SpendingByCategoryWidget(
                            spending = uiState.spendingByCategory
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetWorthWidget(totalBalance: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Net Worth",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            AnimatedCurrencyLarge(
                amountCents = totalBalance,
                modifier = Modifier,
                showSign = false
            )
        }
    }
}

@Composable
private fun AccountsSummaryWidget(
    accounts: List<AccountWithBalance>,
    onAccountClick: (Long) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Accounts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            accounts.take(5).forEachIndexed { index, account ->
                StaggeredListItem(
                    index = index,
                    staggerDelayMillis = 30,
                    itemDurationMillis = 250
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAccountClick(account.account.id) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = account.account.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        AnimatedCurrency(
                            amountCents = account.balance,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (account.balance >= 0)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            if (accounts.size > 5) {
                Text(
                    text = "+${accounts.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecentTransactionsWidget(transactions: List<TransactionWithDetails>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (transactions.isEmpty()) {
                Text(
                    text = "No recent transactions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                transactions.forEachIndexed { index, txn ->
                    StaggeredListItem(
                        index = index,
                        staggerDelayMillis = 30,
                        itemDurationMillis = 250
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = txn.payeeName ?: "Unknown",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = txn.categoryName ?: "Uncategorized",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedCurrency(
                                amountCents = txn.transaction.amount,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (txn.transaction.amount >= 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetProgressWidget(spent: Long, total: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly Budget",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (total > 0) {
                val progress = (spent.toFloat() / total).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (progress > 0.9f) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatCurrency(spent)} of ${formatCurrency(total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No budget set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SpendingByCategoryWidget(spending: Map<String, Long>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Spending This Month",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (spending.isEmpty()) {
                Text(
                    text = "No spending this month",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                spending.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .forEach { (category, amount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatCurrency(amount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardCustomizeDialog(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Dashboard") },
        text = {
            Column {
                uiState.dashboardConfig.widgets.forEach { widget ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = widget.enabled,
                            onCheckedChange = { viewModel.toggleWidget(widget.id, it) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (widget.type) {
                                DashboardWidgetType.NET_WORTH -> "Net Worth"
                                DashboardWidgetType.ACCOUNTS_SUMMARY -> "Accounts Summary"
                                DashboardWidgetType.RECENT_TRANSACTIONS -> "Recent Transactions"
                                DashboardWidgetType.BUDGET_PROGRESS -> "Budget Progress"
                                DashboardWidgetType.SPENDING_BY_CATEGORY -> "Spending by Category"
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.resetToDefaults()
            }) {
                Text("Reset")
            }
        }
    )
}

private fun formatCurrency(cents: Long): String {
    val absCents = kotlin.math.abs(cents)
    val wholeDollars = absCents / 100
    val centsPart = absCents % 100
    val sign = if (cents < 0) "-" else ""
    return "$sign$$wholeDollars.${centsPart.toString().padStart(2, '0')}"
}
