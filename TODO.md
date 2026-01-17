# Finance App - Development Roadmap

## Current Status

✅ **Core Application Complete** - All essential features implemented and working
- Database with encryption
- Full transaction management
- Bank sync (OFX Direct Connect + file import)
- Investment tracking with performance metrics
- Budgeting system
- Reports and charts
- Professional UI with animations
- Security hardening complete

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
- [x] Context menus (right-click)
- [x] Enhanced tooltips (HoverCard component)
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

---

## Completed Phases

All core phases (1-10) are complete:
- ✅ Core Infrastructure (Database, DI, Repositories)
- ✅ Basic UI (All screens implemented)
- ✅ Core Features (Transactions, Categories, Payees, Reconciliation)
- ✅ Security (Encryption, Secure credential storage, Rate limiting)
- ✅ Bank Integration (OFX Direct Connect, File import)
- ✅ Investments (Holdings, Performance tracking, Price refresh) 
- ✅ Budgeting (Budget setup, tracking, visualization)
- ✅ Advanced Features (Dark mode, Backup/restore, Custom reports)
- ✅ Professional UI Overhaul (Design system, Charts, Animations, Navigation)
- ✅ App Icons & Branding (Logo, About dialog)
- ✅ Bulk Transaction Operations (Multi-select with checkboxes, Bulk categorize/tag/delete, Keyboard shortcuts: Ctrl+A, Delete, Escape)

See git history for detailed implementation notes and bug fixes.
