package com.financeapp.domain.categorize

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryModelTrainerTest {

    private val trainer = CategoryModelTrainer()

    // Category ids used in these tests
    private val coffee = 10L
    private val gas = 20L
    private val groceries = 30L

    private fun sample(name: String, categoryId: Long, amountCents: Long = -1000, sic: String? = null) =
        TrainingSample(merchantName = name, sic = sic, amountCents = amountCents, categoryId = categoryId)

    @Test
    fun `predicts the category whose training merchants match the input`() {
        val model = trainer.train(
            listOf(
                sample("Starbucks", coffee),
                sample("Starbucks Coffee", coffee),
                sample("Blue Bottle Coffee", coffee),
                sample("Shell Gas Station", gas),
                sample("Chevron Fuel", gas),
                sample("Whole Foods Market", groceries),
                sample("Safeway Grocery", groceries)
            )
        )

        val scores = model.scores(FeatureExtractor().extract("Starbucks Store 5567", sic = null, amountCents = -650))
        val top = scores.maxByOrNull { it.value }!!.key
        assertEquals(coffee, top, "a Starbucks purchase should score highest for the coffee category")
    }

    @Test
    fun `scores form a probability distribution that sums to one`() {
        val model = trainer.train(
            listOf(
                sample("Starbucks", coffee),
                sample("Shell Gas", gas),
                sample("Whole Foods", groceries)
            )
        )
        val scores = model.scores(FeatureExtractor().extract("Shell", sic = null, amountCents = -4000))
        assertEquals(3, scores.size)
        val sum = scores.values.sum()
        assertTrue(kotlin.math.abs(sum - 1.0) < 1e-6, "softmax scores should sum to 1 but summed to $sum")
        scores.values.forEach { assertTrue(it in 0.0..1.0) }
    }

    @Test
    fun `is robust to class imbalance via complement weighting`() {
        // Coffee massively over-represented; a clear gas merchant must still win for gas.
        val samples = buildList {
            repeat(40) { add(sample("Starbucks Coffee $it", coffee)) }
            add(sample("Shell Gas Station", gas))
            add(sample("Chevron Gas", gas))
        }
        val model = trainer.train(samples)
        val scores = model.scores(FeatureExtractor().extract("Shell Gas Station", sic = null, amountCents = -5000))
        assertEquals(gas, scores.maxByOrNull { it.value }!!.key,
            "complement NB should resist the dominant coffee class for a clear gas merchant")
    }

    @Test
    fun `an unknown merchant still yields a valid distribution`() {
        val model = trainer.train(listOf(sample("Starbucks", coffee), sample("Shell", gas)))
        val scores = model.scores(FeatureExtractor().extract("Zzqq Unrelated", sic = null, amountCents = -100))
        assertEquals(setOf(coffee, gas), scores.keys)
        assertTrue(kotlin.math.abs(scores.values.sum() - 1.0) < 1e-6)
    }

    @Test
    fun `empty training data produces an empty model that abstains`() {
        val model = trainer.train(emptyList())
        assertTrue(model.isEmpty)
        assertTrue(model.scores(listOf("w:starbucks")).isEmpty())
    }

    @Test
    fun `a single category is not enough to discriminate so the model abstains`() {
        val model = trainer.train(listOf(sample("Starbucks", coffee), sample("Starbucks Coffee", coffee)))
        assertTrue(model.isEmpty, "with only one class there is nothing to choose between; model should abstain")
    }
}
