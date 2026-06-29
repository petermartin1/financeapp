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
     *
     * Detection is deliberately broad because not every bank tags checks: some send them as a
     * plain DEBIT with no CHECKNUM and only "CHECK 1234" in the NAME. So in addition to the
     * explicit signals (TRNTYPE=CHECK / a check number) we recognise check-like names — without
     * mistaking real merchants that merely contain the word "check" (Checkers, CheckFree,
     * check-card purchases). See [CheckHeuristics].
     */
    val isCheck: Boolean
        get() = type == TransactionType.CHECK ||
            !checkNumber.isNullOrBlank() ||
            CheckHeuristics.isCheckName(name)

    /**
     * The check number to persist: the explicit field when present, otherwise the digits
     * recovered from a check-like name (so an untagged "CHECK 1234" still keeps its number).
     * Null for non-checks and for checks with no number.
     */
    val effectiveCheckNumber: String?
        get() = checkNumber?.takeIf { it.isNotBlank() } ?: CheckHeuristics.checkNumberFromName(name)
}

/**
 * Recognises check transactions from their imported name/description across banks that don't
 * tag checks explicitly. Matches only names that ARE a check reference — the word check
 * (check/chk/chq/cheque, optionally e-check) optionally followed by no./number/# and digits,
 * or a bare check number — and never names that merely contain "check" as part of a merchant.
 */
object CheckHeuristics {
    // "CHECK", "CHK 1234", "CHECK #1234", "CHECK NO. 1234", "CHECK NUMBER 1234", "E-CHECK 1234".
    private val checkName = Regex(
        "^(?:e-?)?(?:check|cheque|chk|chq)(?:\\s*(?:no\\.?|number|#))?\\s*#?\\s*\\d*$",
        RegexOption.IGNORE_CASE
    )
    // A check word immediately followed by a number, then any trailing boilerplate the bank
    // appends, e.g. "Check 4313 Processed Check -". Requiring digits right after the check word
    // keeps real merchants out: "CHECK INTO CASH" / "CHECK CITY" have a word (not a number) there,
    // and "CHECKERS" / "CHECKCARD 1234 ..." don't start with the check word as a separate token.
    private val checkPrefix = Regex(
        "^(?:e-?)?(?:check|cheque|chk|chq)(?:\\s*(?:no\\.?|number|#))?\\s*#?\\s*\\d+\\b",
        RegexOption.IGNORE_CASE
    )
    // A bare check number such as "1234" or "#1234" (kept short so it can't match account refs).
    private val bareNumber = Regex("^#?\\d{1,7}$")
    private val digits = Regex("\\d+")

    fun isCheckName(name: String): Boolean {
        val n = name.trim()
        return n.isNotEmpty() &&
            (checkName.matches(n) || checkPrefix.containsMatchIn(n) || bareNumber.matches(n))
    }

    /** Digits from a check-like name (e.g. "CHECK 1234" -> "1234"); null if the name isn't a check. */
    fun checkNumberFromName(name: String): String? {
        val n = name.trim()
        if (!isCheckName(n)) return null
        return digits.find(n)?.value
    }
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
