# Preserve Original Imported Name & Surface Check Info — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the original bank-supplied name for every imported transaction, and show the check number + raw text in the ledger so checks no longer read "Unknown".

**Architecture:** Add one nullable `importedName` column to the `TransactionRecord` table and the `Transaction` domain model; populate it during import (OFX/CSV/QIF). The ledger and edit dialog read it back. A small pure helper centralizes the title-fallback logic so it is unit-testable; the badge/tooltip/dialog rendering is Compose-Desktop and verified by build + run.

**Tech Stack:** Kotlin, Exposed ORM 1.x over H2, Compose Multiplatform (desktop/JVM), kotlin.test in `commonTest`.

## Global Constraints

- Persistence is **Exposed + H2** (not SQLDelight). FK enforcement is ON.
- Money is integer cents (`Long`); dates are Unix millis. Not relevant here but do not change column types.
- Shared module targets `jvm("desktop")` only; `java.*`/`String.format` are allowed in `commonMain`.
- New DB column must be **nullable** and added via the existing idempotent `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` migration pattern in `DatabaseDriverFactory.desktop.kt`.
- Tests build the schema via `SchemaUtils.create` from `Tables.kt` (see `TestDatabaseFactory`), so the new column appears in tests automatically once added to the table object. The desktop `ALTER TABLE` migration is only for pre-existing on-disk DBs and is **not** unit-tested (it would only exercise H2 built-in behavior; the existing migrations follow the same untested pattern).
- Run the suite with: `./gradlew :shared:desktopTest`. A single class: `./gradlew :shared:desktopTest --tests "FULLY.Qualified.ClassName"`.

---

### Task 1: Add `importedName` to the data layer (schema, model, repository)

Adds the column and the domain field, wires every write/read path in `TransactionRepositoryImpl`, the test data factory, and the desktop migration. Deliverable: a transaction's `importedName` round-trips through insert/update/read.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt` (after line 56, `checkNumber`)
- Modify: `shared/src/commonMain/kotlin/com/financeapp/domain/model/Transaction.kt` (after `checkNumber`, line 26)
- Modify: `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt` (insert ~166, batchInsert ~198, update ~237, createTransfer inserts ~433/452, `toDomain` ~488)
- Modify: `shared/src/desktopMain/kotlin/com/financeapp/db/DatabaseDriverFactory.desktop.kt` (after the `day_of_month` migration block, line 93)
- Modify: `shared/src/commonTest/kotlin/com/financeapp/test/TestDataFactory.kt` (`createTestTransaction`, params ~95 and body ~112)
- Test: `shared/src/commonTest/kotlin/com/financeapp/data/repository/TransactionRepositoryTest.kt`

**Interfaces:**
- Produces: `Transaction.importedName: String?` (default `null`); column `Transactions.importedName` (`varchar("imported_name", 1024).nullable()`). Later tasks read `transaction.importedName`.

- [ ] **Step 1: Add the column to the schema**

In `Tables.kt`, inside `object Transactions`, add directly after the `checkNumber` line:

```kotlin
    val importedName = varchar("imported_name", 1024).nullable()
```

- [ ] **Step 2: Add the field to the domain model**

In `Transaction.kt`, add to the `Transaction` data class right after `val checkNumber: String? = null,`:

```kotlin
    val importedName: String? = null,
```

- [ ] **Step 3: Add the field to the test data factory**

In `TestDataFactory.kt`, in `createTestTransaction`, add a parameter after `checkNumber: String? = null,`:

```kotlin
        importedName: String? = null,
```

and in the `Transaction(...)` constructor call, after `checkNumber = checkNumber,`:

```kotlin
        importedName = importedName,
```

- [ ] **Step 4: Write the failing round-trip test**

In `TransactionRepositoryTest.kt`, add:

```kotlin
    @Test
    fun importedName_roundtrips_through_insert_and_read() = runTest(testDispatcher) {
        val id = repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = testAccountId,
                importedName = "CHECK 1234 PROCESSED CHECK"
            )
        )
        val loaded = repository.getTransactionById(id)
        assertEquals("CHECK 1234 PROCESSED CHECK", loaded?.importedName)
    }

    @Test
    fun importedName_is_preserved_by_update() = runTest(testDispatcher) {
        val id = repository.insertTransaction(
            TestDataFactory.createTestTransaction(
                accountId = testAccountId,
                importedName = "ORIGINAL BANK TEXT"
            )
        )
        val loaded = repository.getTransactionById(id)!!
        repository.updateTransaction(loaded.copy(memo = "edited"))
        assertEquals("ORIGINAL BANK TEXT", repository.getTransactionById(id)?.importedName)
    }
```

