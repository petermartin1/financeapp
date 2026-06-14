package com.financeapp.ui.fileimport

import java.util.Locale

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.financeapp.data.fileimport.CsvImportConfig
import com.financeapp.data.fileimport.DateFormat
import com.financeapp.data.fileimport.ImportedTransaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onBack: () -> Unit,
    onPickFile: ((String) -> Unit) -> Unit  // Platform-specific file picker
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Transactions") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.previewTransactions.isNotEmpty()) {
                            viewModel.cancelPreview()
                        } else {
                            onBack()
                        }
                    }) {
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Account Selection
            Text("Select Account", style = MaterialTheme.typography.titleMedium)

            var accountExpanded by remember { mutableStateOf(false) }
            val selectedAccount = uiState.accounts.find { it.id == uiState.selectedAccountId }

            ExposedDropdownMenuBox(
                expanded = accountExpanded,
                onExpandedChange = { accountExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedAccount?.name ?: "Select an account",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = accountExpanded,
                    onDismissRequest = { accountExpanded = false }
                ) {
                    uiState.accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            onClick = {
                                viewModel.selectAccount(account.id)
                                accountExpanded = false
                            }
                        )
                    }
                }
            }

            // Format Selection
            Text("File Format", style = MaterialTheme.typography.titleMedium)

            var formatExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = formatExpanded,
                onExpandedChange = { formatExpanded = it }
            ) {
                OutlinedTextField(
                    value = uiState.selectedFormat.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = formatExpanded,
                    onDismissRequest = { formatExpanded = false }
                ) {
                    ImportFormat.entries.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format.displayName) },
                            onClick = {
                                viewModel.selectFormat(format)
                                formatExpanded = false
                            }
                        )
                    }
                }
            }

            // CSV Custom Mapping Options
            if (uiState.selectedFormat == ImportFormat.CSV_CUSTOM) {
                CsvMappingOptions(
                    config = uiState.csvConfig,
                    onConfigChange = { viewModel.updateCsvConfig(it) }
                )
            }

            // Show preview or import button
            if (uiState.previewTransactions.isNotEmpty()) {
                // Preview mode
                Text("Preview (${uiState.previewTransactions.size} transactions)", style = MaterialTheme.typography.titleMedium)

                Card(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                        items(uiState.previewTransactions) { txn ->
                            TransactionPreviewItem(txn)
                            HorizontalDivider()
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.cancelPreview() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { viewModel.confirmImport() },
                        enabled = !uiState.isImporting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Import ${uiState.previewTransactions.size}")
                        }
                    }
                }

                // Refresh preview button for CSV custom
                if (uiState.selectedFormat == ImportFormat.CSV_CUSTOM) {
                    TextButton(
                        onClick = { viewModel.refreshPreview() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh Preview with New Settings")
                    }
                }
            } else {
                // Import Button
                Button(
                    onClick = {
                        onPickFile { content ->
                            viewModel.previewFile(content)
                        }
                    },
                    enabled = uiState.selectedAccountId != null && !uiState.isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Select File to Preview")
                    }
                }
            }

            // Results
            uiState.lastImportSummary?.let { summary ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                "Import Complete",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "${summary.imported} imported, ${summary.duplicates} duplicates skipped",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (summary.errors > 0) {
                                Text(
                                    "${summary.errors} errors",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Error
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Help Text
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Supported formats:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "• OFX/QFX - Standard bank export format\n" +
                        "• QIF - Quicken Interchange Format\n" +
                        "• CSV - With preset or custom column mapping",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Payee Mapping Dialog
        when (uiState.payeeMappingStep) {
            PayeeMappingStep.Analyzing -> {
                // Show loading indicator
                Dialog(
                    onDismissRequest = { viewModel.cancelMapping() }
                ) {
                    Card {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Analyzing payee names...")
                        }
                    }
                }
            }
            PayeeMappingStep.Reviewing -> {
                // Show payee mapping dialog
                PayeeMappingDialog(
                    unresolvedPayees = uiState.unresolvedPayees,
                    currentIndex = uiState.currentPayeeIndex,
                    allPayees = uiState.allPayees,
                    allCategories = uiState.allCategories,
                    allTags = uiState.allTags,
                    similarRecentlyCreated = uiState.similarRecentlyCreated,
                    onMapToExisting = { payeeId, categoryId, tagIds, remember ->
                        viewModel.mapToExistingPayee(payeeId, categoryId, tagIds, remember)
                    },
                    onCreateNew = { name, categoryId, tagIds, remember ->
                        viewModel.createNewPayee(name, categoryId, tagIds, remember)
                    },
                    onNext = { viewModel.nextPayee() },
                    onPrevious = { viewModel.previousPayee() },
                    onSkip = { viewModel.skipPayee() },
                    onSkipAll = { viewModel.skipAllPayees() },
                    onDismiss = { viewModel.cancelMapping() }
                )
            }
            PayeeMappingStep.Importing -> {
                // Show importing progress
                Dialog(
                    onDismissRequest = { }
                ) {
                    Card {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Importing transactions...")
                        }
                    }
                }
            }
            PayeeMappingStep.None -> {
                // No dialog
            }
        }
    }
}

