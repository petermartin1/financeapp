package com.financeapp.data.repository

import com.financeapp.test.*
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.model.Account
import com.financeapp.domain.model.AccountType
import com.financeapp.domain.model.AccountWithBalance
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.Database
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryTest {
    private lateinit var database: Database
    private lateinit var accountRepository: AccountRepository
    private lateinit var transactionRepository: TransactionRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        accountRepository = AccountRepositoryImpl(database, testDispatcher)
        transactionRepository = TransactionRepositoryImpl(database, testDispatcher)
    }

    @AfterTest
    fun teardown() {
        database.clearAllTables()
    }

    // ============================================
    // Basic CRUD Operations
    // ============================================

    @Test
    fun `insertAccount should create account and return id`() = runTest {
        val account = TestDataFactory.createTestAccount(
            name = "New Checking Account",
            type = AccountType.CHECKING
        )

        val id = accountRepository.insertAccount(account)

        assertTrue(id > 0, "Account ID should be positive")
        val retrieved = accountRepository.getAccountById(id)
        assertNotNull(retrieved)
        assertEquals("New Checking Account", retrieved.name)
        assertEquals(AccountType.CHECKING, retrieved.type)
    }

    @Test
    fun `getAccountById should return null for non-existent account`() = runTest {
        val result = accountRepository.getAccountById(99999L)
        assertNull(result)
    }

    @Test
    fun `updateAccount should modify existing account`() = runTest {
        val account = TestDataFactory.createTestAccount(name = "Original Name")
        val id = accountRepository.insertAccount(account)

        val updated = account.copy(
            id = id,
            name = "Updated Name",
            institution = "Updated Bank"
        )
        accountRepository.updateAccount(updated)

        val retrieved = accountRepository.getAccountById(id)
        assertNotNull(retrieved)
        assertEquals("Updated Name", retrieved.name)
        assertEquals("Updated Bank", retrieved.institution)
    }

    @Test
    fun `deleteAccount should remove account from database`() = runTest {
        val account = TestDataFactory.createTestAccount()
        val id = accountRepository.insertAccount(account)

        accountRepository.deleteAccount(id)

        val retrieved = accountRepository.getAccountById(id)
        assertNull(retrieved)
    }

    @Test
    fun `deleteAccount should also delete associated transactions`() = runTest {
        val account = TestDataFactory.createTestAccount()
        val accountId = accountRepository.insertAccount(account)

        // Add transactions to this account
        val txn1 = TestDataFactory.createTestTransaction(accountId = accountId, amount = 10000)
        val txn2 = TestDataFactory.createTestTransaction(accountId = accountId, amount = -5000)
        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)
        transactionRepository.notifyTransactionsChanged()

        // Verify balance exists
        val balanceBefore = accountRepository.getAccountBalance(accountId)
        assertEquals(5000L, balanceBefore)

        // Delete account
        accountRepository.deleteAccount(accountId)

        // Verify account and transactions are gone
        val retrieved = accountRepository.getAccountById(accountId)
        assertNull(retrieved)

        // Balance should be 0 since transactions are deleted
        val balanceAfter = accountRepository.getAccountBalance(accountId)
        assertEquals(0L, balanceAfter)
    }

    // ============================================
    // Reactive Flow - getAllAccounts
    // ============================================

    @Test
    fun `getAllAccounts should emit initial active accounts`() = runTest {
        val account1 = TestDataFactory.createTestAccount(name = "Account 1")
        val account2 = TestDataFactory.createTestAccount(name = "Account 2")

        accountRepository.insertAccount(account1)
        accountRepository.insertAccount(account2)

        // Give the Flow time to refresh
        delay(100)

        accountRepository.getAllAccounts().test(timeout = 5.seconds) {
            val accounts = awaitItem()
            assertEquals(2, accounts.size)
            assertTrue(accounts.any { it.name == "Account 1" })
            assertTrue(accounts.any { it.name == "Account 2" })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllAccounts should emit updates when account inserted`() = runTest {
        accountRepository.getAllAccounts().test(timeout = 5.seconds) {
            // Initial emission (empty)
            val initial = awaitItem()
            assertEquals(0, initial.size)

            // Insert account
            val account = TestDataFactory.createTestAccount(name = "New Account")
            accountRepository.insertAccount(account)

            // Should emit updated list
            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("New Account", updated[0].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllAccounts should emit updates when account updated`() = runTest {
        val account = TestDataFactory.createTestAccount(name = "Original")
        val id = accountRepository.insertAccount(account)

        delay(100) // Let initial state settle

        accountRepository.getAllAccounts().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)
            assertEquals("Original", initial[0].name)

            // Update account
            accountRepository.updateAccount(account.copy(id = id, name = "Updated"))

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("Updated", updated[0].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllAccounts should emit updates when account deleted`() = runTest {
        val account = TestDataFactory.createTestAccount()
        val id = accountRepository.insertAccount(account)

        delay(100)

        accountRepository.getAllAccounts().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)

            accountRepository.deleteAccount(id)

            val updated = awaitItem()
            assertEquals(0, updated.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAllAccounts should notify multiple collectors`() = runTest {
        val account = TestDataFactory.createTestAccount()

        // Start two collectors
        launch {
            accountRepository.getAllAccounts().test {
                val initial = awaitItem()
                assertEquals(0, initial.size)

                val updated = awaitItem()
                assertEquals(1, updated.size)

                cancelAndConsumeRemainingEvents()
            }
        }

        launch {
            accountRepository.getAllAccounts().test {
                awaitItem() // Initial
                awaitItem() // After insert
                cancelAndConsumeRemainingEvents()
            }
        }

        delay(50)
        accountRepository.insertAccount(account)
        delay(150)
    }

    // ============================================
    // Reactive Flow - getAccountsWithBalances
    // ============================================

    @Test
    fun `getAccountsWithBalances should emit accounts with calculated balances`() = runTest {
        val account = TestDataFactory.createTestAccount(name = "Test Account")
        val accountId = accountRepository.insertAccount(account)

        // Add transactions
        val txn1 = TestDataFactory.createTestTransaction(accountId = accountId, amount = 10000, isCleared = true)
        val txn2 = TestDataFactory.createTestTransaction(accountId = accountId, amount = -3000, isCleared = true)
        val txn3 = TestDataFactory.createTestTransaction(accountId = accountId, amount = -2000, isCleared = false)

        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)
        transactionRepository.insertTransaction(txn3)
        transactionRepository.notifyTransactionsChanged()

        delay(100)

        accountRepository.getAccountsWithBalances().test(timeout = 5.seconds) {
            val accountsWithBalances = awaitItem()
            assertEquals(1, accountsWithBalances.size)

            val accountWithBalance = accountsWithBalances[0]
            assertEquals("Test Account", accountWithBalance.account.name)
            assertEquals(5000L, accountWithBalance.balance) // 10000 - 3000 - 2000
            assertEquals(7000L, accountWithBalance.clearedBalance) // 10000 - 3000 (only cleared)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAccountsWithBalances should update when new transaction added`() = runTest {
        val account = TestDataFactory.createTestAccount()
        val accountId = accountRepository.insertAccount(account)

        delay(100)

        accountRepository.getAccountsWithBalances().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)
            assertEquals(0L, initial[0].balance)

            // Add transaction
            val txn = TestDataFactory.createTestTransaction(accountId = accountId, amount = 10000)
            transactionRepository.insertTransaction(txn)
            accountRepository.notifyBalancesChanged()

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals(10000L, updated[0].balance)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getAccountsWithBalances should update when notifyBalancesChanged called`() = runTest {
        val account = TestDataFactory.createTestAccount()
        val accountId = accountRepository.insertAccount(account)

        val txn = TestDataFactory.createTestTransaction(accountId = accountId, amount = 5000)
        transactionRepository.insertTransaction(txn)

        delay(100)

        accountRepository.getAccountsWithBalances().test(timeout = 5.seconds) {
            awaitItem() // Initial state

            // Notify balances changed
            accountRepository.notifyBalancesChanged()

            val updated = awaitItem()
            assertEquals(5000L, updated[0].balance)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Balance Calculations
    // ============================================

    @Test
    fun `getAccountBalance should return zero for account with no transactions`() = runTest {
        val account = TestDataFactory.createTestAccount()
        val accountId = accountRepository.insertAccount(account)

        val balance = accountRepository.getAccountBalance(accountId)
        assertEquals(0L, balance)
    }

    @Test
    fun `getAccountBalance should sum all transactions`() = runTest {
        val account = TestDataFactory.createTestAccount()
        val accountId = accountRepository.insertAccount(account)

        val txn1 = TestDataFactory.createTestTransaction(accountId = accountId, amount = 10000)
        val txn2 = TestDataFactory.createTestTransaction(accountId = accountId, amount = 5000)
        val txn3 = TestDataFactory.createTestTransaction(accountId = accountId, amount = -3000)

        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)
        transactionRepository.insertTransaction(txn3)

        val balance = accountRepository.getAccountBalance(accountId)
        assertEquals(12000L, balance) // 10000 + 5000 - 3000
    }

    @Test
    fun `getClearedBalance should only sum cleared transactions`() = runTest {
        val account = TestDataFactory.createTestAccount()
        val accountId = accountRepository.insertAccount(account)

        val txn1 = TestDataFactory.createTestTransaction(accountId = accountId, amount = 10000, isCleared = true)
        val txn2 = TestDataFactory.createTestTransaction(accountId = accountId, amount = 5000, isCleared = false)
        val txn3 = TestDataFactory.createTestTransaction(accountId = accountId, amount = -3000, isCleared = true)

        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)
        transactionRepository.insertTransaction(txn3)

        val clearedBalance = accountRepository.getClearedBalance(accountId)
        assertEquals(7000L, clearedBalance) // 10000 - 3000 (only cleared)

        val totalBalance = accountRepository.getAccountBalance(accountId)
        assertEquals(12000L, totalBalance) // 10000 + 5000 - 3000 (all)
    }

    @Test
    fun `getAccountBalance should handle negative balances`() = runTest {
        val account = TestDataFactory.createTestAccount(type = AccountType.CREDIT_CARD)
        val accountId = accountRepository.insertAccount(account)

        val txn1 = TestDataFactory.createTestTransaction(accountId = accountId, amount = -50000)
        val txn2 = TestDataFactory.createTestTransaction(accountId = accountId, amount = 10000)

        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)

        val balance = accountRepository.getAccountBalance(accountId)
        assertEquals(-40000L, balance) // -50000 + 10000
    }

    // ============================================
    // Different Account Types
    // ============================================

    @Test
    fun `should support all account types`() = runTest {
        val types = listOf(
            AccountType.CHECKING,
            AccountType.SAVINGS,
            AccountType.CREDIT_CARD,
            AccountType.INVESTMENT,
            AccountType.CASH
        )

        types.forEach { type ->
            val account = TestDataFactory.createTestAccount(
                name = "${type.name} Account",
                type = type
            )
            val id = accountRepository.insertAccount(account)

            val retrieved = accountRepository.getAccountById(id)
            assertNotNull(retrieved)
            assertEquals(type, retrieved.type)
        }
    }

    // ============================================
    // Account Activation/Deactivation
    // ============================================

    @Test
    fun `getAllAccounts should only return active accounts`() = runTest {
        val activeAccount = TestDataFactory.createTestAccount(name = "Active", isActive = true)
        val inactiveAccount = TestDataFactory.createTestAccount(name = "Inactive", isActive = false)

        accountRepository.insertAccount(activeAccount)
        accountRepository.insertAccount(inactiveAccount)

        delay(100)

        accountRepository.getAllAccounts().test(timeout = 5.seconds) {
            val accounts = awaitItem()
            assertEquals(1, accounts.size) // Only active account
            assertEquals("Active", accounts[0].name)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `deactivating account should remove it from getAllAccounts`() = runTest {
        val account = TestDataFactory.createTestAccount(name = "Test", isActive = true)
        val id = accountRepository.insertAccount(account)

        delay(100)

        accountRepository.getAllAccounts().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(1, initial.size)

            // Deactivate
            accountRepository.updateAccount(account.copy(id = id, isActive = false))

            val updated = awaitItem()
            assertEquals(0, updated.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `reactivating account should add it back to getAllAccounts`() = runTest {
        val account = TestDataFactory.createTestAccount(name = "Test", isActive = false)
        val id = accountRepository.insertAccount(account)

        delay(100)

        accountRepository.getAllAccounts().test(timeout = 5.seconds) {
            val initial = awaitItem()
            assertEquals(0, initial.size) // Inactive

            // Reactivate
            accountRepository.updateAccount(account.copy(id = id, isActive = true))

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("Test", updated[0].name)

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============================================
    // Reconciliation
    // ============================================

    @Test
    fun `insertReconciliation should create reconciliation session`() = runTest {
        val account = TestDataFactory.createTestAccount()
        val accountId = accountRepository.insertAccount(account)

        val reconciliationId = accountRepository.insertReconciliation(
            accountId = accountId,
            statementDate = testDate(2024, 3, 31),
            statementBalance = 100000L,
            isCompleted = false
        )

        assertTrue(reconciliationId > 0)
    }

    @Test
    fun `insertReconciliation should handle completed status`() = runTest {
        val account = TestDataFactory.createTestAccount()
        val accountId = accountRepository.insertAccount(account)

        val id1 = accountRepository.insertReconciliation(
            accountId = accountId,
            statementDate = testDate(2024, 3, 31),
            statementBalance = 100000L,
            isCompleted = true
        )

        val id2 = accountRepository.insertReconciliation(
            accountId = accountId,
            statementDate = testDate(2024, 4, 30),
            statementBalance = 120000L,
            isCompleted = false
        )

        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertNotEquals(id1, id2)
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `should handle multiple accounts with different balances`() = runTest {
        val account1 = TestDataFactory.createTestAccount(name = "Account 1")
        val account2 = TestDataFactory.createTestAccount(name = "Account 2")

        val id1 = accountRepository.insertAccount(account1)
        val id2 = accountRepository.insertAccount(account2)

        val txn1 = TestDataFactory.createTestTransaction(accountId = id1, amount = 10000)
        val txn2 = TestDataFactory.createTestTransaction(accountId = id2, amount = 20000)

        transactionRepository.insertTransaction(txn1)
        transactionRepository.insertTransaction(txn2)

        val balance1 = accountRepository.getAccountBalance(id1)
        val balance2 = accountRepository.getAccountBalance(id2)

        assertEquals(10000L, balance1)
        assertEquals(20000L, balance2)
    }

    @Test
    fun `should handle accounts sorted alphabetically`() = runTest {
        accountRepository.insertAccount(TestDataFactory.createTestAccount(name = "Zebra Account"))
        accountRepository.insertAccount(TestDataFactory.createTestAccount(name = "Apple Account"))
        accountRepository.insertAccount(TestDataFactory.createTestAccount(name = "Mango Account"))

        delay(100)

        accountRepository.getAllAccounts().test(timeout = 5.seconds) {
            val accounts = awaitItem()
            assertEquals(3, accounts.size)
            assertEquals("Apple Account", accounts[0].name)
            assertEquals("Mango Account", accounts[1].name)
            assertEquals("Zebra Account", accounts[2].name)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `should handle account with very large balance`() = runTest {
        val account = TestDataFactory.createTestAccount()
        val accountId = accountRepository.insertAccount(account)

        val largeTxn = TestDataFactory.createTestTransaction(
            accountId = accountId,
            amount = 999999999999L // Very large amount
        )

        transactionRepository.insertTransaction(largeTxn)

        val balance = accountRepository.getAccountBalance(accountId)
        assertEquals(999999999999L, balance)
    }

    // ============================================
    // Performance Tests
    // ============================================

    @Test
    fun `getAllAccounts should refresh quickly with many accounts`() = runTest {
        // Insert 50 accounts sequentially to ensure all complete
        val accountIds = (0 until 50).map { i ->
            accountRepository.insertAccount(
                TestDataFactory.createTestAccount(name = "Account $i")
            )
        }

        // Ensure all 50 accounts were inserted
        assertEquals(50, accountIds.size)

        delay(500) // Allow refreshes to complete

        accountRepository.getAllAccounts().test(timeout = 5.seconds) {
            // Keep consuming items until we get all 50 accounts (handles async refresh)
            var accounts = awaitItem()
            while (accounts.size < 50) {
                accounts = awaitItem()
            }
            assertEquals(50, accounts.size)
            cancelAndConsumeRemainingEvents()
        }

        // Performance note: Test verifies that 50 accounts can be handled
        // Timing is system-dependent, so we only verify functionality
    }

    @Test
    fun `getAccountsWithBalances should handle multiple accounts with transactions`() = runTest {
        // Create 10 accounts
        val accountIds = (1..10).map { i ->
            accountRepository.insertAccount(
                TestDataFactory.createTestAccount(name = "Account $i")
            )
        }

        // Add transactions to each
        accountIds.forEach { accountId ->
            repeat(5) {
                transactionRepository.insertTransaction(
                    TestDataFactory.createTestTransaction(
                        accountId = accountId,
                        amount = 1000L
                    )
                )
            }
        }

        accountRepository.notifyBalancesChanged()
        delay(200)

        accountRepository.getAccountsWithBalances().test(timeout = 5.seconds) {
            val accountsWithBalances = awaitItem()
            assertEquals(10, accountsWithBalances.size)

            // Each account should have balance of 5000 (5 transactions × 1000)
            accountsWithBalances.forEach { accountWithBalance ->
                assertEquals(5000L, accountWithBalance.balance)
            }

            cancelAndConsumeRemainingEvents()
        }
    }
}
