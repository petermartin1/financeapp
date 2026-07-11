package com.financeapp.domain.model

import com.financeapp.domain.reporting.SpendingDetailLine

data class CategorySpending(
    val categoryId: Long,
    val categoryName: String,
    val amount: Long,  // in cents (positive)
    val percentage: Float
)

data class MonthlyTrend(
    val year: Int,
    val month: Int,
    val income: Long,
    val expenses: Long,
    val net: Long
)

data class NetWorthPoint(
    val year: Int,
    val month: Int,
    val netWorth: Long
)

data class SpendingReport(
    val categorySpending: List<CategorySpending>,
    val totalSpent: Long,
    /** Drill-down lines per category, keyed by `categoryId ?: 0L` (0L = Uncategorized). */
    val detailLinesByCategory: Map<Long, List<SpendingDetailLine>> = emptyMap()
)

data class IncomeExpenseReport(
    val monthlyTrends: List<MonthlyTrend>,
    val totalIncome: Long,
    val totalExpenses: Long
)

data class NetWorthReport(
    val history: List<NetWorthPoint>,
    val currentNetWorth: Long
)

enum class ReportType(val displayName: String) {
    SPENDING_BY_CATEGORY("Spending by Category"),
    INCOME_VS_EXPENSES("Income vs Expenses"),
    NET_WORTH("Net Worth Trend")
}

enum class ReportPeriod(val displayName: String, val months: Int) {
    ONE_MONTH("1 Month", 1),
    THREE_MONTHS("3 Months", 3),
    SIX_MONTHS("6 Months", 6),
    ONE_YEAR("1 Year", 12),
    ALL_TIME("All Time", -1)
}
