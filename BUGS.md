# Known Bugs and Issues

This document tracks bugs discovered during code review. Issues are prioritized by severity.

## Critical Bugs (Fix Immediately)

### 1. Floating-Point Precision Loss in All Parsers
- **Status:** Fixed
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/CsvParser.kt:138`
  - `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/QifParser.kt:106`
  - `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/OfxParser.kt:169`
- **Issue:** All three parsers use `(amount.toDouble() * 100).toLong()` which loses precision due to floating-point representation.
- **Example:** `"123.45"` can become `12344` instead of `12345`, `"0.01"` can become `0` instead of `1`
- **Fix:** Use string-based conversion to preserve precision (split on decimal, pad zeros, combine).

### 2. Missing Transaction Change Notifications
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt`
- **Lines:** 172, 206, 231
- **Issue:** `notifyTransactionsChanged()` not called in `insertTransaction()`, `batchInsertTransactions()`, and `deleteTransaction()`, causing stale UI data.
- **Fix:** Add `notifyTransactionsChanged()` call at end of each method.

### 3. AppLockRepositoryImpl - Missing Return Statement
- **Status:** Not a bug
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/AppLockRepositoryImpl.kt:27`
- **Issue:** Code review flagged `storedHash == inputHash` as not returning.
- **Analysis:** This is actually valid Kotlin. In expression-body lambdas, the last expression IS the return value. The code is correct.

### 4. Nested Database Transactions
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/ScheduledTransactionRepositoryImpl.kt:131-147`
- **Issue:** Calls `transaction()` inside an already-running transaction when loading payee/category names.
- **Fix:** Load payee/category names via JOIN in the original query or batch lookup before mapping.

### 5. ReportsViewModel - Not Implemented
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/reports/ReportsViewModel.kt:36-45`
- **Issue:** `loadReport()` sets `isLoading = true` then immediately `false` without loading data. Reports page shows nothing.
- **Fix:** Implement actual report loading logic.

---

## High Priority Bugs

### 6. Case Sensitivity in Import Payee Lookup
- **Status:** Not a bug
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/ImportRepository.kt:316`
- **Issue:** Code review flagged case sensitivity issues.
- **Analysis:** The implementation already handles case sensitivity correctly. Both `getPayeesByNames()` and `batchInsertPayees()` return maps with lowercase keys, and lookups use `.lowercase()` consistently.

### 7. DashboardViewModel - Budget Never Loaded
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/dashboard/DashboardViewModel.kt:49-84`
- **Issue:** `monthlyBudgetSpent` and `monthlyBudgetTotal` always show 0 - never populated from repository.
- **Fix:** Load budget data using `budgetRepository.getBudgetsWithSpendingByMonth()`.

### 8. AppViewModel - Always Locks on Init
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/AppViewModel.kt:66-75`
- **Issue:** `checkLockSetup()` sets `isLocked = true` every time ViewModel is created, even after user unlocked.
- **Fix:** Added companion object flag `hasUnlockedThisSession` that tracks unlock state across ViewModel recreations.

### 9. ImportViewModel - Temp ID Collisions
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/fileimport/ImportViewModel.kt:354-355`
- **Issue:** Negative temp IDs for recently created payees use `-(size + 1)` which can collide if payees are removed from state.
- **Fix:** Added monotonically decreasing counter `nextTempPayeeId` that resets only at session boundaries.

### 10. OFX Export - Unclosed Tags
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/backup/ExportRepository.kt:118-124`
- **Issue:** Writes `<TRNTYPE>value` without closing tags, producing invalid OFX format.
- **Fix:** Added closing tags to all OFX elements (CURDEF, BANKID, ACCTID, ACCTTYPE, TRNTYPE, DTPOSTED, TRNAMT, FITID, NAME, MEMO).

### 11. CSV Parser - Doesn't Handle Escaped Quotes
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/CsvParser.kt:69-87`
- **Issue:** `"John ""Johnny"" Doe"` parses incorrectly - embedded quotes (`""`) not handled per RFC 4180.
- **Fix:** Added lookahead in `parseCsvLine()` to detect `""` and convert to single `"` per RFC 4180.

