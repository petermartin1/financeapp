package com.financeapp.ui.backup

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.ExportFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
    onSaveFile: (content: String, filename: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Export") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Export format selector
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Export Format",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExportFormat.entries.forEach { format ->
                            FilterChip(
                                selected = uiState.selectedFormat == format,
                                onClick = { viewModel.setExportFormat(format) },
                                label = { Text(format.displayName) }
                            )
                        }
                    }
                }
            }

            // Export options
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Export Data",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    ExportButton(
                        text = "Export Transactions",
                        icon = Icons.AutoMirrored.Filled.List,
                        isLoading = uiState.isExporting,
                        onClick = {
                            viewModel.exportTransactions { content, filename ->
                                onSaveFile(content, filename)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ExportButton(
                        text = "Export Accounts",
                        icon = Icons.Default.Home,
                        isLoading = uiState.isExporting,
                        onClick = {
                            viewModel.exportAccounts { content, filename ->
                                onSaveFile(content, filename)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ExportButton(
                        text = "Export Categories",
                        icon = Icons.Default.Settings,
                        isLoading = uiState.isExporting,
                        onClick = {
                            viewModel.exportCategories { content, filename ->
                                onSaveFile(content, filename)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ExportButton(
                        text = "Export Budgets",
                        icon = Icons.Default.Star,
                        isLoading = uiState.isExporting,
                        onClick = {
                            viewModel.exportBudgets { content, filename ->
                                onSaveFile(content, filename)
                            }
                        }
                    )
                }
            }

            // Result message
            uiState.lastResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (result.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (result.success)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            result.message,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearResult() }) {
                            Icon(Icons.Default.Close, "Dismiss")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Info text
            Text(
                "Exported files will be saved to your chosen location. " +
                "Use these exports to backup your data or import into other applications.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExportButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
        if (isLoading) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        }
    }
}
