# Lot Analytics Follow-up Plan

## Context
- Holding detail view now surfaces a basic lot summary and the reusable `ManageLotsDialog`.
- We still need richer analytics per lot: gain/loss, current market value, and potential integration with tax lot selection workflows.

## Next Steps
1. **Repository Enhancements**
   - Extend `InvestmentRepository` with a helper that, given a holding ID, returns each lot with derived metrics (market value, gain/loss) using the latest price.
   - Consider a simple DTO (`LotAnalytics`) to keep UI logic clean.
2. **ViewModel Updates**
   - `HoldingDetailViewModel` should collect the new analytics flow along with raw lots.
   - Provide formatted aggregates (e.g., best/worst lot, total unrealized gain) for direct binding in Compose.
3. **UI Improvements**
   - Update `LotsSummaryCard` to show per-lot gain/loss indicators, maybe a small sparkline in the dialog later.
   - Inside `ManageLotsDialog`, include gain/loss next to cost basis to give context before editing.
4. **Testing**
   - Add repository tests that stub price data and validate computed analytics for multiple lots on the same day.
   - Extend `HoldingDetailViewModelTest` to assert the analytics flow emits expected values when repository data changes.
5. **Visual Polish (optional)**
   - Explore a mini chart or stacked bar showing cost basis vs. market value per lot.
   - Consider color coding profitable vs. underwater lots in both summary and dialog rows.

## Open Questions
- Do we want to support lot-specific notes in reports/export?
- Should deleting a lot warn if it would desync historical performance snapshots?

_Feel free to continue from this plan tomorrow!_
