package com.financeapp.data.repository

import com.financeapp.db.schema.Budgets
import com.financeapp.db.schema.Categories
import com.financeapp.db.schema.SplitItems
import com.financeapp.db.schema.Transactions
import com.financeapp.domain.model.Budget
import com.financeapp.domain.model.BudgetWithSpending
import com.financeapp.domain.model.CategoryType
import com.financeapp.domain.model.SplitItem
import com.financeapp.domain.reporting.SimpleSpendingSource
import com.financeapp.domain.reporting.expandSpendingLines
import com.financeapp.domain.repository.BudgetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class BudgetRepositoryImpl(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BudgetRepository {

    // Trigger for reactive budget updates
    private val budgetRefreshTrigger = MutableStateFlow(0L)

    override fun notifyBudgetsChanged() {
        budgetRefreshTrigger.value += 1
    }

    override fun getBudgetsByMonth(year: Int, month: Int): Flow<List<Budget>> =
        budgetRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    Budgets
                        .selectAll().where { (Budgets.year eq year) and (Budgets.month eq month) }
                        .map { it.toDomain() }
                }
            }
        }

    override fun getBudgetsWithSpendingByMonth(year: Int, month: Int): Flow<List<BudgetWithSpending>> =
        budgetRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                // Get all budgets for the month
                val budgets = Budgets
                    .selectAll().where { (Budgets.year eq year) and (Budgets.month eq month) }
                    .map { it.toDomain() }

                // Calculate date range for the month
                val startDate = LocalDate(year, month, 1)
                val endDate = if (month == 12) {
                    LocalDate(year + 1, 1, 1)
                } else {
                    LocalDate(year, month + 1, 1)
                }
                val startMillis = startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                val endMillis = endDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

                // Load the month's non-transfer transactions and expand any split transactions into
                // their per-category lines, so a split purchase counts against each split's budget.
                // Transfers move money between the user's own accounts and are not spending, so they
                // are excluded (consistent with the dashboard and the spending report).
                val monthSources = Transactions
                    .select(Transactions.id, Transactions.categoryId, Transactions.amount)
                    .where {
                        (Transactions.date greaterEq startMillis) and
                        (Transactions.date less endMillis) and
                        Transactions.transferId.isNull()
                    }
                    .map {
                        SimpleSpendingSource(
                            id = it[Transactions.id].value.toLong(),
                            categoryId = it[Transactions.categoryId]?.value?.toLong(),
                            amount = it[Transactions.amount],
                            transferId = null
                        )
                    }

                val splitsByTransactionId = loadSplits(monthSources.map { it.id })

                // Spending per category = sum of outflow (negative) lines in that category.
                val spendingByCategory = expandSpendingLines(monthSources, splitsByTransactionId)
                    .filter { it.amount < 0 && it.categoryId != null }
                    .groupBy { it.categoryId }
                    .mapValues { (_, lines) -> kotlin.math.abs(lines.sumOf { it.amount }) }

                // Get category names
                val categoryNames = Categories.selectAll().associate {
                    it[Categories.id].value.toLong() to it[Categories.name]
                }

                    // Combine budgets with spending
                    budgets.map { budget ->
                        val spent = spendingByCategory[budget.categoryId] ?: 0L
                        val remaining = budget.amount - spent
                        val percentUsed = if (budget.amount > 0) {
                            ((spent * 100) / budget.amount).toInt()
                        } else 0

                        BudgetWithSpending(
                            budget = budget,
                            categoryName = categoryNames[budget.categoryId] ?: "Unknown",
                            spent = spent,
                            remaining = remaining,
                            percentUsed = percentUsed
                        )
                    }
                }
            }
        }

    override suspend fun getBudgetById(id: Long): Budget? = withContext(ioDispatcher) {
        transaction(database) {
            Budgets.selectAll().where { Budgets.id eq id.toInt() }
                .singleOrNull()
                ?.toDomain()
        }
    }

    override suspend fun getBudgetForCategoryAndMonth(
        categoryId: Long,
        year: Int,
        month: Int
    ): Budget? = withContext(ioDispatcher) {
        transaction(database) {
            Budgets.selectAll().where {
                (Budgets.categoryId eq categoryId.toInt()) and
                (Budgets.year eq year) and
                (Budgets.month eq month)
            }
                .singleOrNull()
                ?.toDomain()
        }
    }

    override suspend fun insertOrUpdateBudget(budget: Budget): Long = withContext(ioDispatcher) {
        val id = transaction(database) {
            // Check if exists
            val existing = Budgets.selectAll().where {
                (Budgets.categoryId eq budget.categoryId.toInt()) and
                (Budgets.year eq budget.year) and
                (Budgets.month eq budget.month)
            }.singleOrNull()

            if (existing != null) {
                // Update existing
                Budgets.update({
                    (Budgets.categoryId eq budget.categoryId.toInt()) and
                    (Budgets.year eq budget.year) and
                    (Budgets.month eq budget.month)
                }) {
                    it[amount] = budget.amount
                }
                existing[Budgets.id].value.toLong()
            } else {
                // Insert new
                Budgets.insert {
                    it[categoryId] = budget.categoryId.toInt()
                    it[amount] = budget.amount
                    it[year] = budget.year
                    it[month] = budget.month
                }[Budgets.id].value.toLong()
            }
        }
        notifyBudgetsChanged()
        id
    }

    override suspend fun updateBudget(budget: Budget): Unit = withContext(ioDispatcher) {
        transaction(database) {
            Budgets.update({ Budgets.id eq budget.id.toInt() }) {
                it[amount] = budget.amount
            }
        }
        notifyBudgetsChanged()
    }

    override suspend fun deleteBudget(id: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            Budgets.deleteWhere { Budgets.id eq id.toInt() }
        }
        notifyBudgetsChanged()
    }

    override suspend fun getExpenseCategories(): List<Pair<Long, String>> = withContext(ioDispatcher) {
        transaction(database) {
            Categories
                .selectAll().where { Categories.type eq CategoryType.EXPENSE.name }
                .map { it[Categories.id].value.toLong() to it[Categories.name] }
        }
    }

    // Loads split items for the given transaction ids, keyed by transaction id, within the caller's
    // existing transaction block. Transactions with no splits are simply absent from the map.
    private fun loadSplits(transactionIds: List<Long>): Map<Long, List<SplitItem>> {
        if (transactionIds.isEmpty()) return emptyMap()
        return SplitItems
            .selectAll().where { SplitItems.transactionId inList transactionIds.map { it.toInt() } }
            .map {
                SplitItem(
                    id = it[SplitItems.id].value.toLong(),
                    transactionId = it[SplitItems.transactionId].value.toLong(),
                    categoryId = it[SplitItems.categoryId]?.value?.toLong(),
                    amount = it[SplitItems.amount],
                    memo = it[SplitItems.memo]
                )
            }
            .groupBy { it.transactionId }
    }

    private fun ResultRow.toDomain(): Budget {
        return Budget(
            id = this[Budgets.id].value.toLong(),
            categoryId = this[Budgets.categoryId].value.toLong(),
            amount = this[Budgets.amount],
            year = this[Budgets.year],
            month = this[Budgets.month]
        )
    }
}
