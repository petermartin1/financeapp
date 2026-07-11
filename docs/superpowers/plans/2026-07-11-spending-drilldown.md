# Spending Report Drill-Down Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clicking a pie slice (or category row) on the Spending by Category report shows the matching transactions across all accounts in a panel below the chart; clicking a transaction opens the existing edit dialog, and edits refresh the report in place.

**Architecture:** A new split-aware `expandSpendingDetailLines` keeps each spending line attached to its source `TransactionWithDetails`; the ViewModel builds BOTH the pie aggregation and the drill-down map from the same filtered detail lines, so a slice always sums exactly to its list. `PieChart` gains an optional click API backed by a pure, unit-tested `pieSliceAt` geometry function. The screen adds an inline panel and reuses `EditTransactionDialog` exactly as `GlobalSearchDialog` does.

**Tech Stack:** Kotlin/Compose Multiplatform (desktop JVM), Exposed v1 over H2, Koin, kotlin-test + Turbine.

**Spec:** `docs/superpowers/specs/2026-07-11-spending-drilldown-design.md`

## Global Constraints

- Money is ALWAYS integer cents (`Long`); never `Double` for amounts. Dates in the domain are `kotlinx.datetime.LocalDate`; DB stores epoch millis.
- Test command: `./gradlew :shared:desktopTest` (full suite; currently 587 tests, 0 failures — must stay 0 failures). Focused: `./gradlew :shared:desktopTest --tests "<fqcn>" --console=plain`.
- Exposed 1.3.0: `SqlExpressionBuilder.eq` is deprecation-level ERROR; use top-level `org.jetbrains.exposed.v1.core.eq` (relevant only if you touch queries).
- Uncategorized sentinel: drill-down map key and `CategorySpending.categoryId` use `0L` for "no category" (IntIdTable ids start at 1). `null` selection means "nothing selected".
- Spending filter rule (unchanged from today): line amount `< 0` AND category type is `EXPENSE` or line has no category.
- Commit messages: conventional style (`feat:`, `test:`, …). **NEVER add Co-Authored-By or any AI-attribution trailer.**
- TDD for Tasks 1–4: write the failing test, run it to see it fail, implement, run again, full suite before each commit.

## File Structure

| File | Role |
|---|---|
| `shared/src/commonMain/kotlin/com/financeapp/domain/reporting/SpendingLines.kt` | + `SpendingDetailLine`, `expandSpendingDetailLines` (Task 1) |
| `shared/src/commonTest/kotlin/com/financeapp/domain/reporting/SpendingDetailLinesTest.kt` | Task 1 tests |
| `shared/src/commonMain/kotlin/com/financeapp/domain/repository/TransactionRepository.kt` | + `getTransactionsWithDetailsByDateRange` (Task 2) |
| `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt` | Task 2 impl |
| `shared/src/commonTest/kotlin/com/financeapp/data/repository/TransactionRepositoryDateRangeDetailsTest.kt` | Task 2 tests |
| `shared/src/commonMain/kotlin/com/financeapp/ui/components/charts/PieGeometry.kt` | pure `pieSliceAt` (Task 3) |
| `shared/src/commonTest/kotlin/com/financeapp/ui/components/charts/PieGeometryTest.kt` | Task 3 tests |
| `shared/src/commonMain/kotlin/com/financeapp/ui/components/charts/PieChart.kt` | click + selection params (Task 3) |
| `shared/src/commonMain/kotlin/com/financeapp/domain/model/Report.kt` | `SpendingReport` gains `detailLinesByCategory` (Task 4) |
| `shared/src/commonMain/kotlin/com/financeapp/ui/reports/ReportsViewModel.kt` | detail-line report build, selection, edit/delete (Task 4) |
| `shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt` | ReportsViewModel gains TagRepository (Task 4) |
| `shared/src/commonTest/kotlin/com/financeapp/ui/reports/ReportsViewModelTest.kt` | Task 4 tests (+ update 2 existing constructor calls) |
| `shared/src/commonMain/kotlin/com/financeapp/ui/reports/ReportsScreen.kt` | panel UI, clickable rows, edit dialog (Task 5) |

---

### Task 1: Domain — `SpendingDetailLine` + `expandSpendingDetailLines`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/domain/reporting/SpendingLines.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/domain/reporting/SpendingDetailLinesTest.kt` (create)

**Interfaces:**
- Consumes: existing `expandSpendingLines`, `SpendingLine`, `TransactionWithDetails`, `SplitItem`, `TestDataFactory.createTestTransaction`.
- Produces (Task 4 relies on these exact shapes):
  ```kotlin
  data class SpendingDetailLine(
      val source: TransactionWithDetails,
      val categoryId: Long?,
      val lineAmountCents: Long,
      val isSplitPortion: Boolean
  )
  fun expandSpendingDetailLines(
      transactions: List<TransactionWithDetails>,
      splitsByTransactionId: Map<Long, List<SplitItem>>
  ): List<SpendingDetailLine>
  ```

- [ ] **Step 1: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/financeapp/domain/reporting/SpendingDetailLinesTest.kt`:

```kotlin
package com.financeapp.domain.reporting