- [ ] **Step 5: Run the tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.TransactionRepositoryTest"`
Expected: FAIL — `importedName` is always `null` (write paths don't persist it yet) so `importedName_roundtrips_through_insert_and_read` asserts `"CHECK 1234 PROCESSED CHECK" == null`.

- [ ] **Step 6: Wire the write and read paths in the repository**

In `TransactionRepositoryImpl.kt`:

In `insertTransaction` (the `Transactions.insert { ... }` block), after `it[checkNumber] = transaction.checkNumber`:

```kotlin
                it[importedName] = transaction.importedName
```

In `batchInsertTransactions` (the inner `Transactions.insert { ... }`), after `it[checkNumber] = transaction.checkNumber`:

```kotlin
                        it[importedName] = transaction.importedName
```

In `updateTransaction` (the `Transactions.update { ... }`), after `it[checkNumber] = transaction.checkNumber`:

```kotlin
                it[importedName] = transaction.importedName
```

In `createTransfer`, in BOTH the outgoing and incoming `Transactions.insert { ... }` blocks, after `it[checkNumber] = null`:

```kotlin
                it[importedName] = null
```

In `ResultRow.toDomain()`, after `checkNumber = this[Transactions.checkNumber],`:

```kotlin
            importedName = this[Transactions.importedName],
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.repository.TransactionRepositoryTest"`
Expected: PASS (all tests in the class, including the two new ones).

- [ ] **Step 8: Add the desktop migration for existing databases**

In `DatabaseDriverFactory.desktop.kt`, after the `day_of_month` migration block (the one ending at line 93), add a new block:

```kotlin
        transaction(db) {
            try {
                // Migration: Add imported_name to TransactionRecord so the original bank-supplied
                // name is preserved (checks have no payee; lets the ledger show raw text and lets a
                // mis-associated payee be recovered).
                exec("ALTER TABLE ${Transactions.tableName} ADD COLUMN IF NOT EXISTS imported_name VARCHAR(1024)")
            } catch (e: Exception) {
                println("Warning: Migration may have already been applied: ${e.message}")
            }
        }
```

Ensure `Transactions` is imported at the top of the file (it is referenced by other migrations already; add the import only if the compiler reports it missing).

- [ ] **Step 9: Build to confirm desktop compiles (migration included)**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt \
        shared/src/commonMain/kotlin/com/financeapp/domain/model/Transaction.kt \
        shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt \
        shared/src/desktopMain/kotlin/com/financeapp/db/DatabaseDriverFactory.desktop.kt \
        shared/src/commonTest/kotlin/com/financeapp/test/TestDataFactory.kt \
        shared/src/commonTest/kotlin/com/financeapp/data/repository/TransactionRepositoryTest.kt
git commit -m "feat: persist original imported name on transactions"
```

---

### Task 2: Populate `importedName` during import

Sets `importedName` from the bank-supplied `name` in both import code paths, for checks and non-checks alike. Deliverable: imported transactions carry their original name.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/ImportRepository.kt` (`importWithMappings` insert ~256; `importTransactions` `Transaction(...)` build ~346)
- Test: `shared/src/commonTest/kotlin/com/financeapp/data/fileimport/ImportRepositoryTest.kt`

**Interfaces:**
- Consumes: `Transaction.importedName` and `Transactions.importedName` from Task 1; `ImportedTransaction.name`.

- [ ] **Step 1: Write the failing import tests**

In `ImportRepositoryTest.kt`, add (the helper `importedTxn(fitId, name)` and `insertAccount()` already exist in this file):

```kotlin
    @Test
    fun import_stores_original_name_for_check_and_non_check() = runTest(dispatcher) {
        val accountId = insertAccount()
        val check = importedTxn("fit-chk", "CHECK 1234").copy(checkNumber = "1234")
        val store = importedTxn("fit-store", "SAFEWAY #123")

        importRepository.importPreviewedTransactions(listOf(check, store), accountId).getOrThrow()

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
            "SAFEWAY #123" to PayeeMapping(createNew = true, newPayeeName = "Safeway")
        )

        importRepository.importWithMappings(listOf(store), accountId, mappings).getOrThrow()

        val txn = transactionRepository.getTransactionsByAccount(accountId).first()
            .first { it.importId == "fit-map" }
        assertEquals("SAFEWAY #123", txn.importedName)
    }
```

Note: confirm the `PayeeMapping(...)` arguments against the constructor in `domain/model/PayeeMapping.kt`; use only the parameters that exist (at minimum `createNew` and `newPayeeName`). Adjust the call to match if the names differ.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.fileimport.ImportRepositoryTest"`
Expected: FAIL — `importedName` is `null` because the import paths don't set it yet.

- [ ] **Step 3: Set `importedName` in `importWithMappings`**

In `ImportRepository.kt`, in `importWithMappings`, inside the `Transactions.insert { ... }` loop, after `it[Transactions.checkNumber] = importedTxn.effectiveCheckNumber`:

```kotlin
                        it[Transactions.importedName] = importedTxn.name
```

- [ ] **Step 4: Set `importedName` in `importTransactions`**

In `ImportRepository.kt`, in `importTransactions`, in the `Transaction(...)` constructor inside `newTransactions.map { ... }`, after `checkNumber = importedTxn.effectiveCheckNumber,`:

```kotlin
                    importedName = importedTxn.name,
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.data.fileimport.ImportRepositoryTest"`
Expected: PASS (all tests, including the two new ones).

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/data/fileimport/ImportRepository.kt \
        shared/src/commonTest/kotlin/com/financeapp/data/fileimport/ImportRepositoryTest.kt
git commit -m "feat: populate imported name during OFX/CSV/QIF import"
```

---

### Task 3: Ledger display — title fallback helper, check badge, tooltip

Adds a unit-tested pure helper for the row/dialog title fallback, then uses it in the ledger row, adds a check-number badge, and a hover tooltip showing the original imported text when a payee hides it. Deliverable: imported rows show real bank text (never "Unknown" when an imported name exists), checks show `#1234`, and hovering a payee row reveals the original text.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionDisplay.kt`
- Create: `shared/src/commonTest/kotlin/com/financeapp/ui/transactions/TransactionDisplayTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionsScreen.kt` (`TransactionCard`, title `Column` ~656-698)

**Interfaces:**
- Produces:
  - `fun transactionDisplayTitle(payeeName: String?, importedName: String?, memo: String?): String`
  - `fun importedNameTooltip(displayedTitle: String, importedName: String?): String?` — returns the tooltip text (`"Imported as: <importedName>"`) only when `importedName` is non-blank and differs from `displayedTitle`, else `null`.
- Consumes: `Transaction.importedName`, `Transaction.checkNumber` (via `transaction.transaction`).

- [ ] **Step 1: Write the failing helper tests**

Create `TransactionDisplayTest.kt`:

```kotlin
package com.financeapp.ui.transactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransactionDisplayTest {
    @Test fun title_prefers_payee_then_imported_then_memo_then_unknown() {
        assertEquals("Safeway", transactionDisplayTitle("Safeway", "SAFEWAY #123", "groceries"))
        assertEquals("SAFEWAY #123", transactionDisplayTitle(null, "SAFEWAY #123", "groceries"))
        assertEquals("groceries", transactionDisplayTitle(null, null, "groceries"))
        assertEquals("Unknown", transactionDisplayTitle(null, null, null))
    }

    @Test fun tooltip_only_when_imported_name_differs_from_title() {
        assertEquals("Imported as: SAFEWAY #123", importedNameTooltip("Safeway", "SAFEWAY #123"))
        assertNull(importedNameTooltip("SAFEWAY #123", "SAFEWAY #123")) // same as title
        assertNull(importedNameTooltip("Safeway", null))                // nothing imported
        assertNull(importedNameTooltip("Safeway", "   "))               // blank
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.transactions.TransactionDisplayTest"`
Expected: FAIL with unresolved reference `transactionDisplayTitle` / `importedNameTooltip`.

- [ ] **Step 3: Implement the helper**

Create `TransactionDisplay.kt`:

```kotlin
package com.financeapp.ui.transactions

/** Ledger/edit-dialog title for a transaction: payee, else the original imported bank text,
 *  else memo, else a generic fallback. */
fun transactionDisplayTitle(payeeName: String?, importedName: String?, memo: String?): String =
    payeeName?.takeIf { it.isNotBlank() }
        ?: importedName?.takeIf { it.isNotBlank() }
        ?: memo?.takeIf { it.isNotBlank() }
        ?: "Unknown"

/** Tooltip revealing the original imported text, shown only when a payee (or other title) is
 *  displayed in its place. Null when there is nothing extra to reveal. */
fun importedNameTooltip(displayedTitle: String, importedName: String?): String? =
    importedName?.takeIf { it.isNotBlank() && it != displayedTitle }
        ?.let { "Imported as: $it" }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.transactions.TransactionDisplayTest"`
Expected: PASS.

- [ ] **Step 5: Use the helper + add badge and tooltip in the ledger row**

In `TransactionsScreen.kt`, in `TransactionCard`, replace the title `Text(...)` (currently `text = transaction.payeeName ?: transaction.transaction.memo ?: "Unknown"`, around lines 662-669) with a row that wraps the title in a tooltip and appends a check badge. Replace just that `Text` element with:

```kotlin
                val title = transactionDisplayTitle(
                    transaction.payeeName,
                    transaction.transaction.importedName,
                    transaction.transaction.memo
                )
                val tooltip = importedNameTooltip(title, transaction.transaction.importedName)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val titleText: @Composable () -> Unit = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (tooltip != null) {
                        TooltipArea(tooltip = {
                            Surface(
                                color = MaterialTheme.colorScheme.inverseSurface,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = tooltip,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }) { titleText() }
                    } else {
                        titleText()
                    }
                    transaction.transaction.checkNumber?.let { num ->
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "#$num",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
```

Then add the import for `TooltipArea` at the top of the file:

```kotlin
import androidx.compose.foundation.TooltipArea
```

`Surface`, `Spacer`, `Row`, `Alignment`, `TextOverflow`, `MaterialTheme`, `dp`, and `Modifier` are already imported/used in this file. If the compiler flags any as missing, add the matching import.

- [ ] **Step 6: Build to confirm the UI compiles**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL. (Compose rendering of the badge/tooltip is verified manually in Task 4's run step.)

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionDisplay.kt \
        shared/src/commonTest/kotlin/com/financeapp/ui/transactions/TransactionDisplayTest.kt \
        shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionsScreen.kt
git commit -m "feat: show imported name, check badge, and tooltip in ledger"
```

---

### Task 4: Edit dialog shows the original imported text

Adds a read-only "Imported as" line to the edit dialog and reuses the title helper, so a wrongly-associated payee can be spotted and corrected. Deliverable: opening a transaction shows its original bank text when present.

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/AddTransactionDialog.kt` (`EditTransactionDialog`, read-only header ~462-471)

**Interfaces:**
- Consumes: `transactionDisplayTitle(...)` from Task 3; `Transaction.importedName` (via `txn`).

- [ ] **Step 1: Use the helper for the dialog title and add the imported-as line**

In `AddTransactionDialog.kt`, in `EditTransactionDialog`, replace the read-only title `Text` (currently `text = transaction.payeeName ?: txn.memo ?: "Unknown"`, around line 464) with the helper:

```kotlin
                Text(
                    text = transactionDisplayTitle(transaction.payeeName, txn.importedName, txn.memo),
                    style = MaterialTheme.typography.titleMedium
                )
```

Then, immediately after the amount `Text(...)` block (the one ending at line 471), add:

```kotlin
                txn.importedName?.takeIf { it.isNotBlank() }?.let { imported ->
                    Text(
                        text = "Imported as: $imported",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
```

`transactionDisplayTitle` is in the same package (`com.financeapp.ui.transactions`), so no import is needed.

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full test suite**

Run: `./gradlew :shared:desktopTest`
Expected: BUILD SUCCESSFUL (all tests pass).

- [ ] **Step 4: Manual verification**

Run the app: `./gradlew :desktopApp:run`. Import an OFX/QIF file containing a check (e.g. NAME "CHECK 1234") and a normal merchant. Confirm in the checking-account ledger: the check row shows the raw text with a `#1234` badge (not "Unknown"); assign a payee to the merchant, then hover its row and confirm the "Imported as: ..." tooltip; open it in the edit dialog and confirm the "Imported as" line.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/ui/transactions/AddTransactionDialog.kt
git commit -m "feat: show original imported name in edit dialog"
```

---

## Self-Review

**Spec coverage:**
- Goal 1 (persist original name for all imports) → Task 1 (column/model/repo) + Task 2 (both import paths). ✓
- Goal 2 (show check number in ledger) → Task 3 badge. ✓
- Goal 3 (replace "Unknown" with raw text) → Task 3 `transactionDisplayTitle` fallback. ✓
- Recovery via tooltip → Task 3 `importedNameTooltip`. ✓
- Recovery via edit dialog → Task 4. ✓
- Migration → Task 1 Step 8. ✓ (idempotency intentionally not unit-tested — see Global Constraints.)
- Testing section of spec → Tasks 1–3 have unit tests; UI rendering build- + run-verified (Task 4 Step 4). ✓

**Placeholder scan:** No TBD/TODO; every code step shows the code. The two "confirm against the constructor"/"add import if missing" notes are deliberate guardrails, not deferred work — the primary code is fully specified.

**Type consistency:** `importedName: String?` and `Transactions.importedName` used identically across Tasks 1–4. `transactionDisplayTitle(payeeName, importedName, memo)` and `importedNameTooltip(displayedTitle, importedName)` signatures match between definition (Task 3) and call sites (Tasks 3, 4).