### 12. PerformanceRepository - Hardcoded holdingId
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/PerformanceRepositoryImpl.kt:184`
- **Issue:** `holdingId = 0L` hardcoded with TODO comment - breaks holding snapshot functionality.
- **Fix:** Added symbol-to-holdingId lookup map to resolve holdingId from Holdings table.

### 13. Investment Shares Type Mismatch
- **Status:** Fixed
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/domain/model/Investment.kt:8,15`
  - `shared/src/commonMain/kotlin/com/financeapp/domain/model/Performance.kt:74,89`
- **Issue:** `Holding.shares` is `Double` but `HoldingSnapshot.quantity` is `Long` (1/10000 units) - inconsistent precision across models.
- **Fix:** Standardized on `Double` for all share quantities to match existing Holding.shares convention.

### 14. TransactionRepositoryImpl - Missing Fields in Update
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:208-227`
- **Issue:** `updateTransaction()` doesn't update `importId`, `transactionType`, and `sic` fields.
- **Fix:** Added importId, transactionType, and sic to the update statement.

---

## Medium Priority Issues

### 15. SearchViewModel - Missing updatedAt on Edit
- **Status:** Not a bug
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/search/SearchViewModel.kt:95-114`
- **Issue:** `editTransaction()` doesn't update `updatedAt` timestamp, breaking audit trails.
- **Analysis:** The repository layer (`TransactionRepositoryImpl.updateTransaction`) already sets `updatedAt = now` at line 231. The ViewModel doesn't need to set it.

### 16. TransactionsViewModel - Filter Logic Mismatch
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionsViewModel.kt:155-162`
- **Issue:** If both `showCleared` and `showUncleared` are false, `isFilterActive()` returns true but all transactions are filtered out.
- **Fix:** Added check to treat both-false as both-true (show all), and updated isFilterActive to use XOR.

### 17. PayeeManagementViewModel - Missing Update Notification
- **Status:** Not a bug
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/payees/PayeeManagementViewModel.kt:63-76`
- **Issue:** `updatePayee()` and `setDefaultCategory()` don't trigger UI refresh.
- **Analysis:** The repository layer (`PayeeRepositoryImpl.updatePayee`) already calls `notifyPayeesChanged()` at line 131.

### 18. TemplatesViewModel - No Exception Handling
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/templates/TemplatesViewModel.kt:59-64`
- **Issue:** Flow collect block has no exception handling - ViewModel will crash on error.
- **Fix:** Added `.catch()` operator to handle exceptions and prevent UI from being stuck.

### 19. SnapshotScheduler - Hardcoded Delays
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/domain/service/SnapshotScheduler.kt:169-182`
- **Issue:** `calculateDelayUntilWeeklyTime()` and `calculateDelayUntilMonthlyTime()` ignore parameters, always return 7 or 30 days.
- **Fix:** Implemented proper date calculations using kotlinx-datetime with edge case handling.

### 20. BudgetRepositoryImpl - String Literal for CategoryType
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/BudgetRepositoryImpl.kt:184`
- **Issue:** Uses hardcoded string `"EXPENSE"` instead of `CategoryType.EXPENSE.name`.
- **Fix:** Changed to `CategoryType.EXPENSE.name` for type safety.

### 21. InvestmentRepositoryImpl - Potential Null in leftJoin
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/InvestmentRepositoryImpl.kt:69`
- **Issue:** `leftJoin` means `Accounts.name` could be null, but assigned to non-null field.
- **Fix:** Handle null account names or use `innerJoin()`.

### 22. PerformanceRepositoryImpl - Incorrect Previous Value Logic
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/PerformanceRepositoryImpl.kt:393-425`
- **Issue:** Uses incorrect offset logic to find previous value - fetches wrong snapshot.
- **Fix:** Maintain previous value from prior iteration using a mutable variable.

### 23. ImportViewModel - Auto-Mapping Ignores User Preference
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/fileimport/ImportViewModel.kt:149-165`
- **Issue:** Auto-resolved payees always use payee's default category, ignoring user's previous import preferences.
- **Fix:** Added `preferredCategoryId` field to PayeeAlias schema/model. Aliases now store category preference from import, which takes priority over payee default.

