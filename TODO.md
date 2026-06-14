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

- [ ] **N4. Scheduled-transaction catch-up can double-post on failure.**
  `ScheduledViewModel.enterDueTransactions:107-153` inserts each missed occurrence in its own DB transaction and advances `nextDate` only after the loop; a crash mid-catch-up re-posts already-entered occurrences (scheduled txns have no `importId`/dedup). Advance `nextDate` per occurrence, or wrap the whole catch-up atomically. (Also: due txns post only via a manual "Enter Due" button — no background poster.)
- [ ] **N5. `SnapshotScheduler` is never started.** Registered as a Koin `single` with `createSnapshot()` on a manual button, but `startDaily/Weekly/MonthlySnapshots` are never called → automatic performance history never accrues. Start at bootstrap (and `shutdown()` — R33).
- [x] **N6. Non-supervisor `CoroutineScope(Dispatchers.Main)` in 15 ViewModels. — FIXED.** Added `supervisedViewModelScope()` (`SupervisorJob()` + `CoroutineExceptionHandler`) and swapped all 18 VM scopes to it, so a failure in one `launch {}` no longer cancels the scope/`stateIn` collector, and uncaught exceptions are routed to the new shared `AppErrorBus` (surfaced as a Snackbar in `App.kt`). Test: `CoroutineScopesTest` (scope survives a failing child; error reported; scope still usable). Partially addresses R16/R28 — see those entries.
- [ ] **N7. `String.format` uses the default locale → wrong separators.** `CurrencyText.kt:186,217` + ~10 duplicate formatters (`InvestmentScreen`, `LotComponents`, `ImportScreen`, …). Non-US locales produce mixed separators. Compounds R17.
- [ ] **N8. Reconcile marks `isReconciled` but not `isCleared`.** `ReconcileViewModel.completeReconciliation` → `markTransactionReconciled` sets only `isReconciled`, so `getClearedBalance` (sums `isCleared`) excludes reconciled rows. Reconciled should imply cleared.
- [ ] **N9. `importWithMappings` isn't atomic across steps.** Payees, aliases, transactions, tags are each committed separately (`ImportRepository:133-276`); a mid-way failure leaves orphaned payees/aliases or untagged transactions.
- [ ] **N10. Search re-queries the DB on every keystroke.** `TransactionsViewModel:64-97` feeds `_filter` into the `combine` upstream of `flatMapLatest`, so each character cancels + re-subscribes the full-account query. No debounce (sharper R23).

## P0 — Critical (original)

- [x] **R1. `HoldingWithPrice.marketValue` returns `0L` when price is null. — FIXED.**
  `marketValue`/`gainLoss`/`gainLossPercent` are now `Long?`/`Double?` (null when price unknown); `InvestmentScreen` renders "—" and `InvestmentViewModel` totals/allocation cover priced holdings only. Test: `InvestmentTest`. (Residual `?: 0L` in the Performance tab now FIXED too — see R11/R12 entries: `PerformanceRepositoryImpl` excludes unpriced holdings from `getAllHoldingPerformance`/`getPerformanceSummary` and returns null from `getHoldingPerformance`.)
