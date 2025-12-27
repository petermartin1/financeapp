package com.financeapp.ui.accounts

import com.financeapp.test.*
import com.financeapp.data.repository.*
import com.financeapp.domain.model.AccountType
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import org.jetbrains.exposed.sql.Database
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * ViewModel tests using real repositories (integration-style testing)
 * More valuable than mocked tests as they verify actual data flow
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {

    private lateinit var database: Database
    private lateinit var accountRepository: AccountRepositoryImpl
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var viewModel: AccountsViewModel
    private val testDispatcher = StandardTestDispatcher()

    // Helper function to wait for a specific state condition
    private suspend fun waitForState(
        timeout: kotlin.time.Duration = 10.seconds,
        predicate: (AccountsUiState) -> Boolean
    ): AccountsUiState {
        var result: AccountsUiState? = null
        viewModel.uiState.test(timeout = timeout) {
            while (true) {
                val state = awaitItem()
                if (predicate(state)) {
                    result = state
                    break
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
        return result!!
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        database = createTestDatabase()
        accountRepository = AccountRepositoryImpl(database, testDispatcher)
        transactionRepository = TransactionRepositoryImpl(database, testDispatcher)

        viewModel = AccountsViewModel(accountRepository)
    }

    @AfterTest
    fun teardown() {
        // Cleanup ViewModel to cancel all background coroutines
        viewModel.cleanup()
        // Advance to process cancellation
        testDispatcher.scheduler.advanceUntilIdle()
        // Clear database
        database.clearAllTables()
        // Reset dispatcher
        Dispatchers.resetMain()
    }

    // ============================================
    // Initial State Tests
    // ============================================

    @Test
    fun `initial state shows loading`() {
        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
        assertTrue(state.accounts.isEmpty())
        assertEquals(0L, state.totalBalance)

    }

    @Test
    fun `loadAccounts updates state with empty list initially`() = runTest(timeout = 10.seconds) {
        val state = waitForState(timeout = 10.seconds) { !it.isLoading }
        assertFalse(state.isLoading)
        assertTrue(state.accounts.isEmpty())
        assertEquals(0L, state.totalBalance)

    }

    // ============================================
    // Account Loading Tests
    // ============================================

    @Test
    fun `loadAccounts loads all accounts`() = runTest(timeout = 10.seconds) {
        // Add accounts
        accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Checking", type = AccountType.CHECKING)
        )
        accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Savings", type = AccountType.SAVINGS)
        )

        val state = waitForState(timeout = 10.seconds) { it.accounts.size == 2 }
        assertTrue(state.accounts.any { it.account.name == "Checking" })
        assertTrue(state.accounts.any { it.account.name == "Savings" })

    }

    @Test
    fun `totalBalance sums all account balances correctly`() = runTest(timeout = 10.seconds) {
        // Add accounts with transactions
        val account1 = accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Checking")
        )
        val account2 = accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Savings")
        )

        // Add transactions to create balances
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = account1, amount = 10000)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = account2, amount = 25000)
        )
        transactionRepository.notifyTransactionsChanged()
        accountRepository.notifyBalancesChanged()

        val state = waitForState(timeout = 10.seconds) { it.totalBalance == 35000L }
        assertEquals(35000L, state.totalBalance)

    }

    @Test
    fun `totalBalance handles negative balances correctly`() = runTest(timeout = 10.seconds) {
        // Add account with negative balance (credit card)
        val account1 = accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Credit Card", type = AccountType.CREDIT_CARD)
        )
        val account2 = accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Checking")
        )

        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = account1, amount = -5000)
        )
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = account2, amount = 10000)
        )
        transactionRepository.notifyTransactionsChanged()
        accountRepository.notifyBalancesChanged()

        val state = waitForState(timeout = 10.seconds) { it.totalBalance == 5000L }
        assertEquals(5000L, state.totalBalance) // 10000 + (-5000)

    }

    // ============================================
    // Account CRUD Tests
    // ============================================

    @Test
    fun `addAccount creates new account`() = runTest(timeout = 10.seconds) {
        viewModel.addAccount(
            name = "New Checking",
            type = AccountType.CHECKING,
            institution = "Test Bank",
            accountNumber = "1234"
        )

        val state = waitForState(timeout = 10.seconds) { it.accounts.size == 1 }
        assertEquals("New Checking", state.accounts[0].account.name)
        assertEquals(AccountType.CHECKING, state.accounts[0].account.type)
        assertEquals("Test Bank", state.accounts[0].account.institution)
        assertEquals("1234", state.accounts[0].account.accountNumber)

    }

    @Test
    fun `addAccount sets timestamps correctly`() = runTest(timeout = 10.seconds) {
        viewModel.addAccount(
            name = "Test Account",
            type = AccountType.SAVINGS,
            institution = null,
            accountNumber = null
        )
        val state = waitForState(timeout = 10.seconds) { it.accounts.size == 1 }
        val account = state.accounts[0].account
        assertNotNull(account.createdAt)
        assertNotNull(account.updatedAt)
        assertEquals(account.createdAt, account.updatedAt)

    }

    @Test
    fun `updateAccount modifies account`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Original Name")
        )

        val initialState = waitForState(timeout = 10.seconds) { it.accounts.size == 1 }
        val originalAccount = initialState.accounts[0].account
        assertEquals("Original Name", originalAccount.name)

        viewModel.updateAccount(
            originalAccount.copy(name = "Updated Name", institution = "New Bank")
        )
        val state = waitForState(timeout = 10.seconds) { it.accounts.isNotEmpty() && it.accounts[0].account.name == "Updated Name" }
        assertEquals("Updated Name", state.accounts[0].account.name)
        assertEquals("New Bank", state.accounts[0].account.institution)

    }

    @Test
    fun `updateAccount updates timestamp`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(
            TestDataFactory.createTestAccount()
        )

        val initialState = waitForState(timeout = 10.seconds) { it.accounts.size == 1 }
        val originalAccount = initialState.accounts[0].account
        val originalUpdatedAt = originalAccount.updatedAt

        waitForState(timeout = 10.seconds) { !it.isLoading } // Ensure timestamp difference

        viewModel.updateAccount(
            originalAccount.copy(name = "Updated")
        )

        val updatedState = waitForState(timeout = 10.seconds) { it.accounts[0].account.name == "Updated" }
        val updatedAccount = updatedState.accounts[0].account
        assertTrue(updatedAccount.updatedAt > originalUpdatedAt)

    }

    @Test
    fun `deleteAccount removes account`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(
            TestDataFactory.createTestAccount()
        )

        val initialState = waitForState(timeout = 10.seconds) { it.accounts.size == 1 }
        assertEquals(1, initialState.accounts.size)

        viewModel.deleteAccount(accountId)
        val state = waitForState(timeout = 10.seconds) { it.accounts.size == 0 }
        assertEquals(0, state.accounts.size)

    }

    // ============================================
    // Reactive Updates Tests
    // ============================================

    @Test
    fun `state updates when account added externally`() = runTest(timeout = 10.seconds) {
        val initialState = waitForState(timeout = 10.seconds) { !it.isLoading && it.accounts.isEmpty() }
        assertEquals(0, initialState.accounts.size)

        // Add account externally (simulating another part of the app)
        accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "External Account")
        )

        val state = waitForState(timeout = 10.seconds) { it.accounts.size == 1 }
        assertEquals(1, state.accounts.size)
        assertEquals("External Account", state.accounts[0].account.name)

    }

    @Test
    fun `state updates when account deleted externally`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(
            TestDataFactory.createTestAccount()
        )

        val initialState = waitForState(timeout = 10.seconds) { it.accounts.size == 1 }
        assertEquals(1, initialState.accounts.size)

        // Delete externally
        accountRepository.deleteAccount(accountId)

        val state = waitForState(timeout = 10.seconds) { it.accounts.size == 0 }
        assertEquals(0, state.accounts.size)

    }

    @Test
    fun `totalBalance updates when transaction added to account`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(
            TestDataFactory.createTestAccount()
        )

        val initialState = waitForState(timeout = 10.seconds) { it.accounts.size == 1 }
        assertEquals(0L, initialState.totalBalance)

        // Add transaction to account
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = 15000)
        )
        transactionRepository.notifyTransactionsChanged()
        accountRepository.notifyBalancesChanged()

        val state = waitForState(timeout = 10.seconds) { it.totalBalance == 15000L }
        assertEquals(15000L, state.totalBalance)

    }

    @Test
    fun `totalBalance updates when transaction deleted from account`() = runTest(timeout = 10.seconds) {
        val accountId = accountRepository.insertAccount(
            TestDataFactory.createTestAccount()
        )

        val txnId = transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = 10000)
        )
        transactionRepository.notifyTransactionsChanged()
        accountRepository.notifyBalancesChanged()

        val initialState = waitForState(timeout = 10.seconds) { it.totalBalance == 10000L }
        assertEquals(10000L, initialState.totalBalance)

        // Delete transaction
        transactionRepository.deleteTransaction(txnId)
        transactionRepository.notifyTransactionsChanged()
        accountRepository.notifyBalancesChanged()

        val state = waitForState(timeout = 10.seconds) { it.totalBalance == 0L }
        assertEquals(0L, state.totalBalance)

    }

    // ============================================
    // Account Type Tests
    // ============================================

    @Test
    fun `loads accounts of different types correctly`() = runTest(timeout = 10.seconds) {
        accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Checking", type = AccountType.CHECKING)
        )
        accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Savings", type = AccountType.SAVINGS)
        )
        accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Credit", type = AccountType.CREDIT_CARD)
        )
        accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Investment", type = AccountType.INVESTMENT)
        )

        val state = waitForState(timeout = 10.seconds) { it.accounts.size == 4 }
        assertEquals(4, state.accounts.size)
        assertTrue(state.accounts.any { it.account.type == AccountType.CHECKING })
        assertTrue(state.accounts.any { it.account.type == AccountType.SAVINGS })
        assertTrue(state.accounts.any { it.account.type == AccountType.CREDIT_CARD })
        assertTrue(state.accounts.any { it.account.type == AccountType.INVESTMENT })

    }

    @Test
    fun `handles multiple accounts of same type`() = runTest(timeout = 10.seconds) {
        accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Checking 1", type = AccountType.CHECKING)
        )
        accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Checking 2", type = AccountType.CHECKING)
        )
        accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Checking 3", type = AccountType.CHECKING)
        )

        val state = waitForState(timeout = 10.seconds) { it.accounts.size == 3 }
        assertEquals(3, state.accounts.size)
        assertTrue(state.accounts.all { it.account.type == AccountType.CHECKING })
        assertTrue(state.accounts.any { it.account.name == "Checking 1" })
        assertTrue(state.accounts.any { it.account.name == "Checking 2" })
        assertTrue(state.accounts.any { it.account.name == "Checking 3" })

    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `handles zero balance accounts`() = runTest(timeout = 10.seconds) {
        accountRepository.insertAccount(
            TestDataFactory.createTestAccount(name = "Empty Account")
        )

        val state = waitForState(timeout = 10.seconds) { it.accounts.size == 1 }
        assertEquals(0L, state.accounts[0].balance)
        assertEquals(0L, state.totalBalance)

    }

    @Test
    fun `handles account with null institution and account number`() = runTest(timeout = 10.seconds) {
        viewModel.addAccount(
            name = "Cash",
            type = AccountType.CASH,
            institution = null,
            accountNumber = null
        )
        val state = waitForState(timeout = 10.seconds) { it.accounts.size == 1 }
        assertEquals(1, state.accounts.size)
        assertNull(state.accounts[0].account.institution)
        assertNull(state.accounts[0].account.accountNumber)

    }
}
