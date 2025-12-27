package com.financeapp.data.repository

import com.financeapp.db.schema.Budgets
import com.financeapp.db.schema.Categories
import com.financeapp.db.schema.Payees
import com.financeapp.db.schema.ScheduledTransactions
import com.financeapp.db.schema.SplitItems
import com.financeapp.db.schema.TransactionTemplates
import com.financeapp.db.schema.Transactions
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import com.financeapp.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class CategoryRepositoryImpl(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CategoryRepository {

    // Trigger for reactive category updates
    private val categoryRefreshTrigger = MutableStateFlow(0L)

    override fun notifyCategoriesChanged() {
        categoryRefreshTrigger.value += 1
    }

    override fun getAllCategories(): Flow<List<Category>> =
        categoryRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    Categories.selectAll()
                        .orderBy(Categories.type to SortOrder.ASC, Categories.name to SortOrder.ASC)
                        .map { it.toDomain() }
                }
            }
        }

    override fun getCategoriesByType(type: CategoryType): Flow<List<Category>> =
        categoryRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    Categories.selectAll().where { Categories.type eq type.name }
                        .orderBy(Categories.name to SortOrder.ASC)
                        .map { it.toDomain() }
                }
            }
        }

    override suspend fun getCategoryById(id: Long): Category? = withContext(ioDispatcher) {
        transaction(database) {
            Categories.selectAll().where { Categories.id eq id.toInt() }
                .singleOrNull()
                ?.toDomain()
        }
    }

    override suspend fun insertCategory(category: Category): Long = withContext(ioDispatcher) {
        val id = transaction(database) {
            Categories.insert {
                it[name] = category.name
                it[parentId] = category.parentId?.toInt()
                it[type] = category.type.name
                it[icon] = category.icon
                it[color] = category.color
            }[Categories.id].value.toLong()
        }
        notifyCategoriesChanged()
        id
    }

    override suspend fun updateCategory(category: Category): Unit = withContext(ioDispatcher) {
        transaction(database) {
            Categories.update({ Categories.id eq category.id.toInt() }) {
                it[name] = category.name
                it[parentId] = category.parentId?.toInt()
                it[type] = category.type.name
                it[icon] = category.icon
                it[color] = category.color
            }
        }
        notifyCategoriesChanged()
    }

    override suspend fun deleteCategory(id: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            // First, nullify category_id in all transactions that reference this category
            Transactions.update({ Transactions.categoryId eq id.toInt() }) {
                it[categoryId] = null
            }

            // Also nullify in split items
            SplitItems.update({ SplitItems.categoryId eq id.toInt() }) {
                it[categoryId] = null
            }

            // Nullify in payees' default category
            Payees.update({ Payees.defaultCategoryId eq id.toInt() }) {
                it[defaultCategoryId] = null
            }

            // Nullify in scheduled transactions
            ScheduledTransactions.update({ ScheduledTransactions.categoryId eq id.toInt() }) {
                it[categoryId] = null
            }

            // Nullify in transaction templates
            TransactionTemplates.update({ TransactionTemplates.categoryId eq id.toInt() }) {
                it[categoryId] = null
            }

            // Delete budgets for this category
            Budgets.deleteWhere { Budgets.categoryId eq id.toInt() }

            // Finally, delete the category
            Categories.deleteWhere { Categories.id eq id.toInt() }
        }
        notifyCategoriesChanged()
    }

    private fun ResultRow.toDomain(): Category {
        return Category(
            id = this[Categories.id].value.toLong(),
            name = this[Categories.name],
            parentId = this[Categories.parentId]?.value?.toLong(),
            type = CategoryType.valueOf(this[Categories.type]),
            icon = this[Categories.icon],
            color = this[Categories.color]
        )
    }
}
