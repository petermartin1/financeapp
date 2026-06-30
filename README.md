# FinanceApp

**A local-first, encrypted desktop personal-finance app — a privacy-focused alternative to Quicken.**

Your financial data lives in an encrypted file on *your* machine. No cloud account, no telemetry, no subscription. Import your bank statements, categorize automatically, budget, and track investments — entirely offline.

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)
![Platform: Desktop (JVM)](https://img.shields.io/badge/platform-desktop%20(Windows%20%7C%20macOS%20%7C%20Linux)-lightgrey)
![Built with: Kotlin + Compose](https://img.shields.io/badge/built%20with-Kotlin%20%2B%20Compose-7F52FF)

<!--
  Add a screenshot or short GIF here — it is the single highest-leverage thing for this README.
  Drop the file in docs/screenshots/ and uncomment:

  ![FinanceApp dashboard](docs/screenshots/dashboard.png)
-->
> 📸 **Screenshots coming soon.** (A GIF of an OFX import auto-categorizing transactions goes a long way — drop it in `docs/screenshots/`.)

---

## Why FinanceApp?

Quicken moved to a cloud subscription. Mint shut down. Most modern alternatives (Monarch, Copilot) are ~$100/year and keep your data on their servers. FinanceApp is for people who want a capable, modern finance app **without giving up ownership of their data**.

- 🔒 **Local-first & encrypted.** The database is an AES-encrypted file sealed by *your* master password (envelope encryption with Argon2id). Nothing leaves your machine.
- 🧠 **Learns your categories.** A per-user classifier predicts categories on import and gets better as you correct it — with bundled cold-start knowledge so even your first import is useful.
- 🏦 **Real import support.** OFX / QFX / QIF / CSV, with preview and column mapping, plus OFX Direct Connect for supported banks.
- 📈 **Investments included.** Portfolio overview, cost basis, gain/loss, and asset allocation — not just budgeting.
- 💸 **Free and open source** (AGPL-3.0).

## How it compares

| | **FinanceApp** | Quicken | Monarch / Copilot | Actual Budget | GnuCash |
|---|:---:|:---:|:---:|:---:|:---:|
| Data stays local | ✅ | ❌ (cloud) | ❌ (cloud) | ✅ (self-host) | ✅ |
| Encrypted at rest | ✅ (AES + master password) | — | — | partial | ❌ |
| Price | Free / OSS | Subscription | ~$100/yr | Free / OSS | Free / OSS |
| Modern UI | ✅ (Compose, dark mode) | ✅ | ✅ | ✅ | ❌ (dated) |
| Investments / cost basis | ✅ | ✅ | partial | ❌ | ✅ |
| Auto category prediction | ✅ (learns per-user) | rules | ✅ | rules | ❌ |
| Bank statement import | ✅ OFX/QFX/QIF/CSV | ✅ | ✅ | ✅ | ✅ |
| No account required | ✅ | ❌ | ❌ | ✅ | ✅ |

> FinanceApp targets the desktop and uses **manual/statement import** rather than paid bank-aggregation APIs — that is the deliberate trade for keeping everything local and free.

## Features

- **Accounts** — checking, savings, credit cards, and investment accounts
- **Transactions** — search/filter, splits, tags, bulk operations, transaction templates, transfers (linked auto-matching entries)
- **Categories** — hierarchical income/expense/transfer categories with per-user **auto-categorization on import**
- **Payees** — auto-categorization rules, duplicate merging, per-payee spending history
- **Budgets** — monthly allocations with spending tracking and progress
- **Investments** — portfolio overview, cost basis, gain/loss, asset allocation, stock quotes
- **Reports & charts** — spending by category, income vs. expenses, net worth over time
- **Import** — OFX / QFX / QIF / CSV with preview & column mapping; OFX Direct Connect
- **Reconciliation**, **scheduled/recurring transactions**, **backup & export** (CSV/OFX)
- **Customizable dashboard**, **dark mode**, **keyboard shortcuts**

See [`ROADMAP.md`](ROADMAP.md) for what's planned (goals, loan/mortgage tracking, multi-currency, tax reporting).

## Security & privacy

The H2 database is encrypted at rest with AES and **sealed by your master password** using envelope encryption: a random data-encryption key is wrapped with AES-256-GCM under an Argon2id-derived key (plus an optional recovery key). The vault lives at `~/.financeapp/vault.json`; the master password is required on every launch, and the database is only opened after unlock. There is no cloud sync and no telemetry.

Full design, threat model, and limitations: [`SECURITY.md`](SECURITY.md).

## Tech stack

- **Language/UI:** Kotlin, [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) (Material 3)
- **Persistence:** [Exposed](https://github.com/JetBrains/Exposed) typed DSL over an embedded, AES-encrypted [H2](https://h2database.com) database
- **DI:** Koin · **Networking:** Ktor Client · **Serialization:** kotlinx.serialization
- **Target:** Desktop / JVM only (the local-first design depends on H2, which is JVM-only)

## Getting started

Requires a JDK (17+).

```bash
# Run the desktop app
./gradlew :desktopApp:run

# Build everything
./gradlew build

# Run the test suite (505 tests)
./gradlew :shared:desktopTest
```

> Packaging a native distributable on a non-standard JDK vendor (e.g. Homebrew) may trip Compose's runtime check; pass `-Pcompose.desktop.packaging.checkJdkVendor=false` to `createDistributable` if so.

Your data is stored at `~/.financeapp/finance.mv.db` (encrypted).

## Contributing

Issues and pull requests are welcome. The codebase follows a strict test-first discipline — please include tests with behavior changes, and keep monetary amounts as integer cents. See [`CLAUDE.md`](CLAUDE.md) for architecture and conventions.

## License

Licensed under the [GNU AGPL-3.0](LICENSE). You are free to use, study, modify, and share it; if you run a modified version as a network service, you must make your source available under the same terms.
