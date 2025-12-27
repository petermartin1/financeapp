package com.financeapp.domain.model

enum class DashboardWidgetType {
    NET_WORTH,
    ACCOUNTS_SUMMARY,
    RECENT_TRANSACTIONS,
    BUDGET_PROGRESS,
    SPENDING_BY_CATEGORY
}

data class DashboardWidget(
    val id: String,
    val type: DashboardWidgetType,
    val enabled: Boolean = true,
    val order: Int
)

data class DashboardConfig(
    val widgets: List<DashboardWidget> = defaultWidgets()
)

fun defaultWidgets(): List<DashboardWidget> = listOf(
    DashboardWidget("net_worth", DashboardWidgetType.NET_WORTH, true, 0),
    DashboardWidget("accounts", DashboardWidgetType.ACCOUNTS_SUMMARY, true, 1),
    DashboardWidget("recent", DashboardWidgetType.RECENT_TRANSACTIONS, true, 2),
    DashboardWidget("budget", DashboardWidgetType.BUDGET_PROGRESS, true, 3),
    DashboardWidget("spending", DashboardWidgetType.SPENDING_BY_CATEGORY, true, 4)
)
