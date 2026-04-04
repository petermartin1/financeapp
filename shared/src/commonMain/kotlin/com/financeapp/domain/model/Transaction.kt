package com.financeapp.domain.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class Transaction(
    val id: Long = 0,
    val accountId: Long,
    val date: LocalDate,
    val amount: Long, // in cents, negative for expenses
    val payeeId: Long? = null,
    val categoryId: Long? = null,
    val memo: String? = null,
    val checkNumber: String? = null,
    val isCleared: Boolean = false,
    val isReconciled: Boolean = false,
    val transferId: Long? = null,
    val importId: String? = null,
    val transactionType: String? = null, // CREDIT, DEBIT, CHECK, ATM, TRANSFER, OTHER
    val sic: String? = null, // Standard Industrial Classification code
    val createdAt: Instant,
    val updatedAt: Instant
)

data class TransactionWithDetails(
    val transaction: Transaction,
    val payeeName: String?,
    val categoryName: String?,
    val accountName: String,
    val runningBalance: Long? = null
)
