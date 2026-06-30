package com.financeapp.domain.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Minimal shape needed for spending-by-category math (see
 * com.financeapp.domain.reporting.expandSpendingLines). [Transaction] is the usual implementation;
 * repositories that only load a few columns can supply a lightweight one.
 */
interface SpendingSource {
    val id: Long
    val categoryId: Long?
    val amount: Long
    val transferId: Long?
}

data class Transaction(
    override val id: Long = 0,
    val accountId: Long,
    val date: LocalDate,
    override val amount: Long, // in cents, negative for expenses
    val payeeId: Long? = null,
    override val categoryId: Long? = null,
    val memo: String? = null,
    val checkNumber: String? = null,
    val importedName: String? = null,
    val isCleared: Boolean = false,
    val isReconciled: Boolean = false,
    override val transferId: Long? = null,
    val importId: String? = null,
    val transactionType: String? = null, // CREDIT, DEBIT, CHECK, ATM, TRANSFER, OTHER
    val sic: String? = null, // Standard Industrial Classification code
    val createdAt: Instant,
    val updatedAt: Instant
) : SpendingSource

data class TransactionWithDetails(
    val transaction: Transaction,
    val payeeName: String?,
    val categoryName: String?,
    val accountName: String,
    val runningBalance: Long? = null
)
