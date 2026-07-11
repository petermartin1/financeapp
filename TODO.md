# Finance App - Development Roadmap

---

# 🟢 Code Review Findings — All Closed

**Original review:** 2026-04-16; reassessed 2026-06-07/06-09; closed out 2026-06-19. Every
R1–R34 and N1–N10 finding is now fixed (with regression tests, see git history) or documented
won't-fix/by-design. **No open findings remain.** The final dispositions are kept below for the
record.

**Stack note:** Despite `CLAUDE.md` naming SQLDelight, this codebase uses **Exposed ORM 0.47 + H2** with **foreign-key enforcement ON** (so delete paths must hand-clean child rows). The shared module targets `jvm("desktop")` only — that's why `java.*`/`String.format` compile in `commonMain` (they would break the KMP contract if an iOS/native target were added).

## Resolved / won't-fix (2026-06-19)

- **R32. By design (decided 2026-06-19).** Net worth sums balances of *active* accounts only (`getAccountsWithBalances` → `getAllAccounts` filters `isActive`). A transfer between an active and an inactive account counts only the active leg. This is intended: net worth reflects accounts you currently hold, so a closed/inactive account's balance is correctly excluded. Keeping as-is.
- **R21. FIXED.** `updateTransaction` now drops a `categoryId` that no longer references a live category, so a category deleted while the Edit dialog is open can't cause an FK violation that loses the edit — the transaction just becomes uncategorized (`TransactionRepositoryImpl`, regression test in `TransactionRepositoryTest`). `EditTransactionDialog` also clears a stale selection when the category list updates.
- **R14. Won't-fix.** VMs are Koin singletons (see R34) — created once, alive for the app's lifetime; `cleanup()` only matters if instances are recreated, which never happens. Calling it would cancel a live singleton's scope. Not a leak. Desktop shutdown already stops the long-lived services (R33).
- **R22. Won't-fix.** An 8-character minimum password is a reasonable policy and far stronger than the old 4-digit PIN. No change warranted.
- **R27. Won't-fix.** The desktop JVM packaging has no R8/ProGuard step. Revisit if/when an Android build ships.
- **R29. Won't-fix.** Desktop is a single long-lived window with no Android-style process death or config-change recreation, so `remember`-based nav state is correct. Revisit for Android.

## Agent findings discarded after verification (still discarded)

- **AmountParser rounding `(first3 + 5) / 10`:** correct half-up. Verified.
- **H2 `CIPHER=AES` ECB:** false.
- **Holding `INSERT OR REPLACE` FK break:** N/A — Exposed `insert` used.
- **Date-range off-by-one:** `getTransactionsByDateRange:120` handles boundary + DST.
- **`selectAccountBalance` double-counts transfers:** per-account query is correct (see R32).

---

# Original Roadmap

## In Progress

### Polish & Enhancements
- [ ] Add dashboard mini-charts for widgets
- [ ] Enhance BudgetScreen with better chart visualizations
- [ ] Savings goals: goal linked to a *closed* (inactive) account shows "(deleted account)" in the editor and "Needs an account — edit to relink" on the card even though it's still linked (GoalsScreen.kt; from 2026-07-10 final review) — reword and decide closed-account UX deliberately
- [ ] Savings goals: past-deadline goals label the full remaining amount as "need $X/mo" — remainder is correct, "/mo" suffix is wrong (GoalsScreen.kt; from 2026-07-10 final review)
- [ ] Spending drill-down: panel header "N transactions" counts split lines, not distinct transactions — reword or count distinct txn ids (ReportsScreen.kt; from 2026-07-11 final review)
- [ ] Spending drill-down: add onSliceClick to PieChart's pointerInput keys (or rememberUpdatedState) — latent stale-closure risk when a reload keeps identical slice values (PieChart.kt; from 2026-07-11 final review)
- [ ] Spending drill-down: category list truncates to top 10 while the pie draws all slices — thin-slice categories ranked >10 have no row-click fallback and invisible selection highlight (ReportsScreen.kt; pre-existing truncation, noted 2026-07-11)
- [ ] Spending drill-down tests: cover null-payee/null-category join branch and id-DESC tie-breaker for getTransactionsWithDetailsByDateRange (test-only gap; from 2026-07-11 final review)

## Planned Features

### High Priority

#### Data Export
- [ ] CSV export for transactions
- [ ] CSV export for reports
- [ ] QIF format export
- [ ] PDF export for reports

#### Desktop Features
- [ ] Drag & drop OFX files to import (deferred - experimental APIs unstable)
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
