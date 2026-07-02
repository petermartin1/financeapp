# Subscription Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically detect recurring "subscription" charges (fixed and variable amount) from transaction history and surface them on a dedicated Subscriptions screen with cadence, amount, and next-expected date.

**Architecture:** A pure `SubscriptionDetector` (interval-clustering per payee) produces candidate value objects. A `SubscriptionRepository` persists them in a new `DetectedSubscriptions` table and reconciles re-scans by a stable `matchKey` with sticky confirm/dismiss status. A `SubscriptionScanService` orchestrates scans — after each import and once over existing history on first launch (tracked by a preferences flag, set only after a committed scan for crash-safety). A new Compose screen lists results.

**Tech Stack:** Kotlin/Compose Multiplatform (desktop/JVM), Exposed ORM over H2, Koin DI, kotlinx-datetime, kotlinx-coroutines Flow. Tests: kotlin.test + coroutines-test + Turbine, in-memory H2.

## Global Constraints

- Monetary amounts are integer cents (`Long`), never `Double`. Store amounts as absolute (positive) cents in `DetectedSubscriptions`.
- Dates stored as Unix epoch **milliseconds** (`Long`) via `localDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()`; domain models expose `kotlinx.datetime.LocalDate`.
- All DB access goes through Exposed `transaction(database) { ... }` blocks; repositories take `Database` + an injected `CoroutineDispatcher = Dispatchers.IO` and use `withContext(ioDispatcher)`.
- FK enforcement is ON: any delete path touching a referenced row must hand-clean child rows. `DetectedSubscriptions.payeeId` references `Payees`.
- Package base `com.financeapp`. Target is desktop/JVM only.
- Run the test suite with `./gradlew :shared:desktopTest`. Run a single test class with `./gradlew :shared:desktopTest --tests "FQCN"`.
- Commit messages: do NOT add any `Co-Authored-By: Claude` or AI-attribution trailer.

---

## File Structure

**Create:**
- `shared/src/commonMain/kotlin/com/financeapp/domain/recurrence/Recurrence.kt` — shared month-anchored date-advance helper (extracted so the detector and the scheduled planner share one implementation).
- `shared/src/commonMain/kotlin/com/financeapp/domain/model/DetectedSubscription.kt` — `DetectedSubscription` model + `SubscriptionStatus` enum.
- `shared/src/commonMain/kotlin/com/financeapp/domain/subscriptions/SubscriptionDetector.kt` — pure detector + `SubscriptionSource` interface + `SubscriptionCandidate` output type.
- `shared/src/commonMain/kotlin/com/financeapp/domain/repository/SubscriptionRepository.kt` — repository interface.
- `shared/src/commonMain/kotlin/com/financeapp/data/repository/SubscriptionRepositoryImpl.kt` — Exposed impl + reconciliation.
- `shared/src/commonMain/kotlin/com/financeapp/domain/service/SubscriptionScanService.kt` — scan orchestration.
- `shared/src/commonMain/kotlin/com/financeapp/ui/subscriptions/SubscriptionViewModel.kt` — screen state/actions.
- `shared/src/commonMain/kotlin/com/financeapp/ui/subscriptions/SubscriptionsScreen.kt` — Compose screen.
- Test files mirroring each of the above under `shared/src/commonTest/kotlin/...`.

**Modify:**
- `shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt` — add `DetectedSubscriptions` table.
- `shared/src/desktopMain/kotlin/com/financeapp/db/DatabaseDriverFactory.desktop.kt` — register table in `SchemaUtils.create(...)`.
- `shared/src/commonTest/kotlin/com/financeapp/test/TestDatabaseFactory.kt` — register table in each `SchemaUtils.create(...)` list.
- `shared/src/commonMain/kotlin/com/financeapp/ui/scheduled/ScheduledEntryPlanner.kt` — delegate `nextScheduledDate` to the new helper.
- `shared/src/commonMain/kotlin/com/financeapp/domain/repository/PreferencesRepository.kt` + `data/repository/PreferencesRepositoryImpl.kt` — initial-scan flag.
- `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/ImportRepository.kt` — trigger `scanAfterImport()`.
- `shared/src/commonMain/kotlin/com/financeapp/ui/AppViewModel.kt` — trigger `runInitialScanIfNeeded()` in `startPostUnlock()`.
- `shared/src/commonMain/kotlin/com/financeapp/data/repository/PayeeRepositoryImpl.kt` — null `DetectedSubscriptions.payeeId` on payee delete.
- `shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt` — register repository, service, view model; add ctor args.
- `shared/src/commonMain/kotlin/com/financeapp/App.kt` + `ui/navigation/AppNavigationRail.kt` — new nav destination + render branch.

---

## Task 1: Shared month-anchored recurrence helper

Extract the existing `nextScheduledDate` month-end-anchoring logic into the domain layer so the detector can reuse it without the domain depending on the `ui` package.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/domain/recurrence/Recurrence.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/scheduled/ScheduledEntryPlanner.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/domain/recurrence/RecurrenceTest.kt`

**Interfaces:**
- Produces: `fun nextRecurrenceDate(current: LocalDate, frequency: TransactionFrequency, anchorDay: Int = current.dayOfMonth): LocalDate`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.financeapp.domain.recurrence

import com.financeapp.domain.model.TransactionFrequency
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceTest {
    @Test
    fun `monthly advances one month keeping day`() {
        assertEquals(
            LocalDate(2026, 2, 15),
            nextRecurrenceDate(LocalDate(2026, 1, 15), TransactionFrequency.MONTHLY)
        )
    }

    @Test
    fun `monthly anchored on 31 clamps to short month but keeps anchor`() {
        // From Jan 31 with anchor 31 -> Feb 28 (clamped), not permanently drifting.
        val feb = nextRecurrenceDate(LocalDate(2026, 1, 31), TransactionFrequency.MONTHLY, anchorDay = 31)
        assertEquals(LocalDate(2026, 2, 28), feb)
        // From Feb 28 with anchor 31 -> Mar 31 (anchor re-applied).
        assertEquals(LocalDate(2026, 3, 31), nextRecurrenceDate(feb, TransactionFrequency.MONTHLY, anchorDay = 31))
    }

    @Test
    fun `weekly and yearly advance correctly`() {
        assertEquals(LocalDate(2026, 1, 22), nextRecurrenceDate(LocalDate(2026, 1, 15), TransactionFrequency.WEEKLY))
        assertEquals(LocalDate(2027, 1, 15), nextRecurrenceDate(LocalDate(2026, 1, 15), TransactionFrequency.YEARLY))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.domain.recurrence.RecurrenceTest"`
Expected: FAIL — unresolved reference `nextRecurrenceDate`.

- [ ] **Step 3: Create the helper**

Create `Recurrence.kt` with the logic moved verbatim from `ScheduledEntryPlanner` (now public, in `domain.recurrence`):

```kotlin
package com.financeapp.domain.recurrence

import com.financeapp.domain.model.TransactionFrequency
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Advances [current] by one period. For MONTHLY/YEARLY the result is anchored on [anchorDay]
 * (the intended day-of-month), clamped to the target month's length so a "31st" recurrence lands
 * on the 28th/29th/30th in short months without losing its anchor for later months.
 */
fun nextRecurrenceDate(
    current: LocalDate,
    frequency: TransactionFrequency,
    anchorDay: Int = current.dayOfMonth
): LocalDate =
    when (frequency) {
        TransactionFrequency.DAILY -> current.plus(1, DateTimeUnit.DAY)
        TransactionFrequency.WEEKLY -> current.plus(7, DateTimeUnit.DAY)
        TransactionFrequency.BIWEEKLY -> current.plus(14, DateTimeUnit.DAY)
        TransactionFrequency.MONTHLY -> current.plus(1, DateTimeUnit.MONTH).withClampedDay(anchorDay)
        TransactionFrequency.YEARLY -> current.plus(1, DateTimeUnit.YEAR).withClampedDay(anchorDay)
    }

/** Returns this date with its day replaced by [day], clamped to the number of days in its month. */
private fun LocalDate.withClampedDay(day: Int): LocalDate {
    val daysInMonth = LocalDate(year, month, 1)
        .plus(1, DateTimeUnit.MONTH)
        .minus(1, DateTimeUnit.DAY)
        .dayOfMonth
    return LocalDate(year, monthNumber, day.coerceIn(1, daysInMonth))
}
```

