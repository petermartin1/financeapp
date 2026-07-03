package com.financeapp.ui.subscriptions

import com.financeapp.domain.model.DetectedSubscription
import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.SubscriptionStatus
import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.repository.PayeeRepository
import com.financeapp.domain.repository.SubscriptionRepository
import com.financeapp.ui.supervisedViewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubscriptionUiState(
    val subscriptions: List<DetectedSubscription> = emptyList(),
    val showDismissed: Boolean = false,
    val estimatedMonthlyCents: Long = 0,
    val pendingBridge: DetectedSubscription? = null,   // confirm->"track as scheduled?" offer
    val payees: List<Payee> = emptyList(),             // for the manual-add picker
    val showPayeePicker: Boolean = false
)

class SubscriptionViewModel(
    private val repository: SubscriptionRepository,
    private val payeeRepository: PayeeRepository
) {
    private val scope = supervisedViewModelScope()

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    private var all: List<DetectedSubscription> = emptyList()

    init {
        scope.launch {
            repository.getSubscriptions().collect { list ->
                all = list
                recompute()
            }
        }
        scope.launch {
            payeeRepository.getAllPayees().collect { payees ->
                _uiState.value = _uiState.value.copy(payees = payees)
            }
        }
    }

    fun confirm(id: Long) = launchAction {
        repository.confirm(id)
        // Offer the schedule bridge for payee-mapped, not-yet-bridged subscriptions.
        val sub = all.firstOrNull { it.id == id }
        if (sub?.payeeId != null && sub.scheduledTransactionId == null) {
            _uiState.value = _uiState.value.copy(pendingBridge = sub)
        }
    }

    fun dismiss(id: Long) = launchAction { repository.dismiss(id) }

    /** Accept the bridge offer: create the linked schedule and clear the prompt. */
    fun addScheduledForPending() {
        val pending = _uiState.value.pendingBridge ?: return
        _uiState.value = _uiState.value.copy(pendingBridge = null)
        launchAction { repository.createScheduledFromSubscription(pending.id) }
    }

    fun skipBridge() { _uiState.value = _uiState.value.copy(pendingBridge = null) }

    fun openPayeePicker() { _uiState.value = _uiState.value.copy(showPayeePicker = true) }
    fun closePayeePicker() { _uiState.value = _uiState.value.copy(showPayeePicker = false) }

    /** Manual escape hatch: mark the chosen payee as a subscription. */
    fun markPayeeAsSubscription(payeeId: Long) {
        _uiState.value = _uiState.value.copy(showPayeePicker = false)
        launchAction { repository.markPayeeAsSubscription(payeeId) }
    }

    fun toggleShowDismissed() {
        _uiState.value = _uiState.value.copy(showDismissed = !_uiState.value.showDismissed)
        recompute()
    }

    fun cleanup() {
        scope.cancel()
    }

    private fun launchAction(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    private fun recompute() {
        val showDismissed = _uiState.value.showDismissed
        val visible = all.filter { showDismissed || it.status != SubscriptionStatus.DISMISSED }
        val monthly = visible
            .filter { it.status != SubscriptionStatus.DISMISSED && it.isActive }
            .sumOf { monthlyEquivalentCents(it) }
        _uiState.value = _uiState.value.copy(
            subscriptions = visible,
            estimatedMonthlyCents = monthly
        )
    }

    private fun monthlyEquivalentCents(s: DetectedSubscription): Long = when (s.cadence) {
        TransactionFrequency.DAILY -> s.medianAmountCents * 30
        TransactionFrequency.WEEKLY -> s.medianAmountCents * 52 / 12
        TransactionFrequency.BIWEEKLY -> s.medianAmountCents * 26 / 12
        TransactionFrequency.MONTHLY -> s.medianAmountCents
        TransactionFrequency.YEARLY -> s.medianAmountCents / 12
    }
}
