package com.financeapp.data.fileimport

import kotlinx.datetime.LocalDate

/**
 * A parsed transaction before its import id is assigned. Keeping the id out of parsing lets us
 * derive a *stable* fitId from the transaction's content (not its row position), so the same
 * logical transaction deduplicates correctly across overlapping imports.
 */
internal data class RawImported(
    val date: LocalDate,
    val amount: Long,
    val name: String,
    val memo: String?,
    val checkNumber: String?,
    val type: TransactionType,
    val sic: String? = null
)

/**
 * Assigns each transaction a content-derived fitId. Genuinely identical transactions within the
 * same file are disambiguated by an occurrence index (0, 1, 2, …) so they stay distinct, while
 * remaining stable when the same file (or an overlapping one) is re-imported.
 */
internal fun List<RawImported>.withStableFitIds(prefix: String): List<ImportedTransaction> {
    val occurrences = mutableMapOf<String, Int>()
    return map { raw ->
        val key = ImportFitId.contentKey(raw.date, raw.amount, raw.name, raw.memo)
        val occurrence = occurrences.getOrElse(key) { 0 }
        occurrences[key] = occurrence + 1

        ImportedTransaction(
            fitId = ImportFitId.generate(prefix, raw.date, raw.amount, raw.name, raw.memo, occurrence),
            date = raw.date,
            amount = raw.amount,
            name = raw.name,
            memo = raw.memo,
            checkNumber = raw.checkNumber,
            type = raw.type,
            sic = raw.sic
        )
    }
}

internal object ImportFitId {

    fun contentKey(date: LocalDate, amountCents: Long, name: String, memo: String?): String =
        "$date|$amountCents|${name.trim().lowercase()}|${(memo ?: "").trim().lowercase()}"

    fun generate(prefix: String, date: LocalDate, amountCents: Long, name: String, memo: String?, occurrence: Int): String {
        val hash = fnv1a(contentKey(date, amountCents, name, memo))
        return "${prefix}_${date}_${amountCents}_${hash}_$occurrence"
    }

    /** Deterministic, platform-independent 64-bit FNV-1a hash (hex). */
    private fun fnv1a(input: String): String {
        var hash = 0xcbf29ce484222325uL
        for (ch in input) {
            hash = hash xor ch.code.toULong()
            hash *= 0x100000001b3uL
        }
        return hash.toString(16)
    }
}
