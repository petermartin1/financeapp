# Known Bugs and Issues (Review Pass 5)

This document tracks bugs discovered during a deep code review. Issues are prioritized by severity.

## High Priority Bugs

### 1. Performance Tab Never Finishes Loading + Repeated Collectors
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/investments/PerformanceTabViewModel.kt:50-72`
- **Issue:** `loadPerformanceData()` calls `getAllHoldingPerformance().collect { ... }`, which never completes. The subsequent `loadTimeRangeData()` and the `finally` block never run, so `isLoading` can stay true and chart/metrics never load. Each call to `loadPerformanceData()` starts another infinite collector, causing duplicate updates and leaks.
- **Fix:** Collect holding performance in a separate job (or `stateIn`) and ensure `loadTimeRangeData()` runs outside the infinite collection. Cancel any previous collector when reloading.

### 2. CSV/QIF Import FitId Collisions Drop Legit Transactions
- **Status:** Fixed
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/CsvParser.kt:53-65`
  - `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/QifParser.kt:117-136`
- **Issue:** `fitId` is generated from date + amount + hash of description/name. Multiple same-day transactions with identical amounts/descriptions collide, so the import de-duplication treats real transactions as duplicates and skips them.
- **Fix:** Include a stable per-row identifier (row index, raw line hash, or source-provided ID). At minimum, add a per-file sequence to guarantee uniqueness.

## Medium Priority Issues

### 3. TransactionRepository Flows Aren't Reactive for Several Queries
- **Status:** Fixed
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:42-52`
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:112-130`
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:132-141`
- **Issue:** `getTransactionsByAccount`, `getTransactionsByDateRange`, and `getTransactionsByCategory` return `Flow` but emit only once and ignore `transactionRefreshTrigger`. Consumers collecting these flows won’t update after inserts/updates unless they re-subscribe.
- **Fix:** Mirror the reactive pattern used by `getAllTransactionsWithDetails()` and `getTransactionsWithDetailsByAccount()` by mapping from `transactionRefreshTrigger` (or combining it with filters).

### 4. Scheduled Entry Doesn't Refresh Account Balances
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/scheduled/ScheduledViewModel.kt:94-140`
- **Issue:** `enterDueTransactions()` inserts transactions but never calls `accountRepository.notifyBalancesChanged()`. Account balances remain stale until another action forces a refresh.
- **Fix:** Inject `AccountRepository` and call `notifyBalancesChanged()` once after processing due transactions.

### 5. OFX Export Ignores Account Currency
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/backup/ExportRepository.kt:95-106`
- **Issue:** OFX export hardcodes `<CURDEF>USD</CURDEF>` for every account. Non-USD accounts export incorrect currency metadata.
- **Fix:** Use each account’s stored currency (`Accounts.currency`) when writing `<CURDEF>`.

### 6. SecurityAuditLogger Claims Persistent Logging but Doesn't Persist
- **Status:** Fixed (docs updated)
- **File:** `shared/src/commonMain/kotlin/com/financeapp/security/SecurityAuditLogger.kt:6-45`
- **Issue:** The header comment promises persistent audit logs, but the implementation only logs to console and memory. On restart, all audit events are lost.
- **Fix:** Implement persistent storage (file or secure log) or update the documentation to match behavior.

## Low Priority / Code Quality

### 7. ViewModel Dispose Hooks Don't Cancel Coroutines
- **Status:** Fixed
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/ui/investments/HoldingDetailViewModel.kt:200-204`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/investments/PerformanceTabViewModel.kt:123-125`
- **Issue:** `onDispose()` is a no-op. Both ViewModels start long-lived collections, so they continue running after the UI is closed.
- **Fix:** Cancel the scope (`viewModelScope.cancel()`) in `onDispose()`.

### 8. Accessibility Helpers Are Stubs
- **Status:** Fixed (removed)
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/accessibility/AccessibilityHelpers.kt:76-79`, `shared/src/commonMain/kotlin/com/financeapp/ui/accessibility/AccessibilityHelpers.kt:207-214`
- **Issue:** `formatDateForScreenReader()` returns the input unchanged and `isHighContrastMode()` always returns false. These methods imply accessibility behavior but don’t implement it.
- **Fix:** Implement proper date formatting and high-contrast detection, or remove/rename to avoid misleading callers.

### 9. CSV Exports Don't Escape All Fields
- **Status:** Fixed
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/data/backup/ExportRepository.kt:142-154`
  - `shared/src/commonMain/kotlin/com/financeapp/data/backup/ExportRepository.kt:159-170`
- **Issue:** `exportAccounts()` and `exportCategories()` escape only the name fields. `institution`, `icon`, and `color` can contain commas/quotes/newlines, which will break CSV structure.
- **Fix:** Run `escapeCsv()` on all fields written to CSV.

### 10. Reconcile Flag Doesn't Update `updatedAt`
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:330-336`
- **Issue:** `markTransactionReconciled()` changes transaction state but doesn’t update `updatedAt`, so audit/update timestamps become inconsistent.
- **Fix:** Set `updatedAt` alongside `isReconciled`.
