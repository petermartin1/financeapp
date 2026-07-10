# Savings Goals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Savings goals (target amount + optional deadline, linked to one account) with live balance-based progress, pace feedback, and a Goals screen.

**Architecture:** A new `SavingsGoals` Exposed table stores static config only; progress is computed reactively by combining the goals table with `AccountRepository.getAccountsWithBalances()`. Pace math lives in a pure `GoalProgressCalculator`. UI follows the Subscriptions-feature template (repository interface + impl, Koin, ViewModel, screen, nav rail entry).

**Tech Stack:** Kotlin/Compose Multiplatform (desktop-only), Exposed ORM v1 (`org.jetbrains.exposed.v1.*`) over H2, Koin, kotlinx-coroutines/datetime, kotlin.test + Turbine.

**Spec:** `docs/superpowers/specs/2026-07-09-savings-goals-design.md`

## Global Constraints

- Money is always `Long` cents; never `Double` for amounts.
- Dates/times stored as Unix epoch milliseconds (`Long`).
- FK enforcement is ON: the account delete path must hand-clean goal references (unlink, not delete) **inside the same transaction**.
- All queries inside `transaction(database) { ... }` blocks using Exposed's typed DSL.
- Test suite command: `./gradlew :shared:desktopTest` (tests live in `shared/src/commonTest`).
- Commit messages: conventional prefix (`feat:`/`fix:`/`test:`), **no Co-Authored-By/AI attribution trailers**.
- Table objects are plural; SQL table names singular PascalCase; SQL column names snake_case.

---

### Task 1: `SavingsGoals` schema table

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt` (append after `DetectedSubscriptions`)
- Modify: `shared/src/desktopMain/kotlin/com/financeapp/db/DatabaseDriverFactory.desktop.kt:44-66` (SchemaUtils.create list)
- Modify: `shared/src/commonTest/kotlin/com/financeapp/test/TestDatabaseFactory.kt` (create list, drop list, recreate list)
- Test: `shared/src/commonTest/kotlin/com/financeapp/data/schema/SavingsGoalsSchemaTest.kt`

**Interfaces:**
- Consumes: existing `Accounts` table object.
- Produces: `com.financeapp.db.schema.SavingsGoals` table object with columns `name`, `targetAmount`, `accountId` (nullable FK), `deadline` (nullable), `createdAt`, `archived` — used by Tasks 3 and 4.

- [ ] **Step 1: Write the failing test**

Create `shared/src/commonTest/kotlin/com/financeapp/data/schema/SavingsGoalsSchemaTest.kt`:

```kotlin
package com.financeapp.data.schema

import com.financeapp.db.schema.Accounts
import com.financeapp.db.schema.SavingsGoals
import com.financeapp.test.createTestDatabase
import com.financeapp.test.clearAllTables
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*