@Composable
private fun TransactionPreviewItem(txn: ImportedTransaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                txn.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                txn.date.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatCurrency(txn.amount),
            style = MaterialTheme.typography.bodyMedium,
            color = if (txn.amount >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CsvMappingOptions(
    config: CsvImportConfig,
    onConfigChange: (CsvImportConfig) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("CSV Column Mapping", style = MaterialTheme.typography.titleSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config.dateColumn.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { col ->
                            onConfigChange(config.copy(dateColumn = col))
                        }
                    },
                    label = { Text("Date Col") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = config.amountColumn.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { col ->
                            onConfigChange(config.copy(amountColumn = col))
                        }
                    },
                    label = { Text("Amount Col") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config.descriptionColumn.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { col ->
                            onConfigChange(config.copy(descriptionColumn = col))
                        }
                    },
                    label = { Text("Desc Col") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = config.headerRows.toString(),
                    onValueChange = {
                        it.toIntOrNull()?.let { rows ->
                            onConfigChange(config.copy(headerRows = rows))
                        }
                    },
                    label = { Text("Header Rows") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Date format
            var dateFormatExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = dateFormatExpanded,
                onExpandedChange = { dateFormatExpanded = it }
            ) {
                OutlinedTextField(
                    value = when (config.dateFormat) {
                        DateFormat.MM_DD_YYYY -> "MM/DD/YYYY"
                        DateFormat.DD_MM_YYYY -> "DD/MM/YYYY"
                        DateFormat.YYYY_MM_DD -> "YYYY-MM-DD"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date Format") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dateFormatExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = dateFormatExpanded,
                    onDismissRequest = { dateFormatExpanded = false }
                ) {
                    DateFormat.entries.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(when (format) {
                                DateFormat.MM_DD_YYYY -> "MM/DD/YYYY"
                                DateFormat.DD_MM_YYYY -> "DD/MM/YYYY"
                                DateFormat.YYYY_MM_DD -> "YYYY-MM-DD"
                            }) },
                            onClick = {
                                onConfigChange(config.copy(dateFormat = format))
                                dateFormatExpanded = false
                            }
                        )
                    }
                }
            }

            // Invert amount checkbox
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = config.invertAmount,
                    onCheckedChange = { onConfigChange(config.copy(invertAmount = it)) }
                )
                Text("Invert amounts (for banks that show debits as positive)")
            }
        }
    }
}

private fun formatCurrency(cents: Long): String {
    val absCents = kotlin.math.abs(cents)
    val wholeDollars = absCents / 100
    val centsPart = absCents % 100
    val sign = if (cents < 0) "-" else ""
    return "$sign$${String.format(Locale.ROOT, "%,d", wholeDollars)}.${centsPart.toString().padStart(2, '0')}"
}
