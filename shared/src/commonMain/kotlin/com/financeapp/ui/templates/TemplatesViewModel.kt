package com.financeapp.ui.templates

import com.financeapp.domain.model.TransactionTemplate
import com.financeapp.domain.model.TransactionTemplateWithDetails
import com.financeapp.domain.repository.TemplateRepository
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.CategoryRepository
import com.financeapp.domain.repository.PayeeRepository
import com.financeapp.domain.model.Account
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.Payee
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TemplatesUiState(
    val isLoading: Boolean = true,
    val templates: List<TransactionTemplateWithDetails> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val payees: List<Payee> = emptyList()
)

class TemplatesViewModel(
    private val templateRepository: TemplateRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val payeeRepository: PayeeRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _uiState = MutableStateFlow(TemplatesUiState())
    val uiState: StateFlow<TemplatesUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load accounts, categories, payees
            val accounts = accountRepository.getAccountsWithBalances().first().map { it.account }
            val categories = categoryRepository.getAllCategories().first()
            val payees = payeeRepository.getAllPayees().first()

            _uiState.value = _uiState.value.copy(
                accounts = accounts,
                categories = categories,
                payees = payees
            )

            // Observe templates
            templateRepository.getAllTemplates().collect { templates ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    templates = templates
                )
            }
        }
    }

    fun addTemplate(
        name: String,
        accountId: Long?,
        payeeId: Long?,
        categoryId: Long?,
        amount: Long?,
        memo: String?
    ) {
        scope.launch {
            templateRepository.insertTemplate(
                TransactionTemplate(
                    name = name,
                    accountId = accountId,
                    payeeId = payeeId,
                    categoryId = categoryId,
                    amount = amount,
                    memo = memo
                )
            )
        }
    }

    fun updateTemplate(template: TransactionTemplate) {
        scope.launch {
            templateRepository.updateTemplate(template)
        }
    }

    fun deleteTemplate(id: Long) {
        scope.launch {
            templateRepository.deleteTemplate(id)
        }
    }
}
