# Repository Guidelines

## Project Structure & Module Organization

- `shared/`: Kotlin Multiplatform shared code. Core logic and Compose UI live in `shared/src/commonMain/kotlin`, and the SQLDelight schema lives in `shared/src/commonMain/sqldelight`.
- `desktopApp/`: Desktop application shell and entry point in `desktopApp/src/desktopMain/kotlin`.
- `iosApp/`: Xcode project for the iOS app (not managed by Gradle).
- `shared/src/commonTest` and `shared/src/desktopTest`: Unit tests and platform-specific tests.
- `build/` folders are generated output; do not edit.

## Build, Test, and Development Commands

- `./gradlew build`: Compile all modules and run configured checks.
- `./gradlew :desktopApp:run`: Launch the desktop Compose app.
- `./gradlew :shared:generateCommonMainFinanceDatabaseInterface`: Regenerate SQLDelight database interfaces after schema changes.
- `./gradlew test`: Run all Gradle test tasks.
- `./gradlew :shared:desktopTest`: Run desktop JVM tests only.
- `./gradlew clean`: Remove build outputs.

## Coding Style & Naming Conventions

- Kotlin code follows standard 4-space indentation and idiomatic Kotlin naming (`PascalCase` types, `camelCase` functions/vars).
- Base package is `com.financeapp`; keep new code under this namespace.
- Store monetary values as integer cents (avoid floating point), and store timestamps as Unix epoch milliseconds.
- Use `expect`/`actual` declarations for platform-specific implementations.

## Testing Guidelines

- Common tests use Kotlin Test with `kotlinx-coroutines-test` and Turbine; desktop tests use JUnit.
- Add tests alongside production code: `shared/src/commonTest/kotlin/...` or `shared/src/desktopTest/kotlin/...`.
- Name test files `*Test.kt` and keep test method names descriptive.
- The testing plan targets 80%+ coverage with focus on critical finance flows.

## Commit & Pull Request Guidelines

- Git history is not available in this workspace; use short, imperative commit messages or Conventional Commits (e.g., `feat: add budget summary`).
- PRs should include a summary, testing notes, and screenshots for UI changes.
- Link related issues or roadmap items when applicable.

## Security & Configuration Tips

- Never log secrets or credentials; follow `SECURITY.md` for OFX handling details.
- Keep sensitive data local-only and use existing secure storage utilities where available.