### 24. ExportRepository - Budget Export Performance
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/backup/ExportRepository.kt:174-196`
- **Issue:** Iterates years 2020-2030 making 132 queries even if only 2 budgets exist.
- **Fix:** Query all budgets once with ORDER BY, reducing 132 queries to 1.

### 25. QIF Parser - T and U Fields Overwrite
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/QifParser.kt:36-42`
- **Issue:** Both "T" and "U" prefixes set the same amount field - last one wins.
- **Fix:** Store T and U amounts separately, prefer U (higher precision) when both present.

---

## Low Priority / Code Quality

### 26. Debug Print Statements in Production Code
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/ImportRepository.kt:125,275,360`
- **Issue:** `println()` and `printStackTrace()` calls should use proper logging.
- **Fix:** Removed debug print statements. Exceptions are propagated via Result.failure().

### 27. PayeeMatchingRepositoryImpl - Empty .also Block
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/PayeeMatchingRepositoryImpl.kt:119`
- **Issue:** `.also { similar -> }` block is empty and serves no purpose.
- **Fix:** Removed the empty `.also` block.

### 28. ConnectionsViewModel - Missing Cleanup Method
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/connections/ConnectionsViewModel.kt`
- **Issue:** No `cleanup()` method to cancel coroutines - potential memory leak.
- **Fix:** Added SupervisorJob to scope and cleanup() method to cancel coroutines.

### 29. BackupViewModel - Duplicate Error Handling
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/backup/BackupViewModel.kt:30-102`
- **Issue:** Four nearly identical export functions with copy-pasted error handling.
- **Fix:** Extracted common logic into `performExport()` helper function.

### 30. PerformanceTabViewModel - Race Condition
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/investments/PerformanceTabViewModel.kt:76-98`
- **Issue:** `loadPerformanceMetrics()` and `loadChartData()` called without waiting - can complete out of order.
- **Fix:** Extracted into single `loadTimeRangeData()` suspend function that executes both operations sequentially.

---

## Progress Tracking

| Bug # | Description | Status | Fixed In |
|-------|-------------|--------|----------|
| 1 | Floating-point precision | Fixed | AmountParser.kt |
| 2 | Missing transaction notifications | Fixed | TransactionRepositoryImpl.kt |
| 3 | AppLock missing return | Not a bug | N/A |
| 4 | Nested transactions | Fixed | ScheduledTransactionRepositoryImpl.kt |
| 5 | Reports not implemented | Fixed | ReportsViewModel.kt |
| 6 | Case sensitivity in import | Not a bug | N/A |
| 7 | Dashboard budget not loaded | Fixed | DashboardViewModel.kt |
| 8 | AppViewModel always locks | Fixed | AppViewModel.kt |
| 9 | Temp ID collisions | Fixed | ImportViewModel.kt |
| 10 | OFX unclosed tags | Fixed | ExportRepository.kt |
| 11 | CSV escaped quotes | Fixed | CsvParser.kt |
| 12 | Hardcoded holdingId | Fixed | PerformanceRepositoryImpl.kt |
| 13 | Shares type mismatch | Fixed | Performance.kt |
| 14 | Missing fields in update | Fixed | TransactionRepositoryImpl.kt |
| 15 | SearchViewModel updatedAt | Not a bug | N/A |
| 16 | Filter logic mismatch | Fixed | TransactionsViewModel.kt |
| 17 | PayeeManagement notification | Not a bug | N/A |
| 18 | Templates exception handling | Fixed | TemplatesViewModel.kt |
| 19 | Snapshot scheduler delays | Fixed | SnapshotScheduler.kt |
| 20 | String literal CategoryType | Fixed | BudgetRepositoryImpl.kt |
| 21 | Null in leftJoin | Open | |
| 22 | Previous value logic | Fixed | PerformanceRepositoryImpl.kt |
| 23 | Auto-mapping user preference | Fixed | PayeeAlias, ImportViewModel.kt |
| 24 | Budget export performance | Fixed | ExportRepository.kt |
| 25 | QIF T/U fields | Fixed | QifParser.kt |
| 26 | Debug print statements | Fixed | ImportRepository.kt |
| 27 | Empty .also block | Fixed | PayeeMatchingRepositoryImpl.kt |
| 28 | Missing cleanup method | Fixed | ConnectionsViewModel.kt |
| 29 | Duplicate error handling | Fixed | BackupViewModel.kt |
| 30 | Race condition | Fixed | PerformanceTabViewModel.kt |
