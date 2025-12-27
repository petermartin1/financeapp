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

    fun exportTransactions(onComplete: (String, String) -> Unit) {
        val state = _uiState.value
        _uiState.value = state.copy(isExporting = true, lastResult = null)

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val options = ExportOptions(
                        format = state.selectedFormat,
                        accountId = null
                    )
                    val (content, count) = exportRepository.exportTransactions(options)

                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        lastResult = BackupResult(
                            success = true,
                            message = "Exported $count transactions",
                            recordCount = count
                        )
                    )

                    val filename = "transactions.${state.selectedFormat.extension}"
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

    fun exportAccounts(onComplete: (String, String) -> Unit) {
        _uiState.value = _uiState.value.copy(isExporting = true, lastResult = null)

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val (content, count) = exportRepository.exportAccounts()

                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        lastResult = BackupResult(
                            success = true,
                            message = "Exported $count accounts",
                            recordCount = count
                        )
                    )

                    onComplete(content, "accounts.csv")
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

    fun exportCategories(onComplete: (String, String) -> Unit) {
        _uiState.value = _uiState.value.copy(isExporting = true, lastResult = null)

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val (content, count) = exportRepository.exportCategories()

                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        lastResult = BackupResult(
                            success = true,
                            message = "Exported $count categories",
                            recordCount = count
                        )
                    )

                    onComplete(content, "categories.csv")
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

    fun exportBudgets(onComplete: (String, String) -> Unit) {
        _uiState.value = _uiState.value.copy(isExporting = true, lastResult = null)

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val (content, count) = exportRepository.exportBudgets()

                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        lastResult = BackupResult(
                            success = true,
                            message = "Exported $count budget entries",
                            recordCount = count
                        )
                    )

                    onComplete(content, "budgets.csv")
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

    fun clearResult() {
        _uiState.value = _uiState.value.copy(lastResult = null)
    }
}

data class BackupUiState(
    val selectedFormat: ExportFormat = ExportFormat.CSV,
    val isExporting: Boolean = false,
    val lastResult: BackupResult? = null
)
