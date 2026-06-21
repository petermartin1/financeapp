package com.financeapp.ui.scheduled

import com.financeapp.ui.supervisedViewModelScope

import com.financeapp.domain.model.ScheduledTransaction
import com.financeapp.domain.model.ScheduledTransactionWithDetails
import com.financeapp.domain.model.Transaction
import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.ScheduledTransactionRepository
import com.financeapp.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.*

class ScheduledViewModel(
    private val scheduledTransactionRepository: ScheduledTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) {
    private val scope = supervisedViewModelScope()

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

            val anchorDay = scheduled.dayOfMonth ?: scheduled.nextDate.dayOfMonth
            val newDate = nextScheduledDate(scheduled.nextDate, scheduled.frequency, anchorDay)
            val newDateMillis = newDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

            // Check if past end date
            val endDateMillis = scheduled.endDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
            if (endDateMillis != null && newDateMillis > endDateMillis) {
                scheduledTransactionRepository.updateScheduledTransactionActive(id, false)
            } else {
                scheduledTransactionRepository.updateScheduledTransactionNextDate(id, newDateMillis)
            }
        }
    }

    fun enterDueTransactions() {
        scope.launch {
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            val todayMillis = today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

            val dueTransactions = scheduledTransactionRepository.getDueScheduledTransactions(todayMillis)

            // Plan the catch-up for each schedule, then look up which occurrences are already
            // posted. Each occurrence has a deterministic import id, so a catch-up that crashed
            // part-way can be re-run without double-posting the occurrences it already entered (N4).
            val plans = dueTransactions.associateWith { computeDueEntries(it, today, emptySet()) }
            // Import-id dedup is scoped per account, so group each schedule's candidate ids by the
            // account it posts into before checking which are already present.
            val existingImportIds = dueTransactions
                .flatMap { sched -> plans.getValue(sched).occurrences.map { sched.accountId to it.importId } }
                .groupBy({ it.first }, { it.second })
                .flatMap { (accountId, ids) -> transactionRepository.getExistingImportIds(accountId, ids) }
                .toSet()

            var entered = 0
            for (scheduled in dueTransactions) {
                val plan = plans.getValue(scheduled)
                for (occurrence in plan.occurrences) {
                    if (occurrence.importId in existingImportIds) continue // already posted

                    val now = Clock.System.now()
                    transactionRepository.insertTransaction(
                        Transaction(
                            accountId = scheduled.accountId,
                            date = occurrence.date,
                            amount = scheduled.amount,
                            payeeId = scheduled.payeeId,
                            categoryId = scheduled.categoryId,
                            memo = scheduled.memo,
                            isCleared = false,
                            importId = occurrence.importId,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    entered++
                }

                if (plan.deactivate) {
                    scheduledTransactionRepository.updateScheduledTransactionActive(scheduled.id, false)
                } else {
                    scheduledTransactionRepository.updateScheduledTransactionNextDate(scheduled.id, plan.newNextDateMillis)
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

    fun cleanup() {
        scope.cancel()
    }
}

data class ScheduledUiState(
    val scheduledTransactions: List<ScheduledTransactionWithDetails> = emptyList(),
    val isLoading: Boolean = true,
    val lastEnteredCount: Int? = null
)
