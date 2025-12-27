package com.financeapp.domain.repository

import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>
    fun getCategoriesByType(type: CategoryType): Flow<List<Category>>
    suspend fun getCategoryById(id: Long): Category?
    suspend fun insertCategory(category: Category): Long
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(id: Long)

    /**
     * Notify that categories have changed, triggering UI refresh
     */
    fun notifyCategoriesChanged()
}
