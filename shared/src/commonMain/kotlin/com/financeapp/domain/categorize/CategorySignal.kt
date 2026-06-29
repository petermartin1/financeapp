package com.financeapp.domain.categorize

import com.financeapp.data.categorize.ColdStartKnowledge

/**
 * One layer of the prediction cascade. Returns a [SignalResult] (a canonical category name plus a
 * confidence and reason) or null to defer to the next layer. Implementations must be pure and
 * side-effect free; the [CategoryPredictor] guards against exceptions.
 */
interface CategorySignal {
    val source: PredictionSource
    fun predict(input: PredictionInput): SignalResult?
}

/**
 * Wraps the trained per-user [TransactionCategoryModel] as the top cascade layer. Commits to the
 * top leaf when it clears [CategorizationThresholds.LEAF_MIN]; otherwise rolls sibling-leaf scores
 * up to their parent and commits there if the aggregate clears the floor; otherwise abstains.
 */
class UserModelSignal(
    private val model: TransactionCategoryModel,
    private val resolver: CategoryNameResolver,
    private val featureExtractor: FeatureExtractor = FeatureExtractor()
) : CategorySignal {

    override val source = PredictionSource.LEARNED_MODEL

    override fun predict(input: PredictionInput): SignalResult? {
        if (model.isEmpty) return null

        val features = featureExtractor.extract(input.merchantName, input.sic, input.amountCents)
        val scores = model.scores(features)
        if (scores.isEmpty()) return null

        val top = scores.maxByOrNull { it.value } ?: return null
        if (top.value >= CategorizationThresholds.LEAF_MIN) {
            val name = resolver.nameOf(top.key) ?: return null
            return SignalResult(
                categoryName = name,
                confidence = top.value,
                reason = "Similar to transactions you categorized as $name"
            )
        }

        // Leaf isn't confident on its own — aggregate scores under each leaf's parent.
        val byParent = HashMap<Long, Double>()
        for ((categoryId, score) in scores) {
            val parentId = resolver.parentIdOf(categoryId) ?: continue
            byParent[parentId] = (byParent[parentId] ?: 0.0) + score
        }
        val topParent = byParent.maxByOrNull { it.value } ?: return null
        if (topParent.value >= CategorizationThresholds.LEAF_MIN) {
            val name = resolver.nameOf(topParent.key) ?: return null
            return SignalResult(
                categoryName = name,
                confidence = topParent.value,
                reason = "Looks like $name based on your history"
            )
        }
        return null
    }
}

/** Bundled OFX SIC-code → category. Suggests (never auto-applies) via a fixed prior. */
class SicLexiconSignal(private val coldStart: ColdStartKnowledge) : CategorySignal {
    override val source = PredictionSource.SIC

    override fun predict(input: PredictionInput): SignalResult? {
        val category = coldStart.categoryForSic(input.sic) ?: return null
        return SignalResult(
            categoryName = category,
            confidence = CategorizationThresholds.SIC_PRIOR,
            reason = "Merchant category code ${input.sic?.trim()} is usually $category"
        )
    }
}

/** Bundled merchant-keyword → category, matched on the merchant name. */
class KeywordLexiconSignal(private val coldStart: ColdStartKnowledge) : CategorySignal {
    override val source = PredictionSource.KEYWORD

    override fun predict(input: PredictionInput): SignalResult? {
        val category = coldStart.categoryForName(input.merchantName) ?: return null
        return SignalResult(
            categoryName = category,
            confidence = CategorizationThresholds.KEYWORD_PRIOR,
            reason = "“${input.merchantName.trim()}” looks like $category"
        )
    }
}

/**
 * Last-resort default purely from the amount sign: credits → income, debits → a miscellaneous
 * expense. Always fires, at a deliberately low confidence, so the cascade can offer *something*.
 */
class AmountSignDefaultSignal(
    private val incomeCategoryName: String = "Other Income",
    private val expenseCategoryName: String = "Miscellaneous"
) : CategorySignal {
    override val source = PredictionSource.AMOUNT_SIGN

    override fun predict(input: PredictionInput): SignalResult {
        return if (input.amountCents >= 0) {
            SignalResult(incomeCategoryName, CategorizationThresholds.AMOUNT_SIGN, "Defaulted from a deposit")
        } else {
            SignalResult(expenseCategoryName, CategorizationThresholds.AMOUNT_SIGN, "Defaulted from a withdrawal")
        }
    }
}
