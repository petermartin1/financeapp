package com.financeapp.di

import com.financeapp.data.fileimport.ImportRepository
import com.financeapp.data.ofx.OfxClient
import com.financeapp.data.ofx.OfxRepository
import com.financeapp.data.repository.AccountRepositoryImpl
import com.financeapp.data.repository.AppLockRepositoryImpl
import com.financeapp.data.repository.CategoryRepositoryImpl
import com.financeapp.data.repository.PayeeRepositoryImpl
import com.financeapp.data.repository.PreferencesStore
import com.financeapp.data.repository.TransactionRepositoryImpl
import com.financeapp.data.repository.BudgetRepositoryImpl
import com.financeapp.domain.repository.BudgetRepository
import com.financeapp.db.DatabaseDriverFactory
import com.financeapp.domain.repository.AccountRepository
import org.jetbrains.exposed.sql.Database
import com.financeapp.domain.repository.AppLockRepository
import com.financeapp.domain.repository.CategoryRepository
import com.financeapp.domain.repository.PayeeRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.ui.AppViewModel
import com.financeapp.ui.accounts.AccountsViewModel
import com.financeapp.ui.categories.CategoriesViewModel
import com.financeapp.ui.connections.ConnectionsViewModel
import com.financeapp.ui.fileimport.ImportViewModel
import com.financeapp.ui.reconcile.ReconcileViewModel
import com.financeapp.ui.scheduled.ScheduledViewModel
import com.financeapp.ui.transactions.TransactionsViewModel
import com.financeapp.ui.budget.BudgetViewModel
import com.financeapp.ui.reports.ReportsViewModel
import com.financeapp.data.backup.ExportRepository
import com.financeapp.ui.backup.BackupViewModel
import com.financeapp.ui.payees.PayeeManagementViewModel
import com.financeapp.domain.repository.InvestmentRepository
import com.financeapp.data.repository.InvestmentRepositoryImpl
import com.financeapp.ui.investments.InvestmentViewModel
import com.financeapp.domain.repository.TagRepository
import com.financeapp.data.repository.TagRepositoryImpl
import com.financeapp.data.repository.PreferencesRepositoryImpl
import com.financeapp.domain.repository.PreferencesRepository
import com.financeapp.ui.tags.TagsViewModel
import com.financeapp.ui.dashboard.DashboardViewModel
import com.financeapp.domain.repository.TemplateRepository
import com.financeapp.data.repository.TemplateRepositoryImpl
import com.financeapp.ui.templates.TemplatesViewModel
import com.financeapp.domain.repository.ScheduledTransactionRepository
import com.financeapp.data.repository.ScheduledTransactionRepositoryImpl
import com.financeapp.security.SecureCredentialStore
import com.financeapp.ui.search.SearchViewModel
import com.financeapp.ui.investments.PerformanceTabViewModel
import com.financeapp.domain.repository.QuoteRepository
import com.financeapp.data.repository.QuoteRepositoryImpl
import com.financeapp.domain.repository.PerformanceRepository
import com.financeapp.data.repository.PerformanceRepositoryImpl
import com.financeapp.domain.service.PriceRefreshService
import com.financeapp.domain.service.SnapshotScheduler
import com.financeapp.data.quotes.YahooFinanceClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

expect fun platformModule(): Module

val sharedModule = module {
    single<Database> {
        get<DatabaseDriverFactory>().createDriver()
    }

    single<AppLockRepository> { AppLockRepositoryImpl(get()) }
    single<AccountRepository> { AccountRepositoryImpl(get()) }
    single<TransactionRepository> { TransactionRepositoryImpl(get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get()) }
    single<PayeeRepository> { PayeeRepositoryImpl(get()) }
    single<InvestmentRepository> { InvestmentRepositoryImpl(get()) }
    single<TagRepository> { TagRepositoryImpl(get()) }
    single<PreferencesRepository> { PreferencesRepositoryImpl(get()) }
    single<TemplateRepository> { TemplateRepositoryImpl(get()) }
    single<BudgetRepository> { BudgetRepositoryImpl(get()) }
    single<ScheduledTransactionRepository> { ScheduledTransactionRepositoryImpl(get()) }
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
    single { YahooFinanceClient(get()) }
    single<QuoteRepository> { QuoteRepositoryImpl(get(), get(), get()) }
    single<PerformanceRepository> { PerformanceRepositoryImpl(get(), get(), get()) }
    single { ImportRepository(get(), get(), get()) }
    single { OfxClient() }
    single { SecureCredentialStore() }
    single { OfxRepository(get(), get(), get(), get()) }
    single { ExportRepository(get()) }
    single { PriceRefreshService(get(), get()) }
    single { SnapshotScheduler(get()) }

    single { AppViewModel(get(), get(), get()) }
    single { AccountsViewModel(get()) }
    single { TransactionsViewModel(get(), get(), get(), get(), get()) }
    single { CategoriesViewModel(get()) }
    single { ImportViewModel(get(), get()) }
    single { ConnectionsViewModel(get()) }
    single { ReconcileViewModel(get<TransactionRepository>(), get<AccountRepository>()) }
    single { ScheduledViewModel(get<ScheduledTransactionRepository>(), get<TransactionRepository>()) }
    single { BudgetViewModel(get()) }
    single { ReportsViewModel(get<TransactionRepository>(), get<AccountRepository>()) }
    single { BackupViewModel(get()) }
    factory { PayeeManagementViewModel(get(), get()) }
    factory { InvestmentViewModel(get(), get()) }
    factory { PerformanceTabViewModel(get(), get()) }
    factory { TagsViewModel(get()) }
    factory { DashboardViewModel(get(), get(), get()) }
    factory { TemplatesViewModel(get(), get(), get(), get()) }
    factory { SearchViewModel(get(), get()) }
}

fun appModules() = listOf(platformModule(), sharedModule)
