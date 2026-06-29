package com.financeapp.domain.categorize

import com.financeapp.data.categorize.ColdStartKnowledge
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategorySignalTest {

    private val coffee = Category(id = 3, name = "Coffee Shops", parentId = 1, type = CategoryType.EXPENSE)
    private val restaurants = Category(id = 2, name = "Restaurants", parentId = 1, type = CategoryType.EXPENSE)
    private val food = Category(id = 1, name = "Food & Dining", parentId = null, type = CategoryType.EXPENSE)
    private val gas = Category(id = 20, name = "Gas & Fuel", parentId = 21, type = CategoryType.EXPENSE)
    private val transport = Category(id = 21, name = "Transportation", parentId = null, type = CategoryType.EXPENSE)
    private val income = Category(id = 99, name = "Other Income", parentId = null, type = CategoryType.INCOME)
    private val misc = Category(id = 98, name = "Miscellaneous", parentId = null, type = CategoryType.EXPENSE)

    private val resolver = CategoryNameResolver(
        listOf(coffee, restaurants, food, gas, transport, income, misc)
    )

    private val coldStart = ColdStartKnowledge(
        sicCsv = "sicCode,categoryName\n5814,Restaurants\n5541,Gas & Fuel",
        keywordCsv = "keyword,categoryName\nstarbucks,Coffee Shops\nshell,Gas & Fuel"
    )

    private fun input(name: String, sic: String? = null, amount: Long = -1000) =
        PredictionInput(merchantName = name, sic = sic, amountCents = amount)

    // --- SIC signal ---

    @Test
    fun `sic signal maps a code to a category with the sic prior`() {
        val result = SicLexiconSignal(coldStart).predict(input("WHATEVER", sic = "5814"))!!
        assertEquals("Restaurants", result.categoryName)
        assertEquals(CategorizationThresholds.SIC_PRIOR, result.confidence)
    }

    @Test
    fun `sic signal abstains without a code`() {
        assertNull(SicLexiconSignal(coldStart).predict(input("WHATEVER", sic = null)))
        assertNull(SicLexiconSignal(coldStart).predict(input("WHATEVER", sic = "0000")))
    }

    // --- Keyword signal ---

    @Test
    fun `keyword signal matches a merchant name with the keyword prior`() {
        val result = KeywordLexiconSignal(coldStart).predict(input("STARBUCKS STORE 123"))!!
        assertEquals("Coffee Shops", result.categoryName)
        assertEquals(CategorizationThresholds.KEYWORD_PRIOR, result.confidence)
    }

    @Test
    fun `keyword signal abstains when nothing matches`() {
        assertNull(KeywordLexiconSignal(coldStart).predict(input("UNKNOWN MERCHANT")))
    }

    // --- Amount-sign default ---

    @Test
    fun `amount sign default returns income for credits and expense for debits`() {
        val signal = AmountSignDefaultSignal()
        assertEquals("Other Income", signal.predict(input("Anything", amount = 5000))!!.categoryName)
        assertEquals("Miscellaneous", signal.predict(input("Anything", amount = -5000))!!.categoryName)
        assertEquals(CategorizationThresholds.AMOUNT_SIGN, signal.predict(input("x", amount = -1))!!.confidence)
    }

    // --- User model signal ---

    @Test
    fun `user model signal returns a confident leaf prediction`() {
        val model = CategoryModelTrainer().train(
            buildList {
                repeat(8) { add(TrainingSample("Starbucks Coffee $it", null, -600, coffee.id)) }
                repeat(8) { add(TrainingSample("Shell Gas $it", null, -5000, gas.id)) }
            }
        )
        val signal = UserModelSignal(model, resolver)
        val result = signal.predict(input("Starbucks Coffee", amount = -650))!!
        assertEquals("Coffee Shops", result.categoryName)
        assertTrue(result.confidence >= CategorizationThresholds.LEAF_MIN)
    }

    @Test
    fun `user model signal abstains when the model is empty`() {
        assertNull(UserModelSignal(TransactionCategoryModel.EMPTY, resolver).predict(input("Starbucks")))
    }

    @Test
    fun `user model signal falls back to the parent when no single leaf is confident`() {
        // Two sibling leaves under Food & Dining, both plausible for the input, so the leaf score is
        // split but the parent aggregate is strong.
        val model = CategoryModelTrainer().train(
            buildList {
                repeat(6) { add(TrainingSample("Downtown Cafe Bistro $it", null, -2000, coffee.id)) }
                repeat(6) { add(TrainingSample("Downtown Cafe Bistro $it", null, -2000, restaurants.id)) }
                repeat(6) { add(TrainingSample("Shell Gas $it", null, -5000, gas.id)) }
            }
        )
        val result = UserModelSignal(model, resolver).predict(input("Downtown Cafe Bistro", amount = -2000))
        assertEquals("Food & Dining", result?.categoryName,
            "split sibling leaves should roll up to their parent")
    }
}
