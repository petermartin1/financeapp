package com.financeapp.domain.categorize

import com.financeapp.data.categorize.ColdStartKnowledge
import com.financeapp.domain.model.Category

/**
 * Application-facing entry point for category prediction. Owns the bundled cold-start knowledge and
 * the per-user model cache, and assembles a fresh [CategoryPredictor] for each import batch from the
 * user's current categories (so renamed/added categories are always reflected).
 *
 * The import flow uses [newBatch] once per import and [fillFor] per imported name to turn a raw
 * prediction into the apply-vs-suggest decision. [invalidate] is called after an import completes or
 * whenever transactions are re-categorized, so the learned model retrains lazily and takes over from
 * the cold-start guesses as it improves.
 */
class CategoryPredictionService(
    private val modelStore: CategoryModelStore,
    private val coldStart: ColdStartKnowledge = ColdStartKnowledge.bundled(),
    private val featureExtractor: FeatureExtractor = FeatureExtractor()
) {
    /** Build the cascade for one import batch against the user's current category list. */
    suspend fun newBatch(categories: List<Category>): CategoryPredictor {
        val resolver = CategoryNameResolver(categories)
        val model = modelStore.model()
        return CategoryPredictor(
            signals = listOf(
                UserModelSignal(model, resolver, featureExtractor),
                SicLexiconSignal(coldStart),
                KeywordLexiconSignal(coldStart),
                AmountSignDefaultSignal()
            ),
            resolver = resolver
        )
    }

    /** Drop the cached model so the next batch retrains from fresh data. */
    fun invalidate() = modelStore.invalidate()

    /**
     * Decide what a prediction means for an imported name's category mapping.
     *
     * @param allowSuggestions true when the name will pass through the review dialog (so a
     *   below-auto-apply suggestion can be pre-filled for the user to confirm); false when the name
     *   imports without review (so only confident, auto-apply predictions may be applied silently).
     */
    fun fillFor(prediction: CategoryPrediction?, allowSuggestions: Boolean): CategoryFill = when {
        prediction == null -> CategoryFill.NONE
        prediction.autoApply -> CategoryFill(prediction.categoryId, applyCategory = true, reason = prediction.reason)
        allowSuggestions -> CategoryFill(prediction.categoryId, applyCategory = false, reason = prediction.reason)
        else -> CategoryFill.NONE
    }
}

/**
 * The resolved category decision for one imported name: which category (if any), whether to apply it
 * on import (vs. merely pre-fill it as an editable suggestion), and the reason to show.
 */
data class CategoryFill(
    val categoryId: Long?,
    val applyCategory: Boolean,
    val reason: String?
) {
    companion object {
        val NONE = CategoryFill(categoryId = null, applyCategory = false, reason = null)
    }
}
