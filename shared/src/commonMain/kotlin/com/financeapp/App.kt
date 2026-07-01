package com.financeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.ui.AppViewModel
import com.financeapp.ui.accounts.AccountsScreen
import com.financeapp.ui.categories.CategoriesScreen
import com.financeapp.ui.connections.ConnectionsScreen
import com.financeapp.ui.connections.ConnectionsViewModel
import com.financeapp.ui.fileimport.ImportScreen
import com.financeapp.ui.fileimport.ImportViewModel
import com.financeapp.ui.fileimport.PayeeMappingStep
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
import com.financeapp.ui.investments.PerformanceTabViewModel
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
import com.financeapp.ui.VaultViewModel
import com.financeapp.ui.VaultGate
import com.financeapp.ui.lock.VaultSetupScreen
import com.financeapp.ui.lock.VaultMigrateScreen
import com.financeapp.ui.lock.VaultUnlockScreen
import com.financeapp.ui.lock.RecoveryKeyDialog
import com.financeapp.ui.transactions.TransactionsScreen
import com.financeapp.ui.transactions.TransactionsViewModel
import com.financeapp.ui.theme.FinanceAppTheme
import com.financeapp.ui.theme.ThemeMode
import com.financeapp.ui.navigation.AppNavigationRail
import com.financeapp.ui.search.SearchViewModel
import com.financeapp.ui.search.GlobalSearchDialog
import com.financeapp.ui.error.AppErrorBus
import org.koin.compose.koinInject

@Composable
fun App() {
    val vaultViewModel: VaultViewModel = koinInject()
    val gate by vaultViewModel.gate.collectAsState()
    val recoveryToShow by vaultViewModel.recoveryToShow.collectAsState()
    val vaultError by vaultViewModel.error.collectAsState()

    FinanceAppTheme(themeMode = ThemeMode.SYSTEM) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (gate) {
                    VaultGate.Loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    VaultGate.Setup -> VaultSetupScreen(
                        vaultViewModel::checkStrength,
                        vaultViewModel::setUp
                    )
                    VaultGate.Migrate -> VaultMigrateScreen(
                        vaultViewModel::checkStrength,
                        vaultViewModel::migrate
                    )
                    VaultGate.Locked -> VaultUnlockScreen(
                        vaultError,
                        vaultViewModel::unlock,
                        vaultViewModel::unlockWithRecovery
                    )
                    // First DB touch is deferred to UnlockedApp(), which is only composed once Unlocked.
                    VaultGate.Unlocked -> UnlockedApp()
                }
                recoveryToShow?.let { RecoveryKeyDialog(it, vaultViewModel::dismissRecovery) }
            }
        }
    }
}

