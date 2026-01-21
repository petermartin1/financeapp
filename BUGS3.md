# Known Bugs and Issues (Review Pass 3)

This document tracks bugs discovered during a full repo review. Issues are prioritized by severity.

## High Priority Bugs

### 1. Payee Merge Leaves Orphan References / FK Violations
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/PayeeRepositoryImpl.kt:159-167`
- **Issue:** `mergePayees()` updates only `Transactions` and then deletes the source payee. `ScheduledTransactions`, `TransactionTemplates`, and `PayeeAliases` can still reference the source payee, which can violate FK constraints or leave orphan references.
- **Fix:** Update all payee references (scheduled transactions, templates, aliases) to the target payee before deleting the source.

### 2. Category Delete Ignores Child Categories
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/CategoryRepositoryImpl.kt:95-126`
- **Issue:** Deleting a category does not handle children that reference it via `parentId`. Deleting a parent category can fail due to FK constraints or leave child rows pointing at a deleted parent.
- **Fix:** Reassign or nullify child `parentId` values (or cascade delete) before removing the parent category.

### 3. Reconciliation Double-Counts Cleared Transactions
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/reconcile/ReconcileViewModel.kt:35-111`
- **Issue:** The reconciliation baseline uses `getClearedBalance()` as “reconciled balance”, then adds selected (unreconciled) transactions. If any unreconciled transactions are already cleared, they are double-counted, producing incorrect differences and potentially blocking reconciliation.
- **Fix:** Base the starting balance on reconciled transactions only (or exclude cleared items from the selection list) and consider setting `isCleared = true` when marking transactions reconciled.

## Medium Priority Issues

### 4. Scheduled Transactions Ignore End Date When Selecting Due Items
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/data/repository/ScheduledTransactionRepositoryImpl.kt:69-76`
- **Issue:** `getDueScheduledTransactions()` filters by `isActive` and `nextDate <= today` but ignores `endDate`. Transactions scheduled past their end date can still be returned and inserted.
- **Fix:** Add an `endDate` constraint (e.g., `endDate is null OR nextDate <= endDate`) when selecting due items.

### 5. Scheduled Transaction Entry Can Insert Past End Date
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/scheduled/ScheduledViewModel.kt:94-133`
- **Issue:** `enterDueTransactions()` inserts a transaction before checking `endDate`, so a schedule can create at least one transaction after its end date.
- **Fix:** Skip insertion (and deactivate) when `scheduled.nextDate > endDate` before creating the transaction.

### 6. Search View Edits/Deletes Don’t Refresh Account Balances
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/search/SearchViewModel.kt:89-114`
- **Issue:** Transaction edits and deletes from Search do not notify `AccountRepository` to refresh balances, so account totals can stay stale.
- **Fix:** Inject `AccountRepository` (or a balance notifier) and call `notifyBalancesChanged()` after updates/deletes.

### 7. Snapshot Scheduling Drifts on DST and Clamps Months to 28 Days
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/domain/service/SnapshotScheduler.kt:157-225`
- **Issue:** Daily scheduling uses a fixed 24-hour modulo; DST transitions can shift the trigger time by an hour. Monthly scheduling clamps `dayOfMonth` to 28, so days 29–31 never run even when valid.
- **Fix:** Compute next run time using local date/time with timezone-aware arithmetic; for monthly schedules, snap to the last valid day of month instead of always 28.

### 8. Dividend Shares Unit Mismatch
- **Status:** Open
- **Files:**
  - `shared/src/commonMain/kotlin/com/financeapp/domain/model/Performance.kt:103-114`
  - `shared/src/commonMain/kotlin/com/financeapp/data/repository/PerformanceRepositoryImpl.kt:467-518`
- **Issue:** `DividendEvent.shares` is defined as a `Long` in 1/10000 units, but DB storage uses a `Double` and record/read paths apply inconsistent scaling. This can corrupt stored share counts or diverge from the rest of the investment model (which uses `Double` shares).
- **Fix:** Standardize dividend share units (prefer `Double` like holdings) and remove the ad-hoc `* 10000` conversions.

## Low Priority / Code Quality

### 9. Credential Filename Hash Collisions Can Overwrite Secrets
- **Status:** Open
- **File:** `shared/src/desktopMain/kotlin/com/financeapp/security/SecureCredentialStore.desktop.kt:178-186`
- **Issue:** Credential filenames are based on `hashCode()`, which can collide and overwrite unrelated secrets.
- **Fix:** Use a stable cryptographic hash (e.g., SHA-256) or encode the key safely to a filename to avoid collisions.

### 10. Bulk Mark Cleared/Uncleared Inverts Status
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionsScreen.kt:100-133`
- **Issue:** Bulk “Mark Cleared/Uncleared” actions call `toggleCleared()` with a pre-set `isCleared` value, but `toggleCleared()` always inverts. This flips to the wrong state.
- **Fix:** Add an explicit `setCleared(id, value)` path or update `toggleCleared()` usage to avoid double-inversion.

### 11. Running Balance Incorrect When Filtering
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/TransactionsScreen.kt:232-249`
- **Issue:** Running balances are computed from `filteredTransactions` instead of the full account ledger, so balances become incorrect for search/filter views.
- **Fix:** Compute running balance from the full transaction list and then filter for display (or show a clearly labeled “filtered balance”).

### 12. Amount Parsing Uses Double (Precision/Truncation Risk)
- **Status:** Open
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
- **Fix:** Use `AmountParser.parseToCents()` (or BigDecimal) consistently for all currency parsing.

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
