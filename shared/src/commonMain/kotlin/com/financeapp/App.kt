package com.financeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.ui.AppViewModel
import com.financeapp.ui.accounts.AccountsScreen
import com.financeapp.ui.categories.CategoriesScreen
import com.financeapp.ui.connections.ConnectionsScreen
import com.financeapp.ui.connections.ConnectionsViewModel
import com.financeapp.ui.fileimport.ImportScreen
import com.financeapp.ui.fileimport.ImportViewModel
import com.financeapp.ui.reconcile.ReconcileScreen
import com.financeapp.ui.reconcile.ReconcileStartDialog
import com.financeapp.ui.reconcile.ReconcileViewModel
import com.financeapp.ui.scheduled.ScheduledScreen
import com.financeapp.ui.scheduled.ScheduledViewModel
import com.financeapp.ui.budget.BudgetScreen
import com.financeapp.ui.budget.BudgetViewModel
import com.financeapp.ui.reports.ReportsScreen
import com.financeapp.ui.reports.ReportsViewModel
import com.financeapp.ui.backup.BackupScreen
import com.financeapp.ui.backup.BackupViewModel
import com.financeapp.ui.payees.PayeeManagementScreen
import com.financeapp.ui.payees.PayeeManagementViewModel
import com.financeapp.ui.investments.InvestmentScreen
import com.financeapp.ui.investments.InvestmentViewModel
import com.financeapp.ui.tags.TagsScreen
import com.financeapp.ui.tags.TagsViewModel
import com.financeapp.ui.settings.SettingsScreen
import com.financeapp.ui.dashboard.DashboardScreen
import com.financeapp.ui.dashboard.DashboardViewModel
import com.financeapp.ui.dashboard.DashboardCustomizeDialog
import com.financeapp.ui.templates.TemplatesScreen
import com.financeapp.ui.templates.TemplatesViewModel
import com.financeapp.ui.accounts.AccountsViewModel
import com.financeapp.ui.categories.CategoriesViewModel
import com.financeapp.ui.lock.PinSetupScreen
import com.financeapp.ui.lock.PinUnlockScreen
import com.financeapp.ui.transactions.TransactionsScreen
import com.financeapp.ui.transactions.TransactionsViewModel
import com.financeapp.ui.theme.FinanceAppTheme
import com.financeapp.ui.theme.ThemeMode
import com.financeapp.ui.navigation.AppNavigationRail
import com.financeapp.ui.search.SearchViewModel
import com.financeapp.ui.search.GlobalSearchDialog
import org.koin.compose.koinInject

@Composable
fun App() {
    val viewModel: AppViewModel = koinInject()
    val lockState by viewModel.lockState.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    FinanceAppTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                // First time setup - no PIN set yet
                !lockState.isSetUp -> {
                    PinSetupScreen(
                        onPinSet = { pin ->
                            viewModel.setupPin(pin)
                        }
                    )
                }
                // App is locked - need to enter PIN
                lockState.isLocked -> {
                    PinUnlockScreen(
                        onPinEntered = { pin ->
                            viewModel.verifyPin(pin)
                        },
                        failedAttempts = lockState.failedAttempts,
                        biometricAvailable = viewModel.isBiometricAvailable(),
                        biometricType = viewModel.getBiometricType(),
                        onBiometricClick = {
                            viewModel.authenticateWithBiometric()
                        }
                    )
                }
                // Unlocked - show main content
                else -> {
                    MainContent()
                }
            }
        }
    }
}

enum class Screen {
    DASHBOARD, ACCOUNTS, TRANSACTIONS, CATEGORIES, IMPORT, CONNECTIONS, RECONCILE, SCHEDULED, BUDGET, REPORTS, BACKUP, PAYEES, INVESTMENTS, TAGS, TEMPLATES, SETTINGS
}

