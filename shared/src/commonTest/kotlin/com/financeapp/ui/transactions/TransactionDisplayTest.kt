package com.financeapp.ui.transactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransactionDisplayTest {
    @Test fun title_prefers_payee_then_imported_then_memo_then_unknown() {
        assertEquals("Safeway", transactionDisplayTitle("Safeway", "SAFEWAY #123", "groceries"))
        assertEquals("SAFEWAY #123", transactionDisplayTitle(null, "SAFEWAY #123", "groceries"))
        assertEquals("groceries", transactionDisplayTitle(null, null, "groceries"))
        assertEquals("Unknown", transactionDisplayTitle(null, null, null))
    }

    @Test fun tooltip_only_when_imported_name_differs_from_title() {
        assertEquals("Imported as: SAFEWAY #123", importedNameTooltip("Safeway", "SAFEWAY #123"))
        assertNull(importedNameTooltip("SAFEWAY #123", "SAFEWAY #123")) // same as title
        assertNull(importedNameTooltip("Safeway", null))                // nothing imported
        assertNull(importedNameTooltip("Safeway", "   "))               // blank
    }
}
