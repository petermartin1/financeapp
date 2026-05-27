# Finance App - Development Roadmap

---

# 🔴 Code Review Findings (2026-04-16)

Comprehensive review turned up bugs/flaws across security, data integrity, imports, and UI state. Items have been spot-checked against the live code; see the discarded-findings list at the bottom of this section.

**Stack note:** Despite `CLAUDE.md` naming SQLDelight and `Finance.sq` existing, this codebase actually uses **Exposed ORM + H2**. The `.sq` file appears to be dead/legacy — findings were re-verified against `db/schema/Tables.kt` and `data/repository/*Impl.kt`.

## P0 — Critical (data integrity / money / auth bypass)

- [ ] **R1. `HoldingWithPrice.marketValue` silently returns `0L` when price is null.**
  `shared/src/commonMain/kotlin/com/financeapp/domain/model/Investment.kt:41-42`
  Portfolio shows $0 any time the quote feed is missing a symbol. Change `marketValue` (and downstream `gainLoss`, `totalMarketValue`, etc.) to `Long?` and render "—" when unknown.

- [ ] **R2. Brute-force lockout bypass — `failedAttempts` is in-memory only.**
  `shared/src/commonMain/kotlin/com/financeapp/data/repository/AppLockRepositoryImpl.kt:17`
  Force-closing the app resets the counter; attacker gets unlimited 5-try rounds. Persist the counter *and* a `lockedUntil` timestamp via `PreferencesStore`.

- [ ] **R3. No time-based lockout.**
  `shared/src/commonMain/kotlin/com/financeapp/ui/lock/PinUnlockScreen.kt:109`
  Button is disabled but there's no cool-down. Combined with R2 this is a complete bypass. Add exponential backoff (e.g., 30s → 5min → 1h).

- [ ] **R4. Transfer deletion orphans the counterpart transaction.**
  `shared/src/commonMain/kotlin/com/financeapp/data/repository/TransactionRepositoryImpl.kt:241-265`
  Deleting one leg clears the other's `transferId` but leaves the amount. The orphaned row now shows as a standalone income/expense and corrupts reports. Either delete both legs or prompt the user; never unlink silently.

- [ ] **R5. Split items not validated to sum to parent amount.**
  `shared/src/commonMain/kotlin/com/financeapp/data/repository/TagRepositoryImpl.kt` — `setSplitsForTransaction`
  Delete-all-then-reinsert with no atomicity guard and no `Σsplits == parent.amount` check. Wrap in a single `transaction {}` and validate.

- [ ] **R6. PIN/password held in mutable `String` in lock UI.**
  `shared/src/commonMain/kotlin/com/financeapp/ui/lock/PinUnlockScreen.kt:31, 74`
  `shared/src/commonMain/kotlin/com/financeapp/ui/lock/PinPad.kt:24, 41, 61, 86`
  Assigning `""` doesn't zero the previous `String` on the heap. Back the UI state with a `CharArray`-wrapped `SecureString` so the secret is zeroable on submit/dispose.

- [ ] **R7. Linux fallback writes encryption keys as plaintext Base64 to disk.**
  `shared/src/desktopMain/kotlin/com/financeapp/security/EncryptionKeyManager.desktop.kt:199-203`
  `shared/src/desktopMain/kotlin/com/financeapp/security/SecureCredentialStore.desktop.kt` (credkey path)
  If `secret-tool` is unavailable, the AES-256 DB key goes to `~/.financeapp/.dbkey` as plaintext (file-mode 600). Any filesystem-read attacker decrypts the DB. Require Secret Service, or prompt user for a master password and wrap the key with PBKDF2-derived KEK.

- [ ] **R8. Import `fitId` uses `hashCode()` and a per-file sequence → collisions + false duplicates across imports.**
  `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/QifParser.kt:19, 126`
  `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/CsvParser.kt:54`
  32-bit `hashCode()` is collision-prone; per-file counter means the same transaction in two QIF files gets the same ID. Replace with a stable SHA-256 (or 64-bit FNV) over `(date, amount, payee, memo)`.

## P1 — High (correctness issues that matter at scale)

- [ ] **R9. CSV parser: unclosed quote concatenates rest of file into one field.**
  `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/CsvParser.kt:75-92`
  On EOF with `inQuotes=true`, surface a parse error and abort rather than committing garbage rows.

- [ ] **R10. AmountParser treats European decimal format as US.**
  `shared/src/commonMain/kotlin/com/financeapp/data/fileimport/AmountParser.kt:28-34`
  `"1.234,56"` becomes `1.23456` → parsed as 123¢ instead of 123 456¢. Detect locale heuristically or expose a per-import locale setting.

- [ ] **R11. Reinvested dividends don't update holding cost basis / shares.**
  `data/repository/PerformanceRepositoryImpl.kt` — `recordDividend` (verify exact lines)
  DRIP logs the event but `Holding.shares` and `cost_basis` stay flat. Within the same `transaction {}`, add the reinvestment to the holding.

- [ ] **R12. Performance chart uses previous snapshot value as gain base instead of cost basis.**
  `PerformanceRepositoryImpl.kt` — `getHoldingChartData` (verify exact lines)
  Chart shows compound delta, not true gain-vs-cost. Always compute `snapshot.value - snapshot.costBasis`.

