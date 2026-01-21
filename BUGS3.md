# Known Bugs and Issues (Review Pass 3)

This document tracks bugs discovered during a full repo review. Issues are prioritized by severity.

## High Priority Bugs

### 1. Payee Merge Leaves Orphan References / FK Violations
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/PayeeRepositoryImpl.kt:159-179`
- **Issue:** `mergePayees()` updates only `Transactions` and then deletes the source payee. `ScheduledTransactions`, `TransactionTemplates`, and `PayeeAliases` can still reference the source payee, which can violate FK constraints or leave orphan references.
- **Fix:** Now updates ScheduledTransactions, TransactionTemplates, and PayeeAliases to point to the target payee before deleting the source.

### 2. Category Delete Ignores Child Categories
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/CategoryRepositoryImpl.kt:95-132`
- **Issue:** Deleting a category does not handle children that reference it via `parentId`. Deleting a parent category can fail due to FK constraints or leave child rows pointing at a deleted parent.
- **Fix:** Now nullifies child category `parentId` values before deleting the parent category.

### 3. Reconciliation Double-Counts Cleared Transactions
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/reconcile/ReconcileViewModel.kt:35-111`
- **Issue:** The reconciliation baseline uses `getClearedBalance()` as "reconciled balance", then adds selected (unreconciled) transactions. If any unreconciled transactions are already cleared, they are double-counted, producing incorrect differences and potentially blocking reconciliation.
- **Fix:** Now uses `getReconciledBalance()` which sums only reconciled transactions, not cleared ones.

## Medium Priority Issues

### 4. Scheduled Transactions Ignore End Date When Selecting Due Items
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/ScheduledTransactionRepositoryImpl.kt:69-78`
- **Issue:** `getDueScheduledTransactions()` filters by `isActive` and `nextDate <= today` but ignores `endDate`. Transactions scheduled past their end date can still be returned and inserted.
- **Fix:** Added `endDate` constraint: `endDate.isNull() or (nextDate lessEq endDate)`.

### 5. Scheduled Transaction Entry Can Insert Past End Date
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/scheduled/ScheduledViewModel.kt:94-140`
- **Issue:** `enterDueTransactions()` inserts a transaction before checking `endDate`, so a schedule can create at least one transaction after its end date.
- **Fix:** Now checks `nextDate > endDate` before inserting; if true, deactivates the schedule and skips insertion.

### 6. Search View Edits/Deletes Don't Refresh Account Balances
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/search/SearchViewModel.kt:89-118`
- **Issue:** Transaction edits and deletes from Search do not notify `AccountRepository` to refresh balances, so account totals can stay stale.
- **Fix:** Added `AccountRepository` to constructor and calls `notifyBalancesChanged()` after delete/edit operations.

### 7. Snapshot Scheduling Drifts on DST and Clamps Months to 28 Days
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/domain/service/SnapshotScheduler.kt:157-240`
- **Issue:** Daily scheduling uses a fixed 24-hour modulo; DST transitions can shift the trigger time by an hour. Monthly scheduling clamps `dayOfMonth` to 28, so days 29–31 never run even when valid.
- **Fix:** Daily scheduling now uses timezone-aware date arithmetic. Monthly scheduling uses `lastDayOfMonth()` helper instead of clamping to 28.

### 8. Dividend Shares Unit Mismatch
- **Status:** Fixed
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/domain/model/Performance.kt:103-112`
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/PerformanceRepositoryImpl.kt:467-518`
- **Issue:** `DividendEvent.shares` is defined as a `Long` in 1/10000 units, but DB storage uses a `Double` and record/read paths apply inconsistent scaling. This can corrupt stored share counts or diverge from the rest of the investment model (which uses `Double` shares).
- **Fix:** Changed `DividendEvent.shares` to `Double` and removed the erroneous `* 10000` conversion on read.

## Low Priority / Code Quality

### 9. Credential Filename Hash Collisions Can Overwrite Secrets
- **Status:** Fixed
- **File:** `shared/src/desktopMain/kotlin/com/financeapp/security/SecureCredentialStore.desktop.kt:178-191`
- **Issue:** Credential filenames are based on `hashCode()`, which can collide and overwrite unrelated secrets.
- **Fix:** Now uses SHA-256 hash (first 16 bytes, hex encoded) for collision-resistant filename generation.

### 10. Bulk Mark Cleared/Uncleared Inverts Status
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionsScreen.kt:171-186`
- **Issue:** Bulk "Mark Cleared/Uncleared" actions call `toggleCleared()` with a pre-set `isCleared` value, but `toggleCleared()` always inverts. This flips to the wrong state.
- **Fix:** Added `setCleared(transaction, cleared)` method to ViewModel; bulk operations now use it instead of `toggleCleared()`.

