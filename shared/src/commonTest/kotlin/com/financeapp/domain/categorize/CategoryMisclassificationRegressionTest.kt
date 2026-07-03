package com.financeapp.domain.categorize

import com.financeapp.data.categorize.ColdStartKnowledge
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression for the "medical shows up as dining" misclassification reported on real OFX imports
 * with a lot of categorized history.
 *
 * The card network stamps a medical transaction with an authoritative MCC (SIC 8011 -> Doctor/
 * Medical). But the learned model runs FIRST in the cascade, and its discriminative-overlap gate
 * accepts as little as two shared character trigrams. A medical merchant ("Quest Diagnostics")
 * shares the coincidental trigrams "que"/"ues" with a restaurant the user categorized before
 * ("Quesadilla ...") — no shared *word*, just letter fragments — yet that is enough for the model to
 * fire a confident "Restaurants" and preempt the correct SIC signal.
 */
class CategoryMisclassificationRegressionTest {

    private val dining = Category(id = 1, name = "Food & Dining", parentId = null, type = CategoryType.EXPENSE)
    private val restaurants = Category(id = 2, name = "Restaurants", parentId = 1, type = CategoryType.EXPENSE)
    private val health = Category(id = 50, name = "Health", parentId = null, type = CategoryType.EXPENSE)
    private val doctor = Category(id = 51, name = "Doctor/Medical", parentId = 50, type = CategoryType.EXPENSE)
    private val gasFuel = Category(id = 20, name = "Gas & Fuel", parentId = null, type = CategoryType.EXPENSE)
    private val income = Category(id = 99, name = "Other Income", parentId = null, type = CategoryType.INCOME)
    private val misc = Category(id = 98, name = "Miscellaneous", parentId = null, type = CategoryType.EXPENSE)
    private val categories = listOf(dining, restaurants, health, doctor, gasFuel, income, misc)

    private val resolver = CategoryNameResolver(categories)
    private val coldStart = ColdStartKnowledge(
        sicCsv = "sicCode,categoryName\n8011,Doctor/Medical",
        keywordCsv = "keyword,categoryName"
    )

    private fun predictor(model: TransactionCategoryModel) = CategoryPredictor(
        signals = listOf(
            UserModelSignal(model, resolver),
            SicLexiconSignal(coldStart),
            KeywordLexiconSignal(coldStart),
            AmountSignDefaultSignal()
        ),
        resolver = resolver
    )

    @Test
    fun `an authoritative medical SIC is not overridden by a coincidental-trigram learned guess`() {
        // Lots of real dining history; none of it is a medical merchant. The only thing tying the
        // incoming "Quest Diagnostics" to any of it is the accidental "que"/"ues" trigram fragment.
        val model = CategoryModelTrainer().train(
            buildList {
                repeat(20) { add(TrainingSample("Quesadilla Cantina $it", null, -2500, restaurants.id)) }
                repeat(20) { add(TrainingSample("Chevron Fuel $it", "5541", -4000, gasFuel.id)) }
            }
        )

        val prediction = predictor(model).predict(
            PredictionInput(merchantName = "Quest Diagnostics Lab", sic = "8011", amountCents = -18000)
        )!!

        assertEquals(
            doctor.id,
            prediction.categoryId,
            "an 8011 (physician) MCC must categorize as Doctor/Medical, not be preempted by a " +
                "coincidental-trigram learned guess (got source=${prediction.source})"
        )
    }
}