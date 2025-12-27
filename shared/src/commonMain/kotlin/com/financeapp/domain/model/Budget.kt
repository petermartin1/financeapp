package com.financeapp.domain.model

data class Budget(
    val id: Long = 0,
    val categoryId: Long,
    val amount: Long,  // Monthly budget in cents
    val year: Int,
    val month: Int
)

data class BudgetWithSpending(
    val budget: Budget,
    val categoryName: String,
    val spent: Long,      // Amount spent (positive = expenses)
    val remaining: Long,  // Amount remaining
    val percentUsed: Int  // 0-100+
)

data class BudgetSummary(
    val totalBudgeted: Long,
    val totalSpent: Long,
    val totalRemaining: Long,
    val budgets: List<BudgetWithSpending>
)
