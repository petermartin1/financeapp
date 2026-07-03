# Future Ideas

A codebase-grounded assessment of features this app could add or improve on.
Complements `ROADMAP.md` — where an item is already on the roadmap it's noted as
such; this doc adds the ideas the roadmap doesn't cover plus effort/priority framing.

> Snapshot date: 2026-07-01. Reassess as the code changes.

## Context: what's already strong

This is a mature app, not a skeleton. Already shipped: accounts
(checking/savings/credit/investment/cash), transactions with splits/transfers/tags,
ML-assisted categorization (Complement NB + MCC/SIC cold-start), payees with alias
matching, monthly budgets, investments (holdings, **lots**, dividends, snapshots,
performance), scheduled transactions, templates, reconciliation, OFX/CSV/QIF import,
reports (spending, income/expense, net worth, trends), backup/export, and a serious
encryption/vault story. The gaps below are "next-tier," not "missing basics."

## Lacks altogether

### 1. Realized capital-gains report  — *highest ROI*
No `taxable`/cap-gains reporting today, **but `HoldingLots` already exist**, so the
data model is already there. This is mostly a reporting/aggregation task over existing
lot data (match sells to lots, short vs long term, realized gain/loss). Cheapest path
to a high-value feature. Feeds into Tax Reporting (roadmap #4).

### 2. Savings goals  — *roadmap #1*
No `Goal`/`SavingsGoal` concept anywhere in the code. Headline feature in
Mint/YNAB/Quicken (target amount + deadline, link to account, progress). Self-contained.

### 3. Loan / liability accounts  — *roadmap #2*
`AccountType` is only `CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT, CASH` — no
`LOAN`/`MORTGAGE`/liability type. Can't model a mortgage or auto loan, so a whole
quadrant of net worth is missing. Needs: new account type, amortization schedule,
principal/interest split, payoff projections.

### 4. Cash-flow forecasting
`ScheduledTransactions` are collected but never projected forward. Add a forward
balance projection ("balance over the next 90 days; you dip negative on the 14th").
Natural payoff of scheduling data already being captured.

### 5. Alerts / notifications
No bill-due reminders, budget-overspend warnings, or low-balance alerts. The scheduled
+ budget data to drive these already exists.

### 6. Transaction attachments
No way to attach a receipt/PDF/image to a transaction (expense tracking + audit).

## Could improve on what exists

### 7. Budgets are thin
Monthly, per-category only (`BudgetRepository` is month-keyed CRUD). Missing
**rollover/carryover** (envelope/YNAB-style), rolling/annual budgets, and
"budget the leftover" flows.

### 8. Multi-currency is nominal  — *roadmap #3*
`Account.currency` exists but there is **no exchange-rate handling anywhere**, so a EUR
account and a USD account can't be correctly aggregated into net worth. Either make it
real (rates + base-currency conversion) or drop the field to avoid implying support.

### 9. Bank sync is OFX-only
File/OFX-import heavy. Plaid is listed as "future" in CLAUDE.md. Auto-sync is the
biggest friction gap vs. Mint/Quicken competitors.

### 10. Report export / print
Reports are on-screen only; the export repo is backup-focused, not report-focused.
Add PDF/CSV export of spending and net-worth reports.

## Suggested priority

Biggest bang for the effort, given what the code already provides:

1. **Realized capital-gains report** — data (`HoldingLots`) already exists; mostly reporting.
2. **Savings goals** — self-contained, high user value.
3. **Loan / liability account type + amortization** — closes the most conspicuous net-worth gap.
