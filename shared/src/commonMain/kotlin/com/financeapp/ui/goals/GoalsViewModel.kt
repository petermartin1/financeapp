package com.financeapp.ui.goals

import com.financeapp.domain.model.Account
import com.financeapp.domain.model.AccountType
import com.financeapp.domain.model.GoalWithProgress
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.GoalRepository
import com.financeapp.ui.launchReporting
import com.financeapp.ui.supervisedViewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GoalsUiState(
    val goals: List<GoalWithProgress> = emptyList(),
    val showArchived: Boolean = false,
    val accounts: List<Account> = emptyList()   // picker list, savings-type first
)

class GoalsViewModel(
    private val goalRepository: GoalRepository,
    private val accountRepository: AccountRepository
) {
    private val scope = supervisedViewModelScope()

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    private var all: List<GoalWithProgress> = emptyList()

    init {
        scope.launch {
            goalRepository.getGoalsWithProgress().collect { list ->
                all = list
                recompute()
            }
        }
        scope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(
                    accounts = accounts.sortedByDescending { it.type == AccountType.SAVINGS }
                )
            }
        }
    }

    fun createGoal(name: String, targetAmountCents: Long, accountId: Long, deadlineMs: Long?) =
        scope.launchReporting("create the goal") {
            goalRepository.createGoal(name, targetAmountCents, accountId, deadlineMs)
        }

    fun updateGoal(id: Long, name: String, targetAmountCents: Long, accountId: Long?, deadlineMs: Long?) =
        scope.launchReporting("update the goal") {
            goalRepository.updateGoal(id, name, targetAmountCents, accountId, deadlineMs)
        }

    fun setArchived(id: Long, archived: Boolean) =
        scope.launchReporting("archive the goal") { goalRepository.setArchived(id, archived) }

    fun deleteGoal(id: Long) =
        scope.launchReporting("delete the goal") { goalRepository.deleteGoal(id) }

    fun toggleShowArchived() {
        _uiState.value = _uiState.value.copy(showArchived = !_uiState.value.showArchived)
        recompute()
    }

    fun cleanup() {
        scope.cancel()
    }

    private fun recompute() {
        val showArchived = _uiState.value.showArchived
        _uiState.value = _uiState.value.copy(
            goals = all.filter { showArchived || !it.goal.archived }
        )
    }

    companion object {
        /** "1,234.56" / "$5000" -> positive cents; null when malformed, non-positive, or > 2 decimals. */
        fun parseDollarsToCents(text: String): Long? {
            val cleaned = text.trim().removePrefix("$").replace(",", "")
            if (!Regex("""^\d+(\.\d{1,2})?$""").matches(cleaned)) return null
            val parts = cleaned.split(".")
            val dollars = parts[0].toLongOrNull() ?: return null
            if (dollars > Long.MAX_VALUE / 100 - 1) return null
            val cents = if (parts.size == 2) parts[1].padEnd(2, '0').toLong() else 0L
            val total = dollars * 100 + cents
            return if (total > 0) total else null
        }
    }
}
