# Known Bugs and Issues (Review Pass 6)

This document tracks bugs discovered during a deep code review. Issues are prioritized by severity.

## High Priority Bugs

### 1. Transfer Pair Deletion Leaves Dangling References
- **Status:** Fixed
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:239-242`
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/AccountRepositoryImpl.kt:143-187`
- **Issue:** Transfers are stored as mutual `transfer_id` references. `deleteTransaction()` deletes a single row without clearing the counterpart’s `transfer_id`, and `deleteAccount()` deletes all transactions in an account without handling cross-account transfers. This can violate FK constraints or leave orphaned transfer pointers when one side is removed.
- **Fix:** When deleting a transaction or account, first locate paired transfers and either delete them together or set their `transfer_id` to null. Consider `ON DELETE SET NULL` for `transfer_id` and/or a repository-level delete that handles pairs.

### 2. deleteTransaction Doesn't Clear Tags/Splits
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:239-242`
- **Issue:** `deleteTransaction()` removes the transaction row but does not remove `TransactionTags` or `SplitItems`. With FK enforcement, this can raise constraint errors or leave orphaned rows if constraints are not enforced.
- **Fix:** Delete related `TransactionTags` and `SplitItems` before deleting the transaction, or define cascading deletes at the schema level.

### 3. Transfers Are Created Non-Atomically
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionsViewModel.kt:300-332`
- **Issue:** `addTransfer()` performs three separate DB operations (insert outgoing, insert incoming, then update outgoing). If any step fails, the ledger can end up with a single-sided transfer or an unlinked pair.
- **Fix:** Move transfer creation into a repository method that performs all steps in a single database transaction (and rolls back on failure).

## Medium Priority Issues

### 4. Scheduled Transactions Don’t Catch Up Missed Occurrences
- **Status:** Not fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/scheduled/ScheduledViewModel.kt:96-140`
- **Issue:** `enterDueTransactions()` only inserts one occurrence per scheduled transaction, even if the next date is far in the past. Missed cycles are silently skipped.
- **Fix:** Loop until `nextDate` exceeds today (or `endDate`), inserting each missed occurrence and advancing `nextDate` accordingly.

### 5. Spending-by-Category Counts Transfers/Uncategorized Debits
- **Status:** Not fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:305-323`
- **Issue:** `getSpendingByCategory()` includes all negative transactions regardless of category or transfer type. Transfers and other uncategorized debits inflate spending totals and dashboard charts.
- **Fix:** Filter to expense categories only (e.g., `Category.type == EXPENSE`) and/or exclude transactions marked as transfers or without a category.

### 6. Market Value Truncation Loses Cents for Fractional Shares
- **Status:** Not fixed
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/domain/model/Investment.kt:41-42`
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/PerformanceRepositoryImpl.kt:55-57`
- **Issue:** Market value calculations use `(shares * price).toLong()`, which truncates instead of rounding. This systematically undercounts holdings with fractional shares and skews portfolio totals/snapshots.
- **Fix:** Use rounding (`kotlin.math.round`) or a fixed-point approach to preserve cents.

## Low Priority / Correctness Edge Cases

### 7. Export Amount Formatting Can Be Off by One Cent
- **Status:** Not fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/backup/ExportRepository.kt:118-121`, `shared/src/commonMain/kotlin/com/financeapp/data/backup/ExportRepository.kt:211-240`
- **Issue:** OFX export uses `amount / 100.0`, and CSV uses a custom formatter that truncates rather than rounds. Negative values can end up one cent off, and floating-point formatting may introduce precision artifacts.
- **Fix:** Format currency using integer cents with explicit two-decimal formatting (no floating point).

### 8. Price Refresh Concurrency Race
- **Status:** Not fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/domain/service/PriceRefreshService.kt:56-87`
- **Issue:** `_isRefreshing` is checked and then set without synchronization. Two concurrent calls can both enter refresh, causing overlapping updates and inconsistent `lastError`/`lastRefreshTime`.
- **Fix:** Use a `Mutex`/`AtomicBoolean` compare-and-set to guarantee single-flight refresh.
