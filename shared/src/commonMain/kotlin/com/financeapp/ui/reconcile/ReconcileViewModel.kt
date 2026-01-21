package com.financeapp.ui.reconcile

import com.financeapp.domain.model.Transaction
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.repository.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.*

class ReconcileViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _uiState = MutableStateFlow(ReconcileUiState())
    val uiState: StateFlow<ReconcileUiState> = _uiState.asStateFlow()

    fun startReconciliation(accountId: Long, statementDate: LocalDate, statementBalance: Long) {
        _uiState.value = _uiState.value.copy(
            accountId = accountId,
            statementDate = statementDate,
            statementBalance = statementBalance,
            isActive = true
        )
        loadTransactions(accountId)
    }

    private fun loadTransactions(accountId: Long) {
        scope.launch {
            // Get last reconciled balance (sum of reconciled transactions only)
            val reconciledBalance = accountRepository.getReconciledBalance(accountId)

            // Get unreconciled transactions
            val allTransactions = transactionRepository.getTransactionsByAccount(accountId).first()
            val transactions = allTransactions
                .filter { !it.isReconciled }
                .map { txn ->
                    ReconcileTransaction(
                        id = txn.id,
                        date = txn.date,
                        amount = txn.amount,
                        payeeId = txn.payeeId,
                        memo = txn.memo,
                        isSelected = false
                    )
                }

            _uiState.value = _uiState.value.copy(
                reconciledBalance = reconciledBalance,
                transactions = transactions
            )
            updateDifference()
        }
    }

    fun toggleTransaction(transactionId: Long) {
        val transactions = _uiState.value.transactions.map { txn ->
            if (txn.id == transactionId) {
                txn.copy(isSelected = !txn.isSelected)
            } else {
                txn
            }
        }
        _uiState.value = _uiState.value.copy(transactions = transactions)
        updateDifference()
    }

    fun selectAll() {
        val transactions = _uiState.value.transactions.map { it.copy(isSelected = true) }
        _uiState.value = _uiState.value.copy(transactions = transactions)
        updateDifference()
    }

    fun selectNone() {
        val transactions = _uiState.value.transactions.map { it.copy(isSelected = false) }
        _uiState.value = _uiState.value.copy(transactions = transactions)
        updateDifference()
    }

    private fun updateDifference() {
        val state = _uiState.value
        val selectedTotal = state.transactions
            .filter { it.isSelected }
            .sumOf { it.amount }
        val clearedBalance = state.reconciledBalance + selectedTotal
        val difference = state.statementBalance - clearedBalance

        _uiState.value = state.copy(
            clearedBalance = clearedBalance,
            difference = difference
        )
    }

    fun completeReconciliation() {
        val state = _uiState.value
        if (state.difference != 0L) return

        scope.launch {
            // Mark selected transactions as reconciled
            state.transactions
                .filter { it.isSelected }
                .forEach { txn ->
                    transactionRepository.markTransactionReconciled(txn.id, true)
                }

            // Create reconciliation record
            accountRepository.insertReconciliation(
                accountId = state.accountId,
                statementDate = state.statementDate,
                statementBalance = state.statementBalance,
                isCompleted = true
            )

            _uiState.value = ReconcileUiState(isCompleted = true)
        }
    }

    fun cancelReconciliation() {
        _uiState.value = ReconcileUiState()
    }
}

data class ReconcileUiState(
    val accountId: Long = 0,
    val statementDate: LocalDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val statementBalance: Long = 0,
    val reconciledBalance: Long = 0,
    val clearedBalance: Long = 0,
    val difference: Long = 0,
    val transactions: List<ReconcileTransaction> = emptyList(),
    val isActive: Boolean = false,
    val isCompleted: Boolean = false
)

data class ReconcileTransaction(
    val id: Long,
    val date: LocalDate,
    val amount: Long,
    val payeeId: Long?,
    val memo: String?,
    val isSelected: Boolean
)