### 11. Running Balance Incorrect When Filtering
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionsScreen.kt:240-258`
- **Issue:** Running balances are computed from `filteredTransactions` instead of the full account ledger, so balances become incorrect for search/filter views.
- **Fix:** Running balances now computed from full `uiState.transactions` list; filtered view looks up correct balance by transaction ID.

### 12. Amount Parsing Uses Double (Precision/Truncation Risk)
- **Status:** Fixed
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/AddTransactionDialog.kt:323-336`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionFilterSheet.kt:162-169`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/reconcile/ReconcileScreen.kt:264-270`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/budget/BudgetScreen.kt:322-330`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/scheduled/ScheduledScreen.kt:340-347`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/templates/TemplatesScreen.kt:395-406`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/investments/InvestmentScreen.kt:548-558`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/investments/InvestmentScreen.kt:681-688`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/investments/LotComponents.kt:283-289`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/components/CurrencyText.kt:231-238`
- **Issue:** Amount inputs are converted with `toDouble()` and `(value * 100).toLong()`, which truncates/rounds incorrectly due to floating-point precision.
- **Fix:** Changed all occurrences to use `(value * 100).roundToLong()` to properly round instead of truncate.

### 13. QIF Files Cannot Be Selected in Desktop File Picker
- **Status:** Open
- **File:** `shared/src/desktopMain/kotlin/com/financeapp/FilePicker.desktop.kt:7-22`
- **Issue:** File picker filter allows only `.ofx`, `.qfx`, `.csv` despite the UI supporting QIF imports; `.qif` files can’t be selected.
- **Fix:** Add `.qif` to the filename filter.

### 14. OFX Sync Updates lastSynced Even When All Accounts Fail
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/ofx/OfxRepository.kt:149-181`
- **Issue:** `lastSynced` is updated unconditionally after the loop, even if every account failed, so UI shows a successful sync time on total failure.
- **Fix:** Only update `lastSynced` when at least one account sync succeeds.

### 15. OFX Parser Misses Lowercase or Mixed-Case STMTTRN Blocks
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/OfxParser.kt:73-103`
- **Issue:** `parseTransactions()` uses a case-sensitive regex for `<STMTTRN>` blocks. OFX files with lowercase tags will parse zero transactions.
- **Fix:** Use `RegexOption.IGNORE_CASE` for the STMTTRN block regex.

### 16. OFX Signon Request Doesn’t Escape Credentials
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/ofx/OfxClient.kt:276-299`
- **Issue:** User ID and password are embedded in OFX XML/SGML without escaping. Characters like `&` or `<` can break the request.
- **Fix:** Escape XML/SGML special chars (or restrict them at validation).

### 17. Charts Divide by Zero When Data Range or Total Is Zero
- **Status:** Open
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/ui/components/charts/LineChart.kt:83-127`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/components/charts/PieChart.kt:49-115`
  - `shared/src/commonMain/kotlin/com/financeapp/ui/components/charts/BarChart.kt:48-123`
- **Issue:** `LineChart` divides by `(maxValue - minValue)`, and pie/bar charts divide by totals that can be 0, producing NaN/invalid rendering when all values are zero.
- **Fix:** Guard zero-range/zero-total cases (e.g., treat range as 1 or render a flat line/empty state).

### 18. Holding Deletion Can Fail Due to Dividend/Snapshot FK References
- **Status:** Open
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/InvestmentRepositoryImpl.kt:119-128`
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/AccountRepositoryImpl.kt:70-141`
  - `shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt:210-225`
- **Issue:** `HoldingSnapshots` and `DividendEvents` reference holdings without cascade deletes. Deleting a holding or account can violate FK constraints or leave orphaned snapshot/dividend rows.
- **Fix:** Delete related snapshots/dividends before deleting holdings, or add `onDelete = CASCADE` where appropriate.

### 19. Import “Remember Mapping” Can Fail on Existing Alias
- **Status:** Open
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/ImportRepository.kt:109-202`
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/PayeeMatchingRepositoryImpl.kt:49-73`
- **Issue:** When users choose “remember mapping,” new aliases are inserted without checking for existing alias names. The `alias_name` unique index can throw and abort the import.
- **Fix:** Upsert aliases (delete+insert or update canonical payee) or skip insertion when alias already exists.
