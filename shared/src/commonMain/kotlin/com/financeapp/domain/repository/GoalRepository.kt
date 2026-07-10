package com.financeapp.domain.repository

import com.financeapp.domain.model.GoalWithProgress
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    /** All goals (archived included — UI filters) with live, balance-derived progress. */
    fun getGoalsWithProgress(): Flow<List<GoalWithProgress>>

    /** @throws IllegalArgumentException on blank name or non-positive target. */
    suspend fun createGoal(name: String, targetAmountCents: Long, accountId: Long, deadlineMs: Long?): Long

    /** Returns false when [id] doesn't exist. @throws IllegalArgumentException as [createGoal]. */
    suspend fun updateGoal(id: Long, name: String, targetAmountCents: Long, accountId: Long?, deadlineMs: Long?): Boolean

    suspend fun setArchived(id: Long, archived: Boolean): Boolean

    suspend fun deleteGoal(id: Long): Boolean

    fun notifyGoalsChanged()
}
