package com.financeapp.ui.connections

import com.financeapp.data.ofx.BankConfigs
import com.financeapp.data.ofx.BankConnectionInfo
import com.financeapp.data.ofx.OfxRepository
import com.financeapp.data.ofx.SyncSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class ConnectionsViewModel(
    private val ofxRepository: OfxRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _uiState = MutableStateFlow(ConnectionsUiState())
    val uiState: StateFlow<ConnectionsUiState> = _uiState.asStateFlow()

    init {
        loadConnections()
    }

    private fun loadConnections() {
        scope.launch {
            ofxRepository.getAllConnections().collect { connections ->
                _uiState.value = _uiState.value.copy(connections = connections)
            }
        }
    }

    fun addConnection(bankName: String, userId: String, password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        scope.launch {
            val result = ofxRepository.addConnection(bankName, userId, password)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showAddDialog = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to add connection"
                )
            }
        }
    }

    fun syncConnection(connectionId: Long) {
        _uiState.value = _uiState.value.copy(
            syncingConnectionId = connectionId,
            error = null
        )

        scope.launch {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val startDate = now.minus(30, DateTimeUnit.DAY)

            val result = ofxRepository.syncConnection(connectionId, startDate, now)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    syncingConnectionId = null,
                    lastSyncSummary = result.getOrNull()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    syncingConnectionId = null,
                    error = result.exceptionOrNull()?.message ?: "Sync failed"
                )
            }
        }
    }

    fun deleteConnection(connectionId: Long) {
        scope.launch {
            ofxRepository.deleteConnection(connectionId)
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true, error = null)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, error = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSyncSummary() {
        _uiState.value = _uiState.value.copy(lastSyncSummary = null)
    }

    fun cleanup() {
        scope.cancel()
    }
}

data class ConnectionsUiState(
    val connections: List<BankConnectionInfo> = emptyList(),
    val availableBanks: List<String> = BankConfigs.ALL_BANKS.map { it.name },
    val isLoading: Boolean = false,
    val syncingConnectionId: Long? = null,
    val showAddDialog: Boolean = false,
    val lastSyncSummary: SyncSummary? = null,
    val error: String? = null
)
