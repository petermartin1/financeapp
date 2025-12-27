# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Personal finance app built with Kotlin Multiplatform (KMP) and Compose Multiplatform. Replaces Quicken functionality with local-first, privacy-focused design.

## Build Commands

```bash
# Build all modules
./gradlew build

# Run desktop app
./gradlew :desktopApp:run

# Build Android APK
./gradlew :androidApp:assembleDebug

# Generate SQLDelight code
./gradlew :shared:generateCommonMainFinanceDatabaseInterface

# Clean build
./gradlew clean
```

## Architecture

### Project Structure
- **shared/**: KMP shared code (business logic, database, UI)
  - `commonMain/`: Cross-platform code
  - `androidMain/`, `iosMain/`, `desktopMain/`: Platform-specific implementations
- **androidApp/**: Android application shell
- **desktopApp/**: Desktop (JVM) application shell
- **iosApp/**: iOS application shell (Xcode project)

### Key Technologies
- **UI**: Compose Multiplatform with Material 3
- **Database**: SQLDelight with SQLite (type-safe queries)
- **Networking**: Ktor Client (for Plaid API, stock quotes)
- **DI**: Koin
- **Serialization**: Kotlinx.serialization

### Database Schema
Located in `shared/src/commonMain/sqldelight/com/financeapp/db/Finance.sq`

Core tables:
- `Account`: Bank accounts, credit cards, investment accounts
- `TransactionRecord`: Financial transactions
- `Category`: Transaction categories (income/expense/transfer)
- `Payee`: Who you pay or receive from
- `Budget`: Monthly budget allocations
- `Holding`: Investment positions
- `SecurityPrice`: Historical stock prices
- `ScheduledTransaction`: Recurring transactions

Amounts stored as integers (cents) to avoid floating-point issues.

### Platform-Specific Code Pattern
Use `expect`/`actual` declarations:
```kotlin
// commonMain
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

// androidMain, iosMain, desktopMain
actual class DatabaseDriverFactory { ... }
```

### Data Storage Locations
- **Android**: App internal storage
- **iOS**: App sandbox
- **Desktop**: `~/.financeapp/finance.db`

## Code Conventions

- Package base: `com.financeapp`
- Use `Integer` for monetary amounts (cents), never `Double`
- Date/time stored as Unix timestamps (milliseconds)
- SQLDelight generates `FinanceDatabase` class in `com.financeapp.db`

## Future Integrations
- **Bank sync**: Plaid API (requires API keys)
- **Stock quotes**: Yahoo Finance or Alpha Vantage
- **Encryption**: SQLCipher for database encryption (not yet implemented)
