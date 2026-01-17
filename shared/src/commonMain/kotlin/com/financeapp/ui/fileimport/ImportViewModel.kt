package com.financeapp.ui.fileimport

import com.financeapp.data.fileimport.CsvImportConfig
import com.financeapp.data.fileimport.CsvPresets
import com.financeapp.data.fileimport.DateFormat
import com.financeapp.data.fileimport.ImportedTransaction
import com.financeapp.data.fileimport.ImportRepository
import com.financeapp.data.fileimport.ImportSummary
import com.financeapp.domain.model.Account
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.PayeeMapping
import com.financeapp.domain.model.Tag
import com.financeapp.domain.model.UnresolvedPayee
import com.financeapp.domain.matching.PayeeMatcher
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.CategoryRepository
import com.financeapp.domain.repository.PayeeRepository
import com.financeapp.domain.repository.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImportViewModel(
    private val importRepository: ImportRepository,
    private val accountRepository: AccountRepository,
    private val payeeRepository: PayeeRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val payeeMatcher: PayeeMatcher
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
        loadPayees()
        loadCategories()
        loadTags()
    }

    private fun loadAccounts() {
        scope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(accounts = accounts)
            }
        }
    }

    private fun loadPayees() {
        scope.launch {
            payeeRepository.getAllPayees().collect { payees ->
                _uiState.value = _uiState.value.copy(allPayees = payees)
            }
        }
    }

    private fun loadCategories() {
        scope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.value = _uiState.value.copy(allCategories = categories)
            }
        }
    }

    private fun loadTags() {
        scope.launch {
            tagRepository.getAllTags().collect { tags ->
                _uiState.value = _uiState.value.copy(allTags = tags)
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

    // Import previewed transactions - initiates payee mapping flow
    fun confirmImport() {
        startPayeeMapping()
    }

    // Start payee mapping flow
    fun startPayeeMapping() {
        val state = _uiState.value
        val transactions = state.previewTransactions

        if (transactions.isEmpty()) return

        _uiState.value = state.copy(
            payeeMappingStep = PayeeMappingStep.Analyzing,
            error = null
        )

        scope.launch {
            val result = importRepository.analyzeImportPayees(transactions)

            if (result.isSuccess) {
                val resolutionResult = result.getOrThrow()

                if (resolutionResult.needsReview.isEmpty()) {
                    // No payees need review - import directly with auto-resolved mappings
                    // Look up each payee's default category to apply it automatically
                    val payeesById = _uiState.value.allPayees.associateBy { it.id }
                    val autoMappings = resolutionResult.autoResolved.map { (name, payeeId) ->
                        val payee = payeesById[payeeId]
                        name to PayeeMapping(
                            importedName = name,
                            resolvedPayeeId = payeeId,
                            createNew = false,
                            categoryId = payee?.defaultCategoryId,
                            applyCategory = payee?.defaultCategoryId != null,
                            rememberMapping = false
                        )
                    }.toMap()

                    importWithMappings(autoMappings)
                } else {
                    // Has payees needing review - show mapping dialog
                    // Already sorted alphabetically in repository
                    // Look up each auto-resolved payee's default category to apply it automatically
                    val payeesById = _uiState.value.allPayees.associateBy { it.id }
                    _uiState.value = _uiState.value.copy(
                        payeeMappingStep = PayeeMappingStep.Reviewing,
                        unresolvedPayees = resolutionResult.needsReview,
                        currentPayeeIndex = 0,
                        payeeMappings = resolutionResult.autoResolved.map { (name, payeeId) ->
                            val payee = payeesById[payeeId]
                            name to PayeeMapping(
                                importedName = name,
                                resolvedPayeeId = payeeId,
                                createNew = false,
                                categoryId = payee?.defaultCategoryId,
                                applyCategory = payee?.defaultCategoryId != null,
                                rememberMapping = false
                            )
                        }.toMap().toMutableMap(),
                        recentlyCreatedPayees = emptyList(), // Reset for new import session
                        similarRecentlyCreated = emptyList() // Will be empty for first payee
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    payeeMappingStep = PayeeMappingStep.None,
                    error = result.exceptionOrNull()?.message ?: "Payee analysis failed"
                )
            }
        }
    }

    // Navigation methods
    fun nextPayee() {
        val state = _uiState.value
        if (state.currentPayeeIndex < state.unresolvedPayees.size - 1) {
            val newIndex = state.currentPayeeIndex + 1
            _uiState.value = state.copy(
                currentPayeeIndex = newIndex,
                similarRecentlyCreated = computeSimilarRecentlyCreated(newIndex)
            )
        } else {
            // Last payee - proceed to import
            confirmMappingsAndImport()
        }
    }

    fun previousPayee() {
        val state = _uiState.value
        if (state.currentPayeeIndex > 0) {
            val newIndex = state.currentPayeeIndex - 1
            _uiState.value = state.copy(
                currentPayeeIndex = newIndex,
                similarRecentlyCreated = computeSimilarRecentlyCreated(newIndex)
            )
        }
    }

    /**
     * Compute similar recently created payees for the payee at the given index
     * Uses the proper PayeeMatcher algorithm for consistent matching
     */
    private fun computeSimilarRecentlyCreated(payeeIndex: Int): List<Payee> {
        val state = _uiState.value
        val currentPayee = state.unresolvedPayees.getOrNull(payeeIndex) ?: return emptyList()

        if (state.recentlyCreatedPayees.isEmpty()) {
            return emptyList()
        }

        // Use PayeeMatcher to find similar recently created payees
        // This uses the same Jaro-Winkler + token matching logic as database matches
        val matches = payeeMatcher.findSimilarPayees(
            importedName = currentPayee.importedName,
            existingPayees = state.recentlyCreatedPayees,
            threshold = 0.75
        )

        return matches.map { it.payee }
    }

    fun skipPayee() {
        // Skip current payee - use original name
        val state = _uiState.value
        val currentPayee = state.unresolvedPayees.getOrNull(state.currentPayeeIndex) ?: return

        val updatedMappings = state.payeeMappings.toMutableMap()

        // Create mapping to keep its original name
        updatedMappings[currentPayee.importedName] = PayeeMapping(
            importedName = currentPayee.importedName,
            resolvedPayeeId = null,
            createNew = true,
            newPayeeName = currentPayee.importedName,
            rememberMapping = false
        )

        _uiState.value = state.copy(payeeMappings = updatedMappings)
        nextPayee()
    }

    fun skipAllPayees() {
        // Skip all remaining unresolved payees - use original names
        val state = _uiState.value
        val updatedMappings = state.payeeMappings.toMutableMap()

        for (payee in state.unresolvedPayees) {
            if (payee.importedName !in updatedMappings) {
                updatedMappings[payee.importedName] = PayeeMapping(
                    importedName = payee.importedName,
                    resolvedPayeeId = null,
                    createNew = true,
                    newPayeeName = payee.importedName,
                    rememberMapping = false
                )
            }
        }

        _uiState.value = state.copy(payeeMappings = updatedMappings)
        confirmMappingsAndImport()
    }

    // Resolution methods
    fun mapToExistingPayee(payeeId: Long, categoryId: Long?, tagIds: List<Long>, remember: Boolean) {
        val state = _uiState.value
        val currentPayee = state.unresolvedPayees.getOrNull(state.currentPayeeIndex) ?: return

        val updatedMappings = state.payeeMappings.toMutableMap()

        // Check if this is a recently created payee (negative temp ID)
        if (payeeId < 0) {
            // This is a recently created payee - find its name and create a new mapping to that name
            val recentPayee = state.recentlyCreatedPayees.find { it.id == payeeId }
            if (recentPayee != null) {
                // Use provided category, or fall back to the recently created payee's default category
                val effectiveCategoryId = categoryId ?: recentPayee.defaultCategoryId
                // Map to the same name as the recently created payee
                updatedMappings[currentPayee.importedName] = PayeeMapping(
                    importedName = currentPayee.importedName,
                    resolvedPayeeId = null,
                    createNew = true,
                    newPayeeName = recentPayee.name,
                    categoryId = effectiveCategoryId,
                    tagIds = tagIds,
                    applyCategory = effectiveCategoryId != null,
                    rememberMapping = remember
                )
            }
        } else {
            // Regular existing payee from database
            // Use provided category, or fall back to the payee's default category
            val existingPayee = state.allPayees.find { it.id == payeeId }
            val effectiveCategoryId = categoryId ?: existingPayee?.defaultCategoryId
            updatedMappings[currentPayee.importedName] = PayeeMapping(
                importedName = currentPayee.importedName,
                resolvedPayeeId = payeeId,
                createNew = false,
                categoryId = effectiveCategoryId,
                tagIds = tagIds,
                applyCategory = effectiveCategoryId != null,
                rememberMapping = remember
            )
        }

        _uiState.value = state.copy(payeeMappings = updatedMappings)
    }

    fun createNewPayee(name: String, categoryId: Long?, tagIds: List<Long>, remember: Boolean) {
        val state = _uiState.value
        val currentPayee = state.unresolvedPayees.getOrNull(state.currentPayeeIndex) ?: return


        val updatedMappings = state.payeeMappings.toMutableMap()

        // Create mapping for this payee name
        updatedMappings[currentPayee.importedName] = PayeeMapping(
            importedName = currentPayee.importedName,
            resolvedPayeeId = null,
            createNew = true,
            newPayeeName = name,
            categoryId = categoryId,
            tagIds = tagIds,
            applyCategory = categoryId != null,
            rememberMapping = remember
        )

        // Track this as a "recently created" payee (for suggesting to future payees)
        // Use a temporary ID (negative to distinguish from real IDs)
        val tempId = -(state.recentlyCreatedPayees.size + 1).toLong()
        val newPayee = Payee(
            id = tempId,
            name = name,
            defaultCategoryId = categoryId
        )


        _uiState.value = state.copy(
            payeeMappings = updatedMappings,
            recentlyCreatedPayees = state.recentlyCreatedPayees + newPayee
        )
    }

    // Final import with all mappings
    fun confirmMappingsAndImport() {
        val state = _uiState.value
        importWithMappings(state.payeeMappings)
    }

    private fun importWithMappings(mappings: Map<String, PayeeMapping>) {
        val state = _uiState.value
        val accountId = state.selectedAccountId ?: return
        val transactions = state.previewTransactions

        _uiState.value = state.copy(
            payeeMappingStep = PayeeMappingStep.Importing,
            error = null
        )

        scope.launch {
            val result = importRepository.importWithMappings(transactions, accountId, mappings)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    payeeMappingStep = PayeeMappingStep.None,
                    isImporting = false,
                    lastImportSummary = result.getOrNull(),
                    previewTransactions = emptyList(),
                    fileContent = null,
                    unresolvedPayees = emptyList(),
                    currentPayeeIndex = 0,
                    payeeMappings = emptyMap(),
                    recentlyCreatedPayees = emptyList()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    payeeMappingStep = PayeeMappingStep.None,
                    isImporting = false,
                    error = result.exceptionOrNull()?.message ?: "Import failed",
                    recentlyCreatedPayees = emptyList()
                )
            }
        }
    }

    // Cancel mapping and return to preview
    fun cancelMapping() {
        _uiState.value = _uiState.value.copy(
            payeeMappingStep = PayeeMappingStep.None,
            unresolvedPayees = emptyList(),
            currentPayeeIndex = 0,
            payeeMappings = emptyMap(),
            recentlyCreatedPayees = emptyList()
        )
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
    ),
    // Payee mapping fields
    val payeeMappingStep: PayeeMappingStep = PayeeMappingStep.None,
    val unresolvedPayees: List<UnresolvedPayee> = emptyList(),
    val currentPayeeIndex: Int = 0,
    val payeeMappings: Map<String, PayeeMapping> = emptyMap(),
    val allPayees: List<Payee> = emptyList(),
    val allCategories: List<Category> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val recentlyCreatedPayees: List<Payee> = emptyList(), // Track payees created during this import session
    val similarRecentlyCreated: List<Payee> = emptyList() // Similar recently created payees for current UnresolvedPayee
)

enum class ImportFormat(val displayName: String) {
    OFX_QFX("OFX/QFX (Standard)"),
    QIF("QIF (Quicken)"),
    CSV_CHASE("CSV (Chase Credit Card)"),
    CSV_CITI("CSV (Citibank Credit Card)"),
    CSV_GENERIC("CSV (Generic)"),
    CSV_CUSTOM("CSV (Custom Mapping)")
}

enum class PayeeMappingStep {
    None,       // Not in mapping flow
    Analyzing,  // Running payee analysis
    Reviewing,  // User reviewing mappings
    Importing   // Final import in progress
}
