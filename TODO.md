# Finance App - Development Roadmap

---

# 🔴 Code Review Findings — Open Items

**Original review:** 2026-04-16; reassessed 2026-06-07 and 2026-06-09. The bulk of the
findings (most of R1–R34 and N1–N10) have been fixed and removed from this file — see git
history for the implementations and their regression tests. Only the outstanding items remain
below.

**Stack note:** Despite `CLAUDE.md` naming SQLDelight, this codebase uses **Exposed ORM 0.47 + H2** with **foreign-key enforcement ON** (so delete paths must hand-clean child rows). The shared module targets `jvm("desktop")` only — that's why `java.*`/`String.format` compile in `commonMain` (they would break the KMP contract if an iOS/native target were added).

## P1 — High

- [ ] **R14. ViewModels' manual scopes never cleaned up.** `cleanup()` exists on 13 VMs but is called only from tests. Bounded leak (VMs injected once in `MainContent`); the worse failure mode was removed by the supervised-scope work.

## P2 — Medium

- [ ] **R21. `EditTransactionDialog` caches category object (LOW).** `AddTransactionDialog.kt:422-436` passes `selectedCategory?.id`; a category deleted mid-edit yields a stale id.
- [ ] **R22. PIN minimum length is 8.** `PinSetupScreen.kt:32`.
- [ ] **R27. No R8/ProGuard rules (desktop-only today).**
- [ ] **R29. `App.kt` navigation state is `remember` only (LOW for desktop).** `App.kt:105-107`.

## P3 — Low

- [ ] **R32. Net-worth aggregations must exclude transfer legs (NARROW).** Per-account balances cancel transfer legs between two active accounts; residual risk is transfers to/from inactive accounts (excluded from the active list).

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
