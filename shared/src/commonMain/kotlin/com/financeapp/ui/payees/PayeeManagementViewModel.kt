package com.financeapp.ui.payees

import com.financeapp.domain.model.Category
import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.PayeeWithStats
import com.financeapp.domain.repository.CategoryRepository
import com.financeapp.domain.repository.PayeeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PayeeManagementUiState(
    val payees: List<PayeeWithStats> = emptyList(),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = ""
)

class PayeeManagementViewModel(
    private val payeeRepository: PayeeRepository,
    private val categoryRepository: CategoryRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _searchQuery = MutableStateFlow("")

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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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

    /**
     * Cleanup method to cancel all background coroutines.
     * Should be called when the ViewModel is no longer needed (e.g., in tests).
     */
    fun cleanup() {
        scope.cancel()
    }
}
