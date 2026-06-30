package com.financeapp.ui.transactions

/** Ledger/edit-dialog title for a transaction: payee, else the original imported bank text,
 *  else memo, else a generic fallback. */
fun transactionDisplayTitle(payeeName: String?, importedName: String?, memo: String?): String =
    payeeName?.takeIf { it.isNotBlank() }
        ?: importedName?.takeIf { it.isNotBlank() }
        ?: memo?.takeIf { it.isNotBlank() }
        ?: "Unknown"

/** Tooltip revealing the original imported text, shown only when a payee (or other title) is
 *  displayed in its place. Null when there is nothing extra to reveal. */
fun importedNameTooltip(displayedTitle: String, importedName: String?): String? =
    importedName?.takeIf { it.isNotBlank() && it != displayedTitle }
        ?.let { "Imported as: $it" }
