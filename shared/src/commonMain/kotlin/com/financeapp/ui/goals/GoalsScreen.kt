package com.financeapp.ui.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.Account
import com.financeapp.domain.model.GoalWithProgress
import com.financeapp.ui.components.CurrencyText
import com.financeapp.ui.components.formatCurrency
import com.financeapp.ui.components.forms.DatePickerField
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<GoalWithProgress?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GoalWithProgress?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Savings Goals", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { viewModel.toggleShowArchived() }) {
                Text(if (uiState.showArchived) "Hide archived" else "Show archived")
            }
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add Goal")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.goals.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text("No savings goals yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Set a target amount for an account and track your progress",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.goals, key = { it.goal.id }) { item ->
                    GoalCard(
                        item = item,
                        onEdit = { editing = item },
                        onArchiveToggle = { viewModel.setArchived(item.goal.id, !item.goal.archived) },
                        onDelete = { deleting = item }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        GoalEditorDialog(
            title = "Add Goal",
            initial = null,
            accounts = uiState.accounts,
            onDismiss = { showAddDialog = false },
            onSave = { name, targetCents, accountId, deadlineMs ->
                viewModel.createGoal(name, targetCents, accountId!!, deadlineMs)
                showAddDialog = false
            }
        )
    }

    editing?.let { item ->
        GoalEditorDialog(
            title = "Edit Goal",
            initial = item,
            accounts = uiState.accounts,
            onDismiss = { editing = null },
            onSave = { name, targetCents, accountId, deadlineMs ->
                viewModel.updateGoal(item.goal.id, name, targetCents, accountId, deadlineMs)
                editing = null
            }
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete goal?") },
            text = { Text("\"${item.goal.name}\" will be removed. The linked account and its transactions are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGoal(item.goal.id)
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GoalCard(
    item: GoalWithProgress,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val goal = item.goal
    val progress = item.progress
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progress.isComplete) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.accountName ?: "Needs an account — edit to relink",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.accountName == null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (goal.archived) {
                    AssistChip(onClick = {}, enabled = false, label = { Text("Archived") })
                    Spacer(Modifier.width(8.dp))
                } else if (progress.onTrack != null && !progress.isComplete) {
                    val (label, color) =
                        if (progress.onTrack == true) "On track" to MaterialTheme.colorScheme.primary
                        else "Behind" to MaterialTheme.colorScheme.error
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(label, color = color) }
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit goal") }
                TextButton(onClick = onArchiveToggle) { Text(if (goal.archived) "Unarchive" else "Archive") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete goal", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyText(amountCents = progress.currentCents, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    " of ${formatCurrency(goal.targetAmountCents)}  ·  ${progress.percent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                val deadlinePart = goal.deadlineMs?.let { "by ${formatDeadline(it)}" }
                val neededPart = progress.neededPerMonthCents
                    ?.takeIf { it > 0 }
                    ?.let { "need ${formatCurrency(it)}/mo" }
                val summary = listOfNotNull(deadlinePart, neededPart).joinToString("  ·  ")
                if (summary.isNotEmpty()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditorDialog(
    title: String,
    initial: GoalWithProgress?,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (name: String, targetCents: Long, accountId: Long?, deadlineMs: Long?) -> Unit
) {
    val tz = remember { TimeZone.currentSystemDefault() }
    var name by remember { mutableStateOf(initial?.goal?.name ?: "") }
    var amountText by remember {
        mutableStateOf(initial?.goal?.targetAmountCents?.let { (it / 100.0).toString() } ?: "")
    }
    var accountId by remember { mutableStateOf(initial?.goal?.accountId) }
    var hasDeadline by remember { mutableStateOf(initial?.goal?.deadlineMs != null) }
    var deadlineDate by remember {
        mutableStateOf(
            initial?.goal?.deadlineMs
                ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date }
                ?: Clock.System.now().toLocalDateTime(tz).date
        )
    }
    var accountMenuOpen by remember { mutableStateOf(false) }

    val targetCents = GoalsViewModel.parseDollarsToCents(amountText)
    val nameValid = name.isNotBlank()
    val amountValid = targetCents != null
    val accountValid = accountId != null
    val canSave = nameValid && amountValid && accountValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = !nameValid && name.isNotEmpty(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Target amount") },
                    prefix = { Text("$") },
                    isError = !amountValid && amountText.isNotEmpty(),
                    supportingText = {
                        if (!amountValid && amountText.isNotEmpty()) Text("Enter a positive dollar amount")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = accountMenuOpen,
                    onExpandedChange = { accountMenuOpen = it }
                ) {
                    OutlinedTextField(
                        value = accounts.firstOrNull { it.id == accountId }?.name
                            ?: if (initial != null && initial.goal.accountId != null) "(deleted account)" else "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        isError = !accountValid,
                        supportingText = { if (!accountValid) Text("Pick the account this goal tracks") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = accountMenuOpen,
                        onDismissRequest = { accountMenuOpen = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${account.type.name.lowercase().replace('_', ' ')})") },
                                onClick = {
                                    accountId = account.id
                                    accountMenuOpen = false
                                }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasDeadline, onCheckedChange = { hasDeadline = it })
                    Text("Target date")
                }
                if (hasDeadline) {
                    DatePickerField(
                        selectedDate = deadlineDate,
                        onDateSelected = { deadlineDate = it },
                        label = "Deadline"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val deadlineMs =
                        if (hasDeadline) deadlineDate.atStartOfDayIn(tz).toEpochMilliseconds() else null
                    onSave(name.trim(), targetCents!!, accountId, deadlineMs)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatDeadline(ms: Long): String {
    val date = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${date.dayOfMonth}, ${date.year}"
}