- [x] **R2. Brute-force lockout bypass — `failedAttempts` in-memory only. — FIXED.** `AppLockRepositoryImpl` now persists the failure counter and `lockedUntil` in `PreferencesStore`, so a restart can't reset the lockout. `verifyPin` enforces the lockout server-side (refuses even the correct pin while locked). Tests: `AppLockRepositoryTest` (persist-across-instances / lockout-across-restart cases).
- [x] **R3. No time-based lockout. — FIXED.** After 5 failures `AppLockRepositoryImpl` applies exponential backoff (30s, doubling, capped at 15min); `PinUnlockScreen` shows a live countdown and disables input while locked, re-enabling on expiry. Tests: `AppLockRepositoryTest` (expiry / growth cases).
- [x] **R4. Transfer deletion orphans the counterpart. — FIXED.** `TransactionRepositoryImpl.deleteTransaction:241-268` now deletes *both* legs (plus tags/splits).
- [ ] **R5. Split items not validated to sum to parent amount. — PARTIAL.** `TagRepositoryImpl.setSplitsForTransaction` is now wrapped in one `transaction {}`, but still no `Σsplits == parent.amount` validation (no caller checks it either).
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
- [~] **R16. Exception swallowing / no error surfacing. — PARTIAL.** Shared error channel added (`AppErrorBus`) and wired to a Snackbar; uncaught VM-scope exceptions now surface there (N6). Remaining: per-operation `try/catch` for specific, actionable messages (the global handler only relays `throwable.message`).
- [ ] **R17. Hardcoded `$` currency prefix. — OPEN.** `CurrencyText.formatCurrency:220` ignores `Account.currency`; ~10 duplicate formatters. See N7.
- [ ] **R18. Weekly snapshot day-of-week indexing. — OPEN (LOW).** `SnapshotScheduler.kt:190` — only called with controlled `1..7`, so it won't actually throw; add a guard.

## P2 — Medium (original)

- [x] **R19. Legacy PIN hash compare. — FIXED.** `AppLockRepositoryImpl.matchesStoredPin` now compares the legacy unsalted SHA-256 hash with `MessageDigest.isEqual` (constant-time) instead of `==`, then upgrades to the salted hash. Folded into the R2/R3 rewrite.
- [x] **R20. Windows DPAPI migration returns plaintext key if store fails. — FIXED.** The `migratePlaintextKeyToDpapi` path that returned the plaintext key on DPAPI-store failure is gone; `getFromDpapi` now returns null on any decrypt failure and the shared `keyFromKeystore` helper throws `KeyStorageException` if the DPAPI store write fails instead of returning/leaving a plaintext key. Folded into the R7 rewrite.
- [ ] **R21. `EditTransactionDialog` caches category object. — OPEN (LOW).** `AddTransactionDialog.kt:422-436` passes `selectedCategory?.id`; a category deleted mid-edit yields a stale id.
- [ ] **R22. PIN minimum length is 8. — OPEN.** `PinSetupScreen.kt:32`.
- [ ] **R23. Search has no debounce. — OPEN.** See N10.
- [ ] **R24. Bulk reconcile not atomic. — OPEN.** `ReconcileViewModel.completeReconciliation:103-125` loops per-txn marks + the record. (See also N8.)
- [ ] **R25. Reports hardcode 2000-01-01 for "ALL TIME". — OPEN.** `ReportsViewModel.kt:86`.
- [ ] **R26. `DatabaseSeeder` runs from `AppViewModel.init {}`. — OPEN.** `AppViewModel.kt:42,48-52`.
- [ ] **R27. No R8/ProGuard rules. — OPEN (desktop-only today).**
- [~] **R28. No global error boundary / crash reporter. — PARTIAL.** Global error boundary now exists: VM scopes route uncaught exceptions to `AppErrorBus` → Snackbar (N6/R16). Remaining: a persistent crash reporter/log sink.
- [ ] **R29. `App.kt` navigation state is `remember` only. — OPEN (LOW for desktop).** `App.kt:105-107`.
- [ ] **R30. Backup format has no schema-version header. — OPEN.**

## P3 — Low (cleanups / hardening)

- [ ] **R31. Dead code:** `Finance.sq` unused; also `PinPad.kt` has no callers now. Remove or document.
- [ ] **R32. Net-worth aggregations must exclude transfer legs. — OPEN (NARROW).** Per-account balances cancel transfer legs between two active accounts; residual risk is transfers to/from inactive accounts (excluded from the active list).
- [ ] **R33. Services' scopes never `shutdown()`. — OPEN.** `PriceRefreshService` (started, never stopped); `SnapshotScheduler` (never started — N5).
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
6. P1 robustness: N6 ✅ (supervisor scopes + error bus, done 2026-06-14). N4 (scheduled dedup), N5 (start scheduler) still open.
7. P2/P3 — cleanup backlog (R17/N7 currency, R23/N10 search, …).

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