class SavingsGoalsSchemaTest {
    private lateinit var database: Database

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
    }

    @AfterTest
    fun teardown() = database.clearAllTables()

    @Test
    fun `savings goal row round-trips with nullable account and deadline`() {
        transaction(database) {
            val accountId = Accounts.insert {
                it[name] = "Vacation Fund"
                it[type] = "SAVINGS"
                it[createdAt] = 1L
                it[updatedAt] = 1L
            }[Accounts.id]

            SavingsGoals.insert {
                it[name] = "Hawaii"
                it[targetAmount] = 500_000L
                it[SavingsGoals.accountId] = accountId
                it[deadline] = 1_782_000_000_000L
                it[createdAt] = 1_751_000_000_000L
            }
            SavingsGoals.insert {
                it[name] = "Rainy day"
                it[targetAmount] = 100_000L
                it[SavingsGoals.accountId] = null
                it[deadline] = null
                it[createdAt] = 1_751_000_000_000L
            }

            val rows = SavingsGoals.selectAll().orderBy(SavingsGoals.id).toList()
            assertEquals(2, rows.size)
            assertEquals("Hawaii", rows[0][SavingsGoals.name])
            assertEquals(500_000L, rows[0][SavingsGoals.targetAmount])
            assertEquals(accountId.value, rows[0][SavingsGoals.accountId]?.value)
            assertEquals(1_782_000_000_000L, rows[0][SavingsGoals.deadline])
            assertFalse(rows[0][SavingsGoals.archived], "archived must default to false")
            assertNull(rows[1][SavingsGoals.accountId])
            assertNull(rows[1][SavingsGoals.deadline])
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.schema.SavingsGoalsSchemaTest"`
Expected: FAIL to compile — `Unresolved reference: SavingsGoals`

- [ ] **Step 3: Add the table**

Append to `shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt`:

```kotlin
// Savings goals: target amount + optional deadline, progress = linked account's balance. The
// account link is nullable ONLY so account deletion can unlink (never silently delete) goals.
// See docs/superpowers/specs/2026-07-09-savings-goals-design.md.
object SavingsGoals : IntIdTable("SavingsGoal") {
    val name = varchar("name", 100)
    val targetAmount = long("target_amount")                     // cents, > 0
    val accountId = reference("account_id", Accounts).nullable()
    val deadline = long("deadline").nullable()                   // epoch millis
    val createdAt = long("created_at")                           // epoch millis
    val archived = bool("archived").default(false)
}
```

Then register it everywhere the schema is materialized:

1. `DatabaseDriverFactory.desktop.kt` — add `SavingsGoals` after `DetectedSubscriptions` in the `SchemaUtils.create(...)` call (line ~65).
2. `TestDatabaseFactory.kt` — add `SavingsGoals` after `DetectedSubscriptions` in **both** `SchemaUtils.create(...)` lists, and add `SavingsGoals` as the **first** entry of the `SchemaUtils.drop(...)` list in `clearAllTables()` (children drop before their referenced tables).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.schema.SavingsGoalsSchemaTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt \
        shared/src/desktopMain/kotlin/com/financeapp/db/DatabaseDriverFactory.desktop.kt \
        shared/src/commonTest/kotlin/com/financeapp/test/TestDatabaseFactory.kt \
        shared/src/commonTest/kotlin/com/financeapp/data/schema/SavingsGoalsSchemaTest.kt
git commit -m "feat: add SavingsGoals table and schema wiring"
```

---

### Task 2: Domain models + `GoalProgressCalculator`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/domain/model/SavingsGoal.kt`
- Create: `shared/src/commonMain/kotlin/com/financeapp/domain/goals/GoalProgressCalculator.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/domain/goals/GoalProgressCalculatorTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks (pure Kotlin).
- Produces (used by Tasks 3, 5, 6):
  - `com.financeapp.domain.model.SavingsGoal(id: Long, name: String, targetAmountCents: Long, accountId: Long?, deadlineMs: Long?, createdAtMs: Long, archived: Boolean)`
  - `com.financeapp.domain.model.GoalProgress(currentCents: Long, percent: Int, remainingCents: Long, isComplete: Boolean, neededPerMonthCents: Long?, onTrack: Boolean?)`
  - `com.financeapp.domain.model.GoalWithProgress(goal: SavingsGoal, progress: GoalProgress, accountName: String?)` — `accountName == null` means "unlinked".
  - `com.financeapp.domain.goals.GoalProgressCalculator.calculate(targetCents: Long, balanceCents: Long?, createdAtMs: Long, deadlineMs: Long?, nowMs: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): GoalProgress`

- [ ] **Step 1: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/financeapp/domain/goals/GoalProgressCalculatorTest.kt`:

```kotlin
package com.financeapp.domain.goals

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.*

class GoalProgressCalculatorTest {
    private val tz = TimeZone.UTC
    private fun ms(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).atStartOfDayIn(tz).toEpochMilliseconds()

    @Test
    fun `no deadline gives percent and remaining but no pacing`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = 25_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = null, nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(25_000, p.currentCents)
        assertEquals(25, p.percent)
        assertEquals(75_000, p.remainingCents)
        assertFalse(p.isComplete)
        assertNull(p.neededPerMonthCents)
        assertNull(p.onTrack)
    }

    @Test
    fun `over-funded goal is complete at 100 percent with zero remaining`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = 150_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2027, 1, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(100, p.percent)
        assertEquals(0, p.remainingCents)
        assertTrue(p.isComplete)
        assertEquals(0, p.neededPerMonthCents)
        assertEquals(true, p.onTrack)
    }

    @Test
    fun `negative balance clamps to zero percent`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = -5_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = null, nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(0, p.currentCents)
        assertEquals(0, p.percent)
        assertEquals(100_000, p.remainingCents)
    }

    @Test
    fun `unlinked goal has zero progress and no pacing even with a deadline`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = null,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2027, 1, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(0, p.currentCents)
        assertEquals(0, p.percent)
        assertEquals(100_000, p.remainingCents)
        assertNull(p.neededPerMonthCents)
        assertNull(p.onTrack)
    }

    @Test
    fun `behind pace at halfway point reports behind and needed per month`() {
        // Jan 1 -> Jan 1 next year, target $1,200. At Jul 1 the straight line expects ~$600.
        val p = GoalProgressCalculator.calculate(
            targetCents = 120_000, balanceCents = 30_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2027, 1, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(false, p.onTrack)
        // 6 calendar months left (Jul 1 -> Jan 1), $900 remaining -> $150/month.
        assertEquals(15_000, p.neededPerMonthCents)
    }

    @Test
    fun `ahead of pace at halfway point reports on track`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 120_000, balanceCents = 70_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2027, 1, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(true, p.onTrack)
    }

    @Test
    fun `past deadline with remaining is behind and needs the full remainder`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = 40_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2026, 6, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(false, p.onTrack)
        assertEquals(60_000, p.neededPerMonthCents)
    }

    @Test
    fun `deadline under one month away clamps to one month`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 100_000, balanceCents = 90_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2026, 7, 15), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(10_000, p.neededPerMonthCents, "monthsLeft must clamp to 1")
    }

    @Test
    fun `needed per month rounds up so the plan never undershoots`() {
        // $1,000 remaining over 3 months -> 33,334 cents, not 33,333.
        val p = GoalProgressCalculator.calculate(
            targetCents = 200_000, balanceCents = 100_000,
            createdAtMs = ms(2026, 1, 1), deadlineMs = ms(2026, 10, 1), nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(33_334, p.neededPerMonthCents)
    }

    @Test
    fun `non-positive target is treated as complete`() {
        val p = GoalProgressCalculator.calculate(
            targetCents = 0, balanceCents = 500,
            createdAtMs = ms(2026, 1, 1), deadlineMs = null, nowMs = ms(2026, 7, 1), timeZone = tz
        )
        assertEquals(100, p.percent)
        assertTrue(p.isComplete)
        assertEquals(0, p.remainingCents)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.domain.goals.GoalProgressCalculatorTest"`
Expected: FAIL to compile — `Unresolved reference: GoalProgressCalculator`

- [ ] **Step 3: Implement models and calculator**

Create `shared/src/commonMain/kotlin/com/financeapp/domain/model/SavingsGoal.kt`:

```kotlin
package com.financeapp.domain.model

/** Static goal config; progress is always derived from the linked account's balance. */
data class SavingsGoal(
    val id: Long,
    val name: String,
    val targetAmountCents: Long,
    val accountId: Long?,      // null only after the linked account was deleted
    val deadlineMs: Long?,
    val createdAtMs: Long,
    val archived: Boolean
)

data class GoalProgress(
    val currentCents: Long,          // clamped >= 0
    val percent: Int,                // 0..100
    val remainingCents: Long,        // >= 0
    val isComplete: Boolean,
    val neededPerMonthCents: Long?,  // null when no deadline or unlinked
    val onTrack: Boolean?            // null when no deadline or unlinked
)

data class GoalWithProgress(
    val goal: SavingsGoal,
    val progress: GoalProgress,
    val accountName: String?         // null => "needs an account"
)
```

Create `shared/src/commonMain/kotlin/com/financeapp/domain/goals/GoalProgressCalculator.kt`:

```kotlin
package com.financeapp.domain.goals

import com.financeapp.domain.model.GoalProgress
import kotlinx.datetime.TimeZone
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Pure pace/progress math for savings goals. Progress = linked account balance vs target;
 * pacing is a straight line from goal creation to the deadline (full-balance counting, so a
 * pre-funded account simply starts ahead). See the 2026-07-09 savings-goals spec.
 */
object GoalProgressCalculator {

    fun calculate(
        targetCents: Long,
        balanceCents: Long?,
        createdAtMs: Long,
        deadlineMs: Long?,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): GoalProgress {
        if (balanceCents == null) {
            // Unlinked goal: nothing to measure, no pacing.
            return GoalProgress(
                currentCents = 0,
                percent = 0,
                remainingCents = targetCents.coerceAtLeast(0),
                isComplete = false,
                neededPerMonthCents = null,
                onTrack = null
            )
        }
        val current = balanceCents.coerceAtLeast(0)
        if (targetCents <= 0) {
            // Degenerate target (UI forbids it; be safe anyway): already complete.
            return GoalProgress(current, 100, 0, true, deadlineMs?.let { 0L }, deadlineMs?.let { true })
        }

        val percent = ((current * 100) / targetCents).coerceIn(0, 100).toInt()
        val remaining = (targetCents - current).coerceAtLeast(0)
        val isComplete = current >= targetCents

        if (deadlineMs == null) return GoalProgress(current, percent, remaining, isComplete, null, null)
        if (isComplete) return GoalProgress(current, percent, remaining, true, 0, true)
        if (nowMs >= deadlineMs) {
            // Past deadline with money still to save: the whole remainder is due now.
            return GoalProgress(current, percent, remaining, false, remaining, false)
        }

        val nowDate = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(timeZone).date
        val deadlineDate = Instant.fromEpochMilliseconds(deadlineMs).toLocalDateTime(timeZone).date
        val monthsLeft = nowDate.monthsUntil(deadlineDate).coerceAtLeast(1).toLong()
        val neededPerMonth = (remaining + monthsLeft - 1) / monthsLeft   // ceil: never undershoot

        val totalSpan = deadlineMs - createdAtMs
        // Double is fine here: this is a comparison threshold, not stored money.
        val expected = if (totalSpan <= 0) targetCents.toDouble()
        else targetCents.toDouble() * (nowMs - createdAtMs).coerceAtLeast(0) / totalSpan
        return GoalProgress(current, percent, remaining, false, neededPerMonth, current >= expected)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.domain.goals.GoalProgressCalculatorTest"`
Expected: PASS (10 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/domain/model/SavingsGoal.kt \
        shared/src/commonMain/kotlin/com/financeapp/domain/goals/GoalProgressCalculator.kt \
        shared/src/commonTest/kotlin/com/financeapp/domain/goals/GoalProgressCalculatorTest.kt
git commit -m "feat: add savings goal domain model and progress calculator"
```

---

### Task 3: `GoalRepository` interface + implementation

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/domain/repository/GoalRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/financeapp/data/repository/GoalRepositoryImpl.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/data/repository/GoalRepositoryTest.kt`

**Interfaces:**
- Consumes: `SavingsGoals` table (Task 1); `SavingsGoal`/`GoalWithProgress`/`GoalProgressCalculator` (Task 2); existing `AccountRepository.getAccountsWithBalances(): Flow<List<AccountWithBalance>>` (`AccountWithBalance` has `.account: Account` and `.balance: Long`; `Account.id: Long`, `Account.name: String`).
- Produces (used by Tasks 4, 5, 6):

```kotlin
interface GoalRepository {
    fun getGoalsWithProgress(): Flow<List<GoalWithProgress>>
    suspend fun createGoal(name: String, targetAmountCents: Long, accountId: Long, deadlineMs: Long?): Long
    suspend fun updateGoal(id: Long, name: String, targetAmountCents: Long, accountId: Long?, deadlineMs: Long?): Boolean
    suspend fun setArchived(id: Long, archived: Boolean): Boolean
    suspend fun deleteGoal(id: Long): Boolean
    fun notifyGoalsChanged()
}
```

- [ ] **Step 1: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/financeapp/data/repository/GoalRepositoryTest.kt`:

```kotlin
package com.financeapp.data.repository

import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.GoalRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.test.TestDataFactory
import com.financeapp.test.clearAllTables
import com.financeapp.test.createTestDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GoalRepositoryTest {
    private lateinit var database: Database
    private lateinit var goals: GoalRepository
    private lateinit var accounts: AccountRepository
    private lateinit var transactions: TransactionRepository
    private var accountId: Long = 0
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        accounts = AccountRepositoryImpl(database, dispatcher)
        transactions = TransactionRepositoryImpl(database, dispatcher)
        goals = GoalRepositoryImpl(database, accounts, dispatcher)
        accountId = runBlocking { accounts.insertAccount(TestDataFactory.createTestAccount(name = "Vacation Savings")) }
    }

    @AfterTest
    fun teardown() = database.clearAllTables()

    @Test
    fun `created goal round-trips with account name and zero progress`() = runTest {
        goals.createGoal("Hawaii", 500_000, accountId, deadlineMs = null)
        val list = goals.getGoalsWithProgress().first()
        assertEquals(1, list.size)
        val g = list.single()
        assertEquals("Hawaii", g.goal.name)
        assertEquals(500_000, g.goal.targetAmountCents)
        assertEquals(accountId, g.goal.accountId)
        assertEquals("Vacation Savings", g.accountName)
        assertEquals(0, g.progress.currentCents)
        assertEquals(0, g.progress.percent)
        assertFalse(g.goal.archived)
    }

    @Test
    fun `progress reflects the linked account balance`() = runTest {
        goals.createGoal("Hawaii", 100_000, accountId, deadlineMs = null)
        transactions.insertTransaction(
            TestDataFactory.createTestTransaction(accountId = accountId, amount = 25_000)
        )
        accounts.notifyBalancesChanged()
        val g = goals.getGoalsWithProgress().first().single()
        assertEquals(25_000, g.progress.currentCents)
        assertEquals(25, g.progress.percent)
        assertEquals(75_000, g.progress.remainingCents)
    }

    @Test
    fun `createGoal rejects blank name and non-positive target`() = runTest {
        assertFailsWith<IllegalArgumentException> { goals.createGoal("  ", 100, accountId, null) }
        assertFailsWith<IllegalArgumentException> { goals.createGoal("X", 0, accountId, null) }
        assertFailsWith<IllegalArgumentException> { goals.createGoal("X", -5, accountId, null) }
    }

    @Test
    fun `updateGoal changes fields and returns false for missing id`() = runTest {
        val id = goals.createGoal("Hawaii", 100_000, accountId, deadlineMs = null)
        assertTrue(goals.updateGoal(id, "Maui", 200_000, accountId, deadlineMs = 1_800_000_000_000))
        val g = goals.getGoalsWithProgress().first().single()
        assertEquals("Maui", g.goal.name)
        assertEquals(200_000, g.goal.targetAmountCents)
        assertEquals(1_800_000_000_000, g.goal.deadlineMs)
        assertFalse(goals.updateGoal(9999, "Nope", 100, accountId, null))
    }

    @Test
    fun `setArchived flips the flag and delete removes the row`() = runTest {
        val id = goals.createGoal("Hawaii", 100_000, accountId, deadlineMs = null)
        assertTrue(goals.setArchived(id, true))
        assertTrue(goals.getGoalsWithProgress().first().single().goal.archived)
        assertTrue(goals.deleteGoal(id))
        assertTrue(goals.getGoalsWithProgress().first().isEmpty())
        assertFalse(goals.deleteGoal(id), "second delete is a no-op returning false")
        assertFalse(goals.setArchived(id, false), "archiving a missing id returns false")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.GoalRepositoryTest"`
Expected: FAIL to compile — `Unresolved reference: GoalRepository`

- [ ] **Step 3: Implement interface and repository**

Create `shared/src/commonMain/kotlin/com/financeapp/domain/repository/GoalRepository.kt`:

```kotlin
package com.financeapp.domain.repository

import com.financeapp.domain.model.GoalWithProgress
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    /** All goals (archived included — UI filters) with live, balance-derived progress. */
    fun getGoalsWithProgress(): Flow<List<GoalWithProgress>>

    /** @throws IllegalArgumentException on blank name or non-positive target. */
    suspend fun createGoal(name: String, targetAmountCents: Long, accountId: Long, deadlineMs: Long?): Long

    /** Returns false when [id] doesn't exist. @throws IllegalArgumentException as [createGoal]. */
    suspend fun updateGoal(id: Long, name: String, targetAmountCents: Long, accountId: Long?, deadlineMs: Long?): Boolean

    suspend fun setArchived(id: Long, archived: Boolean): Boolean

    suspend fun deleteGoal(id: Long): Boolean

    fun notifyGoalsChanged()
}
```

Create `shared/src/commonMain/kotlin/com/financeapp/data/repository/GoalRepositoryImpl.kt`:

```kotlin
package com.financeapp.data.repository

import com.financeapp.db.schema.SavingsGoals
import com.financeapp.domain.goals.GoalProgressCalculator
import com.financeapp.domain.model.GoalWithProgress
import com.financeapp.domain.model.SavingsGoal
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.GoalRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class GoalRepositoryImpl(
    private val database: Database,
    private val accountRepository: AccountRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : GoalRepository {
    private val goalsChanged = MutableStateFlow(0L)

    override fun getGoalsWithProgress(): Flow<List<GoalWithProgress>> =
        combine(goalsChanged, accountRepository.getAccountsWithBalances()) { _, accountsWithBalances ->
            val goals = withContext(ioDispatcher) {
                transaction(database) {
                    SavingsGoals.selectAll()
                        .orderBy(SavingsGoals.createdAt to SortOrder.ASC, SavingsGoals.id to SortOrder.ASC)
                        .map { it.toDomain() }
                }
            }
            val now = nowMs()
            goals.map { goal ->
                // Inactive (closed) accounts aren't in the balances flow, so a goal on one shows
                // as "needs an account" — deliberately, since it can no longer grow.
                val linked = goal.accountId?.let { accId ->
                    accountsWithBalances.firstOrNull { it.account.id == accId }
                }
                GoalWithProgress(
                    goal = goal,
                    progress = GoalProgressCalculator.calculate(
                        targetCents = goal.targetAmountCents,
                        balanceCents = linked?.balance,
                        createdAtMs = goal.createdAtMs,
                        deadlineMs = goal.deadlineMs,
                        nowMs = now
                    ),
                    accountName = linked?.account?.name
                )
            }
        }

    override suspend fun createGoal(
        name: String, targetAmountCents: Long, accountId: Long, deadlineMs: Long?
    ): Long = withContext(ioDispatcher) {
        validate(name, targetAmountCents)
        val id = transaction(database) {
            SavingsGoals.insert {
                it[SavingsGoals.name] = name.trim()
                it[targetAmount] = targetAmountCents
                it[SavingsGoals.accountId] = accountId.toInt()
                it[deadline] = deadlineMs
                it[createdAt] = nowMs()
            }[SavingsGoals.id].value.toLong()
        }
        notifyGoalsChanged()
        id
    }

    override suspend fun updateGoal(
        id: Long, name: String, targetAmountCents: Long, accountId: Long?, deadlineMs: Long?
    ): Boolean = withContext(ioDispatcher) {
        validate(name, targetAmountCents)
        val updated = transaction(database) {
            SavingsGoals.update({ SavingsGoals.id eq id.toInt() }) {
                it[SavingsGoals.name] = name.trim()
                it[targetAmount] = targetAmountCents
                it[SavingsGoals.accountId] = accountId?.toInt()
                it[deadline] = deadlineMs
            }
        } > 0
        if (updated) notifyGoalsChanged()
        updated
    }

    override suspend fun setArchived(id: Long, archived: Boolean): Boolean = withContext(ioDispatcher) {
        val updated = transaction(database) {
            SavingsGoals.update({ SavingsGoals.id eq id.toInt() }) {
                it[SavingsGoals.archived] = archived
            }
        } > 0
        if (updated) notifyGoalsChanged()
        updated
    }

    override suspend fun deleteGoal(id: Long): Boolean = withContext(ioDispatcher) {
        val deleted = transaction(database) {
            SavingsGoals.deleteWhere { SavingsGoals.id eq id.toInt() }
        } > 0
        if (deleted) notifyGoalsChanged()
        deleted
    }

    override fun notifyGoalsChanged() {
        goalsChanged.value += 1
    }

    private fun validate(name: String, targetAmountCents: Long) {
        require(name.isNotBlank()) { "Goal name must not be blank" }
        require(targetAmountCents > 0) { "Goal target must be positive" }
    }

    private fun ResultRow.toDomain(): SavingsGoal = SavingsGoal(
        id = this[SavingsGoals.id].value.toLong(),
        name = this[SavingsGoals.name],
        targetAmountCents = this[SavingsGoals.targetAmount],
        accountId = this[SavingsGoals.accountId]?.value?.toLong(),
        deadlineMs = this[SavingsGoals.deadline],
        createdAtMs = this[SavingsGoals.createdAt],
        archived = this[SavingsGoals.archived]
    )
}
```

Note: `deleteWhere` may need `import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq` depending on Exposed version — match whatever `SubscriptionRepositoryImpl.kt` imports for its `deleteWhere` calls.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.GoalRepositoryTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/domain/repository/GoalRepository.kt \
        shared/src/commonMain/kotlin/com/financeapp/data/repository/GoalRepositoryImpl.kt \
        shared/src/commonTest/kotlin/com/financeapp/data/repository/GoalRepositoryTest.kt
git commit -m "feat: add goal repository with live balance-derived progress"
```

---

### Task 4: Unlink goals when their account is deleted

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/data/repository/AccountRepositoryImpl.kt:147-210` (`deleteAccount`)
- Test: `shared/src/commonTest/kotlin/com/financeapp/data/repository/GoalRepositoryTest.kt` (add one test)

**Interfaces:**
- Consumes: `SavingsGoals` table (Task 1), `GoalRepository` (Task 3).
- Produces: no new API — a behavioral guarantee: `AccountRepository.deleteAccount(id)` nulls `SavingsGoals.accountId` for goals on that account, in the same transaction, before the account row is deleted.

- [ ] **Step 1: Write the failing test**

Add to `GoalRepositoryTest.kt`:

```kotlin
    @Test
    fun `deleting the linked account unlinks the goal instead of failing or deleting it`() = runTest {
        val doomedAccountId = accounts.insertAccount(TestDataFactory.createTestAccount(name = "Doomed"))
        val id = goals.createGoal("Orphan-to-be", 100_000, doomedAccountId, deadlineMs = null)

        accounts.deleteAccount(doomedAccountId)   // FK enforcement is ON: must not throw
        goals.notifyGoalsChanged()

        val g = goals.getGoalsWithProgress().first().single { it.goal.id == id }
        assertNull(g.goal.accountId, "goal must be unlinked, not deleted")
        assertNull(g.accountName)
        assertEquals(0, g.progress.currentCents)
        assertNull(g.progress.onTrack)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.GoalRepositoryTest"`
Expected: the new test FAILS — `deleteAccount` throws an H2 referential-integrity exception (a `SavingsGoal` row still references the account).

- [ ] **Step 3: Add the hand-clean step**

In `AccountRepositoryImpl.kt`, add the import:

```kotlin
import com.financeapp.db.schema.SavingsGoals
```

and in `deleteAccount`, immediately before the final `// Finally delete the account` block:

```kotlin
            // Unlink savings goals pointing at this account (never silently delete a goal —
            // the Goals screen shows them as "needs an account" for relinking).
            SavingsGoals.update({ SavingsGoals.accountId eq id.toInt() }) {
                it[accountId] = null
            }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.GoalRepositoryTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/data/repository/AccountRepositoryImpl.kt \
        shared/src/commonTest/kotlin/com/financeapp/data/repository/GoalRepositoryTest.kt
git commit -m "fix: unlink savings goals when their account is deleted"
```

---

### Task 5: `GoalsViewModel` + dollar parsing

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/ui/goals/GoalsViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/ui/goals/GoalsViewModelTest.kt`

**Interfaces:**
- Consumes: `GoalRepository` (Task 3), existing `AccountRepository.getAllAccounts(): Flow<List<Account>>`, `supervisedViewModelScope()` from `com.financeapp.ui`, `Account`/`AccountType` from `domain/model`.
- Produces (used by Task 6):
  - `GoalsUiState(goals: List<GoalWithProgress>, showArchived: Boolean, accounts: List<Account>)`
  - `GoalsViewModel(goalRepository, accountRepository)` with `uiState: StateFlow<GoalsUiState>`, `createGoal(name, targetAmountCents, accountId, deadlineMs)`, `updateGoal(id, name, targetAmountCents, accountId, deadlineMs)`, `setArchived(id, archived)`, `deleteGoal(id)`, `toggleShowArchived()`, `cleanup()`
  - `GoalsViewModel.Companion.parseDollarsToCents(text: String): Long?`

- [ ] **Step 1: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/financeapp/ui/goals/GoalsViewModelTest.kt`:

```kotlin
package com.financeapp.ui.goals

import com.financeapp.data.repository.AccountRepositoryImpl
import com.financeapp.data.repository.GoalRepositoryImpl
import com.financeapp.domain.model.AccountType
import com.financeapp.test.TestDataFactory
import com.financeapp.test.clearAllTables
import com.financeapp.test.createTestDatabase
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var database: Database
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var goals: GoalRepositoryImpl
    private var savingsId: Long = 0
    private var checkingId: Long = 0

    @BeforeTest
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        database = createTestDatabase()
        accounts = AccountRepositoryImpl(database, testDispatcher)
        goals = GoalRepositoryImpl(database, accounts, testDispatcher)
        checkingId = runBlocking {
            accounts.insertAccount(TestDataFactory.createTestAccount(name = "A-Checking", type = AccountType.CHECKING))
        }
        savingsId = runBlocking {
            accounts.insertAccount(TestDataFactory.createTestAccount(name = "Z-Savings", type = AccountType.SAVINGS))
        }
    }

    @AfterTest
    fun teardown() {
        database.clearAllTables()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `archived goals are hidden until toggled`() = runTest(testDispatcher, timeout = 5.seconds) {
        val keptId = runBlocking { goals.createGoal("Active", 100_000, savingsId, null) }
        val archivedId = runBlocking { goals.createGoal("Old", 100_000, savingsId, null) }
        runBlocking { goals.setArchived(archivedId, true) }

        val vm = GoalsViewModel(goals, accounts)
        vm.uiState.test(timeout = 5.seconds) {
            var state = awaitItem()
            while (state.goals.isEmpty()) state = awaitItem()
            assertEquals(listOf(keptId), state.goals.map { it.goal.id }, "archived hidden by default")

            vm.toggleShowArchived()
            var toggled = awaitItem()
            while (toggled.goals.size < 2) toggled = awaitItem()
            assertEquals(setOf(keptId, archivedId), toggled.goals.map { it.goal.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
        vm.cleanup()
    }

    @Test
    fun `account picker lists savings accounts first`() = runTest(testDispatcher, timeout = 5.seconds) {
        val vm = GoalsViewModel(goals, accounts)
        vm.uiState.test(timeout = 5.seconds) {
            var state = awaitItem()
            while (state.accounts.size < 2) state = awaitItem()
            assertEquals(listOf("Z-Savings", "A-Checking"), state.accounts.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
        vm.cleanup()
    }

    @Test
    fun `parseDollarsToCents handles money formats and rejects junk`() {
        assertEquals(123_456, GoalsViewModel.parseDollarsToCents("1,234.56"))
        assertEquals(123_456, GoalsViewModel.parseDollarsToCents("$1234.56"))
        assertEquals(500_000, GoalsViewModel.parseDollarsToCents("5000"))
        assertEquals(50, GoalsViewModel.parseDollarsToCents("0.50"))
        assertEquals(50, GoalsViewModel.parseDollarsToCents("0.5"), "single decimal digit means tens of cents")
        assertNull(GoalsViewModel.parseDollarsToCents(""))
        assertNull(GoalsViewModel.parseDollarsToCents("abc"))
        assertNull(GoalsViewModel.parseDollarsToCents("12.345"), "more than 2 decimals rejected")
        assertNull(GoalsViewModel.parseDollarsToCents("-50"), "negative rejected")
        assertNull(GoalsViewModel.parseDollarsToCents("0"), "zero rejected")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.goals.GoalsViewModelTest"`
Expected: FAIL to compile — `Unresolved reference: GoalsViewModel`

- [ ] **Step 3: Implement the ViewModel**

Create `shared/src/commonMain/kotlin/com/financeapp/ui/goals/GoalsViewModel.kt`:

```kotlin
package com.financeapp.ui.goals

import com.financeapp.domain.model.Account
import com.financeapp.domain.model.AccountType
import com.financeapp.domain.model.GoalWithProgress
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.GoalRepository
import com.financeapp.ui.launchReporting
import com.financeapp.ui.supervisedViewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GoalsUiState(
    val goals: List<GoalWithProgress> = emptyList(),
    val showArchived: Boolean = false,
    val accounts: List<Account> = emptyList()   // picker list, savings-type first
)

class GoalsViewModel(
    private val goalRepository: GoalRepository,
    private val accountRepository: AccountRepository
) {
    private val scope = supervisedViewModelScope()

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    private var all: List<GoalWithProgress> = emptyList()

    init {
        scope.launch {
            goalRepository.getGoalsWithProgress().collect { list ->
                all = list
                recompute()
            }
        }
        scope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(
                    accounts = accounts.sortedByDescending { it.type == AccountType.SAVINGS }
                )
            }
        }
    }

    fun createGoal(name: String, targetAmountCents: Long, accountId: Long, deadlineMs: Long?) =
        scope.launchReporting("create the goal") {
            goalRepository.createGoal(name, targetAmountCents, accountId, deadlineMs)
        }

    fun updateGoal(id: Long, name: String, targetAmountCents: Long, accountId: Long?, deadlineMs: Long?) =
        scope.launchReporting("update the goal") {
            goalRepository.updateGoal(id, name, targetAmountCents, accountId, deadlineMs)
        }

    fun setArchived(id: Long, archived: Boolean) =
        scope.launchReporting("archive the goal") { goalRepository.setArchived(id, archived) }

    fun deleteGoal(id: Long) =
        scope.launchReporting("delete the goal") { goalRepository.deleteGoal(id) }

    fun toggleShowArchived() {
        _uiState.value = _uiState.value.copy(showArchived = !_uiState.value.showArchived)
        recompute()
    }

    fun cleanup() {
        scope.cancel()
    }

    private fun recompute() {
        val showArchived = _uiState.value.showArchived
        _uiState.value = _uiState.value.copy(
            goals = all.filter { showArchived || !it.goal.archived }
        )
    }

    companion object {
        /** "1,234.56" / "$5000" -> positive cents; null when malformed, non-positive, or > 2 decimals. */
        fun parseDollarsToCents(text: String): Long? {
            val cleaned = text.trim().removePrefix("$").replace(",", "")
            if (!Regex("""^\d+(\.\d{1,2})?$""").matches(cleaned)) return null
            val parts = cleaned.split(".")
            val dollars = parts[0].toLongOrNull() ?: return null
            if (dollars > Long.MAX_VALUE / 100 - 1) return null
            val cents = if (parts.size == 2) parts[1].padEnd(2, '0').toLong() else 0L
            val total = dollars * 100 + cents
            return if (total > 0) total else null
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.goals.GoalsViewModelTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/ui/goals/GoalsViewModel.kt \
        shared/src/commonTest/kotlin/com/financeapp/ui/goals/GoalsViewModelTest.kt
git commit -m "feat: add goals view model with archived filtering and dollar parsing"
```

---

### Task 6: Goals screen, navigation, and DI wiring

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/ui/goals/GoalsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/navigation/AppNavigationRail.kt` (add `Goals` destination + `allDestinations`)
- Modify: `shared/src/commonMain/kotlin/com/financeapp/App.kt` (Screen enum ~line 155, koinInject ~line 168, `navigate` route map ~line 230, `currentRoute` map ~line 256, `when(currentScreen)` branch ~line 341)
- Modify: `shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt` (repository + VM registration)

**Interfaces:**
- Consumes: `GoalsViewModel`/`GoalsUiState`/`parseDollarsToCents` (Task 5), `GoalWithProgress` (Task 2), existing components: `CurrencyText`/`formatCurrency` (`com.financeapp.ui.components`), `DatePickerField` (`com.financeapp.ui.components.forms`), `NavigationDestination`/`NavigationGroup` (navigation).
- Produces: `GoalsScreen(viewModel: GoalsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier)`; nav route `"goals"`; `Screen.GOALS` enum case.

- [ ] **Step 1: Wire DI**

In `shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt` add imports:

```kotlin
import com.financeapp.domain.repository.GoalRepository
import com.financeapp.data.repository.GoalRepositoryImpl
```

Next to the `SubscriptionRepository` registration (~line 131) add:

```kotlin
    // args: database, accountRepository
    single<GoalRepository> { GoalRepositoryImpl(get(), get()) }
```

Next to the `SubscriptionViewModel` registration (~line 158) add:

```kotlin
    single { com.financeapp.ui.goals.GoalsViewModel(get(), get()) }
```

- [ ] **Step 2: Add the navigation destination**

In `AppNavigationRail.kt`, after the `Subscriptions` destination (line 73-78) add:

```kotlin
    val Goals = NavigationDestination(
        route = "goals",
        label = "Goals",
        icon = Icons.Default.Flag,
        group = NavigationGroup.TOOLS
    )
```

and add `Goals,` to `allDestinations` after `Subscriptions,` (line 152).

- [ ] **Step 3: Build the screen**

Create `shared/src/commonMain/kotlin/com/financeapp/ui/goals/GoalsScreen.kt`:

```kotlin
package com.financeapp.ui.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.Account
import com.financeapp.domain.model.GoalWithProgress
import com.financeapp.ui.components.CurrencyText
import com.financeapp.ui.components.formatCurrency
import com.financeapp.ui.components.forms.DatePickerField
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<GoalWithProgress?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GoalWithProgress?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Savings Goals", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { viewModel.toggleShowArchived() }) {
                Text(if (uiState.showArchived) "Hide archived" else "Show archived")
            }
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add Goal")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.goals.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text("No savings goals yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Set a target amount for an account and track your progress",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.goals, key = { it.goal.id }) { item ->
                    GoalCard(
                        item = item,
                        onEdit = { editing = item },
                        onArchiveToggle = { viewModel.setArchived(item.goal.id, !item.goal.archived) },
                        onDelete = { deleting = item }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        GoalEditorDialog(
            title = "Add Goal",
            initial = null,
            accounts = uiState.accounts,
            onDismiss = { showAddDialog = false },
            onSave = { name, targetCents, accountId, deadlineMs ->
                viewModel.createGoal(name, targetCents, accountId!!, deadlineMs)
                showAddDialog = false
            }
        )
    }

    editing?.let { item ->
        GoalEditorDialog(
            title = "Edit Goal",
            initial = item,
            accounts = uiState.accounts,
            onDismiss = { editing = null },
            onSave = { name, targetCents, accountId, deadlineMs ->
                viewModel.updateGoal(item.goal.id, name, targetCents, accountId, deadlineMs)
                editing = null
            }
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete goal?") },
            text = { Text("\"${item.goal.name}\" will be removed. The linked account and its transactions are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGoal(item.goal.id)
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun GoalCard(
    item: GoalWithProgress,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val goal = item.goal
    val progress = item.progress
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progress.isComplete) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.accountName ?: "Needs an account — edit to relink",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.accountName == null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (goal.archived) {
                    AssistChip(onClick = {}, enabled = false, label = { Text("Archived") })
                    Spacer(Modifier.width(8.dp))
                } else if (progress.onTrack != null && !progress.isComplete) {
                    val (label, color) =
                        if (progress.onTrack == true) "On track" to MaterialTheme.colorScheme.primary
                        else "Behind" to MaterialTheme.colorScheme.error
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(label, color = color) }
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit goal") }
                TextButton(onClick = onArchiveToggle) { Text(if (goal.archived) "Unarchive" else "Archive") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete goal", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyText(amountCents = progress.currentCents, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    " of ${formatCurrency(goal.targetAmountCents)}  ·  ${progress.percent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                val deadlinePart = goal.deadlineMs?.let { "by ${formatDeadline(it)}" }
                val neededPart = progress.neededPerMonthCents
                    ?.takeIf { it > 0 }
                    ?.let { "need ${formatCurrency(it)}/mo" }
                val summary = listOfNotNull(deadlinePart, neededPart).joinToString("  ·  ")
                if (summary.isNotEmpty()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditorDialog(
    title: String,
    initial: GoalWithProgress?,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (name: String, targetCents: Long, accountId: Long?, deadlineMs: Long?) -> Unit
) {
    val tz = remember { TimeZone.currentSystemDefault() }
    var name by remember { mutableStateOf(initial?.goal?.name ?: "") }
    var amountText by remember {
        mutableStateOf(initial?.goal?.targetAmountCents?.let { (it / 100.0).toString() } ?: "")
    }
    var accountId by remember { mutableStateOf(initial?.goal?.accountId) }
    var hasDeadline by remember { mutableStateOf(initial?.goal?.deadlineMs != null) }
    var deadlineDate by remember {
        mutableStateOf(
            initial?.goal?.deadlineMs
                ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date }
                ?: Clock.System.now().toLocalDateTime(tz).date
        )
    }
    var accountMenuOpen by remember { mutableStateOf(false) }

    val targetCents = GoalsViewModel.parseDollarsToCents(amountText)
    val nameValid = name.isNotBlank()
    val amountValid = targetCents != null
    val accountValid = accountId != null
    val canSave = nameValid && amountValid && accountValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = !nameValid && name.isNotEmpty(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Target amount") },
                    prefix = { Text("$") },
                    isError = !amountValid && amountText.isNotEmpty(),
                    supportingText = {
                        if (!amountValid && amountText.isNotEmpty()) Text("Enter a positive dollar amount")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = accountMenuOpen,
                    onExpandedChange = { accountMenuOpen = it }
                ) {
                    OutlinedTextField(
                        value = accounts.firstOrNull { it.id == accountId }?.name
                            ?: if (initial != null && initial.goal.accountId != null) "(deleted account)" else "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        isError = !accountValid,
                        supportingText = { if (!accountValid) Text("Pick the account this goal tracks") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = accountMenuOpen,
                        onDismissRequest = { accountMenuOpen = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${account.type.name.lowercase().replace('_', ' ')})") },
                                onClick = {
                                    accountId = account.id
                                    accountMenuOpen = false
                                }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasDeadline, onCheckedChange = { hasDeadline = it })
                    Text("Target date")
                }
                if (hasDeadline) {
                    DatePickerField(
                        selectedDate = deadlineDate,
                        onDateSelected = { deadlineDate = it },
                        label = "Deadline"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val deadlineMs =
                        if (hasDeadline) deadlineDate.atStartOfDayIn(tz).toEpochMilliseconds() else null
                    onSave(name.trim(), targetCents!!, accountId, deadlineMs)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatDeadline(ms: Long): String {
    val date = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} ${date.day}, ${date.year}"
}
```

Notes for the implementer:
- If `LinearProgressIndicator(progress = { ... })` (lambda overload) doesn't exist in this Compose version, use `LinearProgressIndicator(progress = progress.percent / 100f, ...)`.
- If `LocalDate.day` doesn't resolve, use `date.dayOfMonth` (kotlinx-datetime API name varies by version).
- If `menuAnchor()` requires arguments in this M3 version, match an existing `ExposedDropdownMenuBox` usage in the codebase (e.g. search `ui/transactions/AddTransactionDialog.kt`).
- `CurrencyText` colors by sign; passing `color = MaterialTheme.colorScheme.onSurface` keeps saved-amount neutral.

- [ ] **Step 4: Wire App.kt**

In `shared/src/commonMain/kotlin/com/financeapp/App.kt`:

1. Add imports:
```kotlin
import com.financeapp.ui.goals.GoalsViewModel
import com.financeapp.ui.goals.GoalsScreen
```
2. `Screen` enum (line ~155): add `GOALS` after `SUBSCRIPTIONS`.
3. In `MainContent()` next to the other `koinInject()` calls (~line 168):
```kotlin
    val goalsViewModel: GoalsViewModel = koinInject()
```
4. In the `navigate` route map (~line 230), after the `"subscriptions"` line:
```kotlin
            "goals" -> Screen.GOALS
```
5. In the `currentRoute` map (~line 256), after the `Screen.SUBSCRIPTIONS` line:
```kotlin
        Screen.GOALS -> "goals"
```
6. In the `when (currentScreen)` content block (~line 341), after the `Screen.SUBSCRIPTIONS` branch:
```kotlin
                Screen.GOALS -> GoalsScreen(
                    viewModel = goalsViewModel,
                    onBack = navigateBack,
                    modifier = Modifier.fillMaxSize()
                )
```

- [ ] **Step 5: Build and run the full test suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (compiles desktopApp + shared)

Run: `./gradlew :shared:desktopTest`
Expected: all tests pass (existing suite + ~19 new)

- [ ] **Step 6: Verify in the running app**

Run: `./gradlew :desktopApp:run`

Manual checks (requires unlocking with the local master password — if no local vault/data exists, note that and rely on the test suite):
1. "Goals" appears in the nav rail Tools group with a flag icon.
2. Add a goal on an account with transactions → progress bar reflects the balance immediately.
3. Add a deadline → "need $X/mo" and On track/Behind chip render.
4. Archive hides the goal; "Show archived" reveals it; delete removes it after confirm.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/ui/goals/GoalsScreen.kt \
        shared/src/commonMain/kotlin/com/financeapp/ui/navigation/AppNavigationRail.kt \
        shared/src/commonMain/kotlin/com/financeapp/App.kt \
        shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt
git commit -m "feat: add Savings Goals screen and navigation"
```

---

## Coverage map (spec section → task)

| Spec section | Task |
|---|---|
| Data model (`SavingsGoals` table) | 1 |
| Domain (`SavingsGoal`, `GoalProgress`, `GoalWithProgress`, calculator + edge cases) | 2 |
| Repository (flow combine, CRUD, validation, Koin) | 3 (Koin in 6) |
| FK hand-clean (unlink on account delete) | 4 |
| UI: ViewModel states, archived filter, dollar parsing | 5 |
| UI: screen, cards, editor dialog, nav entry | 6 |
| Error handling (dialog validation, past deadline allowed, missing-id no-ops) | 3, 5, 6 |
| Testing plan (calculator/repo/VM) | 2, 3+4, 5 |
