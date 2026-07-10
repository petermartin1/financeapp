# Savings Goals — Design

Date: 2026-07-09
Status: Approved

## Purpose

Add savings goals (roadmap #1): a named target amount, optionally with a deadline,
linked to an account, with visual progress and pacing feedback. Closes the
headline-feature gap vs. Mint/YNAB/Quicken noted in FUTURE_IDEAS.md #2.

## Decisions (settled with user)

1. **Progress model**: linked-account balance. A goal's progress is the current
   balance of its linked account. No contribution ledger, no envelopes.
2. **Baseline**: the full account balance counts. No baseline snapshot at goal
   creation; a pre-funded account starts the goal partially (or fully) complete.
3. **Linking**: exactly one account per goal (nullable only after account
   deletion — see FK handling). Multiple goals may exist, each on its own
   account; the UI does not prevent two goals sharing an account, but the math
   then double-counts and that is the user's choice.
4. **Pacing**: optional deadline. When present, show "need $X/month" and an
   on-track/behind indicator from straight-line pacing between goal creation
   and deadline. No trailing-inflow projection (possible v2).
5. **Account deletion**: unlink, don't delete. Deleting a linked account sets
   the goal's `accountId` to null; the goal shows a "needs an account" state
   and can be relinked.

## Architecture

Follows the Subscriptions feature pattern: schema table → domain model →
repository interface (`domain/repository`) + impl (`data/repository`) → Koin
wiring → ViewModel + screen (`ui/goals`) → navigation entry.

Progress is **never stored**. The repository combines the goals table flow with
`AccountRepository`'s existing reactive accounts-with-balances flow, so
progress updates live as transactions change. Pace math lives in a pure,
dependency-free calculator in `domain/`.

## Data model

New Exposed table in `Tables.kt`:

```kotlin
object SavingsGoals : IntIdTable("SavingsGoal") {
    val name = varchar("name", 100)
    val targetAmountCents = long("targetAmountCents")   // must be > 0
    val accountId = reference("accountId", Accounts).nullable()
    val deadlineMs = long("deadlineMs").nullable()      // Unix ms
    val createdAtMs = long("createdAtMs")               // Unix ms
    val archived = bool("archived").default(false)
}
```

- No stored progress or status; "completed" is derived (balance ≥ target).
- `accountId` nullable solely to support unlink-on-account-delete.
- Amounts are `Long` cents throughout (project convention).

## Domain

- `SavingsGoal` domain model mirroring the table.
- `GoalProgress` value type: `currentCents`, `percent` (0–100 clamped),
  `remainingCents` (≥ 0), `neededPerMonthCents: Long?`,
  `onTrack: Boolean?` (both null when no deadline).
- `GoalWithProgress` pairing the goal, its progress, and the linked account's
  display name (null when unlinked).
- `GoalProgressCalculator` (pure object/class, no dependencies):
  - `percent = clamp(balance / target, 0, 1)`; negative balance → 0%.
  - `remaining = max(target - balance, 0)`.
  - With deadline: `neededPerMonth = remaining / monthsLeft`, calendar-aware
    month count, minimum 1 month; **past deadline** with remaining > 0 →
    `onTrack = false` and `neededPerMonth = remaining` (full remainder).
  - On-track: actual balance ≥ straight-line expected value on the line from
    (`createdAtMs`, 0) to (`deadlineMs`, `target`), evaluated at now.
    (Consistent with full-balance counting: pre-funded accounts start ahead.)
  - Over-funded → 100%, remaining 0, onTrack true.
  - Unlinked goal (no account) → progress = 0%, no pacing, flagged unlinked.

## Repository

`GoalRepository` interface in `domain/repository`; `GoalRepositoryImpl` in
`data/repository`:

- `goals(): Flow<List<GoalWithProgress>>` — combines a goal-change trigger flow
  with `AccountRepository`'s accounts-with-balances flow; runs the calculator
  per goal. Archived goals included with an `archived` flag (UI filters).
- `create(name, targetCents, accountId, deadlineMs?)`,
  `update(...)`, `setArchived(id, Boolean)`, `delete(id)`.
- All mutations inside `transaction(database) { }` per convention.
- **FK hand-clean**: `AccountRepositoryImpl.delete` gains a step that nulls
  `SavingsGoals.accountId` for goals referencing the deleted account (FK
  enforcement is ON; this must run in the same transaction as the account
  delete, before the account row is removed).
- Koin: register `GoalRepositoryImpl` in the existing DI module.

## UI

New `ui/goals` package, wired into navigation the same way the Subscriptions
screen was (nav item + route + screen).

- **GoalsScreen**: list of goal cards, "Add goal" action, toggle to show
  archived. Empty state when no goals.
- **Goal card**: name, linked-account name, progress bar, "$saved of $target",
  remaining; with deadline: deadline date, "need $X/mo", on-track/behind chip.
  Completed goals show a completed treatment (100% bar, check). Unlinked goals
  show a "needs an account" state with a relink affordance.
- **Add/edit dialog**: name (required, non-blank), target amount (required,
  > 0, entered as dollars, stored as cents), account picker (savings-type
  accounts listed first, any account allowed), optional deadline date.
  Editing allows changing all fields, including relinking the account.
- **Archive / delete**: archive hides from the default list; delete removes
  the row after a confirm dialog. `SavingsGoals` has no child tables, so no
  further FK cleanup is needed below it.

## Error handling

- Validation in the dialog: blank name, non-positive target, malformed amount,
  or no account selected → inline errors, save disabled. An account is
  required at creation; only account deletion produces an unlinked goal.
- Deadline in the past at creation is allowed (user may track an overdue
  goal); pacing immediately reports behind.
- Repository guards: `delete`/`setArchived`/`update` on a missing id is a
  no-op returning false (matches existing repo conventions).

## Testing (TDD)

1. `GoalProgressCalculatorTest` — pure unit tests first: no deadline,
   behind/ahead pacing, past deadline, over-funded, zero/negative balance,
   month-count edges (deadline < 1 month away, deadline on month boundary),
   unlinked goal.
2. `GoalRepositoryTest` — CRUD round-trip; reactive recomputation (add a
   transaction to the linked account → progress flow emits updated value);
   account delete unlinks the goal (and account delete still succeeds with FK
   enforcement on); archived filtering data.
3. `GoalsViewModelTest` — display states: no deadline, behind, complete,
   unlinked, archived toggle; dialog validation.

## Out of scope (possible follow-ups)

- Dashboard tile summarizing goals.
- Contribution-rate projection (trailing 3-month inflow) for projected
  completion date.
- Envelope-style partitioning of one account across goals.
