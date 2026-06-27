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
) {
    /**
     * A check has no meaningful payee in its imported name (banks send "CHECK", "CHECK 1234",
     * or just the number). Imports therefore must not invent a payee from that name — the
     * transaction is saved with its check number and no payee, for the user to assign later.
     */
    val isCheck: Boolean
        get() = type == TransactionType.CHECK || !checkNumber.isNullOrBlank()
}

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