- [ ] **Step 4: Delegate the existing planner function to the new helper**

In `ScheduledEntryPlanner.kt`, replace the body of `nextScheduledDate` (and remove its now-duplicate private `withClampedDay`) so it delegates. Keep the existing `internal` signature so current callers/tests are untouched:

```kotlin
import com.financeapp.domain.recurrence.nextRecurrenceDate
// ...
internal fun nextScheduledDate(
    current: LocalDate,
    frequency: TransactionFrequency,
    anchorDay: Int = current.dayOfMonth
): LocalDate = nextRecurrenceDate(current, frequency, anchorDay)
```

Remove the old `private fun LocalDate.withClampedDay(...)` from `ScheduledEntryPlanner.kt` and any now-unused imports (`DateTimeUnit`, `minus`) if the file no longer uses them.

- [ ] **Step 5: Run tests to verify pass (new + no regression in scheduled)**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.domain.recurrence.RecurrenceTest" --tests "com.financeapp.ui.scheduled.*"`
Expected: PASS for the new test and all existing scheduled planner/entry tests.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/domain/recurrence/Recurrence.kt \
        shared/src/commonMain/kotlin/com/financeapp/ui/scheduled/ScheduledEntryPlanner.kt \
        shared/src/commonTest/kotlin/com/financeapp/domain/recurrence/RecurrenceTest.kt
git commit -m "refactor: extract shared month-anchored recurrence helper"
```

---

## Task 2: SubscriptionDetector (pure detection algorithm)

The interval-clustering engine. No DB, no Compose. This is the bulk of the test coverage.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/domain/subscriptions/SubscriptionDetector.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/domain/subscriptions/SubscriptionDetectorTest.kt`

**Interfaces:**
- Consumes: `nextRecurrenceDate(...)` (Task 1), `TransactionFrequency` (existing).
- Produces:
  - `interface SubscriptionSource { val payeeId: Long?; val importedName: String?; val amountCents: Long; val date: LocalDate; val transferId: Long? }`
  - `data class SubscriptionCandidate(matchKey: String, payeeId: Long?, displayName: String, cadence: TransactionFrequency, medianAmountCents: Long, minAmountCents: Long, maxAmountCents: Long, isVariable: Boolean, occurrenceCount: Int, firstSeen: LocalDate, lastSeen: LocalDate, nextExpectedDate: LocalDate, confidence: Int)`
  - `class SubscriptionDetector { fun detect(sources: List<SubscriptionSource>): List<SubscriptionCandidate> }`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.financeapp.domain.subscriptions

import com.financeapp.domain.model.TransactionFrequency
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

private data class Src(
    override val payeeId: Long?,
    override val importedName: String?,
    override val amountCents: Long,
    override val date: LocalDate,
    override val transferId: Long? = null
) : SubscriptionSource

private fun monthly(payeeId: Long, amountCents: Long, months: List<Int>) =
    months.map { m -> Src(payeeId, "Netflix", amountCents, LocalDate(2026, m, 15)) }

class SubscriptionDetectorTest {
    private val detector = SubscriptionDetector()

    @Test
    fun `detects a fixed monthly subscription`() {
        val result = detector.detect(monthly(1, -1599, listOf(1, 2, 3, 4)))
        assertEquals(1, result.size)
        val s = result.single()
        assertEquals(TransactionFrequency.MONTHLY, s.cadence)
        assertEquals(1599, s.medianAmountCents)      // stored as positive cents
        assertTrue(!s.isVariable)
        assertEquals(4, s.occurrenceCount)
        assertEquals(LocalDate(2026, 5, 15), s.nextExpectedDate)
        assertEquals("payee:1", s.matchKey)
    }

    @Test
    fun `flags variable-amount recurring as variable`() {
        val src = listOf(
            Src(2, "Electric Co", -8000, LocalDate(2026, 1, 10)),
            Src(2, "Electric Co", -12000, LocalDate(2026, 2, 10)),
            Src(2, "Electric Co", -6000, LocalDate(2026, 3, 10)),
        )
        val s = detector.detect(src).single()
        assertTrue(s.isVariable, "wide amount spread should be variable")
        assertEquals(TransactionFrequency.MONTHLY, s.cadence)
    }

    @Test
    fun `ignores groups with fewer than three occurrences`() {
        val src = monthly(3, -1000, listOf(1, 2))
        assertTrue(detector.detect(src).isEmpty())
    }

    @Test
    fun `rejects erratic gaps as not a subscription`() {
        val src = listOf(
            Src(4, "Random", -500, LocalDate(2026, 1, 1)),
            Src(4, "Random", -500, LocalDate(2026, 1, 6)),   // 5 days
            Src(4, "Random", -500, LocalDate(2026, 3, 20)),  // 73 days
            Src(4, "Random", -500, LocalDate(2026, 3, 25)),  // 5 days
        )
        assertTrue(detector.detect(src).isEmpty())
    }

    @Test
    fun `excludes transfers and inflows`() {
        val src = listOf(
            Src(5, "Paycheck", 300000, LocalDate(2026, 1, 1)),
            Src(5, "Paycheck", 300000, LocalDate(2026, 2, 1)),
            Src(5, "Paycheck", 300000, LocalDate(2026, 3, 1)),
        ) + monthly(6, -1000, listOf(1, 2, 3)).map { it.copy(transferId = 99) }
        assertTrue(detector.detect(src).isEmpty())
    }

    @Test
    fun `collapses duplicate same-day charges before gap analysis`() {
        val src = monthly(7, -1000, listOf(1, 1, 2, 3)) // Jan appears twice same day
        val s = detector.detect(src).single()
        assertEquals(TransactionFrequency.MONTHLY, s.cadence)
        assertEquals(3, s.occurrenceCount) // three distinct dates
    }

    @Test
    fun `groups un-mapped payees by normalized imported name`() {
        val src = listOf(
            Src(null, "SPOTIFY  USA", -1099, LocalDate(2026, 1, 5)),
            Src(null, "spotify usa", -1099, LocalDate(2026, 2, 5)),
            Src(null, "Spotify USA", -1099, LocalDate(2026, 3, 5)),
        )
        val s = detector.detect(src).single()
        assertEquals("name:spotify usa", s.matchKey)
        assertNull(s.payeeId)
        assertEquals(TransactionFrequency.MONTHLY, s.cadence)
    }

    @Test
    fun `weekly cadence detected`() {
        val src = (0..4).map { Src(8, "Gym", -500, LocalDate(2026, 1, 1).plusDays(it * 7)) }
        assertEquals(TransactionFrequency.WEEKLY, detector.detect(src).single().cadence)
    }
}

private fun LocalDate.plusDays(n: Int) =
    LocalDate.fromEpochDays(this.toEpochDays() + n)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.domain.subscriptions.SubscriptionDetectorTest"`
Expected: FAIL — unresolved references `SubscriptionSource`, `SubscriptionDetector`.

- [ ] **Step 3: Implement the detector**

