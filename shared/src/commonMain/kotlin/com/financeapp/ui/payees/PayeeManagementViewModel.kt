package com.financeapp.ui.payees

import com.financeapp.ui.supervisedViewModelScope

import com.financeapp.domain.matching.PayeeMatcher
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.PayeeWithStats
import com.financeapp.domain.model.TransactionWithDetails
import com.financeapp.domain.repository.CategoryRepository
import com.financeapp.domain.repository.PayeeMatchingRepository
import com.financeapp.domain.repository.PayeeRepository
import com.financeapp.domain.repository.TagRepository
import com.financeapp.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PayeeManagementUiState(
    val payees: List<PayeeWithStats> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = ""
)

data class PayeeTransactionsUiState(
    val transactions: List<TransactionWithDetails> = emptyList()
)

class PayeeManagementViewModel(
    private val payeeRepository: PayeeRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val payeeMatchingRepository: PayeeMatchingRepository
) {
    private val scope = supervisedViewModelScope()
    private val payeeMatcher = PayeeMatcher()

    private val _searchQuery = MutableStateFlow("")
    private val _selectedPayeeId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<PayeeManagementUiState> = combine(
        payeeRepository.getPayeesWithStats(),
        categoryRepository.getAllCategories(),
        _searchQuery
    ) { payees, categories, searchQuery ->
        PayeeManagementUiState(
            payees = payees,
            categories = categories,
            isLoading = false,
            searchQuery = searchQuery
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Lazily,
        initialValue = PayeeManagementUiState()
    )

    private val selectedPayeeIds = combine(
        _selectedPayeeId,
        payeeRepository.getAllPayees()
    ) { selectedPayeeId, allPayees ->
        selectedPayeeId to allPayees
    }.mapLatest { (selectedPayeeId, allPayees) ->
        if (selectedPayeeId == null) {
            emptySet()
        } else {
            resolvePayeeIds(selectedPayeeId, allPayees)
        }
    }

    val transactionsUiState: StateFlow<PayeeTransactionsUiState> = _selectedPayeeId
        .flatMapLatest { selectedPayeeId ->
            if (selectedPayeeId == null) {
                // Don't load transactions until a payee is selected
                flowOf(PayeeTransactionsUiState())
            } else {
                combine(
                    selectedPayeeIds,
                    transactionRepository.getAllTransactionsWithDetails()
                ) { payeeIds, transactions ->
                    PayeeTransactionsUiState(
                        transactions = filterTransactionsForPayee(transactions, payeeIds)
                    )
                }
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue = PayeeTransactionsUiState()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectPayee(payeeId: Long?) {
        _selectedPayeeId.value = payeeId
    }

    fun getFilteredPayees(): List<PayeeWithStats> {
        val query = uiState.value.searchQuery.lowercase()
        return if (query.isBlank()) {
            uiState.value.payees
        } else {
            uiState.value.payees.filter { it.payee.name.lowercase().contains(query) }
        }
    }

    fun updatePayee(payee: Payee) {
        scope.launch {
            payeeRepository.updatePayee(payee)
        }
    }

    fun setDefaultCategory(payeeId: Long, categoryId: Long?) {
        scope.launch {
            val payee = payeeRepository.getPayeeById(payeeId)
            payee?.let {
                payeeRepository.updatePayee(it.copy(defaultCategoryId = categoryId))
            }
        }
    }

    fun mergePayees(sourceId: Long, targetId: Long) {
        scope.launch {
            payeeRepository.mergePayees(sourceId, targetId)
        }
    }

    fun deletePayee(id: Long) {
        scope.launch {
            payeeRepository.deletePayee(id)
        }
    }

    fun renamePayee(id: Long, newName: String) {
        scope.launch {
            val payee = payeeRepository.getPayeeById(id)
            payee?.let {
                payeeRepository.updatePayee(it.copy(name = newName))
            }
        }
    }

    fun getCategoryName(categoryId: Long?): String {
        if (categoryId == null) return "None"
        return uiState.value.categories.find { it.id == categoryId }?.name ?: "Unknown"
    }

    private suspend fun resolvePayeeIds(selectedPayeeId: Long, allPayees: List<Payee>): Set<Long> {
        val aliasNames = payeeMatchingRepository.getAliasesByPayeeId(selectedPayeeId)
            .map { it.aliasName }
            .toSet()

        if (aliasNames.isEmpty()) {
            return setOf(selectedPayeeId)
        }

        val aliasPayeeIds = allPayees
            .filter { payeeMatcher.normalize(it.name) in aliasNames }
            .map { it.id }

        return (aliasPayeeIds + selectedPayeeId).toSet()
    }

    private suspend fun filterTransactionsForPayee(
        transactions: List<TransactionWithDetails>,
        payeeIds: Set<Long>
    ): List<TransactionWithDetails> {
        if (payeeIds.isEmpty()) return emptyList()

        val payeeTransactions = transactions.filter { txn ->
            val payeeId = txn.transaction.payeeId
            payeeId != null && payeeId in payeeIds
        }

        val nonTransfers = payeeTransactions.filterNot { txn ->
            txn.transaction.transferId != null || txn.transaction.transactionType == "TRANSFER"
        }

        val splitIds = tagRepository.getSplitTransactionIds(nonTransfers.map { it.transaction.id })
        return nonTransfers.filterNot { it.transaction.id in splitIds }
    }

    /**
     * Cleanup method to cancel all background coroutines.
     * Should be called when the ViewModel is no longer needed (e.g., in tests).
     */
    fun cleanup() {
        scope.cancel()
    }
}
