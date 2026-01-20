package com.financeapp.ui.backup

import com.financeapp.data.backup.ExportRepository
import com.financeapp.domain.model.BackupResult
import com.financeapp.domain.model.ExportFormat
import com.financeapp.domain.model.ExportOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel(
    private val exportRepository: ExportRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _exportContent = MutableStateFlow<String?>(null)
    val exportContent: StateFlow<String?> = _exportContent.asStateFlow()

    fun setExportFormat(format: ExportFormat) {
        _uiState.value = _uiState.value.copy(selectedFormat = format)
    }

    private fun performExport(
        itemName: String,
        filename: String,
        exportAction: () -> Pair<String, Int>,
        onComplete: (String, String) -> Unit
    ) {
        _uiState.value = _uiState.value.copy(isExporting = true, lastResult = null)

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val (content, count) = exportAction()

                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        lastResult = BackupResult(
                            success = true,
                            message = "Exported $count $itemName",
                            recordCount = count
                        )
                    )

                    onComplete(content, filename)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        lastResult = BackupResult(
                            success = false,
                            message = "Export failed: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun exportTransactions(onComplete: (String, String) -> Unit) {
        val state = _uiState.value
        val options = ExportOptions(format = state.selectedFormat, accountId = null)
        performExport(
            itemName = "transactions",
            filename = "transactions.${state.selectedFormat.extension}",
            exportAction = { exportRepository.exportTransactions(options) },
            onComplete = onComplete
        )
    }

    fun exportAccounts(onComplete: (String, String) -> Unit) {
        performExport(
            itemName = "accounts",
            filename = "accounts.csv",
            exportAction = { exportRepository.exportAccounts() },
            onComplete = onComplete
        )
    }

    fun exportCategories(onComplete: (String, String) -> Unit) {
        performExport(
            itemName = "categories",
            filename = "categories.csv",
            exportAction = { exportRepository.exportCategories() },
            onComplete = onComplete
        )
    }

    fun exportBudgets(onComplete: (String, String) -> Unit) {
        performExport(
            itemName = "budget entries",
            filename = "budgets.csv",
            exportAction = { exportRepository.exportBudgets() },
            onComplete = onComplete
        )
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(lastResult = null)
    }
}

data class BackupUiState(
    val selectedFormat: ExportFormat = ExportFormat.CSV,
    val isExporting: Boolean = false,
    val lastResult: BackupResult? = null
)
