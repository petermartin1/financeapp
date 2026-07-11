package com.financeapp.ui.reports

import com.financeapp.data.repository.AccountRepositoryImpl
import com.financeapp.data.repository.CategoryRepositoryImpl
import com.financeapp.data.repository.PayeeRepositoryImpl
import com.financeapp.data.repository.TagRepositoryImpl
import com.financeapp.data.repository.TransactionRepositoryImpl
import com.financeapp.domain.model.CategoryType
import com.financeapp.domain.model.ReportPeriod
import com.financeapp.domain.model.SplitItem
import com.financeapp.test.TestDataFactory
import com.financeapp.test.clearAllTables
import com.financeapp.test.createTestDatabase
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    private lateinit var database: Database
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var categoryRepository: CategoryRepositoryImpl
    private lateinit var accountRepository: AccountRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var viewModel: ReportsViewModel
    private val testDispatcher = StandardTestDispatcher()
    private var accountId: Long = 0

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        database = createTestDatabase()
        transactionRepository = TransactionRepositoryImpl(database, testDispatcher)
        categoryRepository = CategoryRepositoryImpl(database, testDispatcher)
        accountRepository = AccountRepositoryImpl(database, testDispatcher)
        tagRepository = TagRepositoryImpl(database, testDispatcher)
    }

    @AfterTest
    fun teardown() {
        if (::viewModel.isInitialized) viewModel.cleanup()
        testDispatcher.scheduler.advanceUntilIdle()
        database.clearAllTables()
        Dispatchers.resetMain()
    }

    @Test
    fun `spending report excludes negative transactions tagged with an income category`() = runTest(timeout = 10.seconds) {
        accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val groceries = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Groceries", type = CategoryType.EXPENSE)
        )
        val salary = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Salary", type = CategoryType.INCOME)
        )

        // A real expense, and a negative amount mis-tagged with an income category (e.g. a
        // payroll clawback). Only the expense should count as spending.
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -5000, categoryId = groceries)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -8000, categoryId = salary)
        )

        viewModel = ReportsViewModel(transactionRepository, accountRepository, categoryRepository, tagRepository)
        viewModel.setPeriod(ReportPeriod.ALL_TIME)

        var state: ReportsUiState? = null
        viewModel.uiState.test(timeout = 10.seconds) {
            while (true) {
                val s = awaitItem()
                if (!s.isLoading && s.spendingReport.categorySpending.isNotEmpty()) {
                    state = s
                    break
                }
            }
            cancelAndIgnoreRemainingEvents()
        }

        val report = state!!.spendingReport
        val names = report.categorySpending.map { it.categoryName }
        assertTrue(names.contains("Groceries"), "expense category should be counted, got $names")
        assertFalse(names.contains("Salary"), "income-typed category must not be counted as spending, got $names")
        assertEquals(5000L, report.totalSpent)
    }

    @Test
    fun `spending report attributes split amounts to each split category`() = runTest(timeout = 10.seconds) {
        accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val groceries = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Groceries", type = CategoryType.EXPENSE)
        )
        val transport = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Transport", type = CategoryType.EXPENSE)
        )

        // A $100 purchase split $60 Groceries / $40 Transport should be reported under both
        // categories, not entirely under the parent's category.
        val txnId = transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -10000, categoryId = groceries)
        )
        tagRepository.setSplitsForTransaction(
            txnId,
            listOf(
                SplitItem(transactionId = txnId, categoryId = groceries, amount = -6000),
                SplitItem(transactionId = txnId, categoryId = transport, amount = -4000)
            )
        )

        viewModel = ReportsViewModel(transactionRepository, accountRepository, categoryRepository, tagRepository)
        viewModel.setPeriod(ReportPeriod.ALL_TIME)

        var state: ReportsUiState? = null
        viewModel.uiState.test(timeout = 10.seconds) {
            while (true) {
                val s = awaitItem()
                if (!s.isLoading && s.spendingReport.categorySpending.isNotEmpty()) {
                    state = s
                    break
                }
            }
            cancelAndIgnoreRemainingEvents()
        }

        val report = state!!.spendingReport
        val byName = report.categorySpending.associate { it.categoryName to it.amount }
        assertEquals(6000L, byName["Groceries"])
        assertEquals(4000L, byName["Transport"])
        assertEquals(10000L, report.totalSpent)
    }

    private suspend fun awaitLoadedSpendingState(): ReportsUiState {
        var state: ReportsUiState? = null
        viewModel.uiState.test(timeout = 10.seconds) {
            while (true) {
                val s = awaitItem()
                if (!s.isLoading && s.spendingReport.categorySpending.isNotEmpty()) {
                    state = s
                    break
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
        return state!!
    }

    @Test
    fun `every slice total equals the sum of its drill-down lines, including splits`() = runTest(timeout = 10.seconds) {
        accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val groceries = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Groceries", type = CategoryType.EXPENSE)
        )
        val transport = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Transport", type = CategoryType.EXPENSE)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -2500, categoryId = groceries)
        )
        val splitTxnId = transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -10000, categoryId = groceries)
        )
        tagRepository.setSplitsForTransaction(
            splitTxnId,
            listOf(
                SplitItem(transactionId = splitTxnId, categoryId = groceries, amount = -6000),
                SplitItem(transactionId = splitTxnId, categoryId = transport, amount = -4000)
            )
        )

        viewModel = ReportsViewModel(transactionRepository, accountRepository, categoryRepository, tagRepository)
        viewModel.setPeriod(ReportPeriod.ALL_TIME)
        val report = awaitLoadedSpendingState().spendingReport

        // The invariant the whole feature protects: pie and panel come from the same lines.
        report.categorySpending.forEach { slice ->
            val lines = report.detailLinesByCategory[slice.categoryId]!!
            assertEquals(slice.amount, lines.sumOf { abs(it.lineAmountCents) }, "slice ${slice.categoryName}")
        }
        val transportLines = report.detailLinesByCategory[transport]!!
        assertEquals(1, transportLines.size)
        assertTrue(transportLines[0].isSplitPortion)
        assertEquals(-4000L, transportLines[0].lineAmountCents)
        assertEquals(splitTxnId, transportLines[0].source.transaction.id)
    }

    @Test
    fun `uncategorized outflows drill down under the 0 sentinel key`() = runTest(timeout = 10.seconds) {
        accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -3000, categoryId = null)
        )

        viewModel = ReportsViewModel(transactionRepository, accountRepository, categoryRepository, tagRepository)
        viewModel.setPeriod(ReportPeriod.ALL_TIME)
        val report = awaitLoadedSpendingState().spendingReport

        assertEquals(listOf("Uncategorized"), report.categorySpending.map { it.categoryName })
        assertEquals(0L, report.categorySpending[0].categoryId)
        assertEquals(-3000L, report.detailLinesByCategory[0L]!!.single().lineAmountCents)
    }

    @Test
    fun `selection toggles on repeat and clears on period change`() = runTest(timeout = 10.seconds) {
        accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val groceries = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Groceries", type = CategoryType.EXPENSE)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -2500, categoryId = groceries)
        )
        viewModel = ReportsViewModel(transactionRepository, accountRepository, categoryRepository, tagRepository)
        viewModel.setPeriod(ReportPeriod.ALL_TIME)
        awaitLoadedSpendingState()

        viewModel.selectSpendingCategory(groceries)
        assertEquals(groceries, viewModel.uiState.value.selectedSpendingCategoryId)
        viewModel.selectSpendingCategory(groceries)
        assertNull(viewModel.uiState.value.selectedSpendingCategoryId)

        viewModel.selectSpendingCategory(groceries)
        viewModel.setPeriod(ReportPeriod.ONE_MONTH)
        assertNull(viewModel.uiState.value.selectedSpendingCategoryId)
    }

    @Test
    fun `editing a transaction reloads the report and preserves a still-valid selection`() = runTest(timeout = 10.seconds) {
        accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val groceries = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Groceries", type = CategoryType.EXPENSE)
        )
        val dining = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Dining", type = CategoryType.EXPENSE)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -2500, categoryId = groceries)
        )
        val toRecategorizeId = transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -4000, categoryId = groceries)
        )
        viewModel = ReportsViewModel(transactionRepository, accountRepository, categoryRepository, tagRepository)
        viewModel.setPeriod(ReportPeriod.ALL_TIME)
        awaitLoadedSpendingState()
        viewModel.selectSpendingCategory(groceries)

        val toRecategorize = transactionRepository.getTransactionById(toRecategorizeId)!!
        viewModel.editTransaction(
            toRecategorize,
            categoryId = dining,
            memo = toRecategorize.memo,
            date = toRecategorize.date,
            isCleared = toRecategorize.isCleared,
            tagIds = emptyList()
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val byId = state.spendingReport.categorySpending.associate { it.categoryId to it.amount }
        assertEquals(2500L, byId[groceries])
        assertEquals(4000L, byId[dining])
        // Groceries still has lines, so the selection survives the reload.
        assertEquals(groceries, state.selectedSpendingCategoryId)
    }

    @Test
    fun `selection clears when its last line is recategorized away`() = runTest(timeout = 10.seconds) {
        accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val groceries = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Groceries", type = CategoryType.EXPENSE)
        )
        val dining = categoryRepository.insertCategory(
            TestDataFactory.createTestCategory(name = "Dining", type = CategoryType.EXPENSE)
        )
        val onlyId = transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = -2500, categoryId = groceries)
        )
        viewModel = ReportsViewModel(transactionRepository, accountRepository, categoryRepository, tagRepository)
        viewModel.setPeriod(ReportPeriod.ALL_TIME)
        awaitLoadedSpendingState()
        viewModel.selectSpendingCategory(groceries)

        val only = transactionRepository.getTransactionById(onlyId)!!
        viewModel.editTransaction(only, dining, only.memo, only.date, only.isCleared, emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedSpendingCategoryId)
    }
}
