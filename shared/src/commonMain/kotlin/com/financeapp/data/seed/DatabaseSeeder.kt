package com.financeapp.data.seed

import com.financeapp.domain.model.Category
import com.financeapp.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.first

/**
 * Seeds the database with default data for new users.
 * Only seeds data if the database is empty (no existing categories).
 */
class DatabaseSeeder(
    private val categoryRepository: CategoryRepository
) {
    /**
     * Seeds default categories if none exist.
     * Returns true if seeding was performed, false if data already existed.
     */
    suspend fun seedIfEmpty(): Boolean {
        val existingCategories = categoryRepository.getAllCategories().first()

        if (existingCategories.isNotEmpty()) {
            return false
        }

        seedDefaultCategories()
        return true
    }

    /**
     * Forces seeding of default categories (adds to existing).
     * Use with caution - primarily for testing or reset scenarios.
     */
    suspend fun seedDefaultCategories() {
        for (categoryDef in DefaultCategories.allCategories) {
            // Insert parent category
            val parentId = categoryRepository.insertCategory(
                Category(
                    name = categoryDef.name,
                    parentId = null,
                    type = categoryDef.type,
                    icon = categoryDef.icon,
                    color = categoryDef.color
                )
            )

            // Insert subcategories
            for (subcategoryDef in categoryDef.subcategories) {
                categoryRepository.insertCategory(
                    Category(
                        name = subcategoryDef.name,
                        parentId = parentId,
                        type = categoryDef.type,
                        icon = subcategoryDef.icon ?: categoryDef.icon,
                        color = subcategoryDef.color ?: categoryDef.color
                    )
                )
            }
        }
    }
}
