package com.financeapp.ui.reports

import com.financeapp.data.repository.AccountRepositoryImpl
import com.financeapp.data.repository.CategoryRepositoryImpl
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
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    private lateinit var database: Database
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var categoryRepository: CategoryRepositoryImpl
    private lateinit var accountRepository: AccountRepositoryImpl
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

        viewModel = ReportsViewModel(transactionRepository, accountRepository, categoryRepository)
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
        val tagRepository = TagRepositoryImpl(database, testDispatcher)
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

        viewModel = ReportsViewModel(transactionRepository, accountRepository, categoryRepository)
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
}
