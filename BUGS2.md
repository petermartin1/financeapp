# Known Bugs and Issues (Review Pass 2)

This document tracks bugs discovered during deep code review. Issues are prioritized by severity.

## Critical Bugs (Fix Immediately)

### 1. Holding Snapshots Collapse Across Accounts
- **Status:** Open
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt:205-213`
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/PerformanceRepositoryImpl.kt:140-193`
- **Issue:** Holding snapshots are stored by `symbol` only (no holding/account ID). If multiple accounts hold the same symbol, snapshots merge and get misattributed. `getHoldingSnapshotsForDate()` returns `holdingId = 0L`, so snapshots cannot be mapped back to a real holding.
- **Fix:** Store `holding_id` (or account + symbol) in `HoldingSnapshots` and populate it in snapshot creation. Replace symbol-only joins with `holding_id`, and resolve IDs properly in `getHoldingSnapshotsForDate()`.

## High Priority Bugs

### 2. Account Delete Leaves Orphan Data
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/AccountRepositoryImpl.kt:143-194`
- **Issue:** Deleting an account only deletes transactions; holdings, scheduled transactions, templates, reconciliations, connected accounts, and related data remain.
- **Fix:** Now deletes all related data: transaction tags, split items, transactions, holding lots, holdings, scheduled transactions, reconciliation sessions, connected accounts, and nullifies template references.

### 3. Tag Delete Can Leave Orphans or Fail
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/TagRepositoryImpl.kt:83-90`
- **Issue:** `TransactionTag` rows are not cleared when deleting a tag; this can violate FK constraints or leave orphan rows.
- **Fix:** Now deletes TransactionTag entries before deleting the tag.

### 4. Payee Delete Can Leave Orphans or Fail
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/PayeeRepositoryImpl.kt:137-157`
- **Issue:** Transactions still reference the payee; deleting it can violate FK constraints or leave orphan IDs.
- **Fix:** Now nullifies payee references in transactions, scheduled transactions, and templates, and deletes payee aliases before deleting the payee.

### 5. Transaction Update Drops Imported Metadata
- **Status:** Fixed (BUGS.md #14)
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:216-228`
- **Issue:** `updateTransaction()` does not persist `importId`, `transactionType`, or `sic`. Editing a transaction silently clears these fields.
- **Fix:** Include missing fields in the update statement.

