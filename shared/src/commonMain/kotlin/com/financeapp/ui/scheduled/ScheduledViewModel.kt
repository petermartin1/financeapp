package com.financeapp.ui.scheduled

import com.financeapp.domain.model.ScheduledTransaction
import com.financeapp.domain.model.ScheduledTransactionWithDetails
import com.financeapp.domain.model.Transaction
import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.ScheduledTransactionRepository
import com.financeapp.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.*

class ScheduledViewModel(
    private val scheduledTransactionRepository: ScheduledTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _uiState = MutableStateFlow(ScheduledUiState())
    val uiState: StateFlow<ScheduledUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            scheduledTransactionRepository.getAllScheduledTransactions()
                .collect { scheduled ->
                    _uiState.value = _uiState.value.copy(
                        scheduledTransactions = scheduled,
                        isLoading = false
                    )
                }
        }
    }

    fun loadScheduledTransactions() {
        // No longer needed - Flow automatically updates
        // Kept for compatibility in case called from UI
    }

    fun addScheduledTransaction(
        accountId: Long,
        payeeId: Long?,
        categoryId: Long?,
        amount: Long,
        memo: String?,
        frequency: TransactionFrequency,
        nextDate: LocalDate,
        endDate: LocalDate?
    ) {
        scope.launch {
            val scheduledTransaction = ScheduledTransaction(
                id = 0,
                accountId = accountId,
                payeeId = payeeId,
                categoryId = categoryId,
                amount = amount,
                memo = memo,
                frequency = frequency,
                nextDate = nextDate,
                endDate = endDate,
                isActive = true
            )
            scheduledTransactionRepository.insertScheduledTransaction(scheduledTransaction)
        }
    }

    fun deleteScheduledTransaction(id: Long) {
        scope.launch {
            scheduledTransactionRepository.deleteScheduledTransaction(id)
        }
    }

    fun skipNextOccurrence(id: Long) {
        scope.launch {
            val scheduled = scheduledTransactionRepository.getScheduledTransactionById(id)
                ?: return@launch

            val newDate = calculateNextDate(scheduled.nextDate, scheduled.frequency)
            val newDateMillis = newDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

            // Check if past end date
            val endDateMillis = scheduled.endDate?.atStartOfDayIn(TimeZone.currentSystemDefault())?.toEpochMilliseconds()
            if (endDateMillis != null && newDateMillis > endDateMillis) {
                scheduledTransactionRepository.updateScheduledTransactionActive(id, false)
            } else {
                scheduledTransactionRepository.updateScheduledTransactionNextDate(id, newDateMillis)
            }
        }
    }

    fun enterDueTransactions() {
        scope.launch {
            var entered = 0
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            val todayMillis = today.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

            val dueTransactions = scheduledTransactionRepository.getDueScheduledTransactions(todayMillis)

            for (scheduled in dueTransactions) {
                // Skip if nextDate is already past endDate (safety check)
                if (scheduled.endDate != null && scheduled.nextDate > scheduled.endDate) {
                    scheduledTransactionRepository.updateScheduledTransactionActive(scheduled.id, false)
                    continue
                }

                val now = Clock.System.now()

                // Create the transaction
                val transaction = Transaction(
                    accountId = scheduled.accountId,
                    date = scheduled.nextDate,
                    amount = scheduled.amount,
                    payeeId = scheduled.payeeId,
                    categoryId = scheduled.categoryId,
                    memo = scheduled.memo,
                    isCleared = false,
                    createdAt = now,
                    updatedAt = now
                )

                transactionRepository.insertTransaction(transaction)
                entered++

                // Update next date
                val newDate = calculateNextDate(scheduled.nextDate, scheduled.frequency)
                val newDateMillis = newDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()

                // Check if next occurrence would be past end date
                val endDateMillis = scheduled.endDate?.atStartOfDayIn(TimeZone.currentSystemDefault())?.toEpochMilliseconds()
                if (endDateMillis != null && newDateMillis > endDateMillis) {
                    scheduledTransactionRepository.updateScheduledTransactionActive(scheduled.id, false)
                } else {
                    scheduledTransactionRepository.updateScheduledTransactionNextDate(scheduled.id, newDateMillis)
                }
            }
            if (entered > 0) {
                accountRepository.notifyBalancesChanged()
            }
            _uiState.value = _uiState.value.copy(lastEnteredCount = entered)
        }
    }

    fun clearEnteredCount() {
        _uiState.value = _uiState.value.copy(lastEnteredCount = null)
    }

    private fun calculateNextDate(current: LocalDate, frequency: TransactionFrequency): LocalDate {
        return when (frequency) {
            TransactionFrequency.DAILY -> current.plus(1, DateTimeUnit.DAY)
            TransactionFrequency.WEEKLY -> current.plus(7, DateTimeUnit.DAY)
            TransactionFrequency.BIWEEKLY -> current.plus(14, DateTimeUnit.DAY)
            TransactionFrequency.MONTHLY -> current.plus(1, DateTimeUnit.MONTH)
            TransactionFrequency.YEARLY -> current.plus(1, DateTimeUnit.YEAR)
        }
    }
}

data class ScheduledUiState(
    val scheduledTransactions: List<ScheduledTransactionWithDetails> = emptyList(),
    val isLoading: Boolean = true,
    val lastEnteredCount: Int? = null
)
