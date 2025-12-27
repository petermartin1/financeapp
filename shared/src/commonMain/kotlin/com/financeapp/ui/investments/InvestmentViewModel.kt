package com.financeapp.ui.investments

import com.financeapp.domain.model.AccountType
import com.financeapp.domain.model.AssetAllocation
import com.financeapp.domain.model.Holding
import com.financeapp.domain.model.HoldingWithPrice
import com.financeapp.domain.model.PortfolioSummary
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.InvestmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class InvestmentUiState(
    val portfolio: PortfolioSummary = PortfolioSummary(
        holdings = emptyList(),
        totalCostBasis = 0,
        totalMarketValue = 0,
        totalGainLoss = 0,
        totalGainLossPercent = 0.0
    ),
    val assetAllocation: List<AssetAllocation> = emptyList(),
    val investmentAccounts: List<Pair<Long, String>> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTab: Int = 0  // 0 = Holdings, 1 = Allocation
)

class InvestmentViewModel(
    private val investmentRepository: InvestmentRepository,
    private val accountRepository: AccountRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val _uiState = MutableStateFlow(InvestmentUiState())
    val uiState: StateFlow<InvestmentUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        scope.launch {
            // Load investment accounts
            accountRepository.getAllAccounts().collect { accounts ->
                val investmentAccounts = accounts
                    .filter { it.type == AccountType.INVESTMENT }
                    .map { it.id to it.name }
                _uiState.value = _uiState.value.copy(investmentAccounts = investmentAccounts)
            }
        }

        scope.launch {
            investmentRepository.getPortfolio().collect { holdings ->
                val totalCostBasis = holdings.sumOf { it.holding.costBasis }
                val totalMarketValue = holdings.sumOf { it.marketValue }
                val totalGainLoss = totalMarketValue - totalCostBasis
                val totalGainLossPercent = if (totalCostBasis > 0) {
                    (totalGainLoss.toDouble() / totalCostBasis) * 100
                } else 0.0

                val portfolio = PortfolioSummary(
                    holdings = holdings,
                    totalCostBasis = totalCostBasis,
                    totalMarketValue = totalMarketValue,
                    totalGainLoss = totalGainLoss,
                    totalGainLossPercent = totalGainLossPercent
                )

                // Calculate asset allocation
                val allocation = if (totalMarketValue > 0) {
                    holdings.map { h ->
                        AssetAllocation(
                            symbol = h.holding.symbol,
                            name = h.holding.name,
                            marketValue = h.marketValue,
                            percentage = (h.marketValue.toDouble() / totalMarketValue) * 100
                        )
                    }.sortedByDescending { it.marketValue }
                } else emptyList()

                _uiState.value = _uiState.value.copy(
                    portfolio = portfolio,
                    assetAllocation = allocation,
                    isLoading = false
                )
            }
        }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun addHolding(
        accountId: Long,
        symbol: String,
        name: String?,
        shares: Double,
        costBasis: Long
    ) {
        scope.launch {
            val holding = Holding(
                accountId = accountId,
                symbol = symbol.uppercase(),
                name = name,
                shares = shares,
                costBasis = costBasis
            )
            investmentRepository.insertHolding(holding)
        }
    }

    fun updateHolding(holding: Holding) {
        scope.launch {
            investmentRepository.updateHolding(holding)
        }
    }

    fun deleteHolding(id: Long) {
        scope.launch {
            investmentRepository.deleteHolding(id)
        }
    }

    fun updatePrice(symbol: String, price: Long) {
        scope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            investmentRepository.updatePrice(symbol, price, now)
        }
    }
}