### 6. Share Precision Mismatch Across Models
- **Status:** Fixed (BUGS.md #13)
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/domain/model/Investment.kt:3-18`
  - `shared/src/commonMain/kotlin/com/financeapp/domain/model/Performance.kt:70-111`
- **Issue:** Holdings use `Double` shares while performance models use `Long` in 1/10000 units. Conversions truncate and drift.
- **Fix:** Standardized on Double for all share quantities.

## Medium Priority Issues

### 7. Performance Chart Uses Incorrect Previous Value Logic
- **Status:** Fixed (BUGS.md #22)
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/PerformanceRepositoryImpl.kt:381-425`
- **Issue:** Per-point query with `offset` is incorrect for previous values and is O(n^2). Chart deltas can be wrong.
- **Fix:** Now maintains previous value from prior iteration.

### 8. Holding Chart Uses Cost Basis as Prior Value
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/PerformanceRepositoryImpl.kt:442-478`
- **Issue:** For index > 0, `previousValue` uses `cost_basis`, not the previous snapshot value.
- **Fix:** Now maintains previous market value from prior iteration.

### 9. Date Range End Uses Fixed 24h Window (DST Risk)
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:110-121`
- **Issue:** `endMillis = startOfDay + 86400000 - 1` fails on DST days (23/25 hours), leading to missing or extra transactions.
- **Fix:** Now uses `endDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds() - 1` to properly handle DST transitions.

### 10. Snapshot Scheduler Ignores Weekly/Monthly Parameters
- **Status:** Fixed (BUGS.md #19)
- **File:** `shared/src/commonMain/kotlin/com/financeapp/domain/service/SnapshotScheduler.kt:165-182`
- **Issue:** Weekly and monthly delay calculations ignore day/hour parameters and always return 7 or 30 days.
- **Fix:** Implemented proper date calculations using kotlinx-datetime.

### 11. Export Escaping Gaps (CSV/OFX)
- **Status:** Fixed
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/data/backup/ExportRepository.kt:30-58`
  - `shared/src/commonMain/kotlin/com/financeapp/data/backup/ExportRepository.kt:65-126`
- **Issue:** CSV export only escapes memo, not account/payee/category. OFX export doesn't escape memo/payee for XML/SGML-sensitive characters.
- **Fix:** Now escapes all string fields in CSV (account, payee, category, memo); added escapeXml() function to sanitize `<`, `&`, `>`, `"`, `'` in OFX text fields (payeeName, memo).

### 12. Yahoo Price Conversion Truncates Instead of Rounding
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/quotes/YahooFinanceClient.kt:43,98`
- **Issue:** `(price * 100).toLong()` truncates and biases values low.
- **Fix:** Changed to `roundToLong()` for proper rounding.

## Low Priority / Code Quality

### 13. App Lock PIN Hash Uses Unsalted SHA-256
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/AppLockRepositoryImpl.kt:45-48`
- **Issue:** PIN hashing uses a fast, unsalted hash.
- **Fix:** Use PBKDF2/Argon2 with per-user salt (consistent with security docs).

### 14. Transactions Filter Can Hide All Rows
- **Status:** Fixed (BUGS.md #16)
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionsViewModel.kt:137-162`
- **Issue:** If both `showCleared` and `showUncleared` are false, filter is active but yields no results.
- **Fix:** Treats both-false as both-true (show all).

### 15. Search Edit Doesn't Update `updatedAt`
- **Status:** Not a bug (BUGS.md #15)
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/search/SearchViewModel.kt:95-110`
- **Issue:** `updatedAt` isn't touched on edit, breaking audit trails.
- **Analysis:** The repository layer (TransactionRepositoryImpl.updateTransaction) already sets updatedAt automatically.

### 16. Templates ViewModel Missing Error Handling
- **Status:** Fixed (BUGS.md #18)
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/templates/TemplatesViewModel.kt:43-64`
- **Issue:** No `catch` or try-catch around initial loads; errors can leave UI stuck in loading state.
- **Fix:** Added .catch() operator to handle exceptions.

### 17. SQLDelight Schema Diverges From Exposed
- **Status:** Open
- **File:** `shared/src/commonMain/sqldelight/com/financeapp/db/Finance.sq:433-436`
- **Issue:** SQLDelight schema includes a `password` column that does not exist in Exposed schema, and the app uses Exposed only.
- **Fix:** Align schemas or remove unused SQLDelight file.

### 18. Connections ViewModel Lacks Cleanup
- **Status:** Fixed (BUGS.md #28)
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/connections/ConnectionsViewModel.kt:31-37`
- **Issue:** Flow collection runs in a scope that is never canceled.
- **Fix:** Added SupervisorJob to scope and cleanup() method.

### 19. Database Encryption Config Iterations Ignored
- **Status:** Open
- **File:** `shared/src/desktopMain/kotlin/com/financeapp/db/DatabaseDriverFactory.desktop.kt:87-126`
- **Issue:** Config stores iteration/algorithm but `deriveEncryptionKey()` always uses hardcoded values, making the config misleading and preventing future iteration upgrades.
- **Fix:** Pass `iterations`/`algorithm` into `deriveEncryptionKey()` and use config values consistently.

---

## Progress Tracking

| Bug # | Description | Status | Fixed In |
|-------|-------------|--------|----------|
| 1 | Holding snapshots collapse across accounts | Open | |
| 2 | Account delete leaves orphan data | Fixed | AccountRepositoryImpl.kt |
| 3 | Tag delete can leave orphans | Fixed | TagRepositoryImpl.kt |
| 4 | Payee delete can leave orphans | Fixed | PayeeRepositoryImpl.kt |
| 5 | Transaction update drops metadata | Fixed | BUGS.md #14 |
| 6 | Share precision mismatch | Fixed | BUGS.md #13 |
| 7 | Performance chart previous value logic | Fixed | BUGS.md #22 |
| 8 | Holding chart uses cost basis as prior | Fixed | PerformanceRepositoryImpl.kt |
| 9 | Date range DST risk | Fixed | TransactionRepositoryImpl.kt |
| 10 | Snapshot weekly/monthly scheduling ignores inputs | Fixed | BUGS.md #19 |
| 11 | Export escaping gaps | Fixed | ExportRepository.kt |
| 12 | Yahoo price truncation | Fixed | YahooFinanceClient.kt |
| 13 | Unsalted PIN hash | Open | |
| 14 | Filter can hide all rows | Fixed | BUGS.md #16 |
| 15 | Search edit missing updatedAt | Not a bug | BUGS.md #15 |
| 16 | Templates ViewModel missing error handling | Fixed | BUGS.md #18 |
| 17 | SQLDelight schema divergence | Open | |
| 18 | Connections ViewModel lacks cleanup | Fixed | BUGS.md #28 |
| 19 | Database encryption config iterations ignored | Open | |
