package com.financeapp.domain.categorize

import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryNameResolverTest {

    // A tiny hierarchy: Food & Dining (parent) -> Restaurants, Coffee Shops (leaves);
    // Miscellaneous is a childless top-level category (a leaf in its own right).
    private val food = Category(id = 1, name = "Food & Dining", parentId = null, type = CategoryType.EXPENSE)
    private val restaurants = Category(id = 2, name = "Restaurants", parentId = 1, type = CategoryType.EXPENSE)
    private val coffee = Category(id = 3, name = "Coffee Shops", parentId = 1, type = CategoryType.EXPENSE)
    private val misc = Category(id = 4, name = "Miscellaneous", parentId = null, type = CategoryType.EXPENSE)

    private val resolver = CategoryNameResolver(listOf(food, restaurants, coffee, misc))

    @Test
    fun `resolves a canonical leaf name to its id`() {
        assertEquals(2L, resolver.resolve("Restaurants"))
        assertEquals(3L, resolver.resolve("Coffee Shops"))
    }

    @Test
    fun `resolution is case and whitespace insensitive`() {
        assertEquals(2L, resolver.resolve("  restaurants  "))
        assertEquals(3L, resolver.resolve("COFFEE SHOPS"))
    }

    @Test
    fun `returns null for a name the user does not have`() {
        assertNull(resolver.resolve("Cryptocurrency"))
    }

    @Test
    fun `identifies leaves and non-leaves`() {
        assertTrue(resolver.isLeaf(2L), "Restaurants has no children")
        assertTrue(resolver.isLeaf(4L), "Miscellaneous is childless")
        assertFalse(resolver.isLeaf(1L), "Food & Dining has children")
    }

    @Test
    fun `reports the parent id of a leaf`() {
        assertEquals(1L, resolver.parentIdOf(2L))
        assertNull(resolver.parentIdOf(1L), "a top-level category has no parent")
    }

    @Test
    fun `looks up a category name by id`() {
        assertEquals("Coffee Shops", resolver.nameOf(3L))
        assertNull(resolver.nameOf(999L))
    }

    @Test
    fun `prefers a leaf when the same name exists as both parent and child`() {
        // Degenerate but possible: a user renames a child to match a parent. Prefer the leaf so a
        // prediction stays as specific as the data allows.
        val dupParent = Category(id = 10, name = "Gas", parentId = null, type = CategoryType.EXPENSE)
        val dupLeaf = Category(id = 11, name = "Gas", parentId = 10, type = CategoryType.EXPENSE)
        val r = CategoryNameResolver(listOf(dupParent, dupLeaf))
        assertEquals(11L, r.resolve("Gas"))
    }
}
