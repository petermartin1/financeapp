package com.financeapp.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.DetectedSubscription
import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.SubscriptionStatus
import com.financeapp.ui.components.CurrencyText
import com.financeapp.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Subscriptions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::openPayeePicker) { Text("Mark payee") }
                    TextButton(onClick = viewModel::toggleShowDismissed) {
                        Text(if (state.showDismissed) "Hide dismissed" else "Show dismissed")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.subscriptions.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Autorenew,
                    title = "No subscriptions detected yet",
                    message = "Recurring charges will appear here after your next import."
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${state.subscriptions.count { it.status != SubscriptionStatus.DISMISSED }} subscriptions")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("~")
                        CurrencyText(amountCents = state.estimatedMonthlyCents)
                        Text("/mo")
                    }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.subscriptions, key = { it.id }) { sub ->
                        SubscriptionRow(
                            sub = sub,
                            onConfirm = { viewModel.confirm(sub.id) },
                            onDismiss = { viewModel.dismiss(sub.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        // Action bridge: offered after confirming a payee-mapped candidate.
        state.pendingBridge?.let { pending ->
            AlertDialog(
                onDismissRequest = viewModel::skipBridge,
                title = { Text("Track as a scheduled transaction?") },
                text = { Text("Add \"${pending.displayName}\" to your scheduled transactions so it appears in cash-flow forecasts.") },
                confirmButton = { TextButton(onClick = viewModel::addScheduledForPending) { Text("Add") } },
                dismissButton = { TextButton(onClick = viewModel::skipBridge) { Text("Skip") } }
            )
        }

        // Manual escape hatch: pick a payee to mark as a subscription.
        if (state.showPayeePicker) {
            PayeePickerDialog(
                payees = state.payees,
                onPick = viewModel::markPayeeAsSubscription,
                onDismiss = viewModel::closePayeePicker
            )
        }
    }
}

@Composable
private fun PayeePickerDialog(
    payees: List<Payee>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark a payee as a subscription") },
        text = {
            if (payees.isEmpty()) {
                Text("No payees available yet.")
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(payees, key = { it.id }) { payee ->
                        ListItem(
                            headlineContent = { Text(payee.name) },
                            modifier = Modifier.clickable { onPick(payee.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SubscriptionRow(
    sub: DetectedSubscription,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ListItem(
        headlineContent = { Text(sub.displayName + if (!sub.isActive) " (looks cancelled)" else "") },
        supportingContent = {
            Column {
                Text("${sub.cadence.displayName} · next ${sub.nextExpectedDate}")
                if (sub.isVariable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("varies ")
                        CurrencyText(amountCents = sub.minAmountCents)
                        Text("–")
                        CurrencyText(amountCents = sub.maxAmountCents)
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyText(amountCents = sub.medianAmountCents)
                if (sub.scheduledTransactionId != null) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, enabled = false, label = { Text("Tracked") })
                }
                if (sub.status == SubscriptionStatus.CANDIDATE) {
                    TextButton(onClick = onConfirm) { Text("Confirm") }
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    )
}
