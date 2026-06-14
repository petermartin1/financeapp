package com.financeapp.ui.budget

import com.financeapp.ui.supervisedViewModelScope

import com.financeapp.domain.model.Budget
import com.financeapp.domain.model.BudgetSummary
import com.financeapp.domain.repository.BudgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.*

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(
    private val budgetRepository: BudgetRepository
) {
    private val scope = supervisedViewModelScope()

    private val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    private val _selectedYear = MutableStateFlow(now.year)
    private val _selectedMonth = MutableStateFlow(now.monthNumber)

    val uiState: StateFlow<BudgetUiState> = combine(
        _selectedYear,
        _selectedMonth
    ) { year, month ->
        year to month
    }.flatMapLatest { (year, month) ->
        budgetRepository.getBudgetsWithSpendingByMonth(year, month)
            .map { budgetsWithSpending ->
                val sortedBudgets = budgetsWithSpending.sortedByDescending { it.percentUsed }

                val totalBudgeted = budgetsWithSpending.sumOf { it.budget.amount }
                val totalSpent = budgetsWithSpending.sumOf { it.spent }

                val summary = BudgetSummary(
                    totalBudgeted = totalBudgeted,
                    totalSpent = totalSpent,
                    totalRemaining = totalBudgeted - totalSpent,
                    budgets = sortedBudgets
                )

                BudgetUiState(
                    selectedYear = year,
                    selectedMonth = month,
                    summary = summary,
                    isLoading = false
                )
            }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Lazily,
        initialValue = BudgetUiState()
    )

    fun loadBudgets(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
    }

    fun previousMonth() {
        val year = _selectedYear.value
        val month = _selectedMonth.value
        val newMonth = if (month == 1) 12 else month - 1
        val newYear = if (month == 1) year - 1 else year
        loadBudgets(newYear, newMonth)
    }

    fun nextMonth() {
        val year = _selectedYear.value
        val month = _selectedMonth.value
        val newMonth = if (month == 12) 1 else month + 1
        val newYear = if (month == 12) year + 1 else year
        loadBudgets(newYear, newMonth)
    }

    fun addBudget(categoryId: Long, amount: Long) {
        val year = _selectedYear.value
        val month = _selectedMonth.value
        scope.launch {
            budgetRepository.insertOrUpdateBudget(
                Budget(
                    id = 0,
                    categoryId = categoryId,
                    amount = amount,
                    year = year,
                    month = month
                )
            )
        }
    }

    fun updateBudget(budgetId: Long, amount: Long) {
        scope.launch {
            val budget = budgetRepository.getBudgetById(budgetId)
            budget?.let {
                budgetRepository.updateBudget(it.copy(amount = amount))
            }
        }
    }

    fun deleteBudget(budgetId: Long) {
        scope.launch {
            budgetRepository.deleteBudget(budgetId)
        }
    }

    fun getAvailableCategories(onResult: (List<Pair<Long, String>>) -> Unit) {
        scope.launch {
            val categories = budgetRepository.getExpenseCategories()
            onResult(categories)
        }
    }

    /**
     * Cleanup method to cancel all background coroutines.
     * Should be called when the ViewModel is no longer needed (e.g., in tests).
     */
    fun cleanup() {
        scope.cancel()
    }
}

data class BudgetUiState(
    val selectedYear: Int = kotlin.time.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).year,
    val selectedMonth: Int = kotlin.time.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).monthNumber,
    val summary: BudgetSummary = BudgetSummary(0, 0, 0, emptyList()),
    val isLoading: Boolean = true
)
