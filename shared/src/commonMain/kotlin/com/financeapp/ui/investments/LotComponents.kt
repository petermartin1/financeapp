package com.financeapp.ui.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.Holding
import com.financeapp.domain.model.HoldingLot
import com.financeapp.ui.components.forms.DatePickerField
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@Composable
fun ManageLotsDialog(
    holding: Holding,
    lots: List<HoldingLot>,
    onDismiss: () -> Unit,
    onAddLot: (LocalDate, String?, Double, Long, String?) -> Unit,
    onUpdateLot: (HoldingLot, LocalDate, String?, Double, Long, String?) -> Unit,
    onDeleteLot: (Long) -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }
    var editingLot by remember { mutableStateOf<HoldingLot?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lots · ${holding.symbol}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (lots.isEmpty()) {
                    Text(
                        text = "No lots recorded for this holding yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(lots) { lot ->
                            LotRow(
                                lot = lot,
                                onEdit = {
                                    editingLot = lot
                                    showEditor = true
                                },
                                onDelete = { onDeleteLot(lot.id) }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        editingLot = null
                        showEditor = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Lot")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    if (showEditor) {
        val initialDate = editingLot?.acquiredDate?.toLocalDate()
            ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val initialPurpose = editingLot?.purpose ?: ""
        val initialShares = editingLot?.shares?.let { "%.4f".format(it) } ?: ""
        val initialCost = editingLot?.costBasis?.let { "%.2f".format(it / 100.0) } ?: ""
        val initialNotes = editingLot?.notes ?: ""
        LotEditorDialog(
            title = if (editingLot == null) "Add Lot" else "Edit Lot",
            initialDate = initialDate,
            initialPurpose = initialPurpose,
            initialShares = initialShares,
            initialCostBasis = initialCost,
            initialNotes = initialNotes,
            onDismiss = { showEditor = false },
            onConfirm = { date, purpose, shares, costBasis, notes ->
                if (editingLot == null) {
                    onAddLot(date, purpose, shares, costBasis, notes)
                } else {
                    onUpdateLot(editingLot!!, date, purpose, shares, costBasis, notes)
                }
                showEditor = false
            }
        )
    }
}

@Composable
fun LotRow(
    lot: HoldingLot,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formatLotDate(lot.acquiredDate),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = lot.purpose ?: "No purpose",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit lot")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete lot",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Shares", style = MaterialTheme.typography.labelSmall)
                    Text(formatHoldingShares(lot.shares), style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Cost Basis", style = MaterialTheme.typography.labelSmall)
                    Text(formatHoldingCurrency(lot.costBasis), style = MaterialTheme.typography.bodyMedium)
                }
            }
            lot.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LotEditorDialog(
    title: String,
    initialDate: LocalDate,
    initialPurpose: String,
    initialShares: String,
    initialCostBasis: String,
    initialNotes: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, String?, Double, Long, String?) -> Unit
) {
    var date by remember { mutableStateOf(initialDate) }
    var purpose by remember { mutableStateOf(initialPurpose) }
    var shares by remember { mutableStateOf(initialShares) }
    var costBasis by remember { mutableStateOf(initialCostBasis) }
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField(
                    selectedDate = date,
                    onDateSelected = { date = it },
                    label = "Acquired Date",
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose / Lot Label") },
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
                    label = { Text("Cost Basis *") },
                    prefix = { Text("$") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                )
            }
        },
        confirmButton = {
            val isValid = shares.isNotBlank() && costBasis.isNotBlank()
            TextButton(
                onClick = {
                    val shareValue = shares.toDoubleOrNull() ?: 0.0
                    val costValue = ((costBasis.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    onConfirm(
                        date,
                        purpose.ifBlank { null },
                        shareValue,
                        costValue,
                        notes.ifBlank { null }
                    )
                },
                enabled = isValid
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

internal fun formatHoldingCurrency(cents: Long): String {
    val dollars = cents / 100.0
    return "$${String.format("%,.2f", dollars)}"
}

internal fun formatHoldingShares(shares: Double): String =
    String.format("%,.4f", shares)

internal fun formatLotDate(timestamp: Long): String =
    formatLocalDate(timestamp.toLocalDate())

internal fun formatLocalDate(date: LocalDate): String {
    val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$monthName ${date.dayOfMonth}, ${date.year}"
}

internal fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault()).date

internal fun LocalDate.toEpochMillis(): Long =
    this.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
