package com.financeapp.ui.transactions

import com.financeapp.domain.model.Account
import com.financeapp.domain.model.Transaction
import com.financeapp.domain.model.TransactionWithDetails
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.CategoryRepository
import com.financeapp.domain.repository.PayeeRepository
import com.financeapp.domain.repository.TagRepository
import com.financeapp.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class TransactionFilter(
    val searchQuery: String = "",
    val categoryId: Long? = null,
    val showCleared: Boolean = true,
    val showUncleared: Boolean = true,
    val minAmount: Long? = null,
    val maxAmount: Long? = null
)

data class TransactionsUiState(
    val transactions: List<TransactionWithDetails> = emptyList(),
    val filteredTransactions: List<TransactionWithDetails> = emptyList(),
    val accountName: String = "",
    val accountBalance: Long = 0,
    val isLoading: Boolean = true,
    val filter: TransactionFilter = TransactionFilter(),
    val isFilterActive: Boolean = false,
    val accounts: List<Account> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val payeeRepository: PayeeRepository,
    private val tagRepository: TagRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _selectedAccountId = MutableStateFlow(0L)
    private val _filter = MutableStateFlow(TransactionFilter())

    var currentAccountId: Long = 0
        private set

    val uiState: StateFlow<TransactionsUiState> = combine(
        _selectedAccountId,
        _filter,
        accountRepository.getAccountsWithBalances()
    ) { accountId, filter, accountsWithBalances ->
        Triple(accountId, filter, accountsWithBalances)
    }.flatMapLatest { (accountId, filter, accountsWithBalances) ->
        if (accountId == 0L) {
            return@flatMapLatest kotlinx.coroutines.flow.flowOf(TransactionsUiState())
        }

        transactionRepository.getTransactionsWithDetailsByAccount(accountId).map { transactions ->
            val accountInfo = accountsWithBalances.firstOrNull { it.account.id == accountId }
            val accountName = accountInfo?.account?.name ?: ""
            val accountBalance = accountInfo?.balance ?: 0L
            val filtered = applyFilterToTransactions(transactions, filter)
            val isActive = isFilterActive(filter)

            TransactionsUiState(
                transactions = transactions,
                filteredTransactions = filtered,
                accountName = accountName,
                accountBalance = accountBalance,
                isLoading = false,
                filter = filter,
                isFilterActive = isActive,
                accounts = accountsWithBalances.map { it.account }
            )
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Lazily,
        initialValue = TransactionsUiState()
    )

    fun loadTransactions(accountId: Long) {
        currentAccountId = accountId
        _selectedAccountId.value = accountId
    }

    fun updateSearchQuery(query: String) {
        _filter.value = _filter.value.copy(searchQuery = query)
    }

    fun updateFilter(filter: TransactionFilter) {
        _filter.value = filter
    }

    fun clearFilter() {
        _filter.value = TransactionFilter()
    }

    private fun applyFilterToTransactions(
        transactions: List<TransactionWithDetails>,
        filter: TransactionFilter
    ): List<TransactionWithDetails> {
        return transactions.filter { txn ->
            // Search query - check payee, category, memo, amount
            val matchesSearch = if (filter.searchQuery.isBlank()) {
                true
            } else {
                val query = filter.searchQuery.lowercase()
                val payeeMatch = txn.payeeName?.lowercase()?.contains(query) == true
                val categoryMatch = txn.categoryName?.lowercase()?.contains(query) == true
                val memoMatch = txn.transaction.memo?.lowercase()?.contains(query) == true
                val amountMatch = formatAmount(txn.transaction.amount).contains(query)
                payeeMatch || categoryMatch || memoMatch || amountMatch
            }

            // Category filter
            val matchesCategory = filter.categoryId == null ||
                txn.transaction.categoryId == filter.categoryId

            // Cleared status
            // If both showCleared and showUncleared are false, show all (prevent empty results)
            val matchesCleared = when {
                !filter.showCleared && !filter.showUncleared -> true
                txn.transaction.isCleared -> filter.showCleared
                else -> filter.showUncleared
            }

            // Amount range
            val matchesAmount = run {
                val amount = kotlin.math.abs(txn.transaction.amount)
                val minOk = filter.minAmount == null || amount >= filter.minAmount
                val maxOk = filter.maxAmount == null || amount <= filter.maxAmount
                minOk && maxOk
            }

            matchesSearch && matchesCategory && matchesCleared && matchesAmount
        }
    }

    private fun isFilterActive(filter: TransactionFilter): Boolean {
        // If both showCleared and showUncleared are false, treat as if both true (no filter)
        val clearedFilterActive = (filter.showCleared xor filter.showUncleared)
        return filter.searchQuery.isNotBlank() ||
            filter.categoryId != null ||
            clearedFilterActive ||
            filter.minAmount != null ||
            filter.maxAmount != null
    }

    private fun formatAmount(cents: Long): String {
        val absCents = kotlin.math.abs(cents)
        val wholeDollars = absCents / 100
        val centsPart = absCents % 100
        return "$wholeDollars.${centsPart.toString().padStart(2, '0')}"
    }

    fun addTransaction(
        amount: Long,
        payeeName: String?,
        categoryId: Long?,
        memo: String?,
        date: LocalDate,
        isCleared: Boolean,
        tagIds: List<Long> = emptyList()
    ) {
        scope.launch {
            // Get or create payee
            val payeeId = payeeName?.let { name ->
                val existing = payeeRepository.getPayeeByName(name)
                existing?.id ?: payeeRepository.insertPayee(
                    com.financeapp.domain.model.Payee(name = name, defaultCategoryId = categoryId)
                )
            }

            val now = Clock.System.now()
            val transaction = Transaction(
                accountId = currentAccountId,
                date = date,
                amount = amount,
                payeeId = payeeId,
                categoryId = categoryId,
                memo = memo,
                isCleared = isCleared,
                createdAt = now,
                updatedAt = now
            )
            val transactionId = transactionRepository.insertTransaction(transaction)

            // Add tags to the transaction
            if (tagIds.isNotEmpty()) {
                tagRepository.setTransactionTags(transactionId, tagIds)
            }

            // Notify transaction list and account list to refresh
            transactionRepository.notifyTransactionsChanged()
            accountRepository.notifyBalancesChanged()
        }
    }

    fun deleteTransaction(id: Long) {
        scope.launch {
            transactionRepository.deleteTransaction(id)

            // Notify transaction list and account list to refresh
            transactionRepository.notifyTransactionsChanged()
            accountRepository.notifyBalancesChanged()
        }
    }

    fun toggleCleared(transaction: Transaction) {
        scope.launch {
            transactionRepository.updateTransaction(
                transaction.copy(
                    isCleared = !transaction.isCleared,
                    updatedAt = Clock.System.now()
                )
            )
            // Notify transaction list and account balances to refresh
            transactionRepository.notifyTransactionsChanged()
            accountRepository.notifyBalancesChanged()
        }
    }

    fun setCleared(transaction: Transaction, cleared: Boolean) {
        scope.launch {
            transactionRepository.updateTransaction(
                transaction.copy(
                    isCleared = cleared,
                    updatedAt = Clock.System.now()
                )
            )
            transactionRepository.notifyTransactionsChanged()
            accountRepository.notifyBalancesChanged()
        }
    }

    fun editTransaction(
        transaction: Transaction,
        categoryId: Long?,
        memo: String?,
        date: LocalDate,
        isCleared: Boolean,
        tagIds: List<Long>
    ) {
        scope.launch {
            transactionRepository.updateTransaction(
                transaction.copy(
                    categoryId = categoryId,
                    memo = memo,
                    date = date,
                    isCleared = isCleared,
                    updatedAt = Clock.System.now()
                )
            )
            // Update tags
            tagRepository.setTransactionTags(transaction.id, tagIds)

            // Notify that transactions changed (triggers transaction list refresh)
            transactionRepository.notifyTransactionsChanged()

            // Notify that balances changed (triggers account balance refresh)
            accountRepository.notifyBalancesChanged()
        }
    }

    suspend fun getTagsForTransaction(transactionId: Long): List<Long> {
        return tagRepository.getTagsForTransaction(transactionId).map { it.id }
    }

    fun addTransfer(
        amount: Long,
        toAccountId: Long,
        memo: String?,
        date: LocalDate
    ) {
        scope.launch {
            // Get account names for memo
            val fromAccount = accountRepository.getAccountById(currentAccountId)
            val toAccount = accountRepository.getAccountById(toAccountId)

            // Create transfer atomically in a single DB transaction
            transactionRepository.createTransfer(
                fromAccountId = currentAccountId,
                toAccountId = toAccountId,
                amount = amount,
                date = date,
                memo = memo,
                fromAccountName = fromAccount?.name ?: "account",
                toAccountName = toAccount?.name ?: "account"
            )

            accountRepository.notifyBalancesChanged()
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
