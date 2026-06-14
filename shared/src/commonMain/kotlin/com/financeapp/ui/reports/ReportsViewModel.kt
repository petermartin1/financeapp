package com.financeapp.ui.reports

import com.financeapp.ui.supervisedViewModelScope

import com.financeapp.domain.model.*
import com.financeapp.domain.repository.CategoryRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class ReportsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) {
    private val scope = supervisedViewModelScope()

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
            try {
                val (startDate, endDate) = calculateDateRange(state.selectedPeriod)

                when (state.selectedType) {
                    ReportType.SPENDING_BY_CATEGORY -> {
                        val report = loadSpendingReport(startDate, endDate)
                        _uiState.value = _uiState.value.copy(
                            spendingReport = report,
                            isLoading = false
                        )
                    }
                    ReportType.INCOME_VS_EXPENSES -> {
                        val report = loadIncomeExpenseReport(startDate, endDate)
                        _uiState.value = _uiState.value.copy(
                            incomeExpenseReport = report,
                            isLoading = false
                        )
                    }
                    ReportType.NET_WORTH -> {
                        val report = loadNetWorthReport()
                        _uiState.value = _uiState.value.copy(
                            netWorthReport = report,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun calculateDateRange(period: ReportPeriod): Pair<LocalDate, LocalDate> {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startDate = when (period) {
            ReportPeriod.ALL_TIME -> LocalDate(2000, 1, 1) // Far in the past
            else -> now.minus(period.months, DateTimeUnit.MONTH)
        }
        return Pair(startDate, now)
    }

    private suspend fun loadSpendingReport(startDate: LocalDate, endDate: LocalDate): SpendingReport {
        val transactions = transactionRepository.getTransactionsByDateRange(startDate, endDate).first()

        // Filter to expenses only (negative amounts), excluding transfers, and group by category
        val expensesByCategory = transactions
            .filter { it.transferId == null }
            .filter { it.amount < 0 }
            .groupBy { it.categoryId }

        val totalSpent = expensesByCategory.values.flatten().sumOf { kotlin.math.abs(it.amount) }

        // Get category names from category repository (avoids loading all transactions)
        val categories = categoryRepository.getAllCategories().first()
        val categoryNames = categories.associate { it.id to it.name }.toMutableMap()

        val categorySpending = expensesByCategory.map { (categoryId, txns) ->
            val amount = txns.sumOf { kotlin.math.abs(it.amount) }
            val categoryName = categoryId?.let { categoryNames[it] } ?: "Uncategorized"
            val percentage = if (totalSpent > 0) (amount.toFloat() / totalSpent) * 100 else 0f

            CategorySpending(
                categoryId = categoryId ?: 0L,
                categoryName = categoryName,
                amount = amount,
                percentage = percentage
            )
        }.sortedByDescending { it.amount }

        return SpendingReport(
            categorySpending = categorySpending,
            totalSpent = totalSpent
        )
    }

    private suspend fun loadIncomeExpenseReport(startDate: LocalDate, endDate: LocalDate): IncomeExpenseReport {
        val transactions = transactionRepository.getTransactionsByDateRange(startDate, endDate).first()

        // Exclude transfers, then group by year-month
        val nonTransferTransactions = transactions.filter { it.transferId == null }
        val byMonth = nonTransferTransactions.groupBy { Pair(it.date.year, it.date.monthNumber) }

        val monthlyTrends = byMonth.map { (yearMonth, txns) ->
            val (year, month) = yearMonth
            val income = txns.filter { it.amount > 0 }.sumOf { it.amount }
            val expenses = kotlin.math.abs(txns.filter { it.amount < 0 }.sumOf { it.amount })

            MonthlyTrend(
                year = year,
                month = month,
                income = income,
                expenses = expenses,
                net = income - expenses
            )
        }.sortedWith(compareBy({ it.year }, { it.month }))

        val totalIncome = monthlyTrends.sumOf { it.income }
        val totalExpenses = monthlyTrends.sumOf { it.expenses }

        return IncomeExpenseReport(
            monthlyTrends = monthlyTrends,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses
        )
    }

    fun cleanup() {
        scope.cancel()
    }

    private suspend fun loadNetWorthReport(): NetWorthReport {
        // Get current net worth from all accounts
        val accounts = accountRepository.getAccountsWithBalances().first()
        val currentNetWorth = accounts.sumOf { it.balance }

        // For historical data, we'd need snapshots which aren't currently tracked
        // For now, return current value only
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val history = listOf(
            NetWorthPoint(
                year = now.year,
                month = now.monthNumber,
                netWorth = currentNetWorth
            )
        )

        return NetWorthReport(
            history = history,
            currentNetWorth = currentNetWorth
        )
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
