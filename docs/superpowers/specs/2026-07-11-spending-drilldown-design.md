# Spending Report Drill-Down — Design

**Date:** 2026-07-11
**Status:** Approved pending user review
**Feature:** Clicking a pie slice (or category row) on the Spending by Category report shows the matching transactions across all accounts in a panel below the chart; clicking a transaction opens the existing edit dialog, and edits refresh the report in place.

## Decisions (agreed with user)

1. **Drill-down location:** inline panel below the chart on the existing Reports screen, scoped to the currently selected period. Not a dialog, not navigation to the Transactions screen.
2. **Rows are actionable:** clicking a transaction row opens the existing `EditTransactionDialog` (same reuse pattern as `GlobalSearchDialog`). After a save, the report reloads and the selection is preserved.
3. **Data source (Option A):** the drill-down reuses the split-aware spending lines the report is already built from. The pie aggregation is **derived from the same filtered detail lines the panel displays**, so the list always sums exactly to the slice. No second query path, no drift.
4. **Click targets (Option A):** both the pie slices (canvas hit-testing) and the category list rows below the chart are clickable. The legend stays non-interactive (YAGNI).

## Current state (what exists)

- `ReportsScreen` / `ReportsViewModel`: Spending by Category report with period chips; builds `SpendingReport(categorySpending, totalSpent)` via `expandSpendingLines` (split-aware, transfer-excluding), filtering to negative amounts whose category type is `EXPENSE` or null; uncategorized lines are shown as "Uncategorized" with sentinel `categoryId = 0L`.
- `PieChart` / `AnimatedPieChart` (`ui/components/charts/PieChart.kt`): Canvas-drawn slices; doc comment already promises "Click interactions (future enhancement)" but none exist.
- `SpendingLine(categoryId, amount)` (`domain/reporting`): loses transaction identity — insufficient for drill-down display.
- `TransactionWithDetails(transaction, payeeName, categoryName, accountName, …)` exists; `TransactionRepository` has `getAllTransactionsWithDetails()` and `getTransactionsWithDetailsByAccount(...)` but **no date-range variant**.
- `GlobalSearchDialog` shows the established edit-reuse wiring: `EditTransactionDialog(onSave = { categoryId, memo, date, isCleared, tagIds -> ... })`.

## Design

### 1. Domain: detail-line expansion (`domain/reporting`)

New model + function next to `expandSpendingLines`:

```kotlin
/** One spending line with its source transaction, for drill-down display. */
data class SpendingDetailLine(
    val source: TransactionWithDetails,
    val categoryId: Long?,        // split's category when a split portion
    val lineAmountCents: Long,    // split amount or full txn amount (sign preserved)
    val isSplitPortion: Boolean   // true when this line is one split of a larger txn
)

fun expandSpendingDetailLines(
    transactions: List<TransactionWithDetails>,
    splitsByTransactionId: Map<Long, List<SplitItem>>
): List<SpendingDetailLine>
```

Semantics identical to `expandSpendingLines`: transfers excluded, split parents contribute one line per split, unsplit transactions contribute one line. A unit test asserts the `(categoryId, amount)` projection of `expandSpendingDetailLines` equals `expandSpendingLines` for the same input, pinning the two against drift. `expandSpendingLines` remains untouched for its other callers (dashboard, budgets).

### 2. Repository: date-range details

Add to `TransactionRepository` (+ impl), mirroring the existing details-query pair:

```kotlin
fun getTransactionsWithDetailsByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<TransactionWithDetails>>
```

Same joins as `getAllTransactionsWithDetails`, plus the date bounds used by `getTransactionsByDateRange`.

### 3. ViewModel: report built from detail lines

`loadSpendingReport` refactor:

1. Fetch `TransactionWithDetails` for the period (new repo method) + splits + categories.
2. `expandSpendingDetailLines(...)` → filter once: `lineAmountCents < 0` AND category type `EXPENSE` or null (same rule as today).
3. Aggregate `CategorySpending` **from the filtered detail lines** (grouped by `categoryId ?: 0L`, summing `abs(lineAmountCents)`), preserving today's sort and percentage math.
4. Store the same filtered lines grouped for drill-down.

State additions to `ReportsUiState` / `SpendingReport`:

```kotlin
// SpendingReport gains:
val detailLinesByCategory: Map<Long, List<SpendingDetailLine>>  // key = categoryId ?: 0L (0L = Uncategorized)

// ReportsUiState gains:
val selectedSpendingCategoryId: Long? = null  // null = no selection; 0L = Uncategorized slice
```

`0L` is a safe sentinel: `IntIdTable` ids start at 1; it is the same convention `CategorySpending.categoryId` already uses.

New VM API:

