package com.financeapp.domain.categorize

/**
 * Centralized, tunable confidence constants for the category predictor. Kept in one place so the
 * cascade's behaviour can be adjusted without hunting through the signals.
 */
object CategorizationThresholds {
    /** A leaf (or parent-aggregate) score must clear this for the learned model to commit to it. */
    const val LEAF_MIN = 0.60

    /** At or above this the import flow may apply a suggestion silently; below, it only suggests. */
    const val AUTO_APPLY = 0.85

    /** Fixed prior for a SIC-code lexicon hit — high enough to suggest, never to auto-apply. */
    const val SIC_PRIOR = 0.70

    /** Fixed prior for a merchant-keyword lexicon hit. */
    const val KEYWORD_PRIOR = 0.65

    /** Confidence for the last-resort income/expense default. */
    const val AMOUNT_SIGN = 0.30

    /**
     * Softmax temperature applied to the model's *standardized* WCNB logits (see
     * `TransactionCategoryModel.softmax`). Because the logits are z-scored per prediction, a clean
     * learned match consistently puts the top class ~3.3 standard deviations above the field
     * regardless of merchant-name length, so the temperature can be a stable constant: at this value
     * such a match lands above the 0.85 auto-apply floor, while a split/ambiguous match (top class
     * only ~1 sd out) stays near or below the 0.60 leaf floor and defers. Lower = more decisive.
     * Tuned empirically; revisit if feature extraction changes.
     */
    const val CONFIDENCE_TEMPERATURE = 0.8
}
