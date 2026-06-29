package com.financeapp.domain.categorize

/**
 * Turns a transaction's merchant name (plus SIC code and amount sign) into a bag of feature
 * tokens shared by both the trainer and the model, so training and prediction see identical
 * features. Pure, deterministic, and offline.
 *
 * Feature families (each token is namespaced so they never collide):
 *  - `w:<word>`   normalized merchant words (digits stripped, store numbers collapsed)
 *  - `w:#num`     a single placeholder emitted when the name contained any digits
 *  - `c:<trigram>` character 3-grams of each normalized word (robust to spelling/spacing drift)
 *  - `sic:<code>` the SIC code, when present
 *  - `sign:debit|credit` the amount sign
 */
class FeatureExtractor {

    fun extract(merchantName: String, sic: String?, amountCents: Long): List<String> {
        val features = mutableListOf<String>()

        val lower = merchantName.lowercase()
        val hadDigit = lower.any { it.isDigit() }

        // Drop digit runs (store numbers, card suffixes) and fold everything that isn't a letter
        // into a separator, so the surviving tokens are pure merchant words.
        val cleaned = buildString {
            for (ch in lower) {
                append(if (ch.isLetter()) ch else ' ')
            }
        }

        for (word in cleaned.split(' ')) {
            if (word.isEmpty()) continue
            features.add("$WORD_PREFIX$word")
            features.addAll(trigrams(word))
        }

        if (hadDigit) features.add(NUMBER_TOKEN)

        if (!sic.isNullOrBlank()) features.add("$SIC_PREFIX${sic.trim()}")

        features.add(if (amountCents < 0) SIGN_DEBIT else SIGN_CREDIT)

        return features
    }

    private fun trigrams(word: String): List<String> {
        if (word.length < 3) return emptyList()
        val out = ArrayList<String>(word.length - 2)
        for (i in 0..word.length - 3) {
            out.add("$TRIGRAM_PREFIX${word.substring(i, i + 3)}")
        }
        return out
    }

    companion object {
        const val WORD_PREFIX = "w:"
        const val TRIGRAM_PREFIX = "c:"
        const val SIC_PREFIX = "sic:"
        const val NUMBER_TOKEN = "w:#num"
        const val SIGN_PREFIX = "sign:"
        const val SIGN_DEBIT = "sign:debit"
        const val SIGN_CREDIT = "sign:credit"
    }
}