```kotlin
package com.financeapp.domain.subscriptions

import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.recurrence.nextRecurrenceDate
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/** Minimal per-transaction shape the detector needs. Amount is in cents, negative for outflows. */
interface SubscriptionSource {
    val payeeId: Long?
    val importedName: String?
    val amountCents: Long
    val date: LocalDate
    val transferId: Long?
}

data class SubscriptionCandidate(
    val matchKey: String,
    val payeeId: Long?,
    val displayName: String,
    val cadence: TransactionFrequency,
    val medianAmountCents: Long,
    val minAmountCents: Long,
    val maxAmountCents: Long,
    val isVariable: Boolean,
    val occurrenceCount: Int,
    val firstSeen: LocalDate,
    val lastSeen: LocalDate,
    val nextExpectedDate: LocalDate,
    val confidence: Int
)

/**
 * Detects subscription-like recurring charges by clustering each payee's outflow dates into a
 * regular cadence. Pure and total: never throws; non-qualifying groups are simply omitted.
 */
class SubscriptionDetector {

    private companion object {
        const val MIN_OCCURRENCES = 3
        const val CADENCE_TOLERANCE = 0.25          // ±25% of a cadence's nominal day count
        const val MIN_GAP_FIT_FRACTION = 0.6        // majority of gaps must fit the cadence
        const val VARIABLE_THRESHOLD = 0.15         // (max-min)/median above this => variable
        val CANDIDATE_CADENCES = listOf(
            TransactionFrequency.WEEKLY,
            TransactionFrequency.BIWEEKLY,
            TransactionFrequency.MONTHLY,
            TransactionFrequency.YEARLY
        )
    }

    fun detect(sources: List<SubscriptionSource>): List<SubscriptionCandidate> {
        return sources
            .asSequence()
            .filter { it.amountCents < 0 && it.transferId == null }
            .mapNotNull { src -> matchKeyFor(src)?.let { key -> key to src } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (key, group) -> candidateFor(key, group) }
            .sortedByDescending { it.confidence }
    }

    private fun matchKeyFor(src: SubscriptionSource): String? {
        if (src.payeeId != null) return "payee:${src.payeeId}"
        val normalized = src.importedName?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")
        return if (normalized.isNullOrBlank()) null else "name:$normalized"
    }

    private fun candidateFor(matchKey: String, group: List<SubscriptionSource>): SubscriptionCandidate? {
        // Collapse duplicate same-day charges: one entry per date, amount summed for that day.
        val byDate = group.groupBy { it.date }
            .map { (date, sameDay) -> date to sameDay.sumOf { abs(it.amountCents) } }
            .sortedBy { it.first.toEpochDays() }
        if (byDate.size < MIN_OCCURRENCES) return null

        val dates = byDate.map { it.first }
        val amounts = byDate.map { it.second }

        val gaps = dates.zipWithNext { a, b -> (b.toEpochDays() - a.toEpochDays()).toInt() }
        val cadence = classifyCadence(gaps) ?: return null

        val median = amounts.sorted()[amounts.size / 2]
        val min = amounts.min()
        val max = amounts.max()
        val isVariable = median > 0 && (max - min).toDouble() / median > VARIABLE_THRESHOLD

        val firstSeen = dates.first()
        val lastSeen = dates.last()
        val nextExpected = nextRecurrenceDate(lastSeen, cadence, anchorDay = lastSeen.dayOfMonth)
        val confidence = confidenceScore(gaps, cadence, byDate.size)
        val display = group.firstOrNull { it.payeeId != null }?.importedName
            ?: matchKey.removePrefix("name:")

        return SubscriptionCandidate(
            matchKey = matchKey,
            payeeId = group.firstNotNullOfOrNull { it.payeeId },
            displayName = display.ifBlank { matchKey },
            cadence = cadence,
            medianAmountCents = median,
            minAmountCents = min,
            maxAmountCents = max,
            isVariable = isVariable,
            occurrenceCount = byDate.size,
            firstSeen = firstSeen,
            lastSeen = lastSeen,
            nextExpectedDate = nextExpected,
            confidence = confidence
        )
    }

    private fun classifyCadence(gaps: List<Int>): TransactionFrequency? {
        if (gaps.isEmpty()) return null
        val median = gaps.sorted()[gaps.size / 2]
        val best = CANDIDATE_CADENCES.minByOrNull { abs(it.days - median) } ?: return null
        val medianWithin = abs(median - best.days).toDouble() / best.days <= CADENCE_TOLERANCE
        if (!medianWithin) return null
        val fitFraction = gaps.count {
            abs(it - best.days).toDouble() / best.days <= CADENCE_TOLERANCE
        }.toDouble() / gaps.size
        return if (fitFraction >= MIN_GAP_FIT_FRACTION) best else null
    }

    private fun confidenceScore(gaps: List<Int>, cadence: TransactionFrequency, occurrences: Int): Int {
        val occurrenceScore = minOf(1.0, occurrences / 6.0)
        val meanRelDeviation = gaps.map { abs(it - cadence.days).toDouble() / cadence.days }.average()
        val tightnessScore = (1.0 - minOf(1.0, meanRelDeviation / CADENCE_TOLERANCE)).coerceAtLeast(0.0)
        return ((0.5 * occurrenceScore + 0.5 * tightnessScore) * 100).toInt()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.domain.subscriptions.SubscriptionDetectorTest"`