- `selectSpendingCategory(categoryId: Long?)` — toggles: selecting the already-selected id clears it.
- `setPeriod` / `setReportType` clear the selection.
- After an edit-triggered reload, keep the selection if the category still exists in the new report; clear it otherwise (e.g. its last transaction was recategorized away).
- `onTransactionEdited()` (or reuse of `loadReport()`) — called by the screen after `EditTransactionDialog` saves; reloads the report with selection-preservation as above.

Panel rows are sorted by transaction date, newest first.

### 4. Chart: clickable slices

`PieChart` gains two optional parameters (default = today's behavior; `AnimatedPieChart`/`DonutChart` pass them through):

```kotlin
onSliceClick: ((index: Int) -> Unit)? = null,
selectedIndex: Int? = null
```

- **Hit-testing:** a `pointerInput` tap handler on the chart Box. Extract the math into a pure, unit-testable function in the charts package:
  `fun pieSliceAt(tap: Offset, canvasSize: Size, values: List<Float>, centerHoleRatio: Float): Int?` — returns null for taps outside the pie radius or inside the donut hole; otherwise maps the tap angle (normalized to the −90° start used in drawing) through the cumulative sweeps.
- **Selection affordance:** the selected slice is drawn emphasized (slightly increased radius or a `surface`-colored stroke separation — implementer's choice, consistent in light/dark); non-selected slices are unchanged. When `selectedIndex` is null all slices render as today.

### 5. Screen: panel + clickable rows

In `SpendingByCategoryReport` (`ReportsScreen.kt`):

- Pie gets `onSliceClick = { index -> viewModel.selectSpendingCategory(report.categorySpending[index].categoryId) }` and `selectedIndex` derived from the selection. (Slice order == `categorySpending` order — already true, both come from the same sorted list.)
- `CategorySpendingItem` rows become clickable with the same toggle callback and a selected-state background highlight.
- When a selection exists, between the chart card and the category list insert:
  - a **header row**: category name, `formatCurrency(sliceTotal)`, "N transactions", and a clear (✕) button;
  - **transaction rows** (`LazyColumn` `items`): date, payee name (fallback `importedName`, then "—"), account name, memo (single line, ellipsized, only if present), right-aligned `CurrencyText` of `abs(lineAmountCents)`; split portions additionally show "of ${formatCurrency(abs(source.transaction.amount))} split" as supporting text so the list visibly sums to the slice.
- Clicking a row opens `EditTransactionDialog` for `source.transaction`, wired exactly like `GlobalSearchDialog` (same `onSave` shape, saved via the same repository call), then the screen calls the VM's reload. The Uncategorized slice works identically — that is the primary fix-up workflow.
- Empty-selection edge (shouldn't occur since slices exist only when lines exist, but defensively): panel shows the existing `EmptyReportMessage` style text.

### 6. Error handling

- Report loading keeps the current pattern (failures leave `isLoading = false`; existing `catch` in `loadReport`).
- Edit-dialog save failures surface through the same path `GlobalSearchDialog` uses (`AppErrorBus` via repository/VM wrappers) — no new mechanism.
- Hit-testing returns null on any ambiguous tap (outside radius, in hole, zero-total data) — no crash paths.

## Edge cases (pinned)

| Case | Behavior |
|---|---|
| Split transaction, one split matches slice | Row shows the split's amount + "of $X split"; parent's other splits do not appear |
| Uncategorized slice (sentinel 0L) | Fully clickable; rows are the uncategorized outflows; editing one to a category moves it out on reload |
| Refunds/positive lines in an expense category | Excluded from both pie and panel (`< 0` filter), consistent with today |
| Transfers | Excluded everywhere (unchanged) |
| Period/report-type change | Selection cleared |
| Edit removes last line of selected category | Selection cleared on reload |
| Tap in donut hole / outside pie | Ignored |
| Slice too thin to tap | Category list row is the equivalent click target |

## Testing

- **Domain:** `expandSpendingDetailLines` — unsplit, split, transfer-exclusion, and the projection-equivalence test against `expandSpendingLines`.
- **Chart geometry:** `pieSliceAt` — center-of-slice hits, boundary angles, donut hole, outside radius, first/last slice wraparound at the −90° origin.
- **Repository:** `getTransactionsWithDetailsByDateRange` — bounds inclusive, joins populate payee/account names.
- **ViewModel:** slice totals equal the sum of that category's panel lines (the invariant the whole design protects); selection toggle; clear-on-period-change; selection preserved vs cleared across reload; Uncategorized drill-down.
- **UI:** no Compose UI tests (consistent with project convention); manual verification via `./gradlew build` + running the app.

## Out of scope

- Clickable legend entries.
- Drill-down for the Income vs Expenses and Net Worth report types.
- Filtering the panel by account, exporting the list, pagination (personal-scale data; the period already bounds it).
- Multi-select or comparing two categories side by side.