package com.financeapp.ui.fileimport

import com.financeapp.data.fileimport.CsvImportConfig
import com.financeapp.data.fileimport.CsvPresets
import com.financeapp.data.fileimport.DateFormat
import com.financeapp.data.fileimport.ImportedTransaction
import com.financeapp.data.fileimport.ImportRepository
import com.financeapp.data.fileimport.ImportSummary
import com.financeapp.domain.model.Account
import com.financeapp.domain.repository.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImportViewModel(
    private val importRepository: ImportRepository,
    private val accountRepository: AccountRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        scope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(accounts = accounts)
            }
        }
    }

    fun selectAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
    }

    fun selectFormat(format: ImportFormat) {
        _uiState.value = _uiState.value.copy(selectedFormat = format)
    }

    fun updateCsvConfig(config: CsvImportConfig) {
        _uiState.value = _uiState.value.copy(csvConfig = config)
    }

    // Preview file before importing
    fun previewFile(content: String) {
        val state = _uiState.value
        _uiState.value = state.copy(isImporting = true, error = null, fileContent = content)

        scope.launch {
            val result = when (state.selectedFormat) {
                ImportFormat.OFX_QFX -> importRepository.previewOfxFile(content)
                ImportFormat.QIF -> importRepository.previewQifFile(content)
                ImportFormat.CSV_CHASE -> importRepository.previewCsvFile(content, CsvPresets.CHASE_CREDIT)
                ImportFormat.CSV_CITI -> importRepository.previewCsvFile(content, CsvPresets.CITI_CREDIT)
                ImportFormat.CSV_GENERIC -> importRepository.previewCsvFile(content, CsvPresets.GENERIC)
                ImportFormat.CSV_CUSTOM -> importRepository.previewCsvFile(content, state.csvConfig)
            }

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    previewTransactions = result.getOrThrow()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    error = result.exceptionOrNull()?.message ?: "Preview failed"
                )
            }
        }
    }

    // Re-preview with updated CSV config
    fun refreshPreview() {
        val content = _uiState.value.fileContent ?: return
        previewFile(content)
    }

    // Import previewed transactions
    fun confirmImport() {
        val state = _uiState.value
        val accountId = state.selectedAccountId ?: return
        val transactions = state.previewTransactions

        if (transactions.isEmpty()) return

        _uiState.value = state.copy(isImporting = true, error = null)

        scope.launch {
            val result = importRepository.importPreviewedTransactions(transactions, accountId)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    lastImportSummary = result.getOrNull(),
                    previewTransactions = emptyList(),
                    fileContent = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    error = result.exceptionOrNull()?.message ?: "Import failed"
                )
            }
        }
    }

    // Legacy direct import (skip preview)
    fun importFile(content: String) {
        val state = _uiState.value
        val accountId = state.selectedAccountId ?: return

        _uiState.value = state.copy(isImporting = true, error = null)

        scope.launch {
            val result = when (state.selectedFormat) {
                ImportFormat.OFX_QFX -> importRepository.importOfxFile(content, accountId)
                ImportFormat.QIF -> importRepository.importQifFile(content, accountId)
                ImportFormat.CSV_CHASE -> importRepository.importCsvFile(content, accountId, CsvPresets.CHASE_CREDIT)
                ImportFormat.CSV_CITI -> importRepository.importCsvFile(content, accountId, CsvPresets.CITI_CREDIT)
                ImportFormat.CSV_GENERIC -> importRepository.importCsvFile(content, accountId, CsvPresets.GENERIC)
                ImportFormat.CSV_CUSTOM -> importRepository.importCsvFile(content, accountId, state.csvConfig)
            }

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    lastImportSummary = result.getOrNull()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    error = result.exceptionOrNull()?.message ?: "Import failed"
                )
            }
        }
    }

    fun cancelPreview() {
        _uiState.value = _uiState.value.copy(
            previewTransactions = emptyList(),
            fileContent = null
        )
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(
            lastImportSummary = null,
            error = null
        )
    }
}

data class ImportUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedFormat: ImportFormat = ImportFormat.OFX_QFX,
    val isImporting: Boolean = false,
    val lastImportSummary: ImportSummary? = null,
    val error: String? = null,
    val previewTransactions: List<ImportedTransaction> = emptyList(),
    val fileContent: String? = null,
    val csvConfig: CsvImportConfig = CsvImportConfig(
        headerRows = 1,
        dateColumn = 0,
        amountColumn = 1,
        descriptionColumn = 2,
        memoColumn = null,
        dateFormat = DateFormat.MM_DD_YYYY,
        invertAmount = false
    )
)

enum class ImportFormat(val displayName: String) {
    OFX_QFX("OFX/QFX (Standard)"),
    QIF("QIF (Quicken)"),
    CSV_CHASE("CSV (Chase Credit Card)"),
    CSV_CITI("CSV (Citibank Credit Card)"),
    CSV_GENERIC("CSV (Generic)"),
    CSV_CUSTOM("CSV (Custom Mapping)")
}