import com.financeapp.domain.model.SplitItem
import com.financeapp.domain.model.Transaction
import com.financeapp.domain.model.TransactionWithDetails
import com.financeapp.test.TestDataFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpendingDetailLinesTest {

    private fun withDetails(txn: Transaction, accountName: String = "Checking") =
        TransactionWithDetails(
            transaction = txn,
            payeeName = null,
            categoryName = null,
            accountName = accountName
        )

    @Test
    fun `an unsplit transaction yields one line keeping its source`() {
        val txn = withDetails(TestDataFactory.createTestTransaction(id = 1, categoryId = 5, amount = -3000))

        val lines = expandSpendingDetailLines(listOf(txn), emptyMap())

        assertEquals(1, lines.size)
        assertEquals(txn, lines[0].source)
        assertEquals(5L, lines[0].categoryId)
        assertEquals(-3000L, lines[0].lineAmountCents)
        assertFalse(lines[0].isSplitPortion)
    }

    @Test
    fun `transfers are excluded`() {
        val transfer = withDetails(
            TestDataFactory.createTestTransaction(id = 1, categoryId = 5, amount = -3000, transferId = 2)
        )

        assertEquals(emptyList(), expandSpendingDetailLines(listOf(transfer), emptyMap()))
    }

    @Test
    fun `a split transaction yields a flagged line per split sharing the parent source`() {
        val parent = withDetails(TestDataFactory.createTestTransaction(id = 1, categoryId = 99, amount = -10000))
        val splits = mapOf(
            1L to listOf(
                SplitItem(transactionId = 1, categoryId = 5, amount = -6000),
                SplitItem(transactionId = 1, categoryId = 7, amount = -4000)
            )
        )

        val lines = expandSpendingDetailLines(listOf(parent), splits)

        assertEquals(2, lines.size)
        assertTrue(lines.all { it.source == parent && it.isSplitPortion })
        assertEquals(listOf(5L to -6000L, 7L to -4000L), lines.map { it.categoryId to it.lineAmountCents })
    }

    @Test
    fun `an empty split list falls back to the parent line`() {
        val txn = withDetails(TestDataFactory.createTestTransaction(id = 1, categoryId = 5, amount = -3000))

        val lines = expandSpendingDetailLines(listOf(txn), mapOf(1L to emptyList()))

        assertEquals(1, lines.size)
        assertFalse(lines[0].isSplitPortion)
    }

    @Test
    fun `projection onto (categoryId, amount) equals expandSpendingLines for the same input`() {
        // Pins the detail expansion against drifting from the aggregation expansion.
        val txns = listOf(
            TestDataFactory.createTestTransaction(id = 1, categoryId = 5, amount = -3000),
            TestDataFactory.createTestTransaction(id = 2, categoryId = 9, amount = -10000),
            TestDataFactory.createTestTransaction(id = 3, categoryId = 4, amount = -700, transferId = 8),
            TestDataFactory.createTestTransaction(id = 4, categoryId = null, amount = -1234)
        )
        val splits = mapOf(
            2L to listOf(
                SplitItem(transactionId = 2, categoryId = 5, amount = -6000),
                SplitItem(transactionId = 2, categoryId = null, amount = -4000)
            )
        )

        val detailProjection = expandSpendingDetailLines(txns.map { withDetails(it) }, splits)
            .map { SpendingLine(it.categoryId, it.lineAmountCents) }

        assertEquals(expandSpendingLines(txns, splits), detailProjection)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.domain.reporting.SpendingDetailLinesTest" --console=plain`
Expected: FAIL to compile — `unresolved reference: expandSpendingDetailLines` / `SpendingDetailLine`.

- [ ] **Step 3: Implement**

Append to `shared/src/commonMain/kotlin/com/financeapp/domain/reporting/SpendingLines.kt` (add import `com.financeapp.domain.model.TransactionWithDetails` to the existing imports):

```kotlin
/**
 * One spending line that keeps a handle on its source transaction, for drill-down display
 * (amounts in cents, sign preserved). [isSplitPortion] is true when this line is one split of a
 * larger transaction, so UIs can show "of $X split".
 */
data class SpendingDetailLine(
    val source: TransactionWithDetails,
    val categoryId: Long?,
    val lineAmountCents: Long,
    val isSplitPortion: Boolean
)

/**
 * Detail-preserving counterpart of [expandSpendingLines]: identical expansion semantics
 * (transfers excluded, split parents contribute one line per split, unsplit transactions
 * contribute a single line), but each line keeps its source [TransactionWithDetails].
 * SpendingDetailLinesTest pins the (categoryId, amount) projection to [expandSpendingLines]
 * so the two cannot drift.
 */
fun expandSpendingDetailLines(
    transactions: List<TransactionWithDetails>,
    splitsByTransactionId: Map<Long, List<SplitItem>>
): List<SpendingDetailLine> =
    transactions
        .filter { it.transaction.transferId == null }
        .flatMap { txn ->
            val splits = splitsByTransactionId[txn.transaction.id]
            if (splits.isNullOrEmpty()) {
                listOf(
                    SpendingDetailLine(
                        source = txn,
                        categoryId = txn.transaction.categoryId,
                        lineAmountCents = txn.transaction.amount,
                        isSplitPortion = false
                    )
                )
            } else {
                splits.map {
                    SpendingDetailLine(
                        source = txn,
                        categoryId = it.categoryId,
                        lineAmountCents = it.amount,
                        isSplitPortion = true
                    )
                }
            }
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.domain.reporting.SpendingDetailLinesTest" --console=plain`
Expected: PASS (5 tests).

- [ ] **Step 5: Full suite, then commit**

Run: `./gradlew :shared:desktopTest` — expected 592 tests, 0 failures.

```bash
git add shared/src/commonMain/kotlin/com/financeapp/domain/reporting/SpendingLines.kt shared/src/commonTest/kotlin/com/financeapp/domain/reporting/SpendingDetailLinesTest.kt
git commit -m "feat: add detail-preserving spending line expansion for drill-down"
```

---

### Task 2: Repository — `getTransactionsWithDetailsByDateRange`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/domain/repository/TransactionRepository.kt` (after `getTransactionsByDateRange`, line ~13)
- Modify: `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt` (after `getTransactionsByDateRange`, line ~132)
- Test: `shared/src/commonTest/kotlin/com/financeapp/data/repository/TransactionRepositoryDateRangeDetailsTest.kt` (create)

**Interfaces:**
- Consumes: existing `getAllTransactionsWithDetails` (lookup-join pattern) and `getTransactionsByDateRange` (date-bounds pattern) in the same impl file.
- Produces (Task 4 relies on this exact signature):
  ```kotlin
  fun getTransactionsWithDetailsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TransactionWithDetails>>
  ```

- [ ] **Step 1: Write the failing test**

Create `shared/src/commonTest/kotlin/com/financeapp/data/repository/TransactionRepositoryDateRangeDetailsTest.kt`:

```kotlin
package com.financeapp.data.repository

import com.financeapp.test.TestDataFactory
import com.financeapp.test.clearAllTables
import com.financeapp.test.createTestDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRepositoryDateRangeDetailsTest {
    private lateinit var database: Database
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var accountRepository: AccountRepositoryImpl
    private lateinit var categoryRepository: CategoryRepositoryImpl
    private lateinit var payeeRepository: PayeeRepositoryImpl
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        transactionRepository = TransactionRepositoryImpl(database, testDispatcher)
        accountRepository = AccountRepositoryImpl(database, testDispatcher)
        categoryRepository = CategoryRepositoryImpl(database, testDispatcher)
        payeeRepository = PayeeRepositoryImpl(database)
    }

    @AfterTest
    fun teardown() {
        database.clearAllTables()
    }

    @Test
    fun `returns only transactions inside the range with bounds inclusive`() = runTest(context = testDispatcher) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount())
        val inRangeDates = listOf(LocalDate(2026, 3, 1), LocalDate(2026, 3, 15), LocalDate(2026, 3, 31))
        val outOfRangeDates = listOf(LocalDate(2026, 2, 28), LocalDate(2026, 4, 1))
        (inRangeDates + outOfRangeDates).forEach { date ->
            transactionRepository.insertTransaction(
                TestDataFactory.createTestTransaction(accountId = accountId, date = date, amount = -1000)
            )
        }

        val result = transactionRepository
            .getTransactionsWithDetailsByDateRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))
            .first()

        assertEquals(inRangeDates.sortedDescending(), result.map { it.transaction.date })
    }

    @Test
    fun `joins payee, category, and account names`() = runTest(context = testDispatcher) {
        val accountId = accountRepository.insertAccount(TestDataFactory.createTestAccount(name = "My Checking"))
        val categoryId = categoryRepository.insertCategory(TestDataFactory.createTestCategory(name = "Groceries"))
        val payeeId = payeeRepository.insertPayee(TestDataFactory.createTestPayee(name = "Costco"))
        transactionRepository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = accountId,
                date = LocalDate(2026, 3, 10),
                amount = -5000,
                payeeId = payeeId,
                categoryId = categoryId
            )
        )

        val result = transactionRepository
            .getTransactionsWithDetailsByDateRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))
            .first()

        assertEquals(1, result.size)
        assertEquals("Costco", result[0].payeeName)
        assertEquals("Groceries", result[0].categoryName)
        assertEquals("My Checking", result[0].accountName)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.TransactionRepositoryDateRangeDetailsTest" --console=plain`
Expected: FAIL to compile — `unresolved reference: getTransactionsWithDetailsByDateRange`.

- [ ] **Step 3: Implement**

In `TransactionRepository.kt`, after the `getTransactionsByDateRange` declaration add:

```kotlin
    fun getTransactionsWithDetailsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TransactionWithDetails>>
```

In `TransactionRepositoryImpl.kt`, directly after the `getTransactionsByDateRange` override add (this combines that method's date bounds with `getAllTransactionsWithDetails`'s lookup joins — same style as both neighbors):

```kotlin
    override fun getTransactionsWithDetailsByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<TransactionWithDetails>> = transactionRefreshTrigger.map { _ ->
        val tz = TimeZone.currentSystemDefault()
        val startMillis = startDate.atStartOfDayIn(tz).toEpochMilliseconds()
        // Use proper date arithmetic to handle DST (23/25 hour days)
        val endMillis = endDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds() - 1

        withContext(ioDispatcher) {
            transaction(database) {
                val transactions = Transactions
                    .selectAll().where { (Transactions.date greaterEq startMillis) and (Transactions.date lessEq endMillis) }
                    .orderBy(Transactions.date to SortOrder.DESC, Transactions.id to SortOrder.DESC)
                    .map { it.toDomain() }

                val payees = Payees.selectAll().associate { it[Payees.id].value.toLong() to it[Payees.name] }
                val categories = Categories.selectAll().associate { it[Categories.id].value.toLong() to it[Categories.name] }
                val accounts = Accounts.selectAll().associate { it[Accounts.id].value.toLong() to it[Accounts.name] }

                transactions.map { txn ->
                    TransactionWithDetails(
                        transaction = txn,
                        payeeName = txn.payeeId?.let { payees[it] },
                        categoryName = txn.categoryId?.let { categories[it] },
                        accountName = accounts[txn.accountId] ?: ""
                    )
                }
            }
        }
    }
```

All names used (`Transactions`, `Payees`, `Categories`, `Accounts`, `atStartOfDayIn`, `DateTimeUnit`, `plus`, `greaterEq`, `lessEq`, `and`, `SortOrder`, `toDomain`) are already imported/used in this file.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.TransactionRepositoryDateRangeDetailsTest" --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Full suite, then commit**

Run: `./gradlew :shared:desktopTest` — expected 594 tests, 0 failures.

```bash
git add shared/src/commonMain/kotlin/com/financeapp/domain/repository/TransactionRepository.kt shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt shared/src/commonTest/kotlin/com/financeapp/data/repository/TransactionRepositoryDateRangeDetailsTest.kt
git commit -m "feat: add date-range transactions-with-details repository query"
```

---

### Task 3: Chart — pure slice hit-testing + clickable/selected `PieChart`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/ui/components/charts/PieGeometry.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/ui/components/charts/PieGeometryTest.kt` (create)
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/components/charts/PieChart.kt`

**Interfaces:**
- Produces (Task 5 relies on these):
  ```kotlin
  // PieGeometry.kt
  fun pieSliceAt(tapX: Float, tapY: Float, width: Float, height: Float, values: List<Float>, centerHoleRatio: Float = 0f): Int?
  // PieChart and AnimatedPieChart both gain (defaulted, so existing call sites compile unchanged):
  onSliceClick: ((Int) -> Unit)? = null,
  selectedIndex: Int? = null
  ```

- [ ] **Step 1: Write the failing geometry tests**

Create `shared/src/commonTest/kotlin/com/financeapp/ui/components/charts/PieGeometryTest.kt`:

```kotlin
package com.financeapp.ui.components.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PieGeometryTest {

    // A 200x200 canvas: center (100,100), radius 100. Four equal slices start at 12 o'clock
    // and sweep clockwise: [0]=12→3, [1]=3→6, [2]=6→9, [3]=9→12.
    private val quarters = listOf(1f, 1f, 1f, 1f)

    @Test
    fun `taps land in the slice under them, clockwise from 12 o'clock`() {
        assertEquals(0, pieSliceAt(tapX = 140f, tapY = 40f, width = 200f, height = 200f, values = quarters))   // ~1:30
        assertEquals(1, pieSliceAt(tapX = 160f, tapY = 160f, width = 200f, height = 200f, values = quarters))  // ~4:30
        assertEquals(2, pieSliceAt(tapX = 40f, tapY = 160f, width = 200f, height = 200f, values = quarters))   // ~7:30
        assertEquals(3, pieSliceAt(tapX = 40f, tapY = 40f, width = 200f, height = 200f, values = quarters))    // ~10:30
    }

    @Test
    fun `a boundary angle belongs to the next slice`() {
        // Exactly 3 o'clock (90° from top) is the start of slice 1, not the end of slice 0.
        assertEquals(1, pieSliceAt(tapX = 150f, tapY = 100f, width = 200f, height = 200f, values = quarters))
    }

    @Test
    fun `unequal values shift the boundaries proportionally`() {
        // [3,1]: slice 0 covers 270° (12 o'clock clockwise to 9 o'clock), slice 1 the rest.
        val values = listOf(3f, 1f)
        assertEquals(0, pieSliceAt(tapX = 160f, tapY = 160f, width = 200f, height = 200f, values = values)) // ~4:30
        assertEquals(1, pieSliceAt(tapX = 40f, tapY = 40f, width = 200f, height = 200f, values = values))   // ~10:30
    }

    @Test
    fun `taps outside the pie radius miss`() {
        assertNull(pieSliceAt(tapX = 1f, tapY = 1f, width = 200f, height = 200f, values = quarters))
        // Non-square canvas: pie diameter is min(300,200)=200 centered at (150,100); (295,100) is outside.
        assertNull(pieSliceAt(tapX = 295f, tapY = 100f, width = 300f, height = 200f, values = quarters))
    }

    @Test
    fun `taps inside the donut hole miss`() {
        assertNull(pieSliceAt(tapX = 100f, tapY = 100f, width = 200f, height = 200f, values = quarters, centerHoleRatio = 0.5f))
        // Just outside the hole (radius 50) still hits.
        assertEquals(1, pieSliceAt(tapX = 160f, tapY = 100f, width = 200f, height = 200f, values = quarters, centerHoleRatio = 0.5f))
    }

    @Test
    fun `empty or non-positive totals miss`() {
        assertNull(pieSliceAt(tapX = 100f, tapY = 100f, width = 200f, height = 200f, values = emptyList()))
        assertNull(pieSliceAt(tapX = 100f, tapY = 100f, width = 200f, height = 200f, values = listOf(0f, 0f)))
    }

    @Test
    fun `non-square canvas centers the pie on min dimension`() {
        // 300x200: center (150,100). Straight up from center lands in slice 0.
        assertEquals(0, pieSliceAt(tapX = 151f, tapY = 20f, width = 300f, height = 200f, values = quarters))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.components.charts.PieGeometryTest" --console=plain`
Expected: FAIL to compile — `unresolved reference: pieSliceAt`.

- [ ] **Step 3: Implement the geometry**

Create `shared/src/commonMain/kotlin/com/financeapp/ui/components/charts/PieGeometry.kt`:

```kotlin
package com.financeapp.ui.components.charts

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Maps a tap position on a pie-chart canvas to the index of the slice under it, or null when the
 * tap misses (outside the pie radius, inside the donut hole, or nothing drawable).
 *
 * Mirrors the drawing geometry in PieChart/AnimatedPieChart exactly: the pie is a circle of
 * diameter `min(width, height)` centered in the canvas; slices start at -90° (12 o'clock) and
 * sweep clockwise in list order, each proportional to its share of the total. A tap exactly on a
 * boundary angle belongs to the slice that starts there (pinned by PieGeometryTest).
 */
fun pieSliceAt(
    tapX: Float,
    tapY: Float,
    width: Float,
    height: Float,
    values: List<Float>,
    centerHoleRatio: Float = 0f
): Int? {
    val total = values.sum()
    if (values.isEmpty() || total <= 0f) return null

    val dx = tapX - width / 2f
    val dy = tapY - height / 2f
    val radius = min(width, height) / 2f
    val distance = sqrt(dx * dx + dy * dy)
    if (distance > radius) return null
    // The drawn hole radius is (minDimension * centerHoleRatio) / 2 == radius * centerHoleRatio.
    if (centerHoleRatio > 0f && distance < radius * centerHoleRatio) return null

    // atan2 in screen coordinates (y down) increases clockwise with 0° at 3 o'clock; shift by
    // +90° so 0° is the 12 o'clock drawing origin.
    val degrees = atan2(dy, dx) * (180f / PI.toFloat())
    val fromTop = (degrees + 90f + 360f) % 360f

    var cumulative = 0f
    values.forEachIndexed { index, value ->
        cumulative += (value / total) * 360f
        if (fromTop < cumulative) return index
    }
    return values.lastIndex // float rounding exactly at the 360° wrap
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.components.charts.PieGeometryTest" --console=plain`
Expected: PASS (7 tests).

- [ ] **Step 5: Wire clicks and selection into the chart composables**

In `PieChart.kt`:

Add imports:
```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
```

**`PieChart`** — change the signature (new params last, defaulted):
```kotlin
@Composable
fun PieChart(
    data: List<PieChartData>,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
    showLabels: Boolean = true,
    centerHoleRatio: Float = 0f, // 0 = full pie, 0.5 = donut with 50% hole
    totalLabel: String? = null,
    animationEnabled: Boolean = true,
    onSliceClick: ((Int) -> Unit)? = null,
    selectedIndex: Int? = null
) {
```

Replace its `Canvas(modifier = Modifier.fillMaxSize().padding(16.dp))` with (pointerInput comes after padding so tap coordinates share the draw coordinate space):

```kotlin
                val sliceValues = data.map { it.value }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .then(
                            if (onSliceClick != null) {
                                Modifier.pointerInput(sliceValues, centerHoleRatio) {
                                    detectTapGestures { tap ->
                                        pieSliceAt(
                                            tapX = tap.x,
                                            tapY = tap.y,
                                            width = size.width.toFloat(),
                                            height = size.height.toFloat(),
                                            values = sliceValues,
                                            centerHoleRatio = centerHoleRatio
                                        )?.let(onSliceClick)
                                    }
                                }
                            } else Modifier
                        )
                ) {
```

Inside that Canvas block, between the existing slice-drawing `forEachIndexed` loop and the "Draw center hole" section, add the selection emphasis (selected slice redrawn slightly larger, on top of its neighbors, before the hole so donuts stay donuts):

```kotlin
                    // Emphasize the selected slice by redrawing it slightly larger on top.
                    selectedIndex?.let { sel ->
                        if (sel in data.indices && total > 0f) {
                            val selStart = -90f + data.take(sel).sumOf { ((it.value / total) * 360f).toDouble() }.toFloat()
                            val selSweep = (data[sel].value / total) * 360f
                            val bump = size.minDimension * 0.04f
                            drawArc(
                                color = data[sel].color,
                                startAngle = selStart,
                                sweepAngle = selSweep,
                                useCenter = true,
                                size = Size(size.minDimension + bump, size.minDimension + bump),
                                topLeft = Offset(
                                    (size.width - size.minDimension - bump) / 2,
                                    (size.height - size.minDimension - bump) / 2
                                )
                            )
                        }
                    }
```

**`AnimatedPieChart`** — same three changes: add the two defaulted params to its signature, apply the identical `Canvas` modifier replacement (its canvas has the same `Modifier.fillMaxSize().padding(16.dp)`), and insert the identical selection-emphasis block between its slice loop and its "Draw center hole" section.

**`DonutChart` and `AnimatedDonutChart`** — add the two defaulted params and pass them through to the chart they delegate to:
```kotlin
    onSliceClick: ((Int) -> Unit)? = null,
    selectedIndex: Int? = null
```
…and in their bodies add `onSliceClick = onSliceClick, selectedIndex = selectedIndex` to the delegation call.

- [ ] **Step 6: Full suite, then commit**

Run: `./gradlew :shared:desktopTest` — expected 601 tests, 0 failures (existing chart call sites compile unchanged because the new params are defaulted).

```bash
git add shared/src/commonMain/kotlin/com/financeapp/ui/components/charts/PieGeometry.kt shared/src/commonTest/kotlin/com/financeapp/ui/components/charts/PieGeometryTest.kt shared/src/commonMain/kotlin/com/financeapp/ui/components/charts/PieChart.kt
git commit -m "feat: add slice hit-testing and click/selection support to pie charts"
```

---

### Task 4: ViewModel — report built from detail lines, selection, edit/delete

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/domain/model/Report.kt` (`SpendingReport`)
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/reports/ReportsViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt` (line ~156)
- Test: `shared/src/commonTest/kotlin/com/financeapp/ui/reports/ReportsViewModelTest.kt` (extend; update the 2 existing constructor calls)

**Interfaces:**
- Consumes: Task 1's `expandSpendingDetailLines`/`SpendingDetailLine`, Task 2's `getTransactionsWithDetailsByDateRange`, existing `TagRepository.setTransactionTags`/`getTagsForTransaction`, `TransactionRepository.updateTransaction`/`deleteTransaction`, `AccountRepository.notifyBalancesChanged`, `launchReporting` (`com.financeapp.ui.launchReporting`).
- Produces (Task 5 relies on these exact members):
  ```kotlin
  // SpendingReport gains (defaulted):
  val detailLinesByCategory: Map<Long, List<SpendingDetailLine>> = emptyMap()  // key: categoryId ?: 0L
  // ReportsUiState gains:
  val selectedSpendingCategoryId: Long? = null
  // ReportsViewModel constructor becomes:
  ReportsViewModel(transactionRepository, accountRepository, categoryRepository, tagRepository)
  // New ViewModel members:
  fun selectSpendingCategory(categoryKey: Long)   // toggles: re-selecting clears
  fun clearSpendingSelection()
  fun editTransaction(txn: Transaction, categoryId: Long?, memo: String?, date: LocalDate, isCleared: Boolean, tagIds: List<Long>)
  fun deleteTransaction(id: Long)
  suspend fun getTagsForTransaction(transactionId: Long): List<Long>
  ```

- [ ] **Step 1: Write the failing tests**

In `ReportsViewModelTest.kt`:

1. Add imports:
```kotlin
import com.financeapp.data.repository.PayeeRepositoryImpl
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.assertNull
```
2. Add a `tagRepository` field, initialized in `setup()` alongside the others, and delete the local `val tagRepository = ...` inside the split test:
```kotlin
    private lateinit var tagRepository: TagRepositoryImpl
    // in setup():
        tagRepository = TagRepositoryImpl(database, testDispatcher)
```
3. Update BOTH existing `ReportsViewModel(transactionRepository, accountRepository, categoryRepository)` constructions to `ReportsViewModel(transactionRepository, accountRepository, categoryRepository, tagRepository)`.
4. Add these tests:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.reports.ReportsViewModelTest" --console=plain`
Expected: FAIL to compile — `detailLinesByCategory`, `selectedSpendingCategoryId`, `selectSpendingCategory`, `editTransaction` unresolved; 4-arg constructor unresolved.

- [ ] **Step 3: Implement**

**`Report.kt`** — add the import and extend `SpendingReport` (defaulted so other constructors compile):

```kotlin
import com.financeapp.domain.reporting.SpendingDetailLine

data class SpendingReport(
    val categorySpending: List<CategorySpending>,
    val totalSpent: Long,
    /** Drill-down lines per category, keyed by `categoryId ?: 0L` (0L = Uncategorized). */
    val detailLinesByCategory: Map<Long, List<SpendingDetailLine>> = emptyMap()
)
```

**`Modules.kt`** line ~156:
```kotlin
    single { ReportsViewModel(get<TransactionRepository>(), get<AccountRepository>(), get<CategoryRepository>(), get<TagRepository>()) }
```
(`TagRepository` is already imported in `Modules.kt`.)

**`ReportsViewModel.kt`**:

Add imports:
```kotlin
import com.financeapp.domain.repository.TagRepository
import com.financeapp.domain.reporting.expandSpendingDetailLines
import com.financeapp.ui.launchReporting
```
(Remove the now-unused `com.financeapp.domain.reporting.expandSpendingLines` import.)

Constructor gains the fourth repository:
```kotlin
class ReportsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository
) {
```

`ReportsUiState` gains the selection (with doc):
```kotlin
data class ReportsUiState(
    val selectedType: ReportType = ReportType.SPENDING_BY_CATEGORY,
    val selectedPeriod: ReportPeriod = ReportPeriod.ONE_MONTH,
    val spendingReport: SpendingReport = SpendingReport(emptyList(), 0),
    val incomeExpenseReport: IncomeExpenseReport = IncomeExpenseReport(emptyList(), 0, 0),
    val netWorthReport: NetWorthReport = NetWorthReport(emptyList(), 0),
    val isLoading: Boolean = true,
    /** Drill-down selection: key into detailLinesByCategory (0L = Uncategorized); null = none. */
    val selectedSpendingCategoryId: Long? = null
)
```

`setReportType` and `setPeriod` clear the selection:
```kotlin
    fun setReportType(type: ReportType) {
        _uiState.value = _uiState.value.copy(selectedType = type, selectedSpendingCategoryId = null)
        loadReport()
    }

    fun setPeriod(period: ReportPeriod) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period, selectedSpendingCategoryId = null)
        loadReport()
    }
```

In `loadReport()`, the `SPENDING_BY_CATEGORY` branch keeps the selection only if the reloaded report still contains it:
```kotlin
                    ReportType.SPENDING_BY_CATEGORY -> {
                        val report = loadSpendingReport(startDate, endDate)
                        _uiState.value = _uiState.value.copy(
                            spendingReport = report,
                            selectedSpendingCategoryId = _uiState.value.selectedSpendingCategoryId
                                ?.takeIf { report.detailLinesByCategory.containsKey(it) },
                            isLoading = false
                        )
                    }
```

Replace `loadSpendingReport` entirely — the pie is now aggregated from the same filtered detail lines the panel displays, so a slice always sums exactly to its list:
```kotlin
    private suspend fun loadSpendingReport(startDate: LocalDate, endDate: LocalDate): SpendingReport {
        val transactions = transactionRepository.getTransactionsWithDetailsByDateRange(startDate, endDate).first()

        val categories = categoryRepository.getAllCategories().first()
        val categoriesById = categories.associateBy { it.id }
        val categoryNames = categories.associate { it.id to it.name }

        // Expand split transactions into their per-category lines so a split purchase is reported
        // under each split's category instead of the parent's.
        val splitsByTransactionId =
            transactionRepository.getSplitsByTransactionIds(transactions.map { it.transaction.id })

        // Spending = negative, non-transfer outflows. Exclude income- and transfer-typed
        // categories (a refund/charge-back tagged with an income category is not spending),
        // consistent with the dashboard's getSpendingByCategory. Uncategorized outflows are
        // still shown as "Uncategorized".
        val spendingLines = expandSpendingDetailLines(transactions, splitsByTransactionId)
            .filter { it.lineAmountCents < 0 }
            .filter { line ->
                val type = line.categoryId?.let { categoriesById[it]?.type }
                type == null || type == CategoryType.EXPENSE
            }

        // The pie below is aggregated from these same lines, so a slice always sums to its
        // drill-down list.
        val detailLinesByCategory = spendingLines
            .groupBy { it.categoryId ?: 0L }
            .mapValues { (_, lines) -> lines.sortedByDescending { it.source.transaction.date } }

        val totalSpent = spendingLines.sumOf { kotlin.math.abs(it.lineAmountCents) }

        val categorySpending = detailLinesByCategory.map { (categoryKey, lines) ->
            val amount = lines.sumOf { kotlin.math.abs(it.lineAmountCents) }
            val categoryName = if (categoryKey == 0L) "Uncategorized" else categoryNames[categoryKey] ?: "Uncategorized"
            val percentage = if (totalSpent > 0) (amount.toFloat() / totalSpent) * 100 else 0f
            CategorySpending(
                categoryId = categoryKey,
                categoryName = categoryName,
                amount = amount,
                percentage = percentage
            )
        }.sortedByDescending { it.amount }

        return SpendingReport(
            categorySpending = categorySpending,
            totalSpent = totalSpent,
            detailLinesByCategory = detailLinesByCategory
        )
    }
```

Add the new public members (before `cleanup()`), mirroring `SearchViewModel.editTransaction`'s save path:
```kotlin
    fun selectSpendingCategory(categoryKey: Long) {
        val current = _uiState.value.selectedSpendingCategoryId
        _uiState.value = _uiState.value.copy(
            selectedSpendingCategoryId = if (current == categoryKey) null else categoryKey
        )
    }

    fun clearSpendingSelection() {
        _uiState.value = _uiState.value.copy(selectedSpendingCategoryId = null)
    }

    /** Saves an edit from the drill-down panel, then rebuilds the report (same path as search). */
    fun editTransaction(
        txn: Transaction,
        categoryId: Long?,
        memo: String?,
        date: LocalDate,
        isCleared: Boolean,
        tagIds: List<Long>
    ) {
        scope.launchReporting("save the transaction") {
            val updated = txn.copy(
                categoryId = categoryId,
                memo = memo?.ifBlank { null },
                date = date,
                isCleared = isCleared
            )
            transactionRepository.updateTransaction(updated)
            tagRepository.setTransactionTags(txn.id, tagIds)
            accountRepository.notifyBalancesChanged()
            loadReport()
        }
    }

    fun deleteTransaction(id: Long) {
        scope.launchReporting("delete the transaction") {
            transactionRepository.deleteTransaction(id)
            accountRepository.notifyBalancesChanged()
            loadReport()
        }
    }

    suspend fun getTagsForTransaction(transactionId: Long): List<Long> =
        tagRepository.getTagsForTransaction(transactionId).map { it.id }
```
(`Transaction` is already available via the existing `com.financeapp.domain.model.*` import.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.reports.ReportsViewModelTest" --console=plain`
Expected: PASS (7 tests — 2 existing + 5 new).

- [ ] **Step 5: Full suite, then commit**

Run: `./gradlew :shared:desktopTest` — expected 606 tests, 0 failures.

```bash
git add shared/src/commonMain/kotlin/com/financeapp/domain/model/Report.kt shared/src/commonMain/kotlin/com/financeapp/ui/reports/ReportsViewModel.kt shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt shared/src/commonTest/kotlin/com/financeapp/ui/reports/ReportsViewModelTest.kt
git commit -m "feat: build spending report from drill-down detail lines with selection and editing"
```

---

### Task 5: Screen — drill-down panel, clickable slices/rows, edit dialog

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/reports/ReportsScreen.kt`

**Interfaces:**
- Consumes: Task 3's `onSliceClick`/`selectedIndex` on `PieChart`; Task 4's `selectedSpendingCategoryId`, `detailLinesByCategory`, `selectSpendingCategory`, `clearSpendingSelection`, `editTransaction`, `deleteTransaction`, `getTagsForTransaction`; existing `EditTransactionDialog` (from `com.financeapp.ui.transactions`, signature `(transaction: TransactionWithDetails, onDismiss, onSave(categoryId, memo, date, isCleared, tagIds), onDelete, initialTagIds)`).
- Produces: UI only; nothing downstream.

No new unit tests (UI-wiring task, matching project convention) — Tasks 1–4 already cover the logic. Build + full suite must stay green.

- [ ] **Step 1: Update `ReportsScreen.kt`**

Add imports:
```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.financeapp.domain.reporting.SpendingDetailLine
import com.financeapp.ui.transactions.EditTransactionDialog
import kotlinx.coroutines.launch
```

At the `SPENDING_BY_CATEGORY` call site (line ~93), pass the selection and viewModel (and drop the unused `monthNames` argument):
```kotlin
                    ReportType.SPENDING_BY_CATEGORY -> SpendingByCategoryReport(
                        report = uiState.spendingReport,
                        selectedCategoryKey = uiState.selectedSpendingCategoryId,
                        viewModel = viewModel
                    )
```

Replace `SpendingByCategoryReport` with:
```kotlin
@Composable
private fun SpendingByCategoryReport(
    report: SpendingReport,
    selectedCategoryKey: Long?,
    viewModel: ReportsViewModel
) {
    if (report.categorySpending.isEmpty()) {
        EmptyReportMessage("No spending data for this period")
        return
    }

    val colors = listOf(
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFFC107),
        Color(0xFFFF5722), Color(0xFF9C27B0), Color(0xFF00BCD4),
        Color(0xFFE91E63), Color(0xFF8BC34A), Color(0xFF3F51B5),
        Color(0xFFFF9800)
    )

    var transactionToEdit by remember { mutableStateOf<TransactionWithDetails?>(null) }
    var editTagIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    val selectedIndex = report.categorySpending
        .indexOfFirst { it.categoryId == selectedCategoryKey }
        .takeIf { it >= 0 }
    val selectedSpending = selectedIndex?.let { report.categorySpending[it] }
    val selectedLines = selectedCategoryKey?.let { report.detailLinesByCategory[it] }.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pie chart
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Spending Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    CategorySpendingPieChart(
                        categorySpending = report.categorySpending,
                        selectedIndex = selectedIndex,
                        onSliceClick = { index ->
                            viewModel.selectSpendingCategory(report.categorySpending[index].categoryId)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Total: ${formatCurrency(report.totalSpent)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Drill-down panel for the selected slice
        if (selectedSpending != null) {
            item {
                DrillDownHeader(
                    spending = selectedSpending,
                    lineCount = selectedLines.size,
                    onClear = { viewModel.clearSpendingSelection() }
                )
            }
            // No item keys: two splits of one transaction can share id+category, so positional
            // identity is the only always-unique choice here.
            items(selectedLines) { line ->
                SpendingDetailRow(
                    line = line,
                    onClick = {
                        coroutineScope.launch {
                            editTagIds = viewModel.getTagsForTransaction(line.source.transaction.id)
                            transactionToEdit = line.source
                        }
                    }
                )
            }
        }

        // Category list
        items(report.categorySpending.take(10)) { item ->
            val colorIndex = report.categorySpending.indexOf(item) % colors.size
            CategorySpendingItem(
                item = item,
                color = colors[colorIndex],
                selected = item.categoryId == selectedCategoryKey,
                onClick = { viewModel.selectSpendingCategory(item.categoryId) }
            )
        }
    }

    // Edit transaction dialog (same reuse pattern as GlobalSearchDialog)
    transactionToEdit?.let { txn ->
        EditTransactionDialog(
            transaction = txn,
            onDismiss = { transactionToEdit = null },
            onSave = { categoryId, memo, date, isCleared, tagIds ->
                viewModel.editTransaction(txn.transaction, categoryId, memo, date, isCleared, tagIds)
                transactionToEdit = null
            },
            onDelete = {
                viewModel.deleteTransaction(txn.transaction.id)
                transactionToEdit = null
            },
            initialTagIds = editTagIds
        )
    }
}
```

Update `CategorySpendingPieChart` to pass the click/selection through:
```kotlin
@Composable
private fun CategorySpendingPieChart(
    categorySpending: List<CategorySpending>,
    selectedIndex: Int?,
    onSliceClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val chartData = categorySpending.mapIndexed { index, item ->
        PieChartData(
            label = item.categoryName,
            value = item.amount.toFloat() / 100f,
            color = ChartColors.CategoryPalette[index % ChartColors.CategoryPalette.size]
        )
    }

    PieChart(
        data = chartData,
        modifier = modifier,
        showLegend = true,
        showLabels = false,
        onSliceClick = onSliceClick,
        selectedIndex = selectedIndex
    )
}
```

Add the two new composables (below `CategorySpendingPieChart`):
```kotlin
@Composable
private fun DrillDownHeader(
    spending: CategorySpending,
    lineCount: Int,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                spending.categoryName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${formatCurrency(spending.amount)} · $lineCount transaction${if (lineCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, contentDescription = "Clear selection")
        }
    }
}

@Composable
private fun SpendingDetailRow(
    line: SpendingDetailLine,
    onClick: () -> Unit
) {
    val txn = line.source.transaction
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    line.source.payeeName ?: txn.importedName ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${txn.date.monthNumber}/${txn.date.dayOfMonth}/${txn.date.year} · ${line.source.accountName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                txn.memo?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatCurrency(kotlin.math.abs(line.lineAmountCents)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (line.isSplitPortion) {
                    Text(
                        "of ${formatCurrency(kotlin.math.abs(txn.amount))} split",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

Update `CategorySpendingItem` to be clickable with a selection highlight (replace the whole function):
```kotlin
@Composable
private fun CategorySpendingItem(
    item: CategorySpending,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.categoryName,
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { item.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = color
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatCurrency(item.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${item.percentage.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 2: Build and run the full suite**

Run: `./gradlew build` — expected BUILD SUCCESSFUL.
Run: `./gradlew :shared:desktopTest` — expected 606 tests, 0 failures.
(Do NOT run the app interactively — it requires a master password prompt.)

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/ui/reports/ReportsScreen.kt
git commit -m "feat: add spending drill-down panel with clickable pie slices"
```

---

## Coverage map (spec → task)

| Spec requirement | Task |
|---|---|
| `SpendingDetailLine` + `expandSpendingDetailLines` + projection-equivalence pin | 1 |
| `getTransactionsWithDetailsByDateRange` (bounds + joins) | 2 |
| `pieSliceAt` pure hit-testing (boundaries, hole, outside, wraparound) | 3 |
| `PieChart`/`AnimatedPieChart`/donut variants: `onSliceClick` + `selectedIndex` emphasis | 3 |
| Pie aggregated from the same lines the panel shows (invariant test) | 4 |
| `0L` Uncategorized sentinel drill-down | 4 |
| Selection toggle / clear-on-period-change / preserve-vs-clear across edit reload | 4 |
| `editTransaction`/`deleteTransaction`/`getTagsForTransaction` on ReportsViewModel + DI | 4 |
| Panel header (name, total, count, ✕), rows (date, payee, account, memo, amount, "of $X split"), newest-first | 4 (sort) + 5 (render) |
| Clickable category rows with highlight; slice ↔ row selection shared | 5 |
| `EditTransactionDialog` reuse wired like `GlobalSearchDialog` | 5 |
| Tap in hole/outside ignored; thin-slice fallback = category row | 3 + 5 |