@Composable
private fun UnlockedApp() {
    val appViewModel: AppViewModel = koinInject()   // first DB touch happens here, post-unlock
    val vaultViewModel: VaultViewModel = koinInject()
    LaunchedEffect(Unit) { appViewModel.startPostUnlock() }
    // Idle auto-lock monitor, scoped to the unlocked UI so it is cancelled on lock/dispose
    // (and never runs in unit tests that construct the view model directly).
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            vaultViewModel.checkAutoLock()
        }
    }
    val themeMode by appViewModel.themeMode.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Surface background-work failures routed through the shared error bus (N6 / R16 / R28).
    LaunchedEffect(Unit) {
        AppErrorBus.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    FinanceAppTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    // Both pointer and keyboard interaction count as activity for the idle timer;
                    // keyboard-only work (e.g. typing a long note) must not be mistaken for idle.
                    .onPreviewKeyEvent { vaultViewModel.noteActivity(); false }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) { awaitPointerEvent(); vaultViewModel.noteActivity() }
                        }
                    }
            ) {
                MainContent()
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
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
    var navigationStack by remember { mutableStateOf(listOf<Screen>()) }
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
    val performanceTabViewModel: PerformanceTabViewModel = koinInject()
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

    val vaultViewModel: VaultViewModel = koinInject()
    val accountsState by accountsViewModel.uiState.collectAsState()
    val categoriesState by categoriesViewModel.uiState.collectAsState()
    val themeMode by appViewModel.themeMode.collectAsState()
    val importState by importViewModel.uiState.collectAsState()

    // While an import is in flight — especially its interactive payee-review dialog, which runs in
    // a separate window and produces no main-window pointer activity — suspend the idle auto-lock
    // so the vault isn't pulled out from under the user mid-import (bouncing them to the unlock
    // screen and losing their in-progress mappings).
    val importActive = importState.payeeMappingStep != PayeeMappingStep.None || importState.isImporting
    DisposableEffect(importActive) {
        if (importActive) vaultViewModel.beginBusy()
        onDispose { if (importActive) vaultViewModel.endBusy() }
    }

    // Navigation functions
    val navigateTo = { screen: Screen ->
        if (currentScreen != screen) {
            navigationStack = navigationStack + currentScreen
            currentScreen = screen
        }
    }

    val navigateBack = {
        if (navigationStack.isNotEmpty()) {
            currentScreen = navigationStack.last()
            navigationStack = navigationStack.dropLast(1)
        } else {
            // Fallback: if stack is empty, go to default screen
            currentScreen = Screen.ACCOUNTS
        }
    }

    // Central navigation handler for NavigationRail
    val navigate = { route: String ->
        val newScreen = when (route) {
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
        // Clear navigation stack when navigating to top-level screens from rail
        navigationStack = emptyList()
        currentScreen = newScreen
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
                        navigateTo(Screen.TRANSACTIONS)
                    },
                    onCustomize = { showDashboardCustomize = true },
                    modifier = Modifier.fillMaxSize()
                )
                Screen.ACCOUNTS -> AccountsScreen(
                    onAccountClick = { accountId ->
                        selectedAccountId = accountId
                        navigateTo(Screen.TRANSACTIONS)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Screen.TRANSACTIONS -> {
                    if (selectedAccountId != null) {
                        TransactionsScreen(
                            accountId = selectedAccountId!!,
                            viewModel = transactionsViewModel,
                            onBack = navigateBack,
                            onReconcile = { accountId, accountName ->
                                selectedAccountName = accountName
                                showReconcileDialog = true
                            }
                        )
                    } else {
                        // Safety: Navigate back to accounts if no account selected
                        LaunchedEffect(Unit) {
                            navigateBack()
                        }
                    }
                }
                Screen.CATEGORIES -> CategoriesScreen(
                    onBack = navigateBack,
                    modifier = Modifier.fillMaxSize()
                )
                Screen.IMPORT -> ImportScreen(
                    viewModel = importViewModel,
                    onBack = navigateBack,
                    onPickFile = { callback ->
                        pickFile { content ->
                            callback(content)
                        }
                    }
                )
                Screen.CONNECTIONS -> ConnectionsScreen(
                    viewModel = connectionsViewModel,
                    onBack = navigateBack
                )
                Screen.RECONCILE -> ReconcileScreen(
                    viewModel = reconcileViewModel,
                    accountName = selectedAccountName,
                    onBack = navigateBack,
                    onComplete = navigateBack
                )
                Screen.SCHEDULED -> ScheduledScreen(
                    viewModel = scheduledViewModel,
                    accounts = accountsState.accounts.map { it.account },
                    categories = categoriesState.categories,
                    onBack = navigateBack
                )
                Screen.BUDGET -> BudgetScreen(
                    viewModel = budgetViewModel,
                    onBack = navigateBack
                )
                Screen.REPORTS -> ReportsScreen(
                    viewModel = reportsViewModel,
                    onBack = navigateBack
                )
                Screen.BACKUP -> BackupScreen(
                    viewModel = backupViewModel,
                    onBack = navigateBack,
                    onSaveFile = { content, filename ->
                        saveFile(content, filename) { /* result handled by ViewModel */ }
                    }
                )
                Screen.PAYEES -> PayeeManagementScreen(
                    viewModel = payeeManagementViewModel,
                    onBack = navigateBack
                )
                Screen.INVESTMENTS -> InvestmentScreen(
                    viewModel = investmentViewModel,
                    performanceViewModel = performanceTabViewModel,
                    onBack = navigateBack
                )
                Screen.TAGS -> TagsScreen(
                    viewModel = tagsViewModel,
                    onBack = navigateBack
                )
                Screen.TEMPLATES -> TemplatesScreen(
                    viewModel = templatesViewModel,
                    onBack = navigateBack,
                    onUseTemplate = { template ->
                        val accountId = template.accountId ?: selectedAccountId
                        if (accountId != null) {
                            selectedAccountId = accountId
                            navigateTo(Screen.TRANSACTIONS)
                        }
                    }
                )
                Screen.SETTINGS -> SettingsScreen(
                    currentThemeMode = themeMode,
                    onThemeModeChange = { appViewModel.setThemeMode(it) },
                    onBack = navigateBack
                )
            }
        }
    }

    if (showReconcileDialog && selectedAccountId != null) {
        ReconcileStartDialog(
            onDismiss = { showReconcileDialog = false },
            onStart = { date, balance ->
                showReconcileDialog = false
                selectedAccountId?.let { accountId ->
                    reconcileViewModel.startReconciliation(accountId, date, balance)
                    navigateTo(Screen.RECONCILE)
                }
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
