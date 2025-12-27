package com.financeapp.ui.accounts

import com.financeapp.domain.model.Account
import com.financeapp.domain.model.AccountType
import com.financeapp.domain.model.AccountWithBalance
import com.financeapp.domain.repository.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class AccountsUiState(
    val accounts: List<AccountWithBalance> = emptyList(),
    val isLoading: Boolean = true,
    val totalBalance: Long = 0
)

class AccountsViewModel(
    private val accountRepository: AccountRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    val uiState: StateFlow<AccountsUiState> = accountRepository.getAccountsWithBalances()
        .map { accounts ->
            val total = accounts.sumOf { it.balance }
            AccountsUiState(
                accounts = accounts,
                isLoading = false,
                totalBalance = total
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Lazily,
            initialValue = AccountsUiState()
        )

    fun addAccount(
        name: String,
        type: AccountType,
        institution: String?,
        accountNumber: String?
    ) {
        scope.launch {
            val now = Clock.System.now()
            val account = Account(
                name = name,
                type = type,
                institution = institution,
                accountNumber = accountNumber,
                createdAt = now,
                updatedAt = now
            )
            accountRepository.insertAccount(account)
        }
    }

    fun updateAccount(account: Account) {
        scope.launch {
            accountRepository.updateAccount(
                account.copy(updatedAt = Clock.System.now())
            )
        }
    }

    fun deleteAccount(id: Long) {
        scope.launch {
            accountRepository.deleteAccount(id)
        }
    }

    /**
     * Cleanup method to cancel all background coroutines.
     * Should be called when the ViewModel is no longer needed (e.g., in tests).
     */
    fun cleanup() {
        scope.cancel()
    }
}
