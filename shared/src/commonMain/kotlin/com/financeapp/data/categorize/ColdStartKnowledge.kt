package com.financeapp.data.categorize

/**
 * Bundled, offline cold-start knowledge: two small CSV lexicons mapping OFX SIC codes and common
 * merchant keywords to canonical category *names* (resolved to the user's category ids at runtime).
 * This is what makes the very first import useful before any per-user model exists.
 *
 * The data is supplied as CSV text (see [bundled]); parsing tolerates blank and malformed rows so a
 * garbled entry silently drops out rather than breaking an import.
 */
class ColdStartKnowledge(
    sicCsv: String,
    keywordCsv: String
) {
    private val sicToCategory: Map<String, String> = parse(sicCsv) { code -> code }
    // Keywords sorted longest-first so the most specific phrase wins (e.g. "amazon prime" beats
    // "amazon"). Each is matched as a whole word/phrase against the lowercased merchant name.
    private val keywordEntries: List<Pair<String, String>> =
        parse(keywordCsv) { it.lowercase() }
            .entries
            .sortedByDescending { it.key.length }
            .map { it.key to it.value }

    fun categoryForSic(sic: String?): String? {
        val code = sic?.trim() ?: return null
        if (code.isEmpty()) return null
        return sicToCategory[code]
    }

    fun categoryForName(merchantName: String): String? {
        val name = merchantName.lowercase()
        for ((keyword, category) in keywordEntries) {
            if (containsWord(name, keyword)) return category
        }
        return null
    }

    /** Whole-word/phrase containment: the keyword must be bounded by non-letters (or string ends). */
    private fun containsWord(haystack: String, needle: String): Boolean {
        var from = 0
        while (true) {
            val idx = haystack.indexOf(needle, from)
            if (idx < 0) return false
            val before = idx - 1
            val after = idx + needle.length
            val boundedLeft = before < 0 || !haystack[before].isLetter()
            val boundedRight = after >= haystack.length || !haystack[after].isLetter()
            if (boundedLeft && boundedRight) return true
            from = idx + 1
        }
    }

    /**
     * Parses a two-column `key,categoryName` CSV. The first row is treated as a header and skipped.
     * Rows without exactly the two non-blank fields are ignored. [keyTransform] normalizes the key.
     */
    private fun parse(csv: String, keyTransform: (String) -> String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        csv.lineSequence()
            .drop(1) // header
            .forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split(',')
                if (parts.size != 2) return@forEach
                val key = parts[0].trim()
                val category = parts[1].trim()
                if (key.isEmpty() || category.isEmpty()) return@forEach
                out[keyTransform(key)] = category
            }
        return out
    }

    companion object {
        /** The cold-start knowledge shipped with the app. */
        fun bundled(): ColdStartKnowledge =
            ColdStartKnowledge(ColdStartData.SIC_CSV, ColdStartData.MERCHANT_KEYWORDS_CSV)
    }
}
