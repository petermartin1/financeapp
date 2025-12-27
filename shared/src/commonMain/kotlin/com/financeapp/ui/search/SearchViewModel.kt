package com.financeapp.ui.search

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.financeapp.domain.model.TransactionWithDetails
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class SearchUiState(
    val allTransactions: List<TransactionWithDetails> = emptyList(),
    val filteredTransactions: List<TransactionWithDetails> = emptyList(),
    val isLoading: Boolean = false
)

class SearchViewModel(
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    var searchQuery by mutableStateOf("")
        private set

    init {
        loadAllTransactions()
    }

    private fun loadAllTransactions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            transactionRepository.getAllTransactionsWithDetails().collect { transactions ->
                _uiState.value = _uiState.value.copy(
                    allTransactions = transactions,
                    filteredTransactions = filterTransactions(transactions, searchQuery),
                    isLoading = false
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        val filtered = filterTransactions(_uiState.value.allTransactions, query)
        _uiState.value = _uiState.value.copy(filteredTransactions = filtered)
    }

    private fun filterTransactions(
        transactions: List<TransactionWithDetails>,
        query: String
    ): List<TransactionWithDetails> {
        if (query.isBlank()) {
            return transactions
        }

        val lowerQuery = query.lowercase()
        return transactions.filter { txn ->
            // Search in payee name
            txn.payeeName?.lowercase()?.contains(lowerQuery) == true ||
            // Search in memo
            txn.transaction.memo?.lowercase()?.contains(lowerQuery) == true ||
            // Search in category
            txn.categoryName?.lowercase()?.contains(lowerQuery) == true ||
            // Search in account name
            txn.accountName.lowercase().contains(lowerQuery) ||
            // Search in amount (formatted as dollars)
            formatAmount(txn.transaction.amount).contains(lowerQuery)
        }
    }

    private fun formatAmount(amountCents: Long): String {
        val dollars = amountCents / 100.0
        return String.format("%.2f", kotlin.math.abs(dollars))
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(id)
        }
    }

    fun editTransaction(
        txn: com.financeapp.domain.model.Transaction,
        categoryId: Long?,
        memo: String?,
        date: kotlinx.datetime.LocalDate,
        isCleared: Boolean,
        tagIds: List<Long>
    ) {
        viewModelScope.launch {
            val updated = txn.copy(
                categoryId = categoryId,
                memo = memo?.ifBlank { null },
                date = date,
                isCleared = isCleared
            )
            transactionRepository.updateTransaction(updated)

            // Update tags
            tagRepository.setTransactionTags(txn.id, tagIds)
        }
    }

    suspend fun getTagsForTransaction(transactionId: Long): List<Long> {
        return tagRepository.getTagsForTransaction(transactionId).map { it.id }
    }
}
