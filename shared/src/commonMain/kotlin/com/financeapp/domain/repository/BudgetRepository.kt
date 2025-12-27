package com.financeapp.domain.repository

import com.financeapp.domain.model.Budget
import com.financeapp.domain.model.BudgetWithSpending
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getBudgetsByMonth(year: Int, month: Int): Flow<List<Budget>>
    fun getBudgetsWithSpendingByMonth(year: Int, month: Int): Flow<List<BudgetWithSpending>>
    suspend fun getBudgetById(id: Long): Budget?
    suspend fun getBudgetForCategoryAndMonth(categoryId: Long, year: Int, month: Int): Budget?
    suspend fun insertOrUpdateBudget(budget: Budget): Long
    suspend fun updateBudget(budget: Budget)
    suspend fun deleteBudget(id: Long)
    suspend fun getExpenseCategories(): List<Pair<Long, String>>

    /**
     * Notify that budgets have changed, triggering UI refresh
     */
    fun notifyBudgetsChanged()
}
