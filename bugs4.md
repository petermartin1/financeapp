# Known Bugs and Issues (Review Pass 4)

This document tracks bugs discovered during a focused review of recent changes. Issues are prioritized by severity.

## Medium Priority Issues

### 1. Payees Screen Loads All Transactions Even Without Selection
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/payees/PayeeManagementViewModel.kt:78-96`
- **Issue:** `transactionsUiState` always subscribes to `getAllTransactionsWithDetails()`, which loads every transaction when the Payees screen opens even if no payee is selected. On large datasets this can cause unnecessary load and UI latency.
- **Fix:** Gate the transactions flow behind a selected payee (e.g., `flatMapLatest` on `_selectedPayeeId`) so it only fetches when a selection exists.

## Low Priority / UX

### 2. Payee Row Click No Longer Opens Edit Dialog
- **Status:** Open
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/payees/PayeeManagementScreen.kt:369`
- **Issue:** Clicking a payee row now only selects it for the embedded panel, whereas previously a single click opened the Edit dialog. This is a behavior regression and may reduce discoverability for editing.
- **Fix:** Restore click-to-edit or add a clear interaction (e.g., double-click to edit or a dedicated edit affordance).
