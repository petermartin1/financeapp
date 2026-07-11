package com.financeapp.ui.reports

import com.financeapp.ui.supervisedViewModelScope

import com.financeapp.domain.model.*
import com.financeapp.domain.reporting.expandSpendingDetailLines
import com.financeapp.domain.repository.CategoryRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.TagRepository
import com.financeapp.ui.launchReporting
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
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class ReportsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository
) {
    private val scope = supervisedViewModelScope()

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadReport()
    }

    fun setReportType(type: ReportType) {
        _uiState.value = _uiState.value.copy(selectedType = type, selectedSpendingCategoryId = null)
        loadReport()
    }

    fun setPeriod(period: ReportPeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period, selectedSpendingCategoryId = null)
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
                            selectedSpendingCategoryId = _uiState.value.selectedSpendingCategoryId
                                ?.takeIf { report.detailLinesByCategory.containsKey(it) },
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
        return when (period) {
            // "All time" must cover every transaction, including imports older than the old
            // hardcoded 2000-01-01 floor and any future-dated/scheduled entries (R25).
            ReportPeriod.ALL_TIME -> LocalDate(1970, 1, 1) to now.plus(100, DateTimeUnit.YEAR)
            else -> now.minus(period.months, DateTimeUnit.MONTH) to now
        }
    }

    private suspend fun loadSpendingReport(startDate: LocalDate, endDate: LocalDate): SpendingReport {
        val transactions = transactionRepository.getTransactionsWithDetailsByDateRange(startDate, endDate).first()

        val categories = categoryRepository.getAllCategories().first()
        val categoriesById = categories.associateBy { it.id }
        val categoryNames = categories.associate { it.id to it.name }

        // Expand split transactions into their per-category lines so a split purchase is reported
        // under each split's category instead of the parent's.
        val splitsByTransactionId =
            transactionRepository.getSplitsByTransactionIds(transactions.map { it.transaction.id })

        // Spending = negative, non-transfer outflows. Exclude income- and transfer-typed
        // categories (a refund/charge-back tagged with an income category is not spending),
        // consistent with the dashboard's getSpendingByCategory. Uncategorized outflows are
        // still shown as "Uncategorized".
        val spendingLines = expandSpendingDetailLines(transactions, splitsByTransactionId)
            .filter { it.lineAmountCents < 0 }
            .filter { line ->
                val type = line.categoryId?.let { categoriesById[it]?.type }
                type == null || type == CategoryType.EXPENSE
            }

        // The pie below is aggregated from these same lines, so a slice always sums to its
        // drill-down list.
        val detailLinesByCategory = spendingLines
            .groupBy { it.categoryId ?: 0L }
            .mapValues { (_, lines) -> lines.sortedByDescending { it.source.transaction.date } }

        val totalSpent = spendingLines.sumOf { kotlin.math.abs(it.lineAmountCents) }

        val categorySpending = detailLinesByCategory.map { (categoryKey, lines) ->
            val amount = lines.sumOf { kotlin.math.abs(it.lineAmountCents) }
            val categoryName = if (categoryKey == 0L) "Uncategorized" else categoryNames[categoryKey] ?: "Uncategorized"
            val percentage = if (totalSpent > 0) (amount.toFloat() / totalSpent) * 100 else 0f
            CategorySpending(
                categoryId = categoryKey,
                categoryName = categoryName,
                amount = amount,
                percentage = percentage
            )
        }.sortedByDescending { it.amount }

        return SpendingReport(
            categorySpending = categorySpending,
            totalSpent = totalSpent,
            detailLinesByCategory = detailLinesByCategory
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

    fun selectSpendingCategory(categoryKey: Long) {
        val current = _uiState.value.selectedSpendingCategoryId
        _uiState.value = _uiState.value.copy(
            selectedSpendingCategoryId = if (current == categoryKey) null else categoryKey
        )
    }

    fun clearSpendingSelection() {
        _uiState.value = _uiState.value.copy(selectedSpendingCategoryId = null)
    }

    /** Saves an edit from the drill-down panel, then rebuilds the report (same path as search). */
    fun editTransaction(
        txn: Transaction,
        categoryId: Long?,
        memo: String?,
        date: LocalDate,
        isCleared: Boolean,
        tagIds: List<Long>
    ) {
        scope.launchReporting("save the transaction") {
            val updated = txn.copy(
                categoryId = categoryId,
                memo = memo?.ifBlank { null },
                date = date,
                isCleared = isCleared
            )
            transactionRepository.updateTransaction(updated)
            tagRepository.setTransactionTags(txn.id, tagIds)
            accountRepository.notifyBalancesChanged()
            loadReport()
        }
    }

    fun deleteTransaction(id: Long) {
        scope.launchReporting("delete the transaction") {
            transactionRepository.deleteTransaction(id)
            accountRepository.notifyBalancesChanged()
            loadReport()
        }
    }

    suspend fun getTagsForTransaction(transactionId: Long): List<Long> =
        tagRepository.getTagsForTransaction(transactionId).map { it.id }

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
    val isLoading: Boolean = true,
    /** Drill-down selection: key into detailLinesByCategory (0L = Uncategorized); null = none. */
    val selectedSpendingCategoryId: Long? = null
)
