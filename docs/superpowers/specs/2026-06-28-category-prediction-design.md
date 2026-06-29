# Transaction Category Prediction — Design

_Date: 2026-06-28_

## Goal

Suggest a probable category for imported transactions, including **before the user
has created any payees or accumulated any history** (cold start). Predictions are
shown pre-filled in the import flow; they auto-apply only when confident, otherwise
appear as editable suggestions with a short human-readable reason. As the user
categorizes transactions, a per-user model learns and takes over from the bundled
cold-start knowledge.

This delivers "option 1" (a per-user category classifier) plus a cold-start layer so
it is useful on the very first import.

## Scope

- **In scope:** category suggestion during the **import flow** (OFX/QFX, QIF, CSV),
  the per-user learned model, and bundled offline cold-start knowledge.
- **Out of scope (future):** bulk "auto-categorize my existing register"; embedding /
  LLM-based prediction; predicting payees (only categories here).

## Non-negotiables

- **Fully offline / private.** No network, no bundled model binary. The learned
  "model" is derived from the user's own data; cold-start data is static CSV resources.
- **Never breaks an import.** Any predictor failure yields "no suggestion" and the
  import proceeds exactly as it does today.
- **Pure Kotlin, no new dependencies**, in `commonMain`.

## Architecture

A layered predictor. Per imported transaction the cascade is:

1. **Exact learned match** — existing payee/alias → category memory. *Unchanged
   existing code in `ImportViewModel`; not part of the new component.*
2. **Per-user model** — Complement Naive Bayes trained on the user's categorized
   transactions.
3. **SIC lexicon** — bundled OFX SIC-code → category.
4. **Keyword lexicon** — bundled merchant-keyword → category.
5. **Amount-sign default** — Income vs Expense·Miscellaneous (last resort).

The new component owns layers 2–5 and is invoked **only when layer 1 yields nothing**
(the "before I ever create payees" case). First signal at/above its floor wins.

### Components (all `commonMain`, pure Kotlin)

| Unit | Responsibility | Depends on |
|---|---|---|
| `CategoryPredictor` | Orchestrates the signal cascade; applies leaf→parent fallback and confidence gating; returns `CategoryPrediction?`. | `CategorySignal`s, `CategoryNameResolver`, thresholds |
| `CategorySignal` (interface) | `predict(input, ctx): SignalResult?` → `(categoryName, confidence, reason)`. | — |
| `UserModelSignal` | Wraps the trained model as a signal. | `TransactionCategoryModel` |
| `SicLexiconSignal` | SIC code → category name. | `ColdStartKnowledge` |
| `KeywordLexiconSignal` | Normalized-name keyword → category name. | `ColdStartKnowledge` |
| `AmountSignDefaultSignal` | Income/Expense fallback. | — |
| `TransactionCategoryModel` | `scores(features): Map<categoryId, Double>` (pseudo-probabilities). | — |
| `CategoryModelTrainer` | Builds a `TransactionCategoryModel` from categorized transactions. | feature extraction |
| `CategoryModelStore` | **In-memory** cache of the trained model; invalidated on data change. (No DB table.) | trainer |
| `ColdStartKnowledge` | Loads the two CSV resources; exposes lookups by SIC and keyword → category name. | resources |
| `CategoryNameResolver` | Canonical category name → user `categoryId`; leaf → parent. | categories |
| `FeatureExtractor` | Transaction → feature tokens (shared by trainer and model). | name normalization |

### Data types

```
PredictionInput(merchantName: String, sic: String?, amountCents: Long, accountType: ImportedAccountType?)
SignalResult(categoryName: String, confidence: Double, reason: String)
CategoryPrediction(categoryId: Long, confidence: Double, source: PredictionSource, matchedAtLeaf: Boolean, reason: String)
PredictionSource = LEARNED_MODEL | SIC | KEYWORD | AMOUNT_SIGN
```

## Data flow (import)

1. `ImportViewModel` resolves alias/payee category as today (layer 1).
2. If null, it builds a `PredictionInput` from the `ImportedTransaction` and calls
   `CategoryPredictor.predict`.
3. Cascade runs (model → SIC → keyword → amount-sign). If the winning leaf is below
   the leaf threshold, fall back to that leaf's parent.
4. Fill the `PayeeMapping` category:
   - confidence ≥ auto-apply threshold → applied automatically;
   - otherwise pre-filled but unconfirmed (editable), reason string shown.
5. After an import completes — and whenever transactions are (re)categorized — the
   cached model is invalidated so the next prediction retrains lazily.

## Classifier

- **Complement Naive Bayes** (robust under class imbalance), trained on the user's
  transactions that have a `categoryId`. Labels are leaf category IDs.
- **Features** from `FeatureExtractor`:
  - normalized merchant name → word tokens **+ character 3-grams**; digits / store
    numbers collapsed to a placeholder token;
  - `sic=<code>` token when present;
  - `sign=debit|credit` token.
- **Confidence** = normalized top-class score (softmax over class log-scores).
- **Training cost** is milliseconds over a few thousand local rows; runs off the UI
  thread. Cached in memory (`CategoryModelStore`); **no table, no migration**.

## Cold-start data (bundled resources)

Two CSVs map to canonical category **names** (matched to the user's category IDs at
runtime via `CategoryNameResolver`; unresolved names are skipped):

- `sic_categories.csv` — `sicCode,categoryName` (e.g. `5814,Restaurants`,
  `5541,Gas & Fuel`).
- `merchant_keywords.csv` — `keyword,categoryName` (e.g. `starbucks,Coffee Shops`,
  `shell,Gas & Fuel`, `netflix,Cable/Streaming`), matched on the normalized name.

Category names align with `DefaultCategories` leaf names.

## Confidence & thresholds (centralized constants)

- **Leaf vs parent:** leaf if top-class ≥ ~0.60; else parent if parent-aggregate ≥
  ~0.60; else defer to the next signal.
- **Auto-apply:** confidence ≥ ~0.85 — realistically only `LEARNED_MODEL` reaches
  this. Cold-start signals carry fixed lower priors (SIC ~0.70, keyword ~0.65), so
  they always **suggest**, never silently apply. The learned model overrides bundled
  guesses as it improves.

Constants live in one place for easy tuning.

## Error handling

- No signal fires → `predict` returns null → category left blank (today's behavior).
- Predictor exceptions are swallowed and logged; the import path never sees them.
- Missing/garbled CSV resource → that cold-start signal goes quiet; others continue.
- Lexicon category name absent from the user's DB → that entry is skipped.

## Testing (TDD)

- **Per signal:** SIC hit, keyword hit on normalized names, amount-sign default, and
  `UserModelSignal` over a tiny trained model.
- **Model:** train on synthetic history; predict held-out; leaf→parent fallback;
  confidence ordering; class-imbalance behavior.
- **Cascade:** priority (learned beats cold-start); threshold gating (auto vs
  suggest); graceful null when nothing fires.
- **Cold-start loading:** CSV parse, name→ID resolution, missing-category skip.
- **Integration:** fresh DB with zero history still yields SIC/keyword **suggestions**;
  after categorizing, the learned model takes over and can auto-apply.
- **Robustness:** an injected predictor failure never breaks an import.

## Rollout / integration points

- New code under `com.financeapp.domain.categorize` (predictor, signals, model,
  features) and `com.financeapp.data.categorize` (cold-start resource loader).
- Resource CSVs under the shared module resources.
- Single integration touch-point: `ImportViewModel` category-resolution step, used
  only as the fallback after alias/payee resolution.
- No schema change, no migration, no new dependency.