Expected: PASS (all 8 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/domain/subscriptions/SubscriptionDetector.kt \
        shared/src/commonTest/kotlin/com/financeapp/domain/subscriptions/SubscriptionDetectorTest.kt
git commit -m "feat: add subscription detection engine"
```

---

## Task 3: Schema + domain model

Add the persistent table and the `DetectedSubscription` domain model.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt`
- Modify: `shared/src/desktopMain/kotlin/com/financeapp/db/DatabaseDriverFactory.desktop.kt:44` (the `SchemaUtils.create(...)` list)
- Modify: `shared/src/commonTest/kotlin/com/financeapp/test/TestDatabaseFactory.kt` (every `SchemaUtils.create(...)` list — there are three)
- Create: `shared/src/commonMain/kotlin/com/financeapp/domain/model/DetectedSubscription.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/data/schema/DetectedSubscriptionsSchemaTest.kt`

**Interfaces:**
- Produces:
  - Table object `DetectedSubscriptions : IntIdTable("DetectedSubscription")` with columns listed below.
  - `enum class SubscriptionStatus { CANDIDATE, CONFIRMED, DISMISSED }`
  - `data class DetectedSubscription(id, payeeId, displayName, matchKey, cadence, status, medianAmountCents, minAmountCents, maxAmountCents, isVariable, occurrenceCount, firstSeen, lastSeen, nextExpectedDate, confidence, isActive)`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.financeapp.data.schema

import com.financeapp.db.schema.DetectedSubscriptions
import com.financeapp.test.createTestDatabase
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class DetectedSubscriptionsSchemaTest {
    @Test
    fun `can insert and read a detected subscription row`() {
        val db = createTestDatabase()
        transaction(db) {
            DetectedSubscriptions.insertAndGetId {
                it[matchKey] = "payee:1"
                it[cadence] = "MONTHLY"
                it[status] = "CANDIDATE"
                it[medianAmount] = 1599
                it[minAmount] = 1599
                it[maxAmount] = 1599
                it[isVariable] = false
                it[occurrenceCount] = 4
                it[firstSeen] = 1000L
                it[lastSeen] = 2000L
                it[nextExpectedDate] = 3000L
                it[confidence] = 80
                it[isActive] = true
                it[createdAt] = 1L
                it[updatedAt] = 1L
            }
            assertEquals(1, DetectedSubscriptions.selectAll().count().toInt())
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.schema.DetectedSubscriptionsSchemaTest"`
Expected: FAIL — unresolved reference `DetectedSubscriptions`.

- [ ] **Step 3: Add the table**

Append to `Tables.kt`:

```kotlin
// Detected subscriptions (recurring charges surfaced for awareness). See
// docs/superpowers/specs/2026-07-01-subscription-detection-design.md.
object DetectedSubscriptions : IntIdTable("DetectedSubscription") {
    val payeeId = reference("payee_id", Payees).nullable()
    val matchKey = varchar("match_key", 512).uniqueIndex()
    val cadence = varchar("cadence", 50)              // WEEKLY, BIWEEKLY, MONTHLY, YEARLY
    val status = varchar("status", 20).default("CANDIDATE") // CANDIDATE, CONFIRMED, DISMISSED
    val medianAmount = long("median_amount")          // absolute cents
    val minAmount = long("min_amount")
    val maxAmount = long("max_amount")
    val isVariable = bool("is_variable").default(false)
    val occurrenceCount = integer("occurrence_count")
    val firstSeen = long("first_seen")                // epoch millis
    val lastSeen = long("last_seen")
    val nextExpectedDate = long("next_expected_date")
    val confidence = integer("confidence")            // 0-100
    val isActive = bool("is_active").default(true)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}
```

- [ ] **Step 4: Register the table in schema creation (prod + test)**

In `DatabaseDriverFactory.desktop.kt`, add `DetectedSubscriptions,` to the `SchemaUtils.create(...)` argument list (after `DividendEvents`). No `ALTER TABLE` block is needed — this is a new table.

In `TestDatabaseFactory.kt`, add `DetectedSubscriptions,` to **each** `SchemaUtils.create(...)` list (there are three occurrences).

- [ ] **Step 5: Add the domain model**

Create `DetectedSubscription.kt`:

```kotlin
package com.financeapp.domain.model

import kotlinx.datetime.LocalDate

enum class SubscriptionStatus { CANDIDATE, CONFIRMED, DISMISSED }

data class DetectedSubscription(
    val id: Long = 0,
    val payeeId: Long?,
    val displayName: String,
    val matchKey: String,
    val cadence: TransactionFrequency,
    val status: SubscriptionStatus,
    val medianAmountCents: Long,
    val minAmountCents: Long,
    val maxAmountCents: Long,
    val isVariable: Boolean,
    val occurrenceCount: Int,
    val firstSeen: LocalDate,
    val lastSeen: LocalDate,
    val nextExpectedDate: LocalDate,
    val confidence: Int,
    val isActive: Boolean
)
```

- [ ] **Step 6: Run test to verify pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.schema.DetectedSubscriptionsSchemaTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt \
        shared/src/desktopMain/kotlin/com/financeapp/db/DatabaseDriverFactory.desktop.kt \
        shared/src/commonTest/kotlin/com/financeapp/test/TestDatabaseFactory.kt \
        shared/src/commonMain/kotlin/com/financeapp/domain/model/DetectedSubscription.kt \
        shared/src/commonTest/kotlin/com/financeapp/data/schema/DetectedSubscriptionsSchemaTest.kt
git commit -m "feat: add DetectedSubscriptions table and domain model"
```

---

## Task 4: SubscriptionRepository (persistence + reconciliation)

Loads transactions, runs the detector, reconciles results into the table with sticky status, and exposes a reactive list plus confirm/dismiss.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/domain/repository/SubscriptionRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/financeapp/data/repository/SubscriptionRepositoryImpl.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/data/repository/SubscriptionRepositoryTest.kt`

**Interfaces:**
- Consumes: `SubscriptionDetector` (Task 2), `DetectedSubscriptions` table + `DetectedSubscription`/`SubscriptionStatus` (Task 3), `TransactionRepository` for seeding transactions in tests.
- Produces:
  ```kotlin
  interface SubscriptionRepository {
      fun getSubscriptions(): Flow<List<DetectedSubscription>>
      suspend fun rescan()
      suspend fun confirm(id: Long)
      suspend fun dismiss(id: Long)
      fun notifySubscriptionsChanged()
  }
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.financeapp.data.repository

import com.financeapp.domain.model.*
import com.financeapp.domain.repository.SubscriptionRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.subscriptions.SubscriptionDetector
import com.financeapp.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionRepositoryTest {
    private lateinit var database: Database
    private lateinit var subscriptions: SubscriptionRepository
    private lateinit var transactions: TransactionRepository
    private lateinit var accounts: AccountRepository
    private var accountId: Long = 0
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setup() {
        database = createTestDatabase()
        transactions = TransactionRepositoryImpl(database, dispatcher)
        accounts = AccountRepositoryImpl(database, dispatcher)
        subscriptions = SubscriptionRepositoryImpl(database, transactions, SubscriptionDetector(), dispatcher)
        accountId = runBlocking { accounts.insertAccount(TestDataFactory.createTestAccount()) }
    }

    @AfterTest
    fun teardown() = database.clearAllTables()

    private fun insertMonthly(payeeId: Long?, name: String, amountCents: Long, months: List<Int>) = runBlocking {
        months.forEach { m ->
            transactions.insertTransaction(
                TestDataFactory.createTestTransaction(
                    accountId = accountId,
                    payeeId = payeeId,
                    amount = amountCents,
                    date = LocalDate(2026, m, 12),
                    importedName = name
                )
            )
        }
    }

    @Test
    fun `rescan creates candidate rows for recurring charges`() = runTest {
        insertMonthly(null, "Netflix", -1599, listOf(1, 2, 3, 4))
        subscriptions.rescan()
        val list = subscriptions.getSubscriptions().first()
        assertEquals(1, list.size)
        assertEquals(SubscriptionStatus.CANDIDATE, list.single().status)
        assertEquals(TransactionFrequency.MONTHLY, list.single().cadence)
    }

    @Test
    fun `confirmed status survives a rescan and stats update`() = runTest {
        insertMonthly(null, "Netflix", -1599, listOf(1, 2, 3))
        subscriptions.rescan()
        val id = subscriptions.getSubscriptions().first().single().id
        subscriptions.confirm(id)

        insertMonthly(null, "Netflix", -1599, listOf(4)) // a new occurrence
        subscriptions.rescan()

        val after = subscriptions.getSubscriptions().first().single()
        assertEquals(SubscriptionStatus.CONFIRMED, after.status, "confirm must be sticky")
        assertEquals(4, after.occurrenceCount, "stats must update on rescan")
    }

    @Test
    fun `dismissed stays dismissed after rescan`() = runTest {
        insertMonthly(null, "Netflix", -1599, listOf(1, 2, 3))
        subscriptions.rescan()
        val id = subscriptions.getSubscriptions().first().single().id
        subscriptions.dismiss(id)
        subscriptions.rescan()
        assertEquals(SubscriptionStatus.DISMISSED, subscriptions.getSubscriptions().first().single().status)
    }

    @Test
    fun `group that no longer qualifies is marked inactive not deleted`() = runTest {
        insertMonthly(null, "Netflix", -1599, listOf(1, 2, 3))
        subscriptions.rescan()
        assertTrue(subscriptions.getSubscriptions().first().single().isActive)

        // Wipe transactions so the group no longer qualifies, then rescan.
        database.clearTable("Transaction")
        subscriptions.rescan()

        val row = subscriptions.getSubscriptions().first().single()
        assertFalse(row.isActive, "cancelled subscription should remain, marked inactive")
    }
}
```

> Note: this test uses `TestDataFactory.createTestTransaction(... date: LocalDate, importedName: String?)` and `database.clearTable(name)`. If those overloads don't already exist, add them in this task's Step 3 (they are small helpers in the existing test package). `createTestTransaction` must set `amount` and `date` (converted to the repo's stored format) and `importedName`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.SubscriptionRepositoryTest"`
Expected: FAIL — unresolved `SubscriptionRepositoryImpl` (and possibly missing test-factory overloads).

- [ ] **Step 3: Add any missing test helpers**

If absent, add to `TestDataFactory.kt` a `createTestTransaction` overload accepting `payeeId: Long?`, `amount: Long`, `date: LocalDate`, `importedName: String?` (mirror the existing factory, converting `date` the same way other transactions are stored). If absent, add to `TestUtils.kt` (or wherever `clearAllTables` lives):

```kotlin
fun Database.clearTable(tableName: String) = transaction(this) {
    exec("SET REFERENTIAL_INTEGRITY FALSE")
    exec("DELETE FROM \"$tableName\"")
    exec("SET REFERENTIAL_INTEGRITY TRUE")
}
```

- [ ] **Step 4: Write the interface**

```kotlin
package com.financeapp.domain.repository

import com.financeapp.domain.model.DetectedSubscription
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    /** All detected subscriptions, active first then by confidence desc. */
    fun getSubscriptions(): Flow<List<DetectedSubscription>>
    /** Loads all transactions, runs the detector, and reconciles results (sticky status). */
    suspend fun rescan()
    suspend fun confirm(id: Long)
    suspend fun dismiss(id: Long)
    fun notifySubscriptionsChanged()
}
```

- [ ] **Step 5: Implement the repository**

```kotlin
package com.financeapp.data.repository

import com.financeapp.db.schema.DetectedSubscriptions
import com.financeapp.db.schema.Payees
import com.financeapp.domain.model.DetectedSubscription
import com.financeapp.domain.model.SubscriptionStatus
import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.repository.SubscriptionRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.subscriptions.SubscriptionCandidate
import com.financeapp.domain.subscriptions.SubscriptionDetector
import com.financeapp.domain.subscriptions.SubscriptionSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class SubscriptionRepositoryImpl(
    private val database: Database,
    private val transactionRepository: TransactionRepository,
    private val detector: SubscriptionDetector,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SubscriptionRepository {

    private val refreshTrigger = MutableStateFlow(0L)

    override fun notifySubscriptionsChanged() { refreshTrigger.value += 1 }

    private data class Src(
        override val payeeId: Long?,
        override val importedName: String?,
        override val amountCents: Long,
        override val date: LocalDate,
        override val transferId: Long?
    ) : SubscriptionSource

    override fun getSubscriptions(): Flow<List<DetectedSubscription>> =
        refreshTrigger.map {
            withContext(ioDispatcher) {
                transaction(database) {
                    val payeeNames = Payees.selectAll()
                        .associate { it[Payees.id].value.toLong() to it[Payees.name] }
                    DetectedSubscriptions.selectAll()
                        .map { it.toDetectedSubscription(payeeNames) }
                        .sortedWith(compareByDescending<DetectedSubscription> { it.isActive }
                            .thenByDescending { it.confidence })
                }
            }
        }

    override suspend fun rescan() = withContext(ioDispatcher) {
        // Load transactions off any open transaction, then reconcile in one DB transaction.
        val sources = transactionRepository.getAllTransactionsWithDetails().first().map {
            Src(
                payeeId = it.transaction.payeeId,
                importedName = it.payeeName ?: it.transaction.importedName,
                amountCents = it.transaction.amount,
                date = it.transaction.date,
                transferId = it.transaction.transferId
            )
        }
        val candidates = detector.detect(sources)
        val now = System.currentTimeMillis()

        transaction(database) {
            val existing = DetectedSubscriptions.selectAll()
                .associateBy { it[DetectedSubscriptions.matchKey] }
            val seenKeys = candidates.map { it.matchKey }.toSet()

            candidates.forEach { c ->
                val row = existing[c.matchKey]
                if (row == null) {
                    DetectedSubscriptions.insert { it.applyStats(c, now, isNew = true) }
                } else {
                    DetectedSubscriptions.update({ DetectedSubscriptions.matchKey eq c.matchKey }) {
                        it.applyStats(c, now, isNew = false)
                    }
                }
            }
            // Anything previously detected but no longer qualifying -> inactive (kept, not deleted).
            existing.keys.filter { it !in seenKeys }.forEach { key ->
                DetectedSubscriptions.update({ DetectedSubscriptions.matchKey eq key }) {
                    it[isActive] = false
                    it[updatedAt] = now
                }
            }
        }
        notifySubscriptionsChanged()
    }

    override suspend fun confirm(id: Long) = setStatus(id, SubscriptionStatus.CONFIRMED)
    override suspend fun dismiss(id: Long) = setStatus(id, SubscriptionStatus.DISMISSED)

    private suspend fun setStatus(id: Long, status: SubscriptionStatus) = withContext(ioDispatcher) {
        transaction(database) {
            DetectedSubscriptions.update({ DetectedSubscriptions.id eq id.toInt() }) {
                it[DetectedSubscriptions.status] = status.name
                it[updatedAt] = System.currentTimeMillis()
            }
        }
        notifySubscriptionsChanged()
    }

    private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.applyStats(
        c: SubscriptionCandidate, now: Long, isNew: Boolean
    ) {
        this[DetectedSubscriptions.payeeId] = c.payeeId?.toInt()
        this[DetectedSubscriptions.cadence] = c.cadence.name
        this[DetectedSubscriptions.medianAmount] = c.medianAmountCents
        this[DetectedSubscriptions.minAmount] = c.minAmountCents
        this[DetectedSubscriptions.maxAmount] = c.maxAmountCents
        this[DetectedSubscriptions.isVariable] = c.isVariable
        this[DetectedSubscriptions.occurrenceCount] = c.occurrenceCount
        this[DetectedSubscriptions.firstSeen] = c.firstSeen.toMillis()
        this[DetectedSubscriptions.lastSeen] = c.lastSeen.toMillis()
        this[DetectedSubscriptions.nextExpectedDate] = c.nextExpectedDate.toMillis()
        this[DetectedSubscriptions.confidence] = c.confidence
        this[DetectedSubscriptions.isActive] = true
        this[DetectedSubscriptions.updatedAt] = now
        if (isNew) {
            this[DetectedSubscriptions.matchKey] = c.matchKey
            this[DetectedSubscriptions.status] = SubscriptionStatus.CANDIDATE.name
            this[DetectedSubscriptions.createdAt] = now
        }
    }

    private fun ResultRow.toDetectedSubscription(payeeNames: Map<Long, String>): DetectedSubscription {
        val payeeId = this[DetectedSubscriptions.payeeId]?.value?.toLong()
        val matchKey = this[DetectedSubscriptions.matchKey]
        return DetectedSubscription(
            id = this[DetectedSubscriptions.id].value.toLong(),
            payeeId = payeeId,
            displayName = payeeId?.let { payeeNames[it] } ?: matchKey.removePrefix("name:"),
            matchKey = matchKey,
            cadence = TransactionFrequency.valueOf(this[DetectedSubscriptions.cadence]),
            status = SubscriptionStatus.valueOf(this[DetectedSubscriptions.status]),
            medianAmountCents = this[DetectedSubscriptions.medianAmount],
            minAmountCents = this[DetectedSubscriptions.minAmount],
            maxAmountCents = this[DetectedSubscriptions.maxAmount],
            isVariable = this[DetectedSubscriptions.isVariable],
            occurrenceCount = this[DetectedSubscriptions.occurrenceCount],
            firstSeen = this[DetectedSubscriptions.firstSeen].toLocalDate(),
            lastSeen = this[DetectedSubscriptions.lastSeen].toLocalDate(),
            nextExpectedDate = this[DetectedSubscriptions.nextExpectedDate].toLocalDate(),
            confidence = this[DetectedSubscriptions.confidence],
            isActive = this[DetectedSubscriptions.isActive]
        )
    }

    private fun LocalDate.toMillis() = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    private fun Long.toLocalDate(): LocalDate =
        Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
}
```

> If `getAllTransactionsWithDetails()` returns `TransactionWithDetails` whose `transaction.date` is a `LocalDate`, the mapping above is correct. Verify the property names against `TransactionRepository`/`TransactionWithDetails` and adjust if the loaded shape differs.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.SubscriptionRepositoryTest"`
Expected: PASS (all 4 tests).

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/domain/repository/SubscriptionRepository.kt \
        shared/src/commonMain/kotlin/com/financeapp/data/repository/SubscriptionRepositoryImpl.kt \
        shared/src/commonTest/kotlin/com/financeapp/data/repository/SubscriptionRepositoryTest.kt \
        shared/src/commonTest/kotlin/com/financeapp/test/
git commit -m "feat: add subscription repository with sticky-status reconciliation"
```

---

## Task 5: Initial-scan preferences flag

Persist a "done once" flag for the one-time initial scan.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/domain/repository/PreferencesRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/data/repository/PreferencesRepositoryImpl.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/data/repository/PreferencesRepositorySubscriptionFlagTest.kt`

**Interfaces:**
- Produces on `PreferencesRepository`:
  - `suspend fun isSubscriptionInitialScanDone(): Boolean`
  - `suspend fun markSubscriptionInitialScanDone()`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.financeapp.data.repository

import com.financeapp.domain.repository.PreferencesRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferencesRepositorySubscriptionFlagTest {
    private class FakeStore : PreferencesStore {
        private val map = mutableMapOf<String, String>()
        override suspend fun getString(key: String): String? = map[key]
        override suspend fun putString(key: String, value: String) { map[key] = value }
        override suspend fun remove(key: String) { map.remove(key) }
    }

    private val repo: PreferencesRepository = PreferencesRepositoryImpl(FakeStore())

    @Test
    fun `flag defaults false then true after marking`() = runBlocking {
        assertFalse(repo.isSubscriptionInitialScanDone())
        repo.markSubscriptionInitialScanDone()
        assertTrue(repo.isSubscriptionInitialScanDone())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.PreferencesRepositorySubscriptionFlagTest"`
Expected: FAIL — unresolved `isSubscriptionInitialScanDone`.

- [ ] **Step 3: Extend the interface**

Add to `PreferencesRepository`:

```kotlin
    suspend fun isSubscriptionInitialScanDone(): Boolean
    suspend fun markSubscriptionInitialScanDone()
```

- [ ] **Step 4: Implement in `PreferencesRepositoryImpl`**

Add methods and a key constant:

```kotlin
    override suspend fun isSubscriptionInitialScanDone(): Boolean = withContext(Dispatchers.IO) {
        preferencesStore.getString(KEY_SUBSCRIPTION_INITIAL_SCAN_DONE) == "true"
    }

    override suspend fun markSubscriptionInitialScanDone() = withContext(Dispatchers.IO) {
        preferencesStore.putString(KEY_SUBSCRIPTION_INITIAL_SCAN_DONE, "true")
    }
```

And in the `companion object`:

```kotlin
        private const val KEY_SUBSCRIPTION_INITIAL_SCAN_DONE = "subscriptions_initial_scan_done"
```

- [ ] **Step 5: Run test to verify pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.PreferencesRepositorySubscriptionFlagTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/domain/repository/PreferencesRepository.kt \
        shared/src/commonMain/kotlin/com/financeapp/data/repository/PreferencesRepositoryImpl.kt \
        shared/src/commonTest/kotlin/com/financeapp/data/repository/PreferencesRepositorySubscriptionFlagTest.kt
git commit -m "feat: add subscription initial-scan preference flag"
```

---

## Task 6: SubscriptionScanService (orchestration)

Single entry point for both triggers, with crash-safe flag ordering.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/domain/service/SubscriptionScanService.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/domain/service/SubscriptionScanServiceTest.kt`

**Interfaces:**
- Consumes: `SubscriptionRepository` (Task 4), `PreferencesRepository` flag (Task 5).
- Produces:
  - `suspend fun scanAfterImport()`
  - `suspend fun runInitialScanIfNeeded()`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.financeapp.domain.service

import com.financeapp.domain.model.DetectedSubscription
import com.financeapp.domain.repository.SubscriptionRepository
import com.financeapp.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SubscriptionScanServiceTest {
    private class FakeSubs : SubscriptionRepository {
        var rescans = 0
        override fun getSubscriptions(): Flow<List<DetectedSubscription>> = flowOf(emptyList())
        override suspend fun rescan() { rescans++ }
        override suspend fun confirm(id: Long) {}
        override suspend fun dismiss(id: Long) {}
        override fun notifySubscriptionsChanged() {}
    }
    private class FakePrefs : PreferencesRepository {
        var done = false
        var markedAfterRescan: Boolean? = null
        val subs: FakeSubs
        constructor(subs: FakeSubs) { this.subs = subs }
        override suspend fun getThemeMode(): String? = null
        override suspend fun setThemeMode(mode: String) {}
        override suspend fun getDashboardConfig(): String? = null
        override suspend fun setDashboardConfig(config: String) {}
        override suspend fun isSubscriptionInitialScanDone(): Boolean = done
        override suspend fun markSubscriptionInitialScanDone() {
            // capture that the scan ran before the flag was set
            markedAfterRescan = subs.rescans > 0
            done = true
        }
    }

    @Test
    fun `initial scan runs once then is skipped`() = runBlocking {
        val subs = FakeSubs(); val prefs = FakePrefs(subs)
        val svc = SubscriptionScanService(subs, prefs)
        svc.runInitialScanIfNeeded()
        svc.runInitialScanIfNeeded()
        assertEquals(1, subs.rescans)
    }

    @Test
    fun `flag is set only after the scan runs`() = runBlocking {
        val subs = FakeSubs(); val prefs = FakePrefs(subs)
        SubscriptionScanService(subs, prefs).runInitialScanIfNeeded()
        assertEquals(true, prefs.markedAfterRescan)
    }

    @Test
    fun `scanAfterImport always rescans`() = runBlocking {
        val subs = FakeSubs(); val prefs = FakePrefs(subs)
        val svc = SubscriptionScanService(subs, prefs)
        svc.scanAfterImport()
        svc.scanAfterImport()
        assertEquals(2, subs.rescans)
    }

    @Test
    fun `failed scan does not set the flag`() = runBlocking {
        val subs = object : SubscriptionRepository by FakeSubs() {
            override suspend fun rescan() { throw RuntimeException("boom") }
        }
        val prefs = FakePrefs(FakeSubs())
        val svc = SubscriptionScanService(subs, prefs)
        runCatching { svc.runInitialScanIfNeeded() }
        assertFalse(prefs.done, "flag must not be set when the scan fails")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.domain.service.SubscriptionScanServiceTest"`
Expected: FAIL — unresolved `SubscriptionScanService`.

- [ ] **Step 3: Implement the service**

```kotlin
package com.financeapp.domain.service

import com.financeapp.domain.repository.PreferencesRepository
import com.financeapp.domain.repository.SubscriptionRepository

/**
 * Orchestrates subscription detection. [scanAfterImport] re-scans whenever new transactions land;
 * [runInitialScanIfNeeded] scans existing history exactly once (tracked by a preferences flag set
 * only after a committed scan, so a crash mid-scan simply retries next launch).
 */
class SubscriptionScanService(
    private val subscriptionRepository: SubscriptionRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend fun scanAfterImport() {
        subscriptionRepository.rescan()
    }

    suspend fun runInitialScanIfNeeded() {
        if (preferencesRepository.isSubscriptionInitialScanDone()) return
        subscriptionRepository.rescan()               // persist + commit first
        preferencesRepository.markSubscriptionInitialScanDone()  // then set the flag
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.domain.service.SubscriptionScanServiceTest"`
Expected: PASS (all 4 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/domain/service/SubscriptionScanService.kt \
        shared/src/commonTest/kotlin/com/financeapp/domain/service/SubscriptionScanServiceTest.kt
git commit -m "feat: add subscription scan orchestration service"
```

---

## Task 7: Wire scan into the import pipeline + DI

Register the repository and service in Koin, and trigger `scanAfterImport()` when an import completes.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/ImportRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt`

**Interfaces:**
- Consumes: `SubscriptionScanService` (Task 6), `SubscriptionRepository` (Task 4), `SubscriptionDetector` (Task 2).

- [ ] **Step 1: Register repository, detector, and service in DI**

In `Modules.kt`, add imports and, in `sharedModule`, register (place the `single`s alongside the other repositories/services):

```kotlin
single { com.financeapp.domain.subscriptions.SubscriptionDetector() }
single<com.financeapp.domain.repository.SubscriptionRepository> {
    com.financeapp.data.repository.SubscriptionRepositoryImpl(get(), get(), get())
}
single { com.financeapp.domain.service.SubscriptionScanService(get(), get()) }
```

- [ ] **Step 2: Add the scan trigger to `ImportRepository`**

Add a constructor parameter (place it after `database`, before the parser defaults so existing default args are preserved):

```kotlin
class ImportRepository(
    private val transactionRepository: TransactionRepository,
    private val payeeRepository: PayeeRepository,
    private val accountRepository: AccountRepository,
    private val payeeMatchingRepository: PayeeMatchingRepository,
    private val tagRepository: TagRepository,
    private val database: Database,
    private val subscriptionScanService: com.financeapp.domain.service.SubscriptionScanService,
    private val payeeMatcher: PayeeMatcher = PayeeMatcher(),
    private val ofxParser: OfxParser = OfxParser(),
    private val csvParser: CsvParser = CsvParser(),
    private val qifParser: QifParser = QifParser()
) {
```

At each import-completion site (immediately after the `notify...Changed()` calls near the end of `importWithMappings` ~line 289 and `importTransactions` ~line 374), add:

```kotlin
subscriptionScanService.scanAfterImport()
```

- [ ] **Step 3: Update the `ImportRepository` DI registration**

In `Modules.kt`, change the registration to inject the service. The existing line is:

```kotlin
single { ImportRepository(get(), get(), get(), get(), get(), get()) }
```

Change to (add one `get()` for the new `subscriptionScanService` param, matching its constructor position — 7th arg):

```kotlin
single { ImportRepository(get(), get(), get(), get(), get(), get(), get()) }
```

- [ ] **Step 4: Build to verify wiring compiles and existing import tests still pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.fileimport.*"`
Expected: PASS (existing import tests). If an import test constructs `ImportRepository` directly, pass a `SubscriptionScanService` built from the repository under test (or a no-op fake) — update those call sites.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/data/fileimport/ImportRepository.kt \
        shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt \
        shared/src/commonTest
git commit -m "feat: trigger subscription scan after each import"
```

---

## Task 8: Trigger the one-time initial scan at startup

Run `runInitialScanIfNeeded()` after vault unlock, via `AppViewModel.startPostUnlock()` (already invoked by `UnlockedApp()` in `App.kt`).

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/AppViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt`

**Interfaces:**
- Consumes: `SubscriptionScanService` (Task 6).

- [ ] **Step 1: Add the constructor parameter and scan helper**

`AppViewModel` is a plain class with `private val scope = supervisedViewModelScope()` and a `startPostUnlock()` that delegates to `scope.launch` helper methods. Add `subscriptionScanService: com.financeapp.domain.service.SubscriptionScanService` as the last constructor parameter, add a call to a new helper inside `startPostUnlock()` (next to `seedDatabaseIfNeeded()` etc.), and define the helper so a scan failure never blocks launch:

```kotlin
    fun startPostUnlock() {
        if (started) return
        started = true
        seedDatabaseIfNeeded()
        loadThemeMode()
        startPriceRefreshService()
        startSnapshotScheduler()
        runInitialSubscriptionScan()   // added
    }

    private fun runInitialSubscriptionScan() {
        scope.launch {
            try {
                subscriptionScanService.runInitialScanIfNeeded()
            } catch (e: Exception) {
                println("Warning: initial subscription scan failed: ${e.message}")
            }
        }
    }
```

- [ ] **Step 3: Update DI**

In `Modules.kt`, the current registration is:

```kotlin
single { AppViewModel(get(), get(), get(), get()) }
```

Add one `get()` for the new parameter (position last):

```kotlin
single { AppViewModel(get(), get(), get(), get(), get()) }
```

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.*"`
Expected: PASS (no regressions; if an `AppViewModel` test exists, update its constructor call to pass a `SubscriptionScanService`).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/ui/AppViewModel.kt \
        shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt
git commit -m "feat: run one-time initial subscription scan after unlock"
```

---

## Task 9: FK cleanup on payee delete

Keep `DetectedSubscriptions` consistent when a payee is deleted (FK enforcement is ON).

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/data/repository/PayeeRepositoryImpl.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/data/repository/PayeeRepositoryTest.kt` (add one test)

- [ ] **Step 1: Write the failing test**

Add to `PayeeRepositoryTest`:

```kotlin
@Test
fun `deletePayee nulls detected subscription payee reference`() = runBlocking {
    val payeeId = payeeRepository.insertPayee(Payee(id = 0, name = "Netflix", defaultCategoryId = null))
    transaction(database) {
        com.financeapp.db.schema.DetectedSubscriptions.insert {
            it[com.financeapp.db.schema.DetectedSubscriptions.payeeId] = payeeId.toInt()
            it[matchKey] = "payee:$payeeId"
            it[cadence] = "MONTHLY"; it[status] = "CANDIDATE"
            it[medianAmount] = 1599; it[minAmount] = 1599; it[maxAmount] = 1599
            it[isVariable] = false; it[occurrenceCount] = 3
            it[firstSeen] = 1L; it[lastSeen] = 2L; it[nextExpectedDate] = 3L
            it[confidence] = 80; it[isActive] = true; it[createdAt] = 1L; it[updatedAt] = 1L
        }
    }

    payeeRepository.deletePayee(payeeId) // must not throw despite FK

    transaction(database) {
        val row = com.financeapp.db.schema.DetectedSubscriptions.selectAll().single()
        assertNull(row[com.financeapp.db.schema.DetectedSubscriptions.payeeId])
    }
}
```

(Add imports: `org.jetbrains.exposed.v1.jdbc.insert`, `org.jetbrains.exposed.v1.jdbc.selectAll`, `org.jetbrains.exposed.v1.jdbc.transactions.transaction`, `kotlin.test.assertNull` if not already present.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.PayeeRepositoryTest.deletePayee nulls detected subscription payee reference"`
Expected: FAIL — the delete throws a FK violation (or the reference is not nulled).

- [ ] **Step 3: Add the cleanup**

In `deletePayee`, alongside the other nullify blocks (before `Payees.deleteWhere`), add:

```kotlin
// Nullify payee references in detected subscriptions (name-keyed rows survive independently)
DetectedSubscriptions.update({ DetectedSubscriptions.payeeId eq id.toInt() }) {
    it[payeeId] = null
}
```

Ensure `com.financeapp.db.schema.DetectedSubscriptions` is imported (the file already uses `com.financeapp.db.schema.*` per the impl pattern; confirm and add if needed).

- [ ] **Step 4: Run test to verify pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.PayeeRepositoryTest"`
Expected: PASS (new test + existing payee tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/data/repository/PayeeRepositoryImpl.kt \
        shared/src/commonTest/kotlin/com/financeapp/data/repository/PayeeRepositoryTest.kt
git commit -m "fix: hand-clean detected subscriptions on payee delete"
```

---

## Task 10: Subscriptions screen + navigation

The dedicated screen with confirm/dismiss, plus nav registration.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/ui/subscriptions/SubscriptionViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/financeapp/ui/subscriptions/SubscriptionsScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/navigation/AppNavigationRail.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/App.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/ui/subscriptions/SubscriptionViewModelTest.kt`

**Interfaces:**
- Consumes: `SubscriptionRepository` (Task 4), `supervisedViewModelScope()` (existing, `com.financeapp.ui`).
- Produces: `SubscriptionViewModel` (a plain class, matching `ScheduledViewModel` — NOT `androidx.lifecycle.ViewModel`) exposing `val uiState: StateFlow<SubscriptionUiState>` where `data class SubscriptionUiState(val subscriptions: List<DetectedSubscription>, val showDismissed: Boolean, val estimatedMonthlyCents: Long)`, plus `fun confirm(id: Long)`, `fun dismiss(id: Long)`, `fun toggleShowDismissed()`.

> Note: view models here use `supervisedViewModelScope()` which defaults to `Dispatchers.Main`, so the test sets the Main dispatcher (mirroring `TagsViewModelTest`).

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.financeapp.ui.subscriptions

import com.financeapp.domain.model.*
import com.financeapp.domain.repository.SubscriptionRepository
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setup() { kotlinx.coroutines.Dispatchers.setMain(testDispatcher) }
    @AfterTest fun teardown() { kotlinx.coroutines.Dispatchers.resetMain() }

    private fun sub(id: Long, cadence: TransactionFrequency, median: Long, status: SubscriptionStatus) =
        DetectedSubscription(
            id = id, payeeId = null, displayName = "Sub$id", matchKey = "name:sub$id",
            cadence = cadence, status = status, medianAmountCents = median,
            minAmountCents = median, maxAmountCents = median, isVariable = false,
            occurrenceCount = 4, firstSeen = LocalDate(2026, 1, 1), lastSeen = LocalDate(2026, 4, 1),
            nextExpectedDate = LocalDate(2026, 5, 1), confidence = 90, isActive = true
        )

    private class FakeRepo(initial: List<DetectedSubscription>) : SubscriptionRepository {
        val flow = MutableStateFlow(initial)
        var confirmed: Long? = null
        override fun getSubscriptions(): Flow<List<DetectedSubscription>> = flow
        override suspend fun rescan() {}
        override suspend fun confirm(id: Long) { confirmed = id }
        override suspend fun dismiss(id: Long) {}
        override fun notifySubscriptionsChanged() {}
    }

    @Test
    fun `estimated monthly total normalizes yearly to monthly and hides dismissed`() = runTest(testDispatcher, timeout = 5.seconds) {
        val repo = FakeRepo(listOf(
            sub(1, TransactionFrequency.MONTHLY, 1000, SubscriptionStatus.CONFIRMED),   // $10/mo
            sub(2, TransactionFrequency.YEARLY, 12000, SubscriptionStatus.CANDIDATE),   // $120/yr -> $10/mo
            sub(3, TransactionFrequency.MONTHLY, 5000, SubscriptionStatus.DISMISSED)    // hidden
        ))
        val vm = SubscriptionViewModel(repo)
        vm.uiState.test(timeout = 5.seconds) {
            // Skip initial empty emission, take the loaded one.
            val state = awaitItem().let { if (it.subscriptions.isEmpty()) awaitItem() else it }
            assertEquals(2, state.subscriptions.size, "dismissed hidden by default")
            assertEquals(2000, state.estimatedMonthlyCents, "10 + 10 dollars per month")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirm delegates to repository`() = runTest(testDispatcher) {
        val repo = FakeRepo(emptyList())
        SubscriptionViewModel(repo).confirm(7)
        assertEquals(7, repo.confirmed)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.subscriptions.SubscriptionViewModelTest"`
Expected: FAIL — unresolved `SubscriptionViewModel`.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.financeapp.ui.subscriptions

import com.financeapp.domain.model.DetectedSubscription
import com.financeapp.domain.model.SubscriptionStatus
import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.repository.SubscriptionRepository
import com.financeapp.ui.supervisedViewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubscriptionUiState(
    val subscriptions: List<DetectedSubscription> = emptyList(),
    val showDismissed: Boolean = false,
    val estimatedMonthlyCents: Long = 0
)

class SubscriptionViewModel(
    private val repository: SubscriptionRepository
) {
    private val scope = supervisedViewModelScope()

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    private var all: List<DetectedSubscription> = emptyList()

    init {
        scope.launch {
            repository.getSubscriptions().collect { list ->
                all = list
                recompute()
            }
        }
    }

    fun confirm(id: Long) = launchAction { repository.confirm(id) }
    fun dismiss(id: Long) = launchAction { repository.dismiss(id) }
    fun toggleShowDismissed() {
        _uiState.value = _uiState.value.copy(showDismissed = !_uiState.value.showDismissed)
        recompute()
    }

    private fun launchAction(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    private fun recompute() {
        val showDismissed = _uiState.value.showDismissed
        val visible = all.filter { showDismissed || it.status != SubscriptionStatus.DISMISSED }
        val monthly = visible
            .filter { it.status != SubscriptionStatus.DISMISSED && it.isActive }
            .sumOf { monthlyEquivalentCents(it) }
        _uiState.value = _uiState.value.copy(
            subscriptions = visible,
            estimatedMonthlyCents = monthly
        )
    }

    private fun monthlyEquivalentCents(s: DetectedSubscription): Long = when (s.cadence) {
        TransactionFrequency.DAILY -> s.medianAmountCents * 30
        TransactionFrequency.WEEKLY -> s.medianAmountCents * 52 / 12
        TransactionFrequency.BIWEEKLY -> s.medianAmountCents * 26 / 12
        TransactionFrequency.MONTHLY -> s.medianAmountCents
        TransactionFrequency.YEARLY -> s.medianAmountCents / 12
    }
}
```

- [ ] **Step 4: Run the ViewModel test to verify pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.subscriptions.SubscriptionViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Implement the screen**

Create `SubscriptionsScreen.kt`. Follow the existing screen conventions (a `Scaffold` with `TopAppBar`, a back button calling `onBack`, `EmptyState` when empty, and `CurrencyText` for amounts — mirror `ScheduledScreen.kt`). Minimum content:

```kotlin
package com.financeapp.ui.subscriptions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.DetectedSubscription
import com.financeapp.domain.model.SubscriptionStatus
import com.financeapp.ui.components.CurrencyText
import com.financeapp.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: SubscriptionViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Subscriptions") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = viewModel::toggleShowDismissed) {
                        Text(if (state.showDismissed) "Hide dismissed" else "Show dismissed")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.subscriptions.isEmpty()) {
                EmptyState(
                    title = "No subscriptions detected yet",
                    message = "Recurring charges will appear here after your next import."
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${state.subscriptions.count { it.status != SubscriptionStatus.DISMISSED }} subscriptions")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("~")
                        CurrencyText(amountCents = state.estimatedMonthlyCents)
                        Text("/mo")
                    }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.subscriptions, key = { it.id }) { sub ->
                        SubscriptionRow(
                            sub = sub,
                            onConfirm = { viewModel.confirm(sub.id) },
                            onDismiss = { viewModel.dismiss(sub.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    sub: DetectedSubscription,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ListItem(
        headlineContent = { Text(sub.displayName + if (!sub.isActive) " (looks cancelled)" else "") },
        supportingContent = {
            Column {
                Text("${sub.cadence.displayName} · next ${sub.nextExpectedDate}")
                if (sub.isVariable) {
                    Row { Text("varies "); CurrencyText(amountCents = sub.minAmountCents); Text("–"); CurrencyText(amountCents = sub.maxAmountCents) }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CurrencyText(amountCents = sub.medianAmountCents)
                if (sub.status == SubscriptionStatus.CANDIDATE) {
                    TextButton(onClick = onConfirm) { Text("Confirm") }
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    )
}
```

> Verify `EmptyState` and `CurrencyText` parameter names against their definitions (`ui/components/EmptyState.kt`, `ui/components/CurrencyText.kt`) and adjust the call sites to match. Use `HorizontalDivider` or `Divider` per the Material3 version already used elsewhere in the codebase.

- [ ] **Step 6: Register the ViewModel in DI**

In `Modules.kt` add:

```kotlin
single { com.financeapp.ui.subscriptions.SubscriptionViewModel(get()) }
```

- [ ] **Step 7: Add the navigation destination**

In `AppNavigationRail.kt`, add to `AppDestinations` (place in the `TOOLS` group next to `Scheduled`):

```kotlin
val Subscriptions = NavigationDestination(
    route = "subscriptions",
    label = "Subscriptions",
    icon = Icons.Default.Autorenew,
    group = NavigationGroup.TOOLS
)
```

Add `Subscriptions,` to the `allDestinations` list (after `Scheduled`). (`Icons.Default.Autorenew` is a standard Material icon; if unavailable in the bundled icon set, use `Icons.Default.Repeat`.)

- [ ] **Step 8: Wire the screen into `App.kt`**

1. Add `SUBSCRIPTIONS` to the `Screen` enum (line ~153).
2. In the `navigate` route→Screen `when` (line ~226), add: `"subscriptions" -> Screen.SUBSCRIPTIONS`.
3. In the Screen→route `when` (line ~251), add: `Screen.SUBSCRIPTIONS -> "subscriptions"`.
4. In `MainScreen`/`UnlockedApp`, obtain the view model near the others (line ~165): `val subscriptionViewModel: SubscriptionViewModel = koinInject()`.
5. In the render `when` (near `Screen.SCHEDULED ->` at line ~330), add:

```kotlin
Screen.SUBSCRIPTIONS -> SubscriptionsScreen(
    viewModel = subscriptionViewModel,
    onBack = navigateBack,
    modifier = Modifier.fillMaxSize()
)
```

Add the import `import com.financeapp.ui.subscriptions.SubscriptionsScreen` and `import com.financeapp.ui.subscriptions.SubscriptionViewModel`.

- [ ] **Step 9: Build and run the full suite**

Run: `./gradlew :shared:desktopTest`
Expected: PASS (whole suite). Then verify the app compiles: `./gradlew :desktopApp:compileKotlin` (or `:desktopApp:run` for a manual smoke test of the new nav entry).

- [ ] **Step 10: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/ui/subscriptions/ \
        shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt \
        shared/src/commonMain/kotlin/com/financeapp/ui/navigation/AppNavigationRail.kt \
        shared/src/commonMain/kotlin/com/financeapp/App.kt \
        shared/src/commonTest/kotlin/com/financeapp/ui/subscriptions/
git commit -m "feat: add Subscriptions screen and navigation"
```

---

## Final verification

- [ ] Run the entire suite: `./gradlew :shared:desktopTest` — all green.
- [ ] Manual smoke test: `./gradlew :desktopApp:run`, unlock, confirm the Subscriptions nav entry appears under Tools, import a file with a few months of recurring charges, and confirm detected subscriptions appear with cadence, amount, and next-expected date; confirm/dismiss update the list; dismissed items hide behind the toggle.
