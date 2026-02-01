# Known Bugs and Issues (Review Pass 4)

This document tracks bugs discovered during a focused review of recent changes. Issues are prioritized by severity.

## Medium Priority Issues

### 1. Payees Screen Loads All Transactions Even Without Selection
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/payees/PayeeManagementViewModel.kt:80-98`
- **Issue:** `transactionsUiState` always subscribes to `getAllTransactionsWithDetails()`, which loads every transaction when the Payees screen opens even if no payee is selected. On large datasets this can cause unnecessary load and UI latency.
- **Fix:** Changed to use `flatMapLatest` on `_selectedPayeeId` - transactions flow only starts when a payee is selected.

## Low Priority / UX

### 2. Payee Row Click No Longer Opens Edit Dialog
- **Status:** Fixed
- **File:** `shared/src/commonMain/kotlin/com/financeapp/ui/payees/PayeeManagementScreen.kt:371-374`
- **Issue:** Clicking a payee row now only selects it for the embedded panel, whereas previously a single click opened the Edit dialog. This is a behavior regression and may reduce discoverability for editing.
- **Fix:** Added `combinedClickable` - single-click selects for panel, double-click opens edit dialog.

---

## Progress Tracking

| Bug # | Description | Status | Fixed In |
|-------|-------------|--------|----------|
| 1 | Payees screen loads all transactions | Fixed | PayeeManagementViewModel.kt |
| 2 | Payee row click no longer opens edit | Fixed | PayeeManagementScreen.kt |
