# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Personal finance app built with Kotlin/Compose Multiplatform. Replaces Quicken functionality with a local-first, privacy-focused design. **Targets desktop (JVM) only** — the `androidApp` shell exists but is currently unused, and the `shared` module declares only the `jvm("desktop")` target.

## Build Commands

```bash
# Build all modules
./gradlew build

# Run desktop app
./gradlew :desktopApp:run

# Run the test suite
./gradlew :shared:desktopTest

# Clean build
./gradlew clean
```

> The schema is defined as Exposed table objects in Kotlin (see below), so there is no
> code-generation step. `androidApp` is a non-functional shell; there is no Android build.

## Architecture

### Project Structure
- **shared/**: shared code (business logic, database, UI)
  - `commonMain/`: code common to all targets (today, effectively desktop)
  - `desktopMain/`: desktop (JVM) `actual` implementations
- **desktopApp/**: Desktop (JVM) application shell
- **androidApp/**: Android application shell — present but unused

### Key Technologies
- **UI**: Compose Multiplatform with Material 3
- **Database**: **Exposed ORM (typed Kotlin DSL) over H2**, an embedded JVM SQL database, with H2's built-in AES encryption. Foreign-key enforcement is ON, so delete paths must hand-clean child rows.
- **Networking**: Ktor Client (for OFX/bank sync, stock quotes)
- **DI**: Koin
- **Serialization**: Kotlinx.serialization

> Note: `expect`/`actual` scaffolding (e.g. `DatabaseDriverFactory`, `EncryptionKeyManager`)
> remains, but only the desktop `actual`s exist. H2 is JVM-only and **cannot** run on
> Android/iOS — moving off desktop would require migrating the persistence layer (e.g. to
> SQLite/SQLDelight). The desktop-only choice is deliberate.

### Database Schema
Defined as Exposed table objects in `shared/src/commonMain/kotlin/com/financeapp/db/schema/Tables.kt`
(table objects are plural, e.g. `Accounts`, `Transactions`, `Categories`).

Core tables:
- `Accounts`: Bank accounts, credit cards, investment accounts
- `Transactions`: Financial transactions
- `Categories`: Transaction categories (income/expense/transfer)
- `Payees`: Who you pay or receive from
- `Budgets`: Monthly budget allocations
- `Holdings`: Investment positions
- `SecurityPrices`: Historical stock prices
- `ScheduledTransactions`: Recurring transactions

Amounts stored as integers (cents) to avoid floating-point issues.

### Platform-Specific Code Pattern
`expect`/`actual` declarations are still used, e.g. the DB driver factory:
```kotlin
// commonMain
expect class DatabaseDriverFactory(encryptionKey: String) {
    fun createDriver(): Database   // Exposed Database
}

// desktopMain — opens an encrypted H2 database
actual class DatabaseDriverFactory(...) { ... }
```

### Data Storage Locations
- **Desktop**: `~/.financeapp/finance.mv.db` (H2, opened with `jdbc:h2:...;CIPHER=AES`)

## Code Conventions

- Package base: `com.financeapp`
- Use integer cents for monetary amounts (`Long`), never `Double`
- Date/time stored as Unix timestamps (milliseconds)
- Queries use Exposed's typed DSL inside `transaction(database) { ... }` blocks

## Future Integrations
- **Bank sync**: Plaid API (requires API keys)
- **Stock quotes**: Yahoo Finance or Alpha Vantage

## Encryption
The H2 database is encrypted at rest with AES (`CIPHER=AES`). The key is generated and held in
the OS key store via `EncryptionKeyManager` (Keychain on macOS, DPAPI on Windows, Secret Service
on Linux); the app refuses to fall back to a plaintext key file. See `SECURITY.md`.
