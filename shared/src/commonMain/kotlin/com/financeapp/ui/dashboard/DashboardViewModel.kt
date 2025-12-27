package com.financeapp.ui.dashboard

import com.financeapp.domain.model.DashboardConfig
import com.financeapp.domain.model.DashboardWidget
import com.financeapp.domain.model.DashboardWidgetType
import com.financeapp.domain.model.defaultWidgets
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.PreferencesRepository
import com.financeapp.domain.model.AccountWithBalance
import com.financeapp.domain.model.TransactionWithDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class DashboardUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountWithBalance> = emptyList(),
    val totalBalance: Long = 0,
    val recentTransactions: List<TransactionWithDetails> = emptyList(),
    val dashboardConfig: DashboardConfig = DashboardConfig(),
    val monthlyBudgetSpent: Long = 0,
    val monthlyBudgetTotal: Long = 0,
    val spendingByCategory: Map<String, Long> = emptyMap()
)

class DashboardViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load dashboard config
            val configJson = preferencesRepository.getDashboardConfig()
            val config = if (configJson != null) {
                try {
                    json.decodeFromString<DashboardConfig>(configJson)
                } catch (e: Exception) {
                    DashboardConfig()
                }
            } else {
                DashboardConfig()
            }

            // Load accounts
            val accounts = accountRepository.getAccountsWithBalances().first()
            val totalBalance = accounts.sumOf { it.balance }

            // Load recent transactions (last 5)
            val recentTransactions = transactionRepository.getRecentTransactions(5)

            // Calculate spending by category for current month
            val spending = transactionRepository.getSpendingByCategory()

            _uiState.value = DashboardUiState(
                isLoading = false,
                accounts = accounts,
                totalBalance = totalBalance,
                recentTransactions = recentTransactions,
                dashboardConfig = config,
                spendingByCategory = spending
            )
        }
    }

    fun updateWidgetOrder(widgets: List<DashboardWidget>) {
        scope.launch {
            val newConfig = DashboardConfig(widgets)
            _uiState.value = _uiState.value.copy(dashboardConfig = newConfig)
            preferencesRepository.setDashboardConfig(json.encodeToString(newConfig))
        }
    }

    fun toggleWidget(widgetId: String, enabled: Boolean) {
        scope.launch {
            val currentWidgets = _uiState.value.dashboardConfig.widgets.toMutableList()
            val index = currentWidgets.indexOfFirst { it.id == widgetId }
            if (index >= 0) {
                currentWidgets[index] = currentWidgets[index].copy(enabled = enabled)
                val newConfig = DashboardConfig(currentWidgets)
                _uiState.value = _uiState.value.copy(dashboardConfig = newConfig)
                preferencesRepository.setDashboardConfig(json.encodeToString(newConfig))
            }
        }
    }

    fun resetToDefaults() {
        scope.launch {
            val newConfig = DashboardConfig(defaultWidgets())
            _uiState.value = _uiState.value.copy(dashboardConfig = newConfig)
            preferencesRepository.setDashboardConfig(json.encodeToString(newConfig))
        }
    }
}
