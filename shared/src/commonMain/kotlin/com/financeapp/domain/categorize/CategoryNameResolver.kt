package com.financeapp.domain.categorize

import com.financeapp.domain.model.Category

/**
 * Maps canonical category *names* (from signals and the cold-start lexicons) to concrete category
 * ids in the user's own database, and answers the leaf/parent questions the cascade needs. Built
 * once per prediction batch from the current category list.
 *
 * Matching is case- and whitespace-insensitive. When a name collides between a parent and a child
 * (a user could rename a subcategory to match its parent), the leaf wins so predictions stay as
 * specific as the data allows.
 */
class CategoryNameResolver(categories: List<Category>) {

    private val byId: Map<Long, Category> = categories.associateBy { it.id }
    private val childCount: Map<Long, Int> =
        categories.mapNotNull { it.parentId }.groupingBy { it }.eachCount()
    private val idByName: Map<String, Long>

    init {
        // Prefer leaves on name collision: sort non-leaves first so leaves overwrite them.
        idByName = categories
            .sortedByDescending { childCount[it.id] ?: 0 }
            .associate { normalize(it.name) to it.id }
    }

    fun resolve(name: String): Long? = idByName[normalize(name)]

    fun isLeaf(categoryId: Long): Boolean = (childCount[categoryId] ?: 0) == 0

    fun parentIdOf(categoryId: Long): Long? = byId[categoryId]?.parentId

    fun nameOf(categoryId: Long): String? = byId[categoryId]?.name

    private fun normalize(name: String): String = name.trim().lowercase()
}
