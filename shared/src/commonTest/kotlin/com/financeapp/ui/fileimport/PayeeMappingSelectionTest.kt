package com.financeapp.ui.fileimport

import com.financeapp.domain.model.Payee
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the "map an imported payee to an existing one" picker's candidate set. The bug this
 * protects against: a payee the user *created earlier in the same import* (held with a temporary
 * negative id, so it is not in the DB-backed allPayees list) was invisible in the picker because
 * the dialog was fed the fuzzy-matcher-filtered subset instead of the full recently-created list.
 */
class PayeeMappingSelectionTest {

    private fun payee(id: Long, name: String) = Payee(id = id, name = name)

    @Test
    fun `a payee created earlier in this import is selectable even if unlike the current one`() {
        val dbPayees = listOf(payee(1, "Existing Bank"))
        // Created earlier in the import (temporary negative id); nothing about it resembles the
        // current payee, so a similarity filter would have dropped it.
        val recentlyCreated = listOf(payee(-1, "Zzq Unrelated Vendor"))

        val selectable = buildSelectablePayees(dbPayees, recentlyCreated)

        assertTrue(
            selectable.any { it.id == -1L },
            "a payee created earlier in the same import must be selectable in the picker, but was: $selectable"
        )
    }

    @Test
    fun `picker is not empty when the only payees exist solely in this import`() {
        // First-ever import into an empty DB: the only payees are ones being created right now.
        val selectable = buildSelectablePayees(
            allPayees = emptyList(),
            recentlyCreatedPayees = listOf(payee(-1, "Corner Cafe"), payee(-2, "Gas N Go"))
        )
        assertEquals(2, selectable.size, "in-import creations must populate the picker even with an empty DB")
    }

    @Test
    fun `combines db and in-import payees, deduped and sorted by name`() {
        val db = listOf(payee(2, "Whole Foods"), payee(1, "Amazon"))
        val recent = listOf(payee(-1, "Blue Bottle"), payee(2, "Whole Foods")) // id 2 duplicated

        val selectable = buildSelectablePayees(db, recent)

        assertEquals(listOf("Amazon", "Blue Bottle", "Whole Foods"), selectable.map { it.name })
        assertEquals(3, selectable.size, "a payee present in both lists should appear once")
    }
}
