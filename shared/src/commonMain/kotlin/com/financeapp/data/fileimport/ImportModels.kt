package com.financeapp.data.fileimport

import kotlinx.datetime.LocalDate

data class ImportedTransaction(
    val fitId: String,  // Financial Institution Transaction ID
    val date: LocalDate,
    val amount: Long,   // In cents
    val name: String,
    val memo: String?,
    val checkNumber: String?,
    val type: TransactionType,
    val sic: String? = null  // Standard Industrial Classification code
)

enum class TransactionType {
    CREDIT, DEBIT, CHECK, ATM, TRANSFER, OTHER
}

data class ImportedAccount(
    val bankId: String?,
    val accountId: String,
    val accountType: ImportedAccountType,
    val currency: String
)

enum class ImportedAccountType {
    CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT, OTHER
}

data class ImportResult(
    val account: ImportedAccount,
    val transactions: List<ImportedTransaction>,
    val startDate: LocalDate?,
    val endDate: LocalDate?
)

sealed class ImportError : Exception() {
    data class ParseError(override val message: String) : ImportError()
    data class UnsupportedFormat(override val message: String) : ImportError()
    data class InvalidData(override val message: String) : ImportError()
}
