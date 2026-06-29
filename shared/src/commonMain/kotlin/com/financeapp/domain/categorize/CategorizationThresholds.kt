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
     * Softmax temperature for turning the model's WCNB logits into a confidence. Weight-normalized
     * complement scores sit on a compressed scale (~0.1–0.3 gaps for a strong match), so a
     * sub-1 temperature is needed to sharpen them into usable probabilities: a clean learned match
     * (~0.25 logit gap) lands around 0.85+, while a marginal match stays near the 0.60 floor.
     * Lower = more decisive. Tuned empirically; revisit if feature extraction changes.
     */
    const val CONFIDENCE_TEMPERATURE = 0.12
}
