package com.financeapp.domain.categorize

import com.financeapp.data.categorize.ColdStartKnowledge
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CategoryPredictorTest {

    private val food = Category(id = 1, name = "Food & Dining", parentId = null, type = CategoryType.EXPENSE)
    private val restaurants = Category(id = 2, name = "Restaurants", parentId = 1, type = CategoryType.EXPENSE)
    private val coffee = Category(id = 3, name = "Coffee Shops", parentId = 1, type = CategoryType.EXPENSE)
    private val gasFuel = Category(id = 20, name = "Gas & Fuel", parentId = 21, type = CategoryType.EXPENSE)
    private val transport = Category(id = 21, name = "Transportation", parentId = null, type = CategoryType.EXPENSE)
    private val income = Category(id = 99, name = "Other Income", parentId = null, type = CategoryType.INCOME)
    private val misc = Category(id = 98, name = "Miscellaneous", parentId = null, type = CategoryType.EXPENSE)
    private val categories = listOf(food, restaurants, coffee, gasFuel, transport, income, misc)

    private val resolver = CategoryNameResolver(categories)
    private val coldStart = ColdStartKnowledge(
        sicCsv = "sicCode,categoryName\n5814,Restaurants\n5541,Gas & Fuel",
        keywordCsv = "keyword,categoryName\nstarbucks,Coffee Shops\nshell,Gas & Fuel"
    )

    private fun input(name: String, sic: String? = null, amount: Long = -1000) =
        PredictionInput(merchantName = name, sic = sic, amountCents = amount)

    private fun coldStartPredictor(model: TransactionCategoryModel = TransactionCategoryModel.EMPTY) =
        CategoryPredictor(
            signals = listOf(
                UserModelSignal(model, resolver),
                SicLexiconSignal(coldStart),
                KeywordLexiconSignal(coldStart),
                AmountSignDefaultSignal()
            ),
            resolver = resolver
        )

    @Test
    fun `cold start keyword suggestion fires when there is no learned model`() {
        val prediction = coldStartPredictor().predict(input("STARBUCKS #123"))!!
        assertEquals(coffee.id, prediction.categoryId)
        assertEquals(PredictionSource.KEYWORD, prediction.source)
        assertTrue(prediction.matchedAtLeaf)
        assertFalse(prediction.autoApply, "cold-start signals must only suggest, never auto-apply")
    }

    @Test
    fun `sic outranks keyword in the cascade`() {
        // Name matches the 'shell' keyword (Gas & Fuel) but SIC says Restaurants; SIC comes first.
        val prediction = coldStartPredictor().predict(input("SHELL CAFE", sic = "5814"))!!
        assertEquals(restaurants.id, prediction.categoryId)
        assertEquals(PredictionSource.SIC, prediction.source)
    }

    @Test
    fun `the learned model outranks cold-start signals`() {
        val model = CategoryModelTrainer().train(
            buildList {
                // Teach the model that "Shell" is actually groceries-like restaurants for this user,
                // contradicting the cold-start lexicons, to prove the model wins.
                repeat(8) { add(TrainingSample("Shell Corner Store $it", "5541", -3000, restaurants.id)) }
                repeat(8) { add(TrainingSample("Chevron $it", "5541", -4000, gasFuel.id)) }
            }
        )
        val prediction = coldStartPredictor(model).predict(input("Shell Corner Store", sic = "5541"))!!
        assertEquals(restaurants.id, prediction.categoryId)
        assertEquals(PredictionSource.LEARNED_MODEL, prediction.source)
    }

    @Test
    fun `a confident learned match can auto-apply`() {
        val model = CategoryModelTrainer().train(
            buildList {
                repeat(10) { add(TrainingSample("Starbucks Coffee $it", null, -600, coffee.id)) }
                repeat(10) { add(TrainingSample("Shell Gas $it", null, -5000, gasFuel.id)) }
            }
        )
        val prediction = coldStartPredictor(model).predict(input("Starbucks Coffee", amount = -650))!!
        assertEquals(coffee.id, prediction.categoryId)
        assertTrue(prediction.autoApply, "a clean learned match should clear the auto-apply threshold")
    }

    @Test
    fun `amount-sign default is the last resort`() {
        // No SIC, no keyword, no model -> falls all the way through to the sign default.
        val expense = coldStartPredictor().predict(input("Totally Unknown Merchant", amount = -1234))!!
        assertEquals(misc.id, expense.categoryId)
        assertEquals(PredictionSource.AMOUNT_SIGN, expense.source)

        val deposit = coldStartPredictor().predict(input("Mystery Deposit", amount = 50000))!!
        assertEquals(income.id, deposit.categoryId)
    }

    @Test
    fun `skips a signal whose category name the user does not have`() {
        // A cold-start map pointing at a category absent from the user's DB must be skipped, not crash.
        val cs = ColdStartKnowledge(
            sicCsv = "sicCode,categoryName",
            keywordCsv = "keyword,categoryName\nfoo,Nonexistent Category\nstarbucks,Coffee Shops"
        )
        val predictor = CategoryPredictor(
            signals = listOf(KeywordLexiconSignal(cs), AmountSignDefaultSignal()),
            resolver = resolver
        )
        // "foo" resolves to a missing category -> keyword signal result is dropped, falls to default.
        val pred = predictor.predict(input("foo bar", amount = -100))!!
        assertEquals(PredictionSource.AMOUNT_SIGN, pred.source)
    }

    @Test
    fun `a throwing signal never breaks prediction`() {
        val boom = object : CategorySignal {
            override val source = PredictionSource.LEARNED_MODEL
            override fun predict(input: PredictionInput): SignalResult? = throw IllegalStateException("boom")
        }
        val predictor = CategoryPredictor(
            signals = listOf(boom, KeywordLexiconSignal(coldStart), AmountSignDefaultSignal()),
            resolver = resolver
        )
        val pred = predictor.predict(input("STARBUCKS"))!!
        assertEquals(coffee.id, pred.categoryId, "should recover and use the next signal after a crash")
    }

    @Test
    fun `returns null when no signal yields a usable category`() {
        // Only a model that abstains; nothing else. Result must be null (today's blank behavior).
        val predictor = CategoryPredictor(
            signals = listOf(UserModelSignal(TransactionCategoryModel.EMPTY, resolver)),
            resolver = resolver
        )
        assertNull(predictor.predict(input("Whatever", amount = -100)))
    }
}