@Composable
private fun MainContent() {
    var currentScreen by remember { mutableStateOf(Screen.ACCOUNTS) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    val transactionsViewModel: TransactionsViewModel = koinInject()
    val importViewModel: ImportViewModel = koinInject()
    val connectionsViewModel: ConnectionsViewModel = koinInject()
    val reconcileViewModel: ReconcileViewModel = koinInject()
    val scheduledViewModel: ScheduledViewModel = koinInject()
    val budgetViewModel: BudgetViewModel = koinInject()
    val reportsViewModel: ReportsViewModel = koinInject()
    val backupViewModel: BackupViewModel = koinInject()
    val payeeManagementViewModel: PayeeManagementViewModel = koinInject()
    val investmentViewModel: InvestmentViewModel = koinInject()
    val tagsViewModel: TagsViewModel = koinInject()
    val accountsViewModel: AccountsViewModel = koinInject()
    val categoriesViewModel: CategoriesViewModel = koinInject()
    val dashboardViewModel: DashboardViewModel = koinInject()
    val templatesViewModel: TemplatesViewModel = koinInject()
    val appViewModel: AppViewModel = koinInject()
    val searchViewModel: SearchViewModel = koinInject()
    var selectedAccountName by remember { mutableStateOf("") }
    var showReconcileDialog by remember { mutableStateOf(false) }
    var showDashboardCustomize by remember { mutableStateOf(false) }
    var showGlobalSearch by remember { mutableStateOf(false) }

    val accountsState by accountsViewModel.uiState.collectAsState()
    val categoriesState by categoriesViewModel.uiState.collectAsState()
    val themeMode by appViewModel.themeMode.collectAsState()

    // Central navigation handler
    val navigate = { route: String ->
        currentScreen = when (route) {
            "dashboard" -> Screen.DASHBOARD
            "accounts" -> Screen.ACCOUNTS
            "categories" -> Screen.CATEGORIES
            "import" -> Screen.IMPORT
            "connections" -> Screen.CONNECTIONS
            "scheduled" -> Screen.SCHEDULED
            "budget" -> Screen.BUDGET
            "reports" -> Screen.REPORTS
            "backup" -> Screen.BACKUP
            "payees" -> Screen.PAYEES
            "investments" -> Screen.INVESTMENTS
            "tags" -> Screen.TAGS
            "templates" -> Screen.TEMPLATES
            "settings" -> Screen.SETTINGS
            else -> Screen.ACCOUNTS
        }
    }

    // Convert current screen to route for NavigationRail
    val currentRoute = when (currentScreen) {
        Screen.DASHBOARD -> "dashboard"
        Screen.ACCOUNTS -> "accounts"
        Screen.TRANSACTIONS -> "accounts" // Transactions is a sub-screen of accounts
        Screen.CATEGORIES -> "categories"
        Screen.IMPORT -> "import"
        Screen.CONNECTIONS -> "connections"
        Screen.RECONCILE -> "accounts" // Reconcile is a sub-screen of accounts
        Screen.SCHEDULED -> "scheduled"
        Screen.BUDGET -> "budget"
        Screen.REPORTS -> "reports"
        Screen.BACKUP -> "backup"
        Screen.PAYEES -> "payees"
        Screen.INVESTMENTS -> "investments"
        Screen.TAGS -> "tags"
        Screen.TEMPLATES -> "templates"
        Screen.SETTINGS -> "settings"
    }

    Row(modifier = Modifier.fillMaxSize()) {
        AppNavigationRail(
            currentRoute = currentRoute,
            onNavigate = navigate,
            onSearchClick = { showGlobalSearch = true },
            modifier = Modifier.width(80.dp)
        )

        // Main content area
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (currentScreen) {
                Screen.DASHBOARD -> DashboardScreen(
                    viewModel = dashboardViewModel,
                    onAccountClick = { accountId ->
                        selectedAccountId = accountId
                        currentScreen = Screen.TRANSACTIONS
                    },
                    onCustomize = { showDashboardCustomize = true },
                    modifier = Modifier.fillMaxSize()
                )
                Screen.ACCOUNTS -> AccountsScreen(
                    onAccountClick = { accountId ->
                        selectedAccountId = accountId
                        currentScreen = Screen.TRANSACTIONS
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Screen.TRANSACTIONS -> TransactionsScreen(
                    accountId = selectedAccountId!!,
                    viewModel = transactionsViewModel,
                    onBack = { currentScreen = Screen.ACCOUNTS },
                    onReconcile = { accountId, accountName ->
                        selectedAccountName = accountName
                        showReconcileDialog = true
                    }
                )
                Screen.CATEGORIES -> CategoriesScreen(
                    onBack = { /* NavigationRail handles navigation */ },
                    modifier = Modifier.fillMaxSize()
                )
                Screen.IMPORT -> ImportScreen(
                    viewModel = importViewModel,
                    onBack = { /* NavigationRail handles navigation */ },
                    onPickFile = { callback ->
                        pickFile { content ->
                            callback(content)
                        }
                    }
                )
                Screen.CONNECTIONS -> ConnectionsScreen(
                    viewModel = connectionsViewModel,
                    onBack = { /* NavigationRail handles navigation */ }
                )
                Screen.RECONCILE -> ReconcileScreen(
                    viewModel = reconcileViewModel,
                    accountName = selectedAccountName,
                    onBack = { currentScreen = Screen.TRANSACTIONS },
                    onComplete = { currentScreen = Screen.TRANSACTIONS }
                )
                Screen.SCHEDULED -> ScheduledScreen(
                    viewModel = scheduledViewModel,
                    accounts = accountsState.accounts.map { it.account },
                    categories = categoriesState.categories,
                    onBack = { /* NavigationRail handles navigation */ }
                )
                Screen.BUDGET -> BudgetScreen(
                    viewModel = budgetViewModel,
                    onBack = { /* NavigationRail handles navigation */ }
                )
                Screen.REPORTS -> ReportsScreen(
                    viewModel = reportsViewModel,
                    onBack = { /* NavigationRail handles navigation */ }
                )
                Screen.BACKUP -> BackupScreen(
                    viewModel = backupViewModel,
                    onBack = { /* NavigationRail handles navigation */ },
                    onSaveFile = { content, filename ->
                        saveFile(content, filename) { /* result handled by ViewModel */ }
                    }
                )
                Screen.PAYEES -> PayeeManagementScreen(
                    viewModel = payeeManagementViewModel,
                    onBack = { /* NavigationRail handles navigation */ }
                )
                Screen.INVESTMENTS -> InvestmentScreen(
                    viewModel = investmentViewModel,
                    onBack = { /* NavigationRail handles navigation */ }
                )
                Screen.TAGS -> TagsScreen(
                    viewModel = tagsViewModel,
                    onBack = { /* NavigationRail handles navigation */ }
                )
                Screen.TEMPLATES -> TemplatesScreen(
                    viewModel = templatesViewModel,
                    onBack = { /* NavigationRail handles navigation */ },
                    onUseTemplate = { template ->
                        selectedAccountId = template.accountId ?: selectedAccountId
                        currentScreen = Screen.TRANSACTIONS
                    }
                )
                Screen.SETTINGS -> SettingsScreen(
                    currentThemeMode = themeMode,
                    onThemeModeChange = { appViewModel.setThemeMode(it) },
                    onBack = { /* NavigationRail handles navigation */ }
                )
            }
        }
    }

    if (showReconcileDialog) {
        ReconcileStartDialog(
            onDismiss = { showReconcileDialog = false },
            onStart = { date, balance ->
                showReconcileDialog = false
                reconcileViewModel.startReconciliation(selectedAccountId!!, date, balance)
                currentScreen = Screen.RECONCILE
            }
        )
    }

    if (showDashboardCustomize) {
        DashboardCustomizeDialog(
            viewModel = dashboardViewModel,
            onDismiss = { showDashboardCustomize = false }
        )
    }

    if (showGlobalSearch) {
        GlobalSearchDialog(
            viewModel = searchViewModel,
            onDismiss = { showGlobalSearch = false }
        )
    }
}

// Expect function for platform-specific file picking
expect fun pickFile(onFileContent: (String) -> Unit)

// Expect function for platform-specific file saving
expect fun saveFile(content: String, suggestedFilename: String, onResult: (Boolean) -> Unit)
