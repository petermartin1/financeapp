package com.financeapp.domain.model

data class Holding(
    val id: Long = 0,
    val accountId: Long,
    val symbol: String,
    val name: String?,
    val shares: Double,
    val costBasis: Long  // in cents
)

data class HoldingLot(
    val id: Long = 0,
    val holdingId: Long,
    val acquiredDate: Long,
    val purpose: String?,
    val shares: Double,
    val costBasis: Long,
    val notes: String? = null
)

data class SecurityPrice(
    val id: Long = 0,
    val symbol: String,
    val date: Long,  // timestamp
    val price: Long  // in cents
)

data class HoldingWithPrice(
    val holding: Holding,
    val currentPrice: Long?,  // in cents, null if no price available
    val accountName: String
) {
    val marketValue: Long
        get() = currentPrice?.let { (holding.shares * it).toLong() } ?: 0L

    val gainLoss: Long
        get() = marketValue - holding.costBasis

    val gainLossPercent: Double
        get() = if (holding.costBasis > 0) {
            (gainLoss.toDouble() / holding.costBasis) * 100
        } else 0.0
}

data class PortfolioSummary(
    val holdings: List<HoldingWithPrice>,
    val totalCostBasis: Long,
    val totalMarketValue: Long,
    val totalGainLoss: Long,
    val totalGainLossPercent: Double
)

data class AssetAllocation(
    val symbol: String,
    val name: String?,
    val marketValue: Long,
    val percentage: Double
)
