package com.financeapp.domain.categorize

/**
 * Orchestrates the signal cascade for one imported transaction. Signals are tried in order; the
 * first whose result resolves to a category the user actually has wins ("first signal at/above its
 * floor wins"). Each signal already applies its own confidence floor, so the predictor just resolves
 * the name to an id, records whether it landed on a leaf, and returns.
 *
 * Robustness is a non-negotiable: any signal that throws is treated as "no suggestion" so a predictor
 * fault can never break an import. A fully empty cascade returns null (today's blank-category
 * behavior).
 */
class CategoryPredictor(
    private val signals: List<CategorySignal>,
    private val resolver: CategoryNameResolver
) {
    fun predict(input: PredictionInput): CategoryPrediction? {
        for (signal in signals) {
            val result = try {
                signal.predict(input)
            } catch (_: Throwable) {
                // Never let a predictor fault surface to the import path.
                null
            } ?: continue

            val categoryId = resolver.resolve(result.categoryName) ?: continue
            return CategoryPrediction(
                categoryId = categoryId,
                confidence = result.confidence,
                source = signal.source,
                matchedAtLeaf = resolver.isLeaf(categoryId),
                reason = result.reason
            )
        }
        return null
    }
}
