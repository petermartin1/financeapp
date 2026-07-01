package com.financeapp.data.categorize

import com.financeapp.data.seed.DefaultCategories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColdStartKnowledgeTest {

    private val sicCsv = """
        sicCode,categoryName
        5814,Restaurants
        5541,Gas & Fuel
        5411,Groceries
    """.trimIndent()

    private val keywordCsv = """
        keyword,categoryName
        starbucks,Coffee Shops
        whole foods,Groceries
        shell,Gas & Fuel
        netflix,Cable/Streaming
    """.trimIndent()

    private val knowledge = ColdStartKnowledge(sicCsv, keywordCsv)

    @Test
    fun `maps a sic code to its category name`() {
        assertEquals("Restaurants", knowledge.categoryForSic("5814"))
        assertEquals("Gas & Fuel", knowledge.categoryForSic("5541"))
    }

    @Test
    fun `returns null for an unknown or blank sic code`() {
        assertNull(knowledge.categoryForSic("9999"))
        assertNull(knowledge.categoryForSic(null))
        assertNull(knowledge.categoryForSic("  "))
    }

    @Test
    fun `matches a keyword as a whole word inside a merchant name`() {
        assertEquals("Coffee Shops", knowledge.categoryForName("STARBUCKS STORE 5567"))
        assertEquals("Gas & Fuel", knowledge.categoryForName("Shell Oil 12345"))
        assertEquals("Cable/Streaming", knowledge.categoryForName("NETFLIX.COM"))
    }

    @Test
    fun `matches a multi-word keyword`() {
        assertEquals("Groceries", knowledge.categoryForName("WHOLE FOODS MKT #123"))
    }

    @Test
    fun `does not match a keyword embedded mid-word`() {
        // "shell" must not fire for "michelle"
        assertNull(knowledge.categoryForName("MICHELLE SALON"))
    }

    @Test
    fun `prefers the longest matching keyword`() {
        val k = ColdStartKnowledge(
            sicCsv = "sicCode,categoryName",
            keywordCsv = "keyword,categoryName\namazon,Shopping\namazon prime,Cable/Streaming"
        )
        assertEquals("Cable/Streaming", k.categoryForName("AMAZON PRIME VIDEO"))
    }

    @Test
    fun `skips malformed and blank lines without failing`() {
        val messy = ColdStartKnowledge(
            sicCsv = "sicCode,categoryName\n5814,Restaurants\ngarbage-line\n\n,EmptyCode\n7777,",
            keywordCsv = "keyword,categoryName\n\nstarbucks,Coffee Shops\nbadrow"
        )
        assertEquals("Restaurants", messy.categoryForSic("5814"))
        assertNull(messy.categoryForSic("7777"), "row with a blank category name is skipped")
        assertEquals("Coffee Shops", messy.categoryForName("Starbucks"))
    }

    @Test
    fun `the bundled knowledge resolves common real-world merchants`() {
        val bundled = ColdStartKnowledge.bundled()
        assertEquals("Coffee Shops", bundled.categoryForName("STARBUCKS #12345"))
        assertTrue(bundled.categoryForSic("5814") != null, "the restaurant SIC code should be bundled")
    }

    @Test
    fun `the bundled MCC table covers the common credit-card categories`() {
        val b = ColdStartKnowledge.bundled()
        // A spread of merchant-category codes a typical card statement carries.
        assertEquals("Groceries", b.categoryForSic("5411"))
        assertEquals("Hotels", b.categoryForSic("7011"))
        assertEquals("Airfare", b.categoryForSic("4511"))
        assertEquals("Clothing", b.categoryForSic("5651"))
        assertEquals("Electronics", b.categoryForSic("5732"))
        assertEquals("Auto Repairs", b.categoryForSic("7538"))
        assertEquals("Sports", b.categoryForSic("5941"))
        assertEquals("Hobbies", b.categoryForSic("5945"))
        assertEquals("Vacation Activities", b.categoryForSic("7996"))
        assertEquals("Childcare", b.categoryForSic("8351"))
        assertEquals("Donations & Charity", b.categoryForSic("8398"))
        assertEquals("ATM Fees", b.categoryForSic("6011"))
        assertEquals("Utilities", b.categoryForSic("4900"))
        assertEquals("Internet", b.categoryForSic("4816"))
    }

    @Test
    fun `the bundled MCC table is substantially larger than the original seed`() {
        val codes = ColdStartData.SIC_CSV.lineSequence().drop(1)
            .filter { it.isNotBlank() }.count()
        assertTrue(codes >= 100, "expected a broad MCC table, only had $codes codes")
    }

    @Test
    fun `every bundled cold-start category name is a real default category`() {
        val valid: Set<String> = DefaultCategories.allCategories
            .flatMap { listOf(it.name) + it.subcategories.map { s -> s.name } }
            .map { it.trim().lowercase() }
            .toSet()

        fun categoryNames(csv: String): List<String> =
            csv.lineSequence().drop(1)
                .mapNotNull { line ->
                    if (line.isBlank()) return@mapNotNull null
                    val parts = line.split(',')
                    if (parts.size != 2) null else parts[1].trim()
                }
                .toList()

        val offenders = (categoryNames(ColdStartData.SIC_CSV) + categoryNames(ColdStartData.MERCHANT_KEYWORDS_CSV))
            .filter { it.lowercase() !in valid }
            .distinct()

        assertTrue(offenders.isEmpty(), "cold-start data references categories that don't exist: $offenders")
    }
}
