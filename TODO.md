# Finance App - Development Roadmap

---

# 🔴 Code Review Findings

**Original review:** 2026-04-16. **Reassessed against live code + deeper audit:** 2026-06-07.
**Second deep audit:** 2026-06-09 (re-verified N4–N10 and open R items against source; all confirmed.
New issues from that pass are **N11–N26**; R13 downgraded to PARTIAL — see entry).
Each original item (R1–R34) is verified against the *current* source and tagged
**FIXED / PARTIAL / OPEN**. New issues from the 2026-06-07 audit are **N1–N10**.

**Stack note:** Despite `CLAUDE.md` naming SQLDelight and `Finance.sq` existing, this codebase uses **Exposed ORM 0.47 + H2** with **foreign-key enforcement ON** (so delete paths must hand-clean child rows). The shared module targets `jvm("desktop")` only — that's why `java.*`/`String.format` compile in `commonMain` (they would break the KMP contract if an iOS/native target were added). `Finance.sq` is dead/legacy.

## ✅ Fixed in the 2026-06-07 session (with regression tests)

- [x] **N1. `deleteAccount` FK violation on investment accounts.**
  `AccountRepositoryImpl.deleteAccount` now removes `HoldingSnapshots` and `DividendEvents` for the account's holdings before deleting `Holdings`. Test: `AccountRepositoryTest` (dividend events / holding snapshots cases).
- [x] **N2. `deleteCategory` FK violation via `PayeeAlias.preferredCategoryId`.**
  `CategoryRepositoryImpl.deleteCategory` now nullifies `PayeeAliases.preferredCategoryId`. Test: `CategoryRepositoryTest`.
- [x] **N3. Snapshots persisted a fabricated `$0` for missing prices.**
  `PerformanceRepositoryImpl.createPortfolioSnapshot` now skips holdings with no known price instead of writing `marketValue = 0`. Test: `PerformanceRepositoryTest`.

## 🆕 New findings — still OPEN

- [x] **N4. Scheduled-transaction catch-up can double-post on failure. — FIXED.**
  Each caught-up occurrence now gets a deterministic `importId` (`SCHEDULED_<id>_<dateMillis>`); `ScheduledViewModel.enterDueTransactions` plans the catch-up (extracted to the pure `computeDueEntries`) and skips occurrences whose id is already present (`getExistingImportIds`), so a crashed/partial catch-up re-runs without double-posting. Test: `ScheduledEntryPlannerTest` (catch-up, idempotent re-run, end-date, nothing-due). (Background poster — still just the manual "Enter Due" button — remains a separate enhancement.)
- [x] **N5. `SnapshotScheduler` is never started. — FIXED.** `AppViewModel` now injects the scheduler and calls `startDailySnapshots()` at bootstrap (alongside the price-refresh service), so daily performance history accrues automatically. (Its `shutdown()` is still unwired — tracked under R33.)
- [x] **N6. Non-supervisor `CoroutineScope(Dispatchers.Main)` in 15 ViewModels. — FIXED.** Added `supervisedViewModelScope()` (`SupervisorJob()` + `CoroutineExceptionHandler`) and swapped all 18 VM scopes to it, so a failure in one `launch {}` no longer cancels the scope/`stateIn` collector, and uncaught exceptions are routed to the new shared `AppErrorBus` (surfaced as a Snackbar in `App.kt`). Test: `CoroutineScopesTest` (scope survives a failing child; error reported; scope still usable). Partially addresses R16/R28 — see those entries.
- [x] **N7. `String.format` uses the default locale → wrong separators. — FIXED.** Every `String.format` in `ui/` now passes `Locale.ROOT` (consistent `.`/`,` separators regardless of JVM locale); the percentage formatter is extracted to the testable `formatPercent`. Test: `CurrencyTextTest` (formats correctly under `Locale.GERMANY`).
- [x] **N8. Reconcile marks `isReconciled` but not `isCleared`. — FIXED.** `TransactionRepositoryImpl.markTransactionReconciled` now also sets `isCleared = true` when reconciling (un-reconciling leaves the cleared flag alone), so `getClearedBalance` includes reconciled rows. Test: `TransactionRepositoryTest` (reconciling marks cleared + cleared balance includes it).
- [ ] **N9. `importWithMappings` isn't atomic across steps.** Payees, aliases, transactions, tags are each committed separately (`ImportRepository:133-276`); a mid-way failure leaves orphaned payees/aliases or untagged transactions.
- [x] **N10. Search re-queries the DB on every keystroke. — FIXED.** `TransactionsViewModel` now queries transactions by account only (via `transactionsForAccount`/`flatMapLatest`), and the filter is applied in a downstream `combine` — so search/filter changes no longer cancel + re-subscribe the DB query. Verified by compile + review (VM-flow timing tests need infra not present).

