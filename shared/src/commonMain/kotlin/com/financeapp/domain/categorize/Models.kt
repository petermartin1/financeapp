package com.financeapp.domain.categorize

import com.financeapp.data.fileimport.ImportedAccountType

/**
 * Everything the predictor needs about one imported transaction. Built by the import flow from an
 * `ImportedTransaction` and handed to [CategoryPredictor].
 */
data class PredictionInput(
    val merchantName: String,
    val sic: String?,
    val amountCents: Long,
    val accountType: ImportedAccountType? = null
)

/**
 * What a single [CategorySignal] returns: a canonical category *name* (resolved to the user's own
 * category id later), a 0..1 confidence, and a short human-readable reason to surface in the UI.
 */
data class SignalResult(
    val categoryName: String,
    val confidence: Double,
    val reason: String
)

/** Which layer of the cascade produced a prediction. */
enum class PredictionSource {
    LEARNED_MODEL,
    SIC,
    KEYWORD,
    AMOUNT_SIGN
}

/**
 * The predictor's answer: a concrete category id in the user's database, the winning source, its
 * confidence, whether it landed on a leaf category (vs. a parent fallback), and the reason string.
 */
data class CategoryPrediction(
    val categoryId: Long,
    val confidence: Double,
    val source: PredictionSource,
    val matchedAtLeaf: Boolean,
    val reason: String
) {
    /** Whether the import flow may apply this silently instead of merely suggesting it. */
    val autoApply: Boolean get() = confidence >= CategorizationThresholds.AUTO_APPLY
}

/** One categorized transaction used to train the per-user model. */
data class TrainingSample(
    val merchantName: String,
    val sic: String?,
    val amountCents: Long,
    val categoryId: Long
)
