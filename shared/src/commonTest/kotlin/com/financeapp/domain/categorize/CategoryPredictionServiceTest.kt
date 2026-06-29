package com.financeapp.domain.categorize

import com.financeapp.data.categorize.ColdStartKnowledge
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CategoryPredictionServiceTest {

    private val coffee = Category(id = 3, name = "Coffee Shops", parentId = 1, type = CategoryType.EXPENSE)
    private val food = Category(id = 1, name = "Food & Dining", parentId = null, type = CategoryType.EXPENSE)
    private val gas = Category(id = 20, name = "Gas & Fuel", parentId = 21, type = CategoryType.EXPENSE)
    private val transport = Category(id = 21, name = "Transportation", parentId = null, type = CategoryType.EXPENSE)
    private val misc = Category(id = 98, name = "Miscellaneous", parentId = null, type = CategoryType.EXPENSE)
    private val income = Category(id = 99, name = "Other Income", parentId = null, type = CategoryType.INCOME)
    private val categories = listOf(coffee, food, gas, transport, misc, income)

    private val coldStart = ColdStartKnowledge(
        sicCsv = "sicCode,categoryName\n5814,Restaurants",
        keywordCsv = "keyword,categoryName\nstarbucks,Coffee Shops"
    )

    private fun input(name: String, sic: String? = null, amount: Long = -700) =
        PredictionInput(name, sic, amount)

    // The headline integration scenario from the design: a fresh DB with zero history still yields a
    // cold-start *suggestion*; after the user categorizes, the learned model takes over and can
    // auto-apply — overriding the bundled guess.
    @Test
    fun `cold start suggests, then the learned model takes over after categorizing`() = runTest {
        // Mutable "history" the model store trains from.
        var history = emptyList<TrainingSample>()
        val store = CategoryModelStore(trainingData = { history })
        val service = CategoryPredictionService(store, coldStart)

        // 1) Zero history -> cold-start keyword suggestion (suggest only, never auto-apply).
        val cold = service.newBatch(categories).predict(input("STARBUCKS #42"))!!
        assertEquals(coffee.id, cold.categoryId)
        assertEquals(PredictionSource.KEYWORD, cold.source)
        assertFalse(cold.autoApply)
        assertFalse(service.fillFor(cold, allowSuggestions = false).applyCategory,
            "without a review dialog a cold-start suggestion must not be applied")
        assertEquals(coffee.id, service.fillFor(cold, allowSuggestions = true).categoryId,
            "with a review dialog the suggestion is pre-filled for confirmation")

        // 2) User categorizes a bunch of Starbucks as Coffee Shops; model is invalidated.
        history = buildList {
            repeat(12) { add(TrainingSample("Starbucks Coffee $it", null, -650, coffee.id)) }
            repeat(12) { add(TrainingSample("Shell Gas $it", null, -5000, gas.id)) }
        }
        service.invalidate()

        // 3) Now the learned model wins and is confident enough to auto-apply.
        val learned = service.newBatch(categories).predict(input("Starbucks Coffee", amount = -650))!!
        assertEquals(coffee.id, learned.categoryId)
        assertEquals(PredictionSource.LEARNED_MODEL, learned.source)
        assertTrue(learned.autoApply)
        assertTrue(service.fillFor(learned, allowSuggestions = false).applyCategory,
            "a confident learned prediction applies even without review")
    }

    @Test
    fun `fillFor returns nothing for a null prediction`() {
        val service = CategoryPredictionService(CategoryModelStore({ emptyList() }), coldStart)
        val fill = service.fillFor(null, allowSuggestions = true)
        assertNull(fill.categoryId)
        assertFalse(fill.applyCategory)
        assertNull(fill.reason)
    }

    @Test
    fun `newBatch reflects the categories passed in`() = runTest {
        val service = CategoryPredictionService(CategoryModelStore({ emptyList() }), coldStart)
        // Without the Coffee Shops category the keyword target can't resolve, so it falls through
        // to the amount-sign default.
        val withoutCoffee = listOf(misc, income)
        val pred = service.newBatch(withoutCoffee).predict(input("STARBUCKS"))!!
        assertEquals(PredictionSource.AMOUNT_SIGN, pred.source)
    }
}
