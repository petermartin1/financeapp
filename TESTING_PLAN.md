# Comprehensive Testing Plan

**Project**: Finance App (Kotlin Multiplatform)
**Purpose**: Establish comprehensive test coverage for reactive data layer and UI components
**Status**: Planning Phase
**Target Coverage**: 80%+ overall, 100% for critical paths

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Testing Strategy](#testing-strategy)
3. [Testing Layers](#testing-layers)
4. [Test Infrastructure Setup](#test-infrastructure-setup)
5. [Repository Layer Tests](#repository-layer-tests)
6. [ViewModel Layer Tests](#viewmodel-layer-tests)
7. [UI Layer Tests](#ui-layer-tests)
8. [Integration Tests](#integration-tests)
9. [Performance Tests](#performance-tests)
10. [Implementation Phases](#implementation-phases)
11. [Success Metrics](#success-metrics)

---

## Executive Summary

### Current State
- **Code Status**: All reactive repositories implemented and working
- **Test Coverage**: ~0% (no automated tests)
- **Manual Testing**: Basic functionality verified manually
- **Risk Level**: Medium-High (no regression protection)

### Goals
- Achieve 80%+ overall code coverage
- 100% coverage for critical business logic (transactions, accounts, balances)
- Establish CI/CD pipeline for automated testing
- Create regression test suite
- Document testing patterns for future features

### Timeline
- **Phase 1** (Week 1): Infrastructure setup + Critical repository tests
- **Phase 2** (Week 2): ViewModel tests + Basic UI tests
- **Phase 3** (Week 3): Integration tests + Cross-repository tests
- **Phase 4** (Week 4): Performance tests + Documentation

---

## Testing Strategy

### Testing Pyramid

```
                    ┌─────────────┐
                    │  E2E Tests  │  (10% - Manual + Automated)
                    │   ~50 tests │
                    └─────────────┘
                  ┌──────────────────┐
                  │ Integration Tests│  (20% - Repository interactions)
                  │    ~100 tests    │
                  └──────────────────┘
               ┌────────────────────────┐
               │   Unit Tests (UI)      │  (30% - ViewModels, UI components)
               │      ~200 tests        │
               └────────────────────────┘
          ┌──────────────────────────────────┐
          │   Unit Tests (Data Layer)        │  (40% - Repositories, Models)
          │          ~300 tests              │
          └──────────────────────────────────┘
```

### Test Types

1. **Unit Tests** (Fast, Isolated)
   - Repository logic
   - ViewModel state management
   - Business logic functions
   - Data transformations

2. **Integration Tests** (Medium speed)
   - Repository + Database interactions
   - Cross-repository data flows
   - ViewModel + Repository interactions

3. **UI Tests** (Slower)
   - Compose component behavior
   - User interaction flows
   - Screen navigation

4. **E2E Tests** (Slowest, High value)
   - Critical user journeys
   - Multi-screen workflows
   - Data persistence verification

---

## Testing Layers

### Layer 1: Model & Utility Tests
**Target Coverage**: 100%
**Rationale**: Pure functions, easy to test, no dependencies

- Data class conversions
- Amount formatting (cents to dollars)
- Date utilities
- Validation logic

### Layer 2: Repository Tests
**Target Coverage**: 90%+
**Rationale**: Core business logic, database interactions

- CRUD operations
- Reactive Flow emissions
- Notification triggers
- Query correctness
- Edge cases (empty data, large datasets)

### Layer 3: ViewModel Tests
**Target Coverage**: 85%+
**Rationale**: UI state management, user interactions

- State initialization
- User action handling
- Error states
- Loading states
- Data transformation for UI

### Layer 4: UI Component Tests
**Target Coverage**: 70%+
**Rationale**: Compose UI behavior

- Component rendering
- User interactions (clicks, text input)
- State-driven UI updates
- Navigation triggers

### Layer 5: Integration Tests
**Target Coverage**: Critical paths only
**Rationale**: Expensive but high value

- End-to-end user workflows
- Multi-repository scenarios
- Database + UI integration

---

## Test Infrastructure Setup

### Dependencies to Add

**File**: `shared/build.gradle.kts`

```kotlin
kotlin {
    sourceSets {
        val commonTest by getting {
            dependencies {
                // Core testing
                implementation(kotlin("test"))

                // Coroutines testing
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")

                // Flow testing
                implementation("app.cash.turbine:turbine:1.1.0")

                // Mock framework
                implementation("io.mockk:mockk:1.13.9")

                // Assertions
                implementation("com.willowtreeapps.assertk:assertk:0.28.0")
            }
        }

        val desktopTest by getting {
            dependencies {
                // H2 in-memory database for testing
                implementation("com.h2database:h2:2.2.224")

                // Compose UI testing
                implementation("androidx.compose.ui:ui-test-junit4:1.5.4")
            }
        }

        val androidInstrumentedTest by getting {
            dependencies {
                implementation("androidx.test.ext:junit:1.1.5")
                implementation("androidx.test.espresso:espresso-core:3.5.1")
                implementation("androidx.compose.ui:ui-test-junit4:1.5.4")
            }
        }
    }
}
```

### Test Utilities

**File**: `shared/src/commonTest/kotlin/com/financeapp/test/TestUtils.kt`

```kotlin
package com.financeapp.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Run test with main dispatcher set to TestDispatcher
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runTestWithDispatcher(block: suspend TestScope.() -> Unit) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        block()
    } finally {
        Dispatchers.resetMain()
    }
}

/**
 * Create a test LocalDate
 */
fun testDate(year: Int = 2024, month: Int = 1, day: Int = 1): LocalDate {
    return LocalDate(year, month, day)
}

/**
 * Create a test Instant
 */
fun testInstant(epochMillis: Long = 1704067200000): Instant {
    return Instant.fromEpochMilliseconds(epochMillis)
}

/**
 * Format amount in cents to dollars for assertions
 */
fun Long.toDollars(): Double = this / 100.0
```

**File**: `shared/src/commonTest/kotlin/com/financeapp/test/FlowTestUtils.kt`

```kotlin
package com.financeapp.test

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Test that a Flow emits expected values reactively
 */
suspend fun <T> Flow<T>.testReactivity(
    timeout: Duration = 5.seconds,
    expectedInitial: T,
    mutation: suspend () -> Unit,
    expectedAfterMutation: T
) {
    test(timeout = timeout) {
        // Collect initial emission
        val initial = awaitItem()
        assert(initial == expectedInitial) {
            "Expected initial: $expectedInitial, got: $initial"
        }

        // Perform mutation
        mutation()

        // Collect reactive update
        val updated = awaitItem()
        assert(updated == expectedAfterMutation) {
            "Expected after mutation: $expectedAfterMutation, got: $updated"
        }

        cancelAndConsumeRemainingEvents()
    }
}

/**
 * Test that a Flow emits a sequence of values
 */
suspend fun <T> Flow<T>.testEmissions(
    timeout: Duration = 5.seconds,
    expectedValues: List<T>
) {
    test(timeout = timeout) {
        expectedValues.forEach { expected ->
            val actual = awaitItem()
            assert(actual == expected) {
                "Expected: $expected, got: $actual"
            }
        }
        cancelAndConsumeRemainingEvents()
    }
}
```

**File**: `shared/src/commonTest/kotlin/com/financeapp/test/TestDatabaseFactory.kt`

```kotlin
package com.financeapp.test

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.financeapp.db.schema.*

/**
 * Create an in-memory H2 database for testing
 */
fun createTestDatabase(): Database {
    val db = Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver"
    )

    // Create all tables
    transaction(db) {
        SchemaUtils.create(
            Accounts,
            Transactions,
            Categories,
            Payees,
            Tags,
            TransactionTags,
            Budgets,
            ScheduledTransactions,
            Holdings,
            Securities,
            SecurityPrices
        )
    }

    return db
}

/**
 * Clear all data from test database
 */
fun Database.clearAllTables() {
    transaction(this) {
        SchemaUtils.drop(
            TransactionTags,
            Transactions,
            ScheduledTransactions,
            Budgets,
            Holdings,
            SecurityPrices,
            Securities,
            Accounts,
            Categories,
            Payees,
            Tags
        )
        SchemaUtils.create(
            Accounts,
            Transactions,
            Categories,
            Payees,
            Tags,
            TransactionTags,
            Budgets,
            ScheduledTransactions,
            Holdings,
            Securities,
            SecurityPrices
        )
    }
}
```

**File**: `shared/src/commonTest/kotlin/com/financeapp/test/TestDataFactory.kt`

```kotlin
package com.financeapp.test

import com.financeapp.domain.model.*
import kotlinx.datetime.Clock

/**
 * Factory for creating test data objects
 */
object TestDataFactory {

    fun createTestAccount(
        id: Long = 1,
        name: String = "Test Checking",
        type: String = "CHECKING",
        balance: Long = 100000 // $1,000.00
    ) = Account(
        id = id,
        name = name,
        type = type,
        initialBalance = balance,
        currentBalance = balance,
        currency = "USD",
        isActive = true,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    fun createTestCategory(
        id: Long = 1,
        name: String = "Groceries",
        type: CategoryType = CategoryType.EXPENSE
    ) = Category(
        id = id,
        name = name,
        type = type,
        parentId = null,
        createdAt = Clock.System.now()
    )

    fun createTestPayee(
        id: Long = 1,
        name: String = "Test Store"
    ) = Payee(
        id = id,
        name = name,
        defaultCategoryId = null
    )

    fun createTestTransaction(
        id: Long = 1,
        accountId: Long = 1,
        amount: Long = -5000, // -$50.00 (expense)
        payeeId: Long? = 1,
        categoryId: Long? = 1,
        date: LocalDate = testDate(),
        memo: String? = "Test transaction"
    ) = Transaction(
        id = id,
        accountId = accountId,
        date = date,
        amount = amount,
        payeeId = payeeId,
        categoryId = categoryId,
        memo = memo,
        isCleared = false,
        isReconciled = false,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )

    fun createTestTag(
        id: Long = 1,
        name: String = "Business"
    ) = Tag(
        id = id,
        name = name,
        color = null
    )

    fun createTestBudget(
        id: Long = 1,
        categoryId: Long = 1,
        year: Int = 2024,
        month: Int = 1,
        amount: Long = 50000 // $500.00
    ) = Budget(
        id = id,
        categoryId = categoryId,
        year = year,
        month = month,
        amount = amount
    )
}
```

---

## Repository Layer Tests

### Priority: CRITICAL
**Timeline**: Phase 1 (Week 1)

### TransactionRepository Tests

**File**: `shared/src/commonTest/kotlin/com/financeapp/data/repository/TransactionRepositoryTest.kt`

```kotlin
package com.financeapp.data.repository

import com.financeapp.test.*
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TransactionRepositoryTest {

    private lateinit var database: Database
    private lateinit var repository: TransactionRepositoryImpl

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        repository = TransactionRepositoryImpl(database)
        // Insert test account first
        // ... setup code
    }

    @AfterTest
    fun teardown() {
        database.clearAllTables()
    }

    // 1. Basic CRUD Tests

    @Test
    fun `insertTransaction creates transaction with generated id`() = runTest {
        val transaction = TestDataFactory.createTestTransaction(id = 0)
        val insertedId = repository.insertTransaction(transaction)
        assertTrue(insertedId > 0)
    }

    @Test
    fun `getTransactionById returns correct transaction`() = runTest {
        val transaction = TestDataFactory.createTestTransaction()
        val id = repository.insertTransaction(transaction)
        val retrieved = repository.getTransactionById(id)
        assertNotNull(retrieved)
        assertEquals(transaction.amount, retrieved.amount)
    }

    @Test
    fun `updateTransaction modifies transaction`() = runTest {
        // Insert, modify, verify
    }

    @Test
    fun `deleteTransaction removes transaction`() = runTest {
        // Insert, delete, verify null
    }

    // 2. Reactive Flow Tests

    @Test
    fun `getTransactionsWithDetailsByAccount emits initial empty list`() = runTest {
        repository.getTransactionsWithDetailsByAccount(1).test {
            val initial = awaitItem()
            assertTrue(initial.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getTransactionsWithDetailsByAccount updates after insert`() = runTest {
        repository.getTransactionsWithDetailsByAccount(1).test {
            // Initial empty
            awaitItem()

            // Insert transaction
            val txn = TestDataFactory.createTestTransaction(accountId = 1)
            repository.insertTransaction(txn)
            repository.notifyTransactionsChanged()

            // Should emit updated list
            val updated = awaitItem()
            assertEquals(1, updated.size)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `getTransactionsWithDetailsByAccount updates after delete`() = runTest {
        // Similar pattern
    }

    @Test
    fun `notifyTransactionsChanged triggers all collectors`() = runTest {
        // Multiple collectors verify they all receive updates
    }

    // 3. Query Tests

    @Test
    fun `getTransactionsByAccount returns only matching account`() = runTest {
        // Insert transactions for accounts 1 and 2
        // Query account 1, verify only account 1 transactions
    }

    @Test
    fun `getTransactionsByDateRange returns only transactions in range`() = runTest {
        // Insert transactions with different dates
        // Query range, verify filtering
    }

    @Test
    fun `getRecentTransactions limits results correctly`() = runTest {
        // Insert 50 transactions
        // Query limit 10, verify only 10 returned
    }

    // 4. Performance Tests

    @Test
    fun `refresh completes in under 200ms for 100 transactions`() = runTest {
        // Insert 100 transactions
        // Measure time for notifyTransactionsChanged
        // Assert < 200ms
    }

    @Test
    fun `batch insert completes efficiently`() = runTest {
        // Batch insert 100 transactions
        // Verify all inserted
        // Verify single notification
    }
}
```

### CategoryRepository Tests

**File**: `shared/src/commonTest/kotlin/com/financeapp/data/repository/CategoryRepositoryTest.kt`

**Test Count**: ~25 tests
**Coverage Target**: 95%+

Key test scenarios:
- CRUD operations (insert, update, delete)
- Reactive Flow emissions on data changes
- `getCategoriesByType()` filtering
- Parent-child category relationships
- Notification triggers for all mutations

### AccountRepository Tests

**File**: `shared/src/commonTest/kotlin/com/financeapp/data/repository/AccountRepositoryTest.kt`

**Test Count**: ~30 tests
**Coverage Target**: 95%+

Key test scenarios:
- Account CRUD operations
- Balance calculations
- `getAccountsWithBalances()` reactive updates
- Different account types (checking, savings, credit card)
- Account activation/deactivation

### PayeeRepository Tests

**File**: `shared/src/commonTest/kotlin/com/financeapp/data/repository/PayeeRepositoryTest.kt`

**Test Count**: ~20 tests
**Coverage Target**: 90%+

Key test scenarios:
- Payee CRUD operations
- `getPayeesWithStats()` transaction counts
- `mergePayees()` updates all related transactions
- `batchInsertPayees()` efficiency
- Payee name lookups

### TagRepository Tests

**File**: `shared/src/commonTest/kotlin/com/financeapp/data/repository/TagRepositoryTest.kt`

**Test Count**: ~25 tests
**Coverage Target**: 90%+

Key test scenarios:
- Tag CRUD operations
- Transaction-tag associations
- `setTransactionTags()` replaces all tags
- `getTagsForTransaction()` returns current tags
- Tag color management

### BudgetRepository Tests

**File**: `shared/src/commonTest/kotlin/com/financeapp/data/repository/BudgetRepositoryTest.kt`

**Test Count**: ~30 tests
**Coverage Target**: 90%+

Key test scenarios:
- Budget CRUD operations
- `getBudgetsWithSpendingByMonth()` calculation accuracy
- Upsert logic (insert vs update for same category-month)
- Multiple budgets per month (different categories)
- Spending calculations based on transaction data

---

## ViewModel Layer Tests

### Priority: HIGH
**Timeline**: Phase 2 (Week 2)

### TransactionsViewModel Tests

**File**: `shared/src/commonTest/kotlin/com/financeapp/ui/transactions/TransactionsViewModelTest.kt`

```kotlin
class TransactionsViewModelTest {

    private lateinit var mockRepository: TransactionRepository
    private lateinit var viewModel: TransactionsViewModel

    @BeforeTest
    fun setup() {
        mockRepository = mockk<TransactionRepository>()
        // Setup mock responses
        viewModel = TransactionsViewModel(mockRepository, ...)
    }

    @Test
    fun `initial state is loading`() {
        val state = viewModel.uiState.value
        assertTrue(state.isLoading)
    }

    @Test
    fun `loadTransactions updates state with transactions`() = runTest {
        // Given repository returns test transactions
        val testTransactions = listOf(TestDataFactory.createTestTransaction())
        coEvery { mockRepository.getTransactionsWithDetailsByAccount(any()) } returns
            flowOf(testTransactions)

        // When loading transactions
        viewModel.loadTransactions(accountId = 1)
        advanceUntilIdle()

        // Then state contains transactions
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.transactions.size)
    }

    @Test
    fun `addTransaction calls repository and refreshes`() = runTest {
        // Verify repository method called
        // Verify notification sent
    }

    @Test
    fun `deleteTransaction calls repository and refreshes`() = runTest {
        // Similar pattern
    }

    @Test
    fun `search filters transactions by query`() = runTest {
        // Load transactions
        // Set search query
        // Verify filtered results
    }

    @Test
    fun `toggleCleared updates transaction cleared status`() = runTest {
        // Toggle, verify repository called
    }
}
```

### AccountsViewModel Tests
**Test Count**: ~15 tests
**Coverage Target**: 85%+

### CategoriesViewModel Tests
**Test Count**: ~15 tests
**Coverage Target**: 85%+

### BudgetViewModel Tests
**Test Count**: ~20 tests
**Coverage Target**: 85%+

### TagsViewModel Tests
**Test Count**: ~12 tests
**Coverage Target**: 85%+

### PayeeManagementViewModel Tests
**Test Count**: ~15 tests
**Coverage Target**: 85%+

---

## UI Layer Tests

### Priority: MEDIUM
**Timeline**: Phase 2 (Week 2)

### Compose UI Component Tests

**File**: `shared/src/desktopTest/kotlin/com/financeapp/ui/transactions/TransactionsScreenTest.kt`

```kotlin
@OptIn(ExperimentalTestApi::class)
class TransactionsScreenTest {

    @Test
    fun `clicking transaction opens edit dialog`() = runComposeUiTest {
        // Arrange: Set up screen with test data
        setContent {
            // TransactionsScreen with test ViewModel
        }

        // Act: Click first transaction
        onNodeWithText("Test Transaction").performClick()

        // Assert: Dialog is displayed
        onNodeWithText("Edit Transaction").assertIsDisplayed()
    }

    @Test
    fun `add transaction button shows dialog`() = runComposeUiTest {
        // Similar pattern
    }

    @Test
    fun `search filters transaction list`() = runComposeUiTest {
        // Type in search field
        // Verify filtered results
    }
}
```

**Coverage Target**: 70%+ for UI components

Focus on:
- User interactions (clicks, text input)
- State-driven rendering
- Navigation flows
- Dialog displays

---

## Integration Tests

### Priority: CRITICAL
**Timeline**: Phase 3 (Week 3)

### Reactive Update Tests (CRITICAL)

**Purpose**: Ensure every mutation triggers ALL affected UI components to update immediately.

**File**: `shared/src/commonTest/kotlin/com/financeapp/integration/ReactiveUpdateTest.kt`

```kotlin
/**
 * CRITICAL: These tests verify that when a repository mutation occurs,
 * ALL screens and components that display that data update immediately
 * without requiring manual refresh or navigation.
 */
class ReactiveUpdateTest {

    @Test
    fun `adding transaction updates - transaction list, account balance, budget spending, dashboard`() = runTest {
        // Setup: Create account, category, budget
        val accountId = accountRepo.insertAccount(TestDataFactory.createTestAccount())
        val categoryId = categoryRepo.insertCategory(TestDataFactory.createTestCategory())
        budgetRepo.insertOrUpdateBudget(TestDataFactory.createTestBudget(categoryId = categoryId))

        // Monitor all affected flows simultaneously
        launch {
            // 1. Transaction list should update
            transactionRepo.getTransactionsWithDetailsByAccount(accountId).test {
                awaitItem() // Initial empty
                // Transaction added below...
                val updated = awaitItem()
                assertEquals(1, updated.size)
                cancelAndConsumeRemainingEvents()
            }
        }

        launch {
            // 2. Account balance should update
            accountRepo.getAccountsWithBalances().test {
                val initial = awaitItem()
                val initialBalance = initial.first().currentBalance
                // Transaction added below...
                val updated = awaitItem()
                assertNotEquals(initialBalance, updated.first().currentBalance)
                cancelAndConsumeRemainingEvents()
            }
        }

        launch {
            // 3. Budget spending should update
            budgetRepo.getBudgetsWithSpendingByMonth(2024, 1).test {
                val initial = awaitItem()
                assertEquals(0, initial.first().spent)
                // Transaction added below...
                val updated = awaitItem()
                assertTrue(updated.first().spent > 0)
                cancelAndConsumeRemainingEvents()
            }
        }

        // Act: Add transaction
        transactionRepo.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                categoryId = categoryId,
                amount = -5000
            )
        )
        transactionRepo.notifyTransactionsChanged()
        accountRepo.notifyBalancesChanged()
        budgetRepo.notifyBudgetsChanged()

        // All assertions in launch blocks verify updates occurred
    }

    @Test
    fun `adding category updates - category list, transaction dialog, budget screen`() = runTest {
        // Verify category appears immediately in:
        // - CategoriesScreen
        // - AddTransactionDialog category dropdown
        // - BudgetScreen category dropdown
    }

    @Test
    fun `adding payee updates - payee list, transaction dialog, autocomplete`() = runTest {
        // Verify payee appears immediately in:
        // - PayeeManagementScreen
        // - AddTransactionDialog payee field
        // - Payee autocomplete suggestions
    }

    @Test
    fun `adding tag updates - tag list, transaction dialog`() = runTest {
        // Verify tag appears immediately in:
        // - TagsScreen
        // - EditTransactionDialog tag selector
    }

    @Test
    fun `editing transaction updates - transaction list, account balance, old and new category budgets`() = runTest {
        // When changing transaction category:
        // - Old category budget should decrease spending
        // - New category budget should increase spending
        // - Account balance should reflect any amount change
        // - Transaction list should show updated details
    }

    @Test
    fun `deleting transaction updates - transaction list, account balance, budget spending, dashboard`() = runTest {
        // All screens showing the transaction should update immediately
    }

    @Test
    fun `deleting category updates - category list and nullifies transaction categories`() = runTest {
        // Verify category removed from lists
        // Verify transactions with that category now show "Uncategorized"
    }

    @Test
    fun `merging payees updates - payee list and all transaction displays`() = runTest {
        // Create 2 payees with transactions
        // Merge payee2 into payee1
        // Verify:
        // - Payee2 removed from PayeeManagementScreen
        // - All transactions show payee1
        // - Transaction counts updated for payee1
    }

    @Test
    fun `updating budget amount updates - budget screen immediately`() = runTest {
        // Change budget amount
        // Verify BudgetScreen shows new amount without refresh
    }

    @Test
    fun `toggling transaction cleared status updates - transaction list checkbox`() = runTest {
        // Toggle cleared
        // Verify checkbox updates in TransactionsScreen
    }

    @Test
    fun `adding account updates - account list, dashboard, transaction dialog account selector`() = runTest {
        // Verify account appears in all places immediately
    }
}
```

### Cross-Repository Integration Tests

**File**: `shared/src/commonTest/kotlin/com/financeapp/integration/ReactiveIntegrationTest.kt`

```kotlin
class ReactiveIntegrationTest {

    private lateinit var database: Database
    private lateinit var transactionRepo: TransactionRepository
    private lateinit var accountRepo: AccountRepository
    private lateinit var budgetRepo: BudgetRepository

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        transactionRepo = TransactionRepositoryImpl(database)
        accountRepo = AccountRepositoryImpl(database)
        budgetRepo = BudgetRepositoryImpl(database)
    }

    @Test
    fun `adding transaction updates account balance`() = runTest {
        // Create account with $1000
        val accountId = accountRepo.insertAccount(
            TestDataFactory.createTestAccount(balance = 100000)
        )

        // Monitor account balance
        accountRepo.getAccountsWithBalances().test {
            val initial = awaitItem()
            assertEquals(100000, initial.first().currentBalance)

            // Add $50 expense transaction
            transactionRepo.insertTransaction(
                TestDataFactory.createTestTransaction(
                    accountId = accountId,
                    amount = -5000
                )
            )
            accountRepo.notifyBalancesChanged()

            // Balance should update to $950
            val updated = awaitItem()
            assertEquals(95000, updated.first().currentBalance)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `adding transaction updates budget spending`() = runTest {
        // Create budget for category
        // Add transaction in that category
        // Verify budget spending updates
    }

    @Test
    fun `deleting category nullifies transaction categories`() = runTest {
        // Create category and transaction with that category
        // Delete category
        // Verify transaction.categoryId is null
    }

    @Test
    fun `merging payees updates all transactions`() = runTest {
        // Create 2 payees
        // Create transactions for both
        // Merge payee 2 into payee 1
        // Verify all transactions now have payee 1
    }
}
```

**Test Count**: ~25 integration tests
**Coverage**: Critical cross-repository flows

---

## Performance Tests

### Priority: MEDIUM
**Timeline**: Phase 4 (Week 4)

### Performance Benchmarks

**File**: `shared/src/commonTest/kotlin/com/financeapp/performance/RepositoryPerformanceTest.kt`

```kotlin
class RepositoryPerformanceTest {

    @Test
    fun `transaction refresh completes in under 200ms for 100 transactions`() = runTest {
        // Setup: Insert 100 transactions
        repeat(100) {
            repository.insertTransaction(TestDataFactory.createTestTransaction())
        }

        // Measure: Time for refresh
        val startTime = System.currentTimeMillis()
        repository.notifyTransactionsChanged()
        repository.getTransactionsWithDetailsByAccount(1).first()
        val duration = System.currentTimeMillis() - startTime

        // Assert: < 200ms
        assertTrue(duration < 200, "Refresh took ${duration}ms, expected < 200ms")
    }

    @Test
    fun `account balance calculation under 100ms for 1000 transactions`() = runTest {
        // Similar pattern
    }

    @Test
    fun `budget spending calculation under 150ms for 500 transactions`() = runTest {
        // Similar pattern
    }
}
```

### Performance Targets

| Operation | Dataset Size | Target Time |
|-----------|-------------|-------------|
| Transaction refresh | 100 transactions | < 200ms |
| Account balance calc | 1000 transactions | < 100ms |
| Budget spending calc | 500 transactions | < 150ms |
| Category list refresh | 50 categories | < 50ms |
| Payee stats calculation | 100 payees, 500 txns | < 150ms |

---

## Implementation Phases

### Phase 1: Foundation (Week 1)
**Goal**: Setup infrastructure + critical repository tests

**Tasks**:
1. ✅ Add test dependencies to `build.gradle.kts`
2. ✅ Create test utility classes
   - `TestUtils.kt`
   - `FlowTestUtils.kt`
   - `TestDatabaseFactory.kt`
   - `TestDataFactory.kt`
3. ✅ Write TransactionRepository tests (25 tests)
4. ✅ Write AccountRepository tests (30 tests)
5. ✅ Write CategoryRepository tests (25 tests)
6. ⏸️ Verify all tests pass
7. ⏸️ Achieve 90%+ coverage for these 3 repositories

**Success Criteria**:
- All infrastructure in place
- 80+ repository tests passing
- CI pipeline running tests

---

### Phase 2: ViewModels + Basic UI (Week 2)
**Goal**: Test ViewModel layer + key UI components

**Tasks**:
1. ⏸️ Write TransactionsViewModel tests (20 tests)
2. ⏸️ Write AccountsViewModel tests (15 tests)
3. ⏸️ Write CategoriesViewModel tests (15 tests)
4. ⏸️ Write BudgetViewModel tests (20 tests)
5. ⏸️ Write TagRepository tests (25 tests)
6. ⏸️ Write PayeeRepository tests (20 tests)
7. ⏸️ Write basic UI component tests (30 tests)

**Success Criteria**:
- 145+ tests passing
- 85%+ ViewModel coverage
- Basic UI interaction tests working

---

### Phase 3: Integration Tests (Week 3)
**Goal**: Test cross-repository interactions + end-to-end flows

**Tasks**:
1. ⏸️ Write cross-repository integration tests (25 tests)
2. ⏸️ Write end-to-end user workflow tests (20 tests)
3. ⏸️ Write remaining ViewModel tests (TagsViewModel, PayeeManagementViewModel)
4. ⏸️ Write BudgetRepository tests (30 tests)
5. ⏸️ Test error scenarios and edge cases (20 tests)

**Success Criteria**:
- 240+ total tests passing
- All critical user workflows covered
- 80%+ overall code coverage

---

### Phase 4: Performance + Polish (Week 4)
**Goal**: Performance testing + documentation

**Tasks**:
1. ⏸️ Write performance benchmark tests (15 tests)
2. ⏸️ Optimize slow operations identified by benchmarks
3. ⏸️ Write remaining UI tests (40 tests)
4. ⏸️ Document testing patterns and best practices
5. ⏸️ Create testing guidelines for future features
6. ⏸️ Setup automated test reporting

**Success Criteria**:
- 300+ total tests passing
- All performance targets met
- 80%+ overall coverage
- Testing documentation complete

---

## Success Metrics

### Coverage Targets

| Layer | Target Coverage | Critical Paths |
|-------|----------------|----------------|
| Models | 100% | 100% |
| Repositories | 100% | 100% |
| ViewModels | 100% | 100% |
| UI Components | 100% | 100% |
| **Overall** | **100%** | **100%** |

### Test Counts

| Test Type | Target Count | Current |
|-----------|-------------|---------|
| Unit (Data) | 200 | 0 |
| Unit (UI) | 100 | 0 |
| Integration | 50 | 0 |
| E2E | 20 | 0 |
| Performance | 15 | 0 |
| **Total** | **385** | **0** |

### Quality Gates

All of these must pass before merging to main:

✅ All tests passing (0 failures)
✅ **100% code coverage** across all layers
✅ All performance benchmarks met
✅ No flaky tests (tests must be deterministic)
✅ **All reactive update tests passing** - mutations must trigger UI updates
✅ CI/CD pipeline green

---

## Testing Best Practices

### 1. Test Naming Convention

```kotlin
@Test
fun `method name - scenario - expected result`() {
    // Example: `getAllCategories - after insert - emits updated list`
}
```

### 2. AAA Pattern (Arrange, Act, Assert)

```kotlin
@Test
fun testExample() {
    // Arrange: Set up test data
    val transaction = TestDataFactory.createTestTransaction()

    // Act: Perform the action
    val id = repository.insertTransaction(transaction)

    // Assert: Verify the result
    assertTrue(id > 0)
}
```

### 3. One Assertion Focus Per Test

Each test should verify one specific behavior.

### 4. Use Test Factories

Always use `TestDataFactory` for consistent test data.

### 5. Clean Up Resources

```kotlin
@AfterTest
fun teardown() {
    database.clearAllTables()
}
```

### 6. Avoid Test Interdependence

Each test must run independently in any order.

---

## CI/CD Integration

### GitHub Actions Workflow

**File**: `.github/workflows/test.yml`

```yaml
name: Run Tests

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Run Tests
      run: ./gradlew test --continue

    - name: Generate Coverage Report
      run: ./gradlew koverXmlReport

    - name: Upload Coverage to Codecov
      uses: codecov/codecov-action@v3
      with:
        files: ./build/reports/kover/report.xml

    - name: Publish Test Results
      uses: EnricoMi/publish-unit-test-result-action@v2
      if: always()
      with:
        files: '**/build/test-results/**/*.xml'
```

---

## Appendix: Quick Reference

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Class
```bash
./gradlew test --tests "TransactionRepositoryTest"
```

### Run Tests with Coverage
```bash
./gradlew koverHtmlReport
open build/reports/kover/html/index.html
```

### Run Performance Tests Only
```bash
./gradlew test --tests "*PerformanceTest"
```

---

## Next Steps

### Immediate Actions (This Week)

1. **Review this plan** - Agree on approach, priorities, timeline
2. **Setup infrastructure** - Add dependencies, create test utilities
3. **Start Phase 1** - Begin with TransactionRepository tests
4. **Setup CI/CD** - Configure GitHub Actions for automated testing

### Questions to Discuss

1. **Timeline**: Is 4 weeks realistic? Should we compress or extend?
2. **Coverage targets**: Are 80% overall / 95% critical paths the right goals?
3. **Priorities**: Should we focus on certain repositories/features first?
4. **Resources**: Who will write the tests? Need to assign ownership?
5. **CI/CD**: Do we have GitHub Actions set up? Any infrastructure needs?
6. **Performance targets**: Are the ms targets realistic for your hardware?

---

**Document Status**: Draft
**Last Updated**: 2025-11-28
**Next Review**: After Phase 1 completion
