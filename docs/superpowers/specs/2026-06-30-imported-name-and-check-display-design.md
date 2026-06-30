# Preserve original imported name & surface check info in the ledger

**Date:** 2026-06-30
**Status:** Approved (design)

## Problem

When importing bank transactions, checks show as "Unknown" in the ledger. Investigation
showed the underlying issues are narrower and partly different from the initial report:

- **Checks already do not get a payee.** Imports detect checks (`ImportedTransaction.isCheck`)
  and deliberately leave `payeeId` null (`ImportRepository.kt`). No change needed here.
- **The check number is already stored** in `Transactions.checkNumber` (and even recovered from
  names like "CHECK 1234"), but it is **never displayed** in the ledger.
- **The "Unknown" text** comes from `TransactionsScreen.kt`: the row title is
  `payeeName ?: memo ?: "Unknown"`. A check with no payee and no memo falls through to "Unknown".
- **The original imported name/description is never persisted.** It is used transiently during
  import to map to a payee, then discarded. There is no column for it, so a mis-associated payee
  cannot be recovered or audited.

## Goals

1. Persist the original imported name/description for **every** imported transaction (all of
   OFX, CSV, QIF), so a mis-associated payee can be spotted, displayed, and corrected.
2. Display the **check number** in the ledger.
3. Replace the "Unknown" ledger title with meaningful raw bank text for imported rows.

## Non-goals

- Changing check detection or payee-skipping behavior (already correct).
- Storing a full raw import payload / audit table (rejected — see Approaches).
- Editing or re-deriving the imported name after import; it is write-once at import, read-only after.

## Approach (chosen)

**Single `importedName` column on `TransactionRecord`.** Add one nullable column, populate it at
import for every transaction across all three parsers, and read it in the ledger and edit dialog.
The memo is already persisted separately, so the original *name* is the only missing piece.

Rejected alternatives:
- **Separate 1:1 `ImportedTransactionRecord` table** — adds a join for display and another child
  table to hand-clean on transaction delete (FK enforcement is ON). Overkill for "show the
  original name."
- **Reuse the memo field** — conflates two distinct fields and corrupts memos banks actually send.

## Design

### 1. Data

- **Schema** (`db/schema/Tables.kt`): add `val importedName = varchar("imported_name", 1024).nullable()`
  to `Transactions` (table `TransactionRecord`). 1024 is generous enough for any realistic bank
  description without going unbounded.
- **Domain model** (`domain/model/Transaction.kt`): add `val importedName: String? = null` to
  `Transaction`. Because `TransactionWithDetails` embeds the full `Transaction`, the value reaches
  the UI automatically — no separate read-path plumbing.
- **Migration** (`db/DatabaseDriverFactory.desktop.kt`): add, following the existing idempotent
  pattern:
  ```kotlin
  exec("ALTER TABLE ${Transactions.tableName} ADD COLUMN IF NOT EXISTS imported_name VARCHAR(1024)")
  ```
  wrapped in its own `transaction(db) { try { ... } catch ... }` block like the others.
- **Repository write paths** (`data/repository/TransactionRepositoryImpl.kt`): set
  `it[importedName] = transaction.importedName` in `insertTransaction`, `batchInsertTransactions`,
  and `updateTransaction`; clear it (`= null`) wherever the transfer-unlink paths null out
  `importId`/`sic`. Map it back in `ResultRow.toDomain()`:
  `importedName = this[Transactions.importedName]`.
- **Import paths** (`data/fileimport/ImportRepository.kt`): in both `importTransactions` and the
  mapping-aware `importWithMappings`, set `importedName = importedTxn.name` for **every**
  transaction (checks and non-checks alike). Check-number persistence is unchanged.

Manually-added transactions leave `importedName` null.

### 2. Ledger display (`ui/transactions/TransactionsScreen.kt`)

1. **Title fallback** changes from `payeeName ?: memo ?: "Unknown"` to
   `payeeName ?: importedName ?: memo ?: "Unknown"`. Imported rows now show real bank text instead
   of "Unknown".
2. **Check-number badge**: when `transaction.checkNumber != null`, render a small `[#1234]`
   chip/badge beside the title. Reads existing data; this is the in-ledger check-number indicator.
3. **Tooltip**: wrap the title in a Compose-Desktop tooltip (`TooltipArea`/`TooltipBox`) that shows
   `Imported as: <importedName>` **only when** `importedName` is present **and** differs from the
   displayed title (i.e. a payee is assigned). Lets you hover a mis-matched row to see what the
   bank actually sent.

The check display layout follows the approved mock: raw text as the title line, with a `#1234`
badge for checks.

### 3. Edit dialog

In the transaction edit/detail dialog, add a read-only `Imported as: <importedName>` line, shown
only when `importedName` is present. This is the primary surface for fixing a wrongly-associated
payee.

### 4. Testing (TDD)

- **Import/repository**: `importedName` is persisted for checks and non-checks across OFX, CSV, and
  QIF; survives the `importWithMappings` payee-mapping path; null for manually-added transactions.
- **Round-trip**: `ResultRow.toDomain()` returns `importedName`; `updateTransaction` preserves it.
- **Migration**: column is added idempotently on a pre-existing DB (re-running is a no-op).
- **UI logic** (where unit-testable): title fallback prefers payee, then importedName, then memo,
  then "Unknown"; tooltip text only present when importedName differs from the shown title.

## Affected files

- `shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt`
- `shared/src/commonMain/kotlin/com/financeapp/domain/model/Transaction.kt`
- `shared/src/desktopMain/kotlin/com/financeapp/db/DatabaseDriverFactory.desktop.kt`
- `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt`
- `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/ImportRepository.kt`
- `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionsScreen.kt`
- transaction edit/detail dialog (`ui/transactions/AddTransactionDialog.kt` / edit dialog)
- corresponding tests under `shared/src/desktopTest/...`