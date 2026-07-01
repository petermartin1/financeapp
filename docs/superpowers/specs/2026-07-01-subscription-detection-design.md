# Subscription Detection — Design

**Date:** 2026-07-01
**Status:** Approved (design), pending implementation plan

## Goal

Give the user awareness of recurring charges ("what am I paying for") by automatically
detecting subscription-like transactions from their existing history and surfacing them on a
dedicated screen with cadence, amount, and next-expected date. This is an awareness feature,
not an alerting or cancellation tool.

## Scope decisions

Settled during brainstorming:

- **Primary goal:** awareness — a browsable list of detected recurring charges.
- **Detection breadth:** include **variable-amount** recurring charges (utilities, phone, usage
  billing), not just fixed-amount subscriptions. Cadence and amount are evaluated independently.
- **State model:** **persist** detected candidates in a new table with user **confirm/dismiss**;
  status is sticky across re-scans.
- **Run trigger:** scan **after each import**, plus a **one-time initial scan** over existing
  history on first launch after the feature ships.
- **Surfacing:** a dedicated **Subscriptions** screen (no dashboard card in v1).
- **Detection algorithm:** interval-clustering per payee (Approach A).

### Explicitly out of scope (future extensions)

- Dashboard summary card.
- Auto-creating `ScheduledTransactions` from confirmed subscriptions (feeds cash-flow forecasting).
- Known-merchant / MCC-SIC list as a confidence booster (the code categorization infra exists;
  deferred to keep one clear detection path in v1).
- Price-increase / new-subscription alerts.

## Architecture

Three layers keep the pure detection logic isolated from persistence and UI:

1. **`SubscriptionDetector`** (domain, pure — no DB, no Compose)
   - Input: a list of transactions. Output: candidate value objects.
   - Home of all detection thresholds. Total function: never throws on odd data; groups that
     don't qualify are simply omitted.

2. **`SubscriptionRepository`** (domain interface + `data` impl)
   - Owns the `DetectedSubscriptions` table.
   - Loads transactions, invokes the detector, runs sticky-status reconciliation by `matchKey`,
     and provides CRUD for confirm/dismiss.
   - Exposed `transaction {}` blocks; exposes a `Flow<List<DetectedSubscription>>` for the screen,
     consistent with the other repositories.

3. **`SubscriptionScanService`** (domain — single orchestration entry point)
   - `scanAfterImport()` — invoked when an import completes, hooked into the existing import
     pipeline where imports already refresh derived data.
   - `runInitialScanIfNeeded()` — checks the `subscriptions.initialScanDone` preference flag; if
     unset, scans full history, persists, commits, **then** sets the flag. Called once after vault
     unlock.
   - Both methods funnel through the same repository scan+reconcile call — one code path.

## Data model

New table `DetectedSubscriptions` (Exposed `IntIdTable`, following `db/schema/Tables.kt`
conventions), plus a `DetectedSubscription` domain model.

Columns:

| Column | Type | Notes |
|---|---|---|
| `payeeId` | FK → `Payees`, nullable | Null when grouped by imported name |
| `matchKey` | String | Stable grouping identity: `payee:<id>` or `name:<normalized importedName>` |
| `cadence` | enum (`TransactionFrequency`) | WEEKLY / BIWEEKLY / MONTHLY / YEARLY (DAILY excluded as noise) |
| `status` | enum | `CANDIDATE` / `CONFIRMED` / `DISMISSED` |
| `medianAmountCents` | Long | |
| `minAmountCents` | Long | |
| `maxAmountCents` | Long | |
| `isVariable` | Boolean | Derived: `(max − min) / median > 0.15`; stored for display/filtering |
| `occurrenceCount` | Int | |
| `firstSeen` | LocalDate | |
| `lastSeen` | LocalDate | |
| `nextExpectedDate` | LocalDate | `lastSeen + cadence` via calendar math |
| `confidence` | Int (0–100) | From cadence regularity + occurrence count |
| `isActive` | Boolean | False when a previously-detected group no longer qualifies (looks cancelled); kept, not deleted |
| `createdAt` / `updatedAt` | Instant | |

### Reconciliation rule (sticky status)

A re-scan matches candidates to existing rows by `matchKey`:

- `CONFIRMED` and `DISMISSED` rows are **sticky** — a re-scan updates their stats (amount,
  `lastSeen`, `nextExpectedDate`, `occurrenceCount`) but never reverts status to `CANDIDATE`.
- New groups appear as `CANDIDATE`.
- A previously-detected group that no longer qualifies is marked `isActive = false` (kept visible
  with a "looks cancelled" badge — serves the awareness goal) rather than deleted.
- Reconciliation runs in a single Exposed transaction so it is atomic.

## Detection algorithm (interval-clustering per payee)

1. **Filter** to outflows only (`amount < 0`); exclude transfers (`transferId != null`) and refunds.
2. **Group** by `matchKey` (`payee:<id>`, falling back to `name:<normalized importedName>` so
   subscriptions from un-mapped payees still group).
3. For each group with **≥ 3 occurrences**: sort by date, collapse duplicate same-day charges,
   compute consecutive day-gaps.
4. **Cadence fit:** take the median gap and match it to the nearest known cadence only if the gaps
   are consistent — most gaps fall within **±25%** of that cadence's day count. Reject erratic
   groups (high gap spread ⇒ not a subscription).
5. **Amount stats:** median / min / max over the group;
   `isVariable = (max − min) / median > 0.15` (≈15% band absorbs sales-tax / FX jitter for
   "fixed" ones).
6. **Confidence:** blend of occurrence count (more cycles ⇒ higher) and cadence tightness (lower
   gap variance ⇒ higher). Used for sort order and display only, not a hard gate beyond the ≥3
   minimum.
7. **Next expected:** `lastSeen + cadence` using the same `LocalDate` calendar APIs and month-anchor
   pattern as `ScheduledPlanner`, so month-end subscriptions don't drift to the 28th.

### Edge cases

- Fewer than 3 occurrences ⇒ ignored.
- A payee charged on two valid cadences (e.g. monthly *and* annually) ⇒ emit the dominant one only
  in v1.
- Duplicate same-day charges ⇒ collapsed before gap analysis.

## One-time initial scan tracking

Tracked with an explicit persisted flag, **not** an emptiness check on the table (empty is
ambiguous — it also means "scanned and found nothing" or "user dismissed everything," which would
cause the scan to re-run every launch and resurrect dismissed rows).

- New `PreferencesRepository` methods over the existing key/value `PreferencesStore`:
  - `isSubscriptionInitialScanDone(): Boolean` (key `subscriptions.initialScanDone`)
  - `markSubscriptionInitialScanDone()`
- **Crash-safe ordering:** persist detected rows and commit **first**, then set the flag. If the app
  dies mid-scan the flag is never set, so it re-runs next launch — harmless because the scan is
  **idempotent** (reconciliation keys on `matchKey`, so a re-run produces the same candidates, not
  duplicates).
- This idempotency also yields a free "Rescan history" action: the initial scan with the flag ignored.

## UI — Subscriptions screen

New nav destination under `ui/subscriptions/` (existing screen + ViewModel pattern).

- **List** sorted by confidence descending (highest-confidence subscriptions first). Each row: payee/name, cadence ("Monthly"), median
  amount, a *variable* indicator + range when `isVariable`, last-charged date, and **next expected
  date**.
- **Status actions:** `CANDIDATE` rows show **Confirm** / **Dismiss**; `CONFIRMED` shown normally;
  `DISMISSED` hidden by default behind a "Show dismissed" toggle. Inactive ("looks cancelled") rows
  get a subtle badge but remain listed.
- **Header summary:** count + estimated monthly total (annual cadences normalized to a monthly
  figure).
- **Empty state** via the existing `EmptyState` component.
- `SubscriptionViewModel` exposes a `Flow` from the repository; confirm/dismiss call through and the
  list updates reactively.

## Error handling

- Detector is pure/total — never throws; non-qualifying groups are omitted.
- Scan failures (import-triggered or initial) are caught and logged, and never crash the import or
  block app launch. The initial-scan flag is set only on success, so a failed initial scan retries
  next launch.
- Next-expected uses the `ScheduledPlanner` calendar/month-anchor pattern to avoid month-end drift.
- FK invariant (persistence rule): deleting a payee must null / hand-clean
  `DetectedSubscriptions.payeeId`; name-keyed rows survive independently.
- Reconciliation runs in a single Exposed transaction (atomic); startup and import scans cannot
  meaningfully corrupt each other.

## Database migration impact

Purely additive. `SchemaUtils.create(...)` in `DatabaseDriverFactory.desktop.kt` only creates
tables that don't yet exist; adding `DetectedSubscriptions` to that list creates one new empty
table. No existing table is altered, no data is rewritten or deleted, and no `ALTER TABLE`
migration block is needed (this adds a table, not a column). Worst case on update is an empty new
table. Existing transactions are read-only inputs.

## Testing (TDD)

**Detector unit tests (bulk, pure/fast):**
- Monthly / weekly / biweekly / yearly happy paths.
- Fixed vs variable classification at the 15% boundary.
- < 3 occurrences ignored.
- Erratic gaps rejected.
- ±25% cadence tolerance edges.
- Duplicate same-day charges collapsed.
- Month-end next-expected anchoring.

**Repository / reconciliation tests:**
- Candidate → confirmed stickiness across a re-scan.
- Dismissed stays dismissed.
- Stats update on new occurrences.
- Cancelled group → `isActive = false`, not deleted.
- Payee-delete cleanup of `payeeId`.

**Service tests:**
- `runInitialScanIfNeeded` runs once and sets the flag only after commit.
- Re-runs after a simulated mid-scan failure without producing duplicates.
- `scanAfterImport` reconciles incrementally.