- [ ] **R13. `addTransaction` submits `$0` when amount parse fails.**
  `shared/src/commonMain/kotlin/com/financeapp/ui/transactions/AddTransactionDialog.kt:~329`
  `parseDecimalToCents(amountText) ?: 0L`. Disable submit when parser returns null; show inline validation error.

- [ ] **R14. ViewModels manually create `CoroutineScope(Dispatchers.Main)` with no Koin lifecycle hook.**
  ~16 ViewModels across `ui/**/*ViewModel.kt`. `cleanup()` exists in several but is never invoked. Flow collectors leak for app lifetime. Migrate to `viewModelScope` (KMP-ViewModel lib), or register a Koin `onClose` callback.

- [ ] **R15. Race: multiple `loadData()` calls run concurrent collectors.**
  e.g., `TemplatesViewModel:44-68`, `DashboardViewModel:57-103`, `ImportViewModel` init.
  Cancel the prior `Job` before launching a new collect, or adopt a single `SharingStarted.WhileSubscribed` flow.

- [ ] **R16. Exception swallowing in `addTransaction` / `addTransfer` / dashboard config.**
  `TransactionsViewModel.kt:174-215, 287-311`, `DashboardViewModel.kt:~65` (`catch (e: Exception) { DashboardConfig() }`), `CurrencyText.kt:269`. Route to a shared error bus; never silently swallow.

- [ ] **R17. Hardcoded `$` currency prefix everywhere.**
  `ui/components/CurrencyText.kt:220` and callers. `Account.currency` is stored but ignored for display. Use a formatter keyed on the account's currency code; fall back to USD.

- [ ] **R18. Weekly snapshot day-of-week indexing looks error-prone.**
  `domain/service/SnapshotScheduler.kt:~190` — `DayOfWeek.entries[dayOfWeek - 1]` lacks bounds check.

## P2 — Medium (UX, maintainability, robustness)

- [ ] **R19. Legacy PIN hash (pre-v2) uses unsalted SHA-256 with non-constant-time compare.**
  `AppLockRepositoryImpl.kt:37, 113-116`. Affects only the migration path but tighten to `MessageDigest.isEqual`.
- [ ] **R20. Windows DPAPI migration returns plaintext key if DPAPI store fails.**
  `EncryptionKeyManager.desktop.kt:141-146`. Return `null` and surface an error.
- [ ] **R21. `EditTransactionDialog` caches category/payee objects that may be deleted under it.**
  `AddTransactionDialog.kt:431-436`. Re-resolve by ID on save.
- [ ] **R22. PIN minimum length is 8; OWASP recommends 12+ for financial apps.**
  `PinSetupScreen.kt:32`.
- [ ] **R23. Search has no debounce.** `TransactionsViewModel.kt:104-106`.
- [ ] **R24. Bulk reconcile / bulk category update not atomic.**
  `ReconcileViewModel.kt:108-113`. Wrap in one `transaction {}`.
- [ ] **R25. Date-range reports hardcode 2000-01-01 start for "ALL TIME".** `ReportsViewModel.kt:~86`.
- [ ] **R26. `DatabaseSeeder` runs from `AppViewModel.init {}`.** Move to app-start bootstrap.
- [ ] **R27. No R8/ProGuard rules for Koin + Kotlinx Serialization + Exposed.**
- [ ] **R28. No global error boundary / crash reporter.**
- [ ] **R29. `App.kt` navigation state is `remember { mutableStateOf }` only.** Rotation/process-death drops the stack. Use `rememberSaveable`.
- [ ] **R30. Backup format has no schema-version header.** Add a version field.

## P3 — Low (cleanups / hardening)

- [ ] **R31. Dead code:** `shared/src/commonMain/sqldelight/com/financeapp/db/Finance.sq` appears unused; Exposed is live. Remove or document.
- [ ] **R32. Net-worth aggregations must exclude transfer legs.** Audit call sites of `getAllAccountBalances`, etc.
- [ ] **R33. Services create their own scopes with `shutdown()` never called.** `PriceRefreshService`, `SnapshotScheduler`.
- [ ] **R34. Koin singletons for transient ViewModels.** Review `single` vs `factory` in `di/Modules.kt`.

## Agent findings discarded after verification

- **AmountParser rounding formula:** `(first3 + 5) / 10` correctly half-ups 3+ decimal-place inputs. Verified against test cases.
- **H2 `CIPHER=AES` defaults to ECB:** False — H2 uses AES-CBC with a per-page IV.
- **SQLDelight `INSERT OR REPLACE` on Holding breaks FKs:** Not applicable; project uses Exposed; `InvestmentRepositoryImpl.insertHolding:116-128` uses plain `insert`.
- **Date range off-by-one at month boundary:** `TransactionRepositoryImpl.getTransactionsByDateRange:120` uses `endDate.plus(1, DAY).atStartOfDayIn(tz) - 1` which handles the boundary + DST correctly.
- **`selectAccountBalance` double-counts transfers:** Per-account query is correct. See R32 for the aggregation-side risk.

## Suggested execution order

1. P0 auth (R2, R3, R6) — one focused PR
2. P0 data integrity (R1, R4, R5) — one PR, with regression tests
3. P0 key storage (R7) — Linux/Windows hardening
4. P0 import stability (R8) — before any bulk import runs
5. P1 batch — CSV quoting, DRIP, chart base, ViewModel scopes
6. P2/P3 — cleanup backlog

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
