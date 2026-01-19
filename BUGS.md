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
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/PerformanceRepositoryImpl.kt:184`
- **Issue:** `holdingId = 0L` hardcoded with TODO comment - breaks holding snapshot functionality.
- **Fix:** Resolve holding ID from symbol or pass as parameter.

### 13. Investment Shares Type Mismatch
- **Status:** Open
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/domain/model/Investment.kt:8,15`
  - `shared/src/commonMain/kotlin/com/financeapp/domain/model/Performance.kt:74,89`
- **Issue:** `Holding.shares` is `Double` but `HoldingSnapshot.quantity` is `Long` (1/10000 units) - inconsistent precision across models.
- **Fix:** Standardize on `Long` in 1/10000 units for all share quantities.

### 14. TransactionRepositoryImpl - Missing Fields in Update
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:208-227`
- **Issue:** `updateTransaction()` doesn't update `importId`, `transactionType`, and `sic` fields.
- **Fix:** Add missing fields to update statement.

---

## Medium Priority Issues

### 15. SearchViewModel - Missing updatedAt on Edit
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/search/SearchViewModel.kt:95-114`
- **Issue:** `editTransaction()` doesn't update `updatedAt` timestamp, breaking audit trails.
- **Fix:** Add `updatedAt = Clock.System.now()` to the copy operation.

### 16. TransactionsViewModel - Filter Logic Mismatch
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionsViewModel.kt:155-162`
- **Issue:** If both `showCleared` and `showUncleared` are false, `isFilterActive()` returns true but all transactions are filtered out.
- **Fix:** Validate that at least one option is selected.

### 17. PayeeManagementViewModel - Missing Update Notification
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/payees/PayeeManagementViewModel.kt:63-76`
- **Issue:** `updatePayee()` and `setDefaultCategory()` don't trigger UI refresh.
- **Fix:** Ensure repository notifies listeners after updates.

### 18. TemplatesViewModel - No Exception Handling
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/templates/TemplatesViewModel.kt:59-64`
- **Issue:** Flow collect block has no exception handling - ViewModel will crash on error.
- **Fix:** Wrap in try-catch or use `.catch()` on the flow.

### 19. SnapshotScheduler - Hardcoded Delays
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/domain/service/SnapshotScheduler.kt:169-182`
- **Issue:** `calculateDelayUntilWeeklyTime()` and `calculateDelayUntilMonthlyTime()` ignore parameters, always return 7 or 30 days.
- **Fix:** Implement proper date calculations using kotlinx-datetime.

### 20. BudgetRepositoryImpl - String Literal for CategoryType
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/BudgetRepositoryImpl.kt:184`
- **Issue:** Uses hardcoded string `"EXPENSE"` instead of `CategoryType.EXPENSE.name`.
- **Fix:** Use enum reference for type safety.

### 21. InvestmentRepositoryImpl - Potential Null in leftJoin
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/InvestmentRepositoryImpl.kt:69`
- **Issue:** `leftJoin` means `Accounts.name` could be null, but assigned to non-null field.
- **Fix:** Handle null account names or use `innerJoin()`.

### 22. PerformanceRepositoryImpl - Incorrect Previous Value Logic
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/PerformanceRepositoryImpl.kt:393-405`
- **Issue:** Uses incorrect offset logic to find previous value - fetches wrong snapshot.
- **Fix:** Use `zipWithNext()` or maintain previous value from prior iteration.

### 23. ImportViewModel - Auto-Mapping Ignores User Preference
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/fileimport/ImportViewModel.kt:149-165`
- **Issue:** Auto-resolved payees always use payee's default category, ignoring user's previous import preferences.
- **Fix:** Check saved alias for category preference before defaulting.

### 24. ExportRepository - Budget Export Performance
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/backup/ExportRepository.kt:183-184`
- **Issue:** Iterates years 2020-2030 making 132 queries even if only 2 budgets exist.
- **Fix:** Query all budgets once and group in code.

### 25. QIF Parser - T and U Fields Overwrite
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/QifParser.kt:36-37`
- **Issue:** Both "T" and "U" prefixes set the same amount field - last one wins.
- **Fix:** Handle T and U separately or document precedence.

---

## Low Priority / Code Quality

### 26. Debug Print Statements in Production Code
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/ImportRepository.kt:125,275,360`
- **Issue:** `println()` and `printStackTrace()` calls should use proper logging.
- **Fix:** Use logging framework or remove debug statements.

### 27. PayeeMatchingRepositoryImpl - Empty .also Block
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/PayeeMatchingRepositoryImpl.kt:119`
- **Issue:** `.also { similar -> }` block is empty and serves no purpose.
- **Fix:** Remove empty block.

### 28. ConnectionsViewModel - Missing Cleanup Method
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/connections/ConnectionsViewModel.kt`
- **Issue:** No `cleanup()` method to cancel coroutines - potential memory leak.
- **Fix:** Add cleanup method like other ViewModels.

### 29. BackupViewModel - Duplicate Error Handling
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/backup/BackupViewModel.kt:30-158`
- **Issue:** Four nearly identical export functions with copy-pasted error handling.
- **Fix:** Extract common error handling logic.

### 30. PerformanceTabViewModel - Race Condition
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/investments/PerformanceTabViewModel.kt:77-83`
- **Issue:** `loadPerformanceMetrics()` and `loadChartData()` called without waiting - can complete out of order.
- **Fix:** Use `combine()` or sequential loading.

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
| 12 | Hardcoded holdingId | Open | |
| 13 | Shares type mismatch | Open | |
| 14 | Missing fields in update | Open | |
| 15 | SearchViewModel updatedAt | Open | |
| 16 | Filter logic mismatch | Open | |
| 17 | PayeeManagement notification | Open | |
| 18 | Templates exception handling | Open | |
| 19 | Snapshot scheduler delays | Open | |
| 20 | String literal CategoryType | Open | |
| 21 | Null in leftJoin | Open | |
| 22 | Previous value logic | Open | |
| 23 | Auto-mapping user preference | Open | |
| 24 | Budget export performance | Open | |
| 25 | QIF T/U fields | Open | |
| 26 | Debug print statements | Open | |
| 27 | Empty .also block | Open | |
| 28 | Missing cleanup method | Open | |
| 29 | Duplicate error handling | Open | |
| 30 | Race condition | Open | |
