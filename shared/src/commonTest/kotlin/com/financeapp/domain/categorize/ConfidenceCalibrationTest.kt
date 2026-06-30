package com.financeapp.domain.categorize

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Calibration tests for the learned model's confidence at realistic category counts.
 *
 * Regression guard for the bug where confidence collapsed toward 1/N once the user had many
 * categories: a clean exact-match prediction scored ~0.09 across a dozen categories, never clearing
 * the leaf (0.60) or auto-apply (0.85) floors, so the learned model effectively never fired. The fix
 * standardizes the per-class logits before the softmax, so confidence reflects how far the top class
 * stands out from the field rather than the absolute (name-length-dependent) logit scale.
 */
class ConfidenceCalibrationTest {

    private val fx = FeatureExtractor()

    private fun s(name: String, cat: Long, amt: Long = -1000) =
        TrainingSample(merchantName = name, sic = null, amountCents = amt, categoryId = cat)

    /** A dozen distinct categories, each with a couple of clearly-separable merchants. */
    private fun manyCategoryModel(): TransactionCategoryModel = CategoryModelTrainer().train(
        buildList {
            add(s("Starbucks", 1)); add(s("Starbucks", 1)); add(s("Peets Coffee", 1))
            add(s("Shell", 2)); add(s("Chevron", 2)); add(s("Exxon", 2))
            add(s("Whole Foods", 3)); add(s("Safeway", 3)); add(s("Kroger", 3))
            add(s("Netflix", 4)); add(s("Hulu", 4))
            add(s("Comcast Internet", 5)); add(s("Xfinity", 5))
            add(s("Delta Airlines", 6)); add(s("United Airlines", 6))
            add(s("Marriott Hotel", 7)); add(s("Hilton", 7))
            add(s("CVS Pharmacy", 8)); add(s("Walgreens", 8))
            add(s("Home Depot", 9)); add(s("Lowes", 9))
            add(s("Planet Fitness", 10)); add(s("LA Fitness", 10))
            add(s("Amazon", 11)); add(s("Target", 11))
        }
    )

    private fun topConfidence(model: TransactionCategoryModel, name: String): Double =
        model.scores(fx.extract(name, sic = null, amountCents = -650)).values.max()

    @Test
    fun `exact-match merchant clears the auto-apply floor across many categories`() {
        val model = manyCategoryModel()
        val conf = topConfidence(model, "Starbucks")
        assertTrue(
            conf >= CategorizationThresholds.AUTO_APPLY,
            "a clean exact-match across many categories should be confident enough to auto-apply, was $conf"
        )
    }

    @Test
    fun `confidence is calibrated independent of merchant name length`() {
        // "Shell" (5 chars, few trigrams) and "Comcast Internet" (long, many trigrams) are both exact
        // repeats of a single category. Their confidence should be comparable, not differ by the
        // raw logit scale that name length induces.
        val model = manyCategoryModel()
        val shortName = topConfidence(model, "Shell")
        val longName = topConfidence(model, "Comcast Internet")
        assertTrue(
            kotlin.math.abs(shortName - longName) < 0.10,
            "equally-clean matches of different name lengths should be similarly confident, " +
                "were short=$shortName long=$longName"
        )
    }

    @Test
    fun `an unknown merchant stays below the leaf floor so the cascade defers`() {
        val model = manyCategoryModel()
        // Either an outright abstention (empty) or a sub-floor score is acceptable here; what must not
        // happen is a confident commitment to a category for a merchant the model has never seen.
        val scores = model.scores(fx.extract("Zzyx Unrelated Vendor", sic = null, amountCents = -650))
        val conf = scores.values.maxOrNull() ?: 0.0
        assertTrue(
            conf < CategorizationThresholds.LEAF_MIN,
            "a merchant matching nothing in the model should not be confident, was $conf"
        )
    }
}
