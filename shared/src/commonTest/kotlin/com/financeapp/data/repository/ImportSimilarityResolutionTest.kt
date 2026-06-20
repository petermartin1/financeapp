package com.financeapp.data.repository

import com.financeapp.test.createTestDatabase
import com.financeapp.test.clearAllTables
import com.financeapp.domain.matching.PayeeMatcher
import com.financeapp.domain.model.Payee
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.exposed.sql.Database
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end checks on the real resolvePayeeNames path that drives the import payee-mapping
 * dialog: imported names must surface (a) similar EXISTING payees from the database, and
 * (b) similar names found earlier in the SAME file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportSimilarityResolutionTest {
    private lateinit var database: Database
    private lateinit var payeeRepo: PayeeRepositoryImpl
    private lateinit var matchingRepo: PayeeMatchingRepositoryImpl
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        payeeRepo = PayeeRepositoryImpl(database, dispatcher)
        matchingRepo = PayeeMatchingRepositoryImpl(database, PayeeMatcher(), dispatcher)
    }

    @AfterTest
    fun teardown() = database.clearAllTables()

    private suspend fun seedPayees(vararg names: String): List<Payee> {
        names.forEach { payeeRepo.insertPayee(Payee(name = it)) }
        return names.mapIndexed { i, n -> Payee(id = (i + 1).toLong(), name = n) }
    }

    @Test
    fun `surfaces similar existing payees for OFX names with processor prefixes and store numbers`() = runTest {
        val existing = seedPayees("Amazon", "Starbucks", "Blue Bottle", "Trader Joes")
        val imported = listOf(
            "AMAZON MKTPL*RT4GH",
            "STARBUCKS STORE 04123",
            "SQ *BLUE BOTTLE COFFEE",
            "TRADER JOES #546 ARLINGTON"
        )

        val result = matchingRepo.resolvePayeeNames(imported, existing, 0.75)

        val byName = result.needsReview.associateBy { it.importedName }
        assertEquals("Amazon", byName.getValue("AMAZON MKTPL*RT4GH").suggestedMatches.first().payee.name)
        assertEquals("Starbucks", byName.getValue("STARBUCKS STORE 04123").suggestedMatches.first().payee.name)
        assertEquals("Blue Bottle", byName.getValue("SQ *BLUE BOTTLE COFFEE").suggestedMatches.first().payee.name)
        assertEquals("Trader Joes", byName.getValue("TRADER JOES #546 ARLINGTON").suggestedMatches.first().payee.name)
    }

    @Test
    fun `surfaces names that are similar within the same file`() = runTest {
        val existing = seedPayees("Amazon") // unrelated existing payee
        val imported = listOf(
            "STARBUCKS STORE 04123",
            "STARBUCKS STORE 09988"
        )

        val result = matchingRepo.resolvePayeeNames(imported, existing, 0.75)

        val second = result.needsReview.first { it.importedName == "STARBUCKS STORE 09988" }
        assertTrue(
            second.similarInImport.contains("STARBUCKS STORE 04123"),
            "Second Starbucks line should be flagged as similar to the first one in the same file, " +
                "but similarInImport was ${second.similarInImport}"
        )
    }
}