## P0 — Critical (original)

- [x] **R1. `HoldingWithPrice.marketValue` returns `0L` when price is null. — FIXED.**
  `marketValue`/`gainLoss`/`gainLossPercent` are now `Long?`/`Double?` (null when price unknown); `InvestmentScreen` renders "—" and `InvestmentViewModel` totals/allocation cover priced holdings only. Test: `InvestmentTest`. (Residual `?: 0L` in the Performance tab now FIXED too — see R11/R12 entries: `PerformanceRepositoryImpl` excludes unpriced holdings from `getAllHoldingPerformance`/`getPerformanceSummary` and returns null from `getHoldingPerformance`.)
- [x] **R2. Brute-force lockout bypass — `failedAttempts` in-memory only. — FIXED.** `AppLockRepositoryImpl` now persists the failure counter and `lockedUntil` in `PreferencesStore`, so a restart can't reset the lockout. `verifyPin` enforces the lockout server-side (refuses even the correct pin while locked). Tests: `AppLockRepositoryTest` (persist-across-instances / lockout-across-restart cases).
- [x] **R3. No time-based lockout. — FIXED.** After 5 failures `AppLockRepositoryImpl` applies exponential backoff (30s, doubling, capped at 15min); `PinUnlockScreen` shows a live countdown and disables input while locked, re-enabling on expiry. Tests: `AppLockRepositoryTest` (expiry / growth cases).
- [x] **R4. Transfer deletion orphans the counterpart. — FIXED.** `TransactionRepositoryImpl.deleteTransaction:241-268` now deletes *both* legs (plus tags/splits).
- [x] **R5. Split items not validated to sum to parent amount. — FIXED.** `TagRepositoryImpl.setSplitsForTransaction` now looks up the parent transaction's amount and `require`s that the split amounts sum to it (an empty list still clears splits); throws `IllegalArgumentException` otherwise, inside the existing `transaction {}` so nothing partial persists. Test: `TagRepositoryTest` (accepts matching, rejects mismatched, clears on empty).
- [ ] **R6. PIN/password in mutable `String` in lock UI. — OPEN.** `PinUnlockScreen.kt:31,74` (`PinPad.kt` is now dead code). Back with a zeroable `SecureString`.
- [x] **R7. Linux fallback writes encryption keys as plaintext Base64. — FIXED (refuse-without-keystore policy).** `EncryptionKeyManager.desktop.kt` no longer has a plaintext file fallback: it resolves the DB key from Keychain/DPAPI/Secret Service, migrates a legacy plaintext key file into the key store (preserving existing encrypted DBs) then secure-deletes it, and throws `KeyStorageException` when no key store is available. `SecureCredentialStore.desktop.kt` master key gets the same treatment (throws; callers catch and fail gracefully). Platform shell-out paths remain untested (no CI keystore); logic verified by compile + review.
- [x] **R8. Import `fitId` uses `hashCode()` + per-file index → false dups across overlapping imports. — FIXED.** Both parsers now build `RawImported` records and assign a stable fitId via `ImportFitId` (FNV-1a hash over `date|amount|name|memo`) plus a per-file *occurrence* index for genuine in-file duplicates. The id no longer depends on row position, so the same transaction dedups across overlapping imports. Tests: `CsvParserTest`/`QifParserTest` (stable-across-position, distinct-in-file-duplicates, independent-of-unrelated-rows). (Note: id format changed, so transactions imported before this fix carry old-format `importId`s; a one-time re-import could re-add them.)

## P1 — High (original)

