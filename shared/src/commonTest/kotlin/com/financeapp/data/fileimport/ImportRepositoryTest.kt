package com.financeapp.data.fileimport

import com.financeapp.data.repository.AccountRepositoryImpl
import com.financeapp.data.repository.PayeeMatchingRepositoryImpl
import com.financeapp.data.repository.PayeeRepositoryImpl
import com.financeapp.data.repository.TagRepositoryImpl
import com.financeapp.data.repository.TransactionRepositoryImpl
import com.financeapp.domain.matching.PayeeMatcher
import com.financeapp.domain.model.PayeeMapping
import com.financeapp.domain.model.Tag
import com.financeapp.test.TestDataFactory
import com.financeapp.test.clearAllTables
import com.financeapp.test.createTestDatabase
import com.financeapp.test.testDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: Database
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var payeeRepository: PayeeRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var importRepository: ImportRepository

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        transactionRepository = TransactionRepositoryImpl(database, dispatcher)
        payeeRepository = PayeeRepositoryImpl(database, dispatcher)
        tagRepository = TagRepositoryImpl(database, dispatcher)
        importRepository = ImportRepository(
            transactionRepository = transactionRepository,
            payeeRepository = payeeRepository,
            accountRepository = AccountRepositoryImpl(database, dispatcher),
            payeeMatchingRepository = PayeeMatchingRepositoryImpl(database, PayeeMatcher(), dispatcher),
            tagRepository = tagRepository,
            database = database
        )
    }

    @AfterTest
    fun tearDown() {
        database.clearAllTables()
    }

    private suspend fun insertAccount(): Long =
        AccountRepositoryImpl(database, dispatcher).insertAccount(TestDataFactory.createTestAccount())

    private fun importedTxn(fitId: String, name: String) = ImportedTransaction(
        fitId = fitId,
        date = testDate(),
        amount = -5000,
        name = name,
        memo = null,
        checkNumber = null,
        type = TransactionType.DEBIT
    )

    private fun importedCheck(
        fitId: String,
        checkNumber: String? = "1234",
        name: String = "CHECK",
        type: TransactionType = TransactionType.CHECK
    ) = ImportedTransaction(
        fitId = fitId,
        date = testDate(),
        amount = -5000,
        name = name,
        memo = null,
        checkNumber = checkNumber,
        type = type
    )

    @Test
    fun `importWithMappings creates payee, transaction, alias and tags`() = runTest {
        val accountId = insertAccount()
        val tagId = tagRepository.insertTag(Tag(name = "Groceries", color = null))

        val mappings = mapOf(
            "WHOLE FOODS" to PayeeMapping(
                importedName = "WHOLE FOODS",
                resolvedPayeeId = null,
                createNew = true,
                newPayeeName = "Whole Foods",
                categoryId = null,
                tagIds = listOf(tagId),
                applyCategory = false,
                rememberMapping = true
            )
        )

        val result = importRepository.importWithMappings(listOf(importedTxn("F1", "WHOLE FOODS")), accountId, mappings)

        assertTrue(result.isSuccess, "import should succeed")
        assertEquals(1, result.getOrThrow().imported)
        assertTrue(payeeRepository.getAllPayees().first().any { it.name == "Whole Foods" })

        val saved = transactionRepository.getTransactionByImportId("F1")
        requireNotNull(saved) { "imported transaction should exist" }
        assertTrue(tagRepository.getTagsForTransaction(saved.id).any { it.id == tagId })
    }

    @Test
    fun `analyzeImportPayees excludes checks from payee resolution`() = runTest {
        insertAccount()

        val transactions = listOf(
            importedTxn("F1", "WHOLE FOODS"),
            importedCheck("F2", checkNumber = "1234", name = "CHECK 1234"),
            // A check identified only by TRNTYPE, with no check number, must also be excluded.
            importedCheck("F3", checkNumber = null, name = "CHECK", type = TransactionType.CHECK)
        )

        val result = importRepository.analyzeImportPayees(transactions).getOrThrow()

        val consideredNames = result.autoResolved.keys + result.needsReview.map { it.importedName }
        assertTrue("WHOLE FOODS" in consideredNames, "real payee should be considered")
        assertTrue(consideredNames.none { it.startsWith("CHECK") }, "checks must not be offered as payees")
    }

    @Test
    fun `importWithMappings imports a check with no payee but keeps the check number`() = runTest {
        val accountId = insertAccount()

        // Checks are excluded from analysis, so the user supplies no mapping for them.
        val result = importRepository.importWithMappings(
            listOf(importedCheck("C1", checkNumber = "1234", name = "CHECK 1234")),
            accountId,
            emptyMap()
        )

        assertTrue(result.isSuccess, "check import should succeed")
        assertEquals(1, result.getOrThrow().imported)
        assertTrue(payeeRepository.getAllPayees().first().isEmpty(), "no payee should be created for a check")

        val saved = transactionRepository.getTransactionByImportId("C1")
        requireNotNull(saved) { "imported check should exist" }
        assertNull(saved.payeeId, "check must have no payee assigned")
        assertEquals("1234", saved.checkNumber, "check number must be preserved for later payee assignment")
    }

    @Test
    fun `direct import does not create a payee for checks`() = runTest {
        val accountId = insertAccount()

        val result = importRepository.importPreviewedTransactions(
            listOf(
                importedTxn("D1", "WHOLE FOODS"),
                importedCheck("D2", checkNumber = "5678", name = "CHECK")
            ),
            accountId
        )

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().imported)

        val payeeNames = payeeRepository.getAllPayees().first().map { it.name }
        assertTrue(payeeNames.any { it.equals("WHOLE FOODS", ignoreCase = true) }, "real payee created")
        assertTrue(payeeNames.none { it.equals("CHECK", ignoreCase = true) }, "no payee created for the check")

        val check = transactionRepository.getTransactionByImportId("D2")
        requireNotNull(check)
        assertNull(check.payeeId, "check must have no payee assigned")
        assertEquals("5678", check.checkNumber)
    }

    @Test
    fun `direct import does not create a payee for a name-only check (untagged)`() = runTest {
        val accountId = insertAccount()

        // The bank sent the check as a plain DEBIT with no CHECKNUM — only "CHECK 1234" in the name.
        // This is the case the earlier fix missed: isCheck must catch it by name.
        val result = importRepository.importPreviewedTransactions(
            listOf(
                importedTxn("D1", "WHOLE FOODS"),
                importedTxn("D2", "CHECK 1234")
            ),
            accountId
        )

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().imported)

        val payeeNames = payeeRepository.getAllPayees().first().map { it.name }
        assertTrue(payeeNames.any { it.equals("WHOLE FOODS", ignoreCase = true) }, "real payee created")
        assertTrue(payeeNames.none { it.contains("CHECK", ignoreCase = true) }, "no payee created for the untagged check")

        val check = transactionRepository.getTransactionByImportId("D2")
        requireNotNull(check)
        assertNull(check.payeeId, "untagged check must have no payee assigned")
        assertEquals("1234", check.checkNumber, "check number must be recovered from the name and preserved")
    }

    @Test
    fun `direct import still creates a payee for a merchant whose name contains check`() = runTest {
        val accountId = insertAccount()

        val result = importRepository.importPreviewedTransactions(
            listOf(importedTxn("M1", "CHECKERS")),
            accountId
        )

        assertTrue(result.isSuccess)
        val payeeNames = payeeRepository.getAllPayees().first().map { it.name }
        assertTrue(payeeNames.any { it.equals("CHECKERS", ignoreCase = true) }, "Checkers is a real merchant, not a check")
    }

    @Test
    fun import_stores_original_name_for_check_and_non_check() = runTest(dispatcher) {
        val accountId = insertAccount()
        val checkImport = importedTxn("fit-chk", "CHECK 1234").copy(checkNumber = "1234")
        val store = importedTxn("fit-store", "SAFEWAY #123")

        importRepository.importPreviewedTransactions(listOf(checkImport, store), accountId).getOrThrow()

        val txns = transactionRepository.getTransactionsByAccount(accountId).first()
        val checkTxn = txns.first { it.importId == "fit-chk" }
        val storeTxn = txns.first { it.importId == "fit-store" }
        assertEquals("CHECK 1234", checkTxn.importedName)
        assertNull(checkTxn.payeeId)           // checks still get no payee
        assertEquals("SAFEWAY #123", storeTxn.importedName)
    }

    @Test
    fun importWithMappings_stores_original_name() = runTest(dispatcher) {
        val accountId = insertAccount()
        val store = importedTxn("fit-map", "SAFEWAY #123")
        val mappings = mapOf(
            "SAFEWAY #123" to PayeeMapping(
                importedName = "SAFEWAY #123",
                resolvedPayeeId = null,
                createNew = true,
                newPayeeName = "Safeway"
            )
        )

        importRepository.importWithMappings(listOf(store), accountId, mappings).getOrThrow()

        val txn = transactionRepository.getTransactionsByAccount(accountId).first()
            .first { it.importId == "fit-map" }
        assertEquals("SAFEWAY #123", txn.importedName)
    }

    @Test
    fun `importWithMappings rolls back everything when a step fails (atomicity)`() = runTest {
        val accountId = insertAccount()

        // Reference a tag id that does not exist -> FK violation when tagging the transaction,
        // which must roll back the payee and transaction inserted earlier in the same step.
        val mappings = mapOf(
            "ACME" to PayeeMapping(
                importedName = "ACME",
                resolvedPayeeId = null,
                createNew = true,
                newPayeeName = "Acme Corp",
                categoryId = null,
                tagIds = listOf(999_999L),
                applyCategory = false,
                rememberMapping = true
            )
        )

        val result = importRepository.importWithMappings(listOf(importedTxn("F2", "ACME")), accountId, mappings)

        assertTrue(result.isFailure, "import should fail on the bad tag reference")
        // Nothing must have been persisted — no orphaned payee, no transaction.
        assertTrue(payeeRepository.getAllPayees().first().none { it.name == "Acme Corp" }, "payee must be rolled back")
        assertNull(transactionRepository.getTransactionByImportId("F2"), "transaction must be rolled back")
    }
}
