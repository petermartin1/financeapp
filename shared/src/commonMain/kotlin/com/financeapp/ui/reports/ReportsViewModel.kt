package com.financeapp.ui.reports

import com.financeapp.domain.model.*
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReportsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadReport()
    }

    fun setReportType(type: ReportType) {
        _uiState.value = _uiState.value.copy(selectedType = type)
        loadReport()
    }

    fun setPeriod(period: ReportPeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
        loadReport()
    }

    private fun loadReport() {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true)

        scope.launch {
            // TODO: Implement report loading with Exposed queries
            // This requires complex aggregate queries that should be added to repositories
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}

data class ReportsUiState(
    val selectedType: ReportType = ReportType.SPENDING_BY_CATEGORY,
    val selectedPeriod: ReportPeriod = ReportPeriod.ONE_MONTH,
    val spendingReport: SpendingReport = SpendingReport(emptyList(), 0),
    val incomeExpenseReport: IncomeExpenseReport = IncomeExpenseReport(emptyList(), 0, 0),
    val netWorthReport: NetWorthReport = NetWorthReport(emptyList(), 0),
    val isLoading: Boolean = true
)