- [x] **R9. CSV parser quoting. — FIXED.** `CsvParser.parseCsvRows` now tokenizes the whole document into rows of fields, so quoted fields may contain commas, escaped quotes (`""`), and embedded newlines (RFC 4180), instead of splitting on physical lines first. Test: `CsvParserTest` (multi-line quoted field, embedded comma).
- [x] **R10. AmountParser treats European decimals as US. — FIXED.** `AmountParser.normalizeSeparators` resolves `.`/`,` as decimal vs thousands (right-most separator is the decimal when both are present; a lone comma grouping 3 digits is US thousands, otherwise a European decimal comma) and strips currency symbols/spaces generically. `"1.234,56"` → 123456¢. Tests: `AmountParserTest` (US + European cases).
- [x] **R11. Reinvested dividends don't update holding shares/cost basis. — FIXED.** `PerformanceRepositoryImpl.recordDividend` now, for a reinvested dividend with a known price, buys `amount / price` shares: it adds those shares + the dividend amount to the holding's cost basis (and appends a matching `HoldingLot` when the position is lot-tracked, so lot recalculation can't drop the reinvested shares). No price → records the event only, no fabricated shares. Tests: `PerformanceRepositoryTest` (reinvest / cash / no-price / lot-consistency cases).
- [x] **R12. Holding chart uses previous snapshot value as gain base, not cost basis. — FIXED.** `PerformanceRepositoryImpl.getHoldingChartData` now measures each point's gain/loss as `value - costBasis`. Test: `PerformanceRepositoryTest`.
- [x] **R13. `addTransaction` submits `$0` on parse failure. — FIXED.** `AddTransactionDialog.kt:355` `enabled` guard requires `toDoubleOrNull() != null`, making the `?: 0L` fallback unreachable.
- [ ] **R14. ViewModels' manual scopes never cleaned up. — OPEN.** `cleanup()` exists on 13 VMs but is called only from tests. Bounded leak (VMs injected once in `MainContent`); see N6 for the worse failure mode.
- [x] **R15. Concurrent `loadData()` collectors. — FIXED (where it mattered).** `TransactionsViewModel` uses `flatMapLatest`; `DashboardViewModel:75` cancels the prior `observeJob`.
- [x] **R16. Exception swallowing / no error surfacing. — FIXED.** Beyond the `AppErrorBus`→Snackbar channel (N6), there's now a `launchReporting("save the transaction") { … }` helper that surfaces a specific message ("Couldn't save the transaction. …") on failure; applied to the money-critical mutations (`TransactionsViewModel` add/edit/delete/clear/transfer, `ReconcileViewModel.completeReconciliation`). Other VMs can adopt it incrementally; the generic supervised handler still catches everything else. Test: `CoroutineScopesTest`.
- [~] **R17. Hardcoded `$` currency prefix. — PARTIAL.** Locale-correct separators are now fixed everywhere (N7), but `formatCurrency` still hardcodes the `$` symbol and ignores `Account.currency`. Remaining: thread the account's currency symbol through the `CurrencyText` call sites.
- [x] **R18. Weekly snapshot day-of-week indexing. — FIXED.** `SnapshotScheduler` now `coerceIn(1, 7)`s the day-of-week before indexing `DayOfWeek.entries`.

## P2 — Medium (original)

- [x] **R19. Legacy PIN hash compare. — FIXED.** `AppLockRepositoryImpl.matchesStoredPin` now compares the legacy unsalted SHA-256 hash with `MessageDigest.isEqual` (constant-time) instead of `==`, then upgrades to the salted hash. Folded into the R2/R3 rewrite.
- [x] **R20. Windows DPAPI migration returns plaintext key if store fails. — FIXED.** The `migratePlaintextKeyToDpapi` path that returned the plaintext key on DPAPI-store failure is gone; `getFromDpapi` now returns null on any decrypt failure and the shared `keyFromKeystore` helper throws `KeyStorageException` if the DPAPI store write fails instead of returning/leaving a plaintext key. Folded into the R7 rewrite.
- [ ] **R21. `EditTransactionDialog` caches category object. — OPEN (LOW).** `AddTransactionDialog.kt:422-436` passes `selectedCategory?.id`; a category deleted mid-edit yields a stale id.
- [ ] **R22. PIN minimum length is 8. — OPEN.** `PinSetupScreen.kt:32`.
- [x] **R23. Search has no debounce. — FIXED.** `TransactionsViewModel` debounces the filter flow (`SEARCH_DEBOUNCE_MS = 200ms`) so rapid typing doesn't refilter on every keystroke. See N10 for the re-query fix.
- [x] **R24. Bulk reconcile not atomic. — FIXED.** New `AccountRepository.completeReconciliation` marks all selected transactions reconciled+cleared and inserts the session record in one `transaction {}`; `ReconcileViewModel` calls it instead of looping per-txn marks + a separate insert. Test: `AccountRepositoryTest` (marks reconciled/cleared + records session + balances).
- [x] **R25. Reports hardcode 2000-01-01 for "ALL TIME". — FIXED.** `ReportsViewModel.calculateDateRange` now spans `1970-01-01`..`now + 100y` for `ALL_TIME`, so older imports and future-dated/scheduled entries are no longer clipped.
- [~] **R26. `DatabaseSeeder` runs from `AppViewModel.init {}`. — WON'T FIX (not a bug).** `DatabaseSeeder.seedIfEmpty` only inserts *default categories* (no fake accounts/transactions) and only when none exist — desirable first-run onboarding. The remaining point is purely where it's triggered; moving it yields no behavioral benefit. Left as-is by design.
- [ ] **R27. No R8/ProGuard rules. — OPEN (desktop-only today).**
- [x] **R28. No global error boundary / crash reporter. — FIXED.** VM scopes route uncaught exceptions to `AppErrorBus` → Snackbar (N6/R16), and `AppErrorBus` now also writes every reported error (with stack trace) to a persistent `CrashLog` sink (`~/.financeapp/logs/error.log`; injectable for tests). Test: `CoroutineScopesTest`.
- [ ] **R29. `App.kt` navigation state is `remember` only. — OPEN (LOW for desktop).** `App.kt:105-107`.
- [x] **R30. Backup format has no schema-version header. — N/A (not applicable).** There is no proprietary backup/restore format: `ExportRepository` only exports to standard **CSV/OFX** (round-tripped back through the file-import parsers). A schema-version header would break those interchange formats, so a version header doesn't apply. If a full versioned DB backup is added later, give *that* a header.

## P3 — Low (cleanups / hardening)

- [x] **R31. Dead code. — FIXED.** Removed `Finance.sq` (SQLDelight is not in the build — the app uses Exposed) and `PinPad.kt` (no callers; the lock UI uses `PinUnlockScreen`/`PinSetupScreen`). Build verified green afterwards.
- [ ] **R32. Net-worth aggregations must exclude transfer legs. — OPEN (NARROW).** Per-account balances cancel transfer legs between two active accounts; residual risk is transfers to/from inactive accounts (excluded from the active list).
- [x] **R33. Services' scopes never `shutdown()`. — FIXED.** The desktop `Main.kt` `onCloseRequest` now retrieves `PriceRefreshService` and `SnapshotScheduler` from Koin and calls `shutdown()` before `exitApplication()`.
- [ ] **R34. Koin `single` vs `factory` for ViewModels. — OPEN.** `di/Modules.kt`.

## Agent findings discarded after verification (still discarded)

- **AmountParser rounding `(first3 + 5) / 10`:** correct half-up. Verified.
- **H2 `CIPHER=AES` ECB:** false.
- **Holding `INSERT OR REPLACE` FK break:** N/A — Exposed `insert` used.
- **Date-range off-by-one:** `getTransactionsByDateRange:120` handles boundary + DST.
- **`selectAccountBalance` double-counts transfers:** per-account query is correct (see R32).

## Suggested execution order

1. ✅ P0 data integrity N1–N3 (done, 2026-06-07).
2. ✅ P0 money/correctness: R11 (DRIP), R12 (chart base), and the residual `?: 0L` in `PerformanceRepositoryImpl` (performance-tab valuation) — done, 2026-06-14, with tests.
3. P0 auth: R2, R3 ✅ (done, 2026-06-14 — persist lockout + exponential backoff + R19 constant-time legacy compare). R6 (SecureString) still open.
4. ✅ P0 key storage: R7, R20 — done, 2026-06-14 (refuse-without-keystore policy; no plaintext key fallback; legacy keys migrated into the OS key store).
5. ✅ P0 import stability: R8, R9, R10 — done, 2026-06-14, with tests.
6. ✅ P1 robustness: N6 (supervisor scopes + error bus), N4 (scheduled dedup), N5 (start scheduler) — done 2026-06-14.
7. P2/P3 — cleanup backlog: N7 ✅ (locale separators), R23/N10 ✅ (search debounce + no re-query) — done 2026-06-14. R17 partial (currency symbol), plus R5/R18/R21/R22/R24/R25/R26/R29–R34 remaining.

---

# Original Roadmap

## Current Status

✅ **Core Application Complete** - All essential features implemented and working
- Database with encryption
- Full transaction management
- Bank sync (OFX Direct Connect + file import)
- Investment tracking with performance metrics
- Budgeting system
- Reports and charts
- Professional UI with animations
- Security hardening complete

## In Progress

### Polish & Enhancements
- [ ] Add dashboard mini-charts for widgets
- [ ] Enhance BudgetScreen with better chart visualizations

## Planned Features

### High Priority

#### Data Export
- [ ] CSV export for transactions
- [ ] CSV export for reports
- [ ] QIF format export
- [ ] PDF export for reports

#### Desktop Features
- [ ] Drag & drop OFX files to import (deferred - experimental APIs unstable)
- [x] Context menus (right-click)
- [x] Enhanced tooltips (HoverCard component)
- [ ] Drag transactions between accounts

### Medium Priority

#### Investment Enhancements
- [ ] Dividend tracking and history
- [ ] Tax lot management (FIFO/LIFO)
- [ ] Asset allocation analysis
- [ ] Portfolio rebalancing suggestions
- [ ] Import brokerage statements

#### Advanced Reports
- [ ] Net worth over time chart
- [ ] Cash flow analysis
- [ ] Year-over-year comparisons
- [ ] Customizable date ranges for all reports
- [ ] Tax reports (capital gains, income summary)

#### Search & Filtering
- [ ] Natural language search ("groceries last month")
- [ ] Advanced search builder UI
- [ ] Search history and saved searches

### Low Priority

#### Multi-Device Sync
- [ ] Local network sync between devices
- [ ] Syncthing integration
- [ ] Conflict resolution strategy
- [ ] Sync status indicators

#### Plaid Integration (Modern Bank Sync)
- [ ] Plaid API integration
- [ ] OAuth flow for bank connections
- [ ] Replace/supplement OFX Direct Connect
- [ ] Automatic transaction categorization

#### Additional Polish
- [ ] Illustrations for empty states
- [ ] Systematic spacing/color/typography audit of older screens
- [ ] Icon caching optimization
- [ ] Enhanced error messages with recovery suggestions
- [ ] Welcome/onboarding flow for first-time users

## Optional Security Enhancements
*Note: Database is already encrypted with AES-256, these are additional layers*

- [ ] App lock with PIN/password on launch
- [ ] Biometric authentication (Touch ID/Face ID)
- [ ] Field-level encryption for account numbers
- [ ] Session timeout/auto-lock

## Testing & Quality
*Currently relying on manual testing and type safety*

- [ ] Unit tests for ViewModels
- [ ] Unit tests for Repositories
- [ ] Integration tests for database operations
- [ ] UI tests for critical flows (add transaction, import, sync)
- [ ] Performance benchmarks
- [ ] Memory leak detection

## Documentation
*Current documentation: SECURITY.md, CLAUDE.md, inline code comments*

- [ ] User guide / help documentation
- [ ] Video tutorials for key features
- [ ] Developer setup guide
- [ ] API documentation for repositories
- [ ] Architecture decision records (ADRs)

## Platform Expansion
*Currently desktop-only (JVM), Android shell exists but unused*

- [ ] Android app implementation (reuse shared code)
- [ ] Mobile-specific UI patterns
- [ ] Platform-specific features (biometrics, notifications)

---

## Completed Phases

All core phases (1-10) are complete:
- ✅ Core Infrastructure (Database, DI, Repositories)
- ✅ Basic UI (All screens implemented)
- ✅ Core Features (Transactions, Categories, Payees, Reconciliation)
- ✅ Security (Encryption, Secure credential storage, Rate limiting)
- ✅ Bank Integration (OFX Direct Connect, File import)
- ✅ Investments (Holdings, Performance tracking, Price refresh) 
- ✅ Budgeting (Budget setup, tracking, visualization)
- ✅ Advanced Features (Dark mode, Backup/restore, Custom reports)
- ✅ Professional UI Overhaul (Design system, Charts, Animations, Navigation)
- ✅ App Icons & Branding (Logo, About dialog)
- ✅ Bulk Transaction Operations (Multi-select with checkboxes, Bulk categorize/tag/delete, Keyboard shortcuts: Ctrl+A, Delete, Escape)

See git history for detailed implementation notes and bug fixes.
