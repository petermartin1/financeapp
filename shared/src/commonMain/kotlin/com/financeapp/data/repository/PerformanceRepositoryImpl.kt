package com.financeapp.data.repository

import com.financeapp.db.schema.*
import com.financeapp.domain.model.*
import com.financeapp.domain.repository.InvestmentRepository
import com.financeapp.domain.repository.PerformanceRepository
import com.financeapp.domain.repository.QuoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Implementation of PerformanceRepository using Exposed ORM
 */
class PerformanceRepositoryImpl(
    private val database: Database,
    private val investmentRepository: InvestmentRepository,
    private val quoteRepository: QuoteRepository
) : PerformanceRepository {

    override suspend fun createPortfolioSnapshot(snapshotType: SnapshotType): Long = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()

        // Get all current holdings with their current prices (BEFORE transaction)
        val holdings = investmentRepository.getAllHoldings()
        val holdingsWithPrices = holdings.map { holding ->
            val quote = investmentRepository.getLatestPrice(holding.symbol)
            Triple(holding, quote?.price ?: 0L, holding.costBasis)
        }

        var totalValue = 0L
        var totalCostBasis = 0L

        transaction(database) {
            // Create portfolio snapshot
            val snapshotId = PortfolioSnapshots.insertAndGetId {
                it[date] = now
                it[PortfolioSnapshots.snapshotType] = snapshotType.name
                it[PortfolioSnapshots.totalValue] = 0L // Will update after calculating
                it[PortfolioSnapshots.totalCostBasis] = 0L
                it[PortfolioSnapshots.totalGainLoss] = 0L
            }

            // Create holding snapshots and calculate totals
            holdingsWithPrices.forEach { (holding, currentPrice, costBasisValue) ->
                val marketValue = (holding.shares * currentPrice).toLong()

                totalValue += marketValue
                totalCostBasis += costBasisValue

                HoldingSnapshots.insert {
                    it[portfolioSnapshotId] = snapshotId
                    it[holdingId] = holding.id.toInt()
                    it[symbol] = holding.symbol
                    it[shares] = holding.shares
                    it[costBasis] = costBasisValue
                    it[HoldingSnapshots.marketValue] = marketValue
                    it[price] = currentPrice
                }
            }

            // Update portfolio snapshot totals
            PortfolioSnapshots.update({ PortfolioSnapshots.id eq snapshotId }) {
                it[PortfolioSnapshots.totalValue] = totalValue
                it[PortfolioSnapshots.totalCostBasis] = totalCostBasis
                it[PortfolioSnapshots.totalGainLoss] = totalValue - totalCostBasis
            }

            snapshotId.value.toLong()
        }
    }

    override fun getPortfolioSnapshots(startDate: Long, endDate: Long): Flow<List<PortfolioSnapshot>> = flow {
        val snapshots = withContext(Dispatchers.IO) {
            transaction(database) {
                PortfolioSnapshots
                    .selectAll().where { (PortfolioSnapshots.date greaterEq startDate) and (PortfolioSnapshots.date lessEq endDate) }
                    .orderBy(PortfolioSnapshots.date to SortOrder.ASC)
                    .map { row ->
                        PortfolioSnapshot(
                            id = row[PortfolioSnapshots.id].value.toLong(),
                            date = row[PortfolioSnapshots.date],
                            totalValue = row[PortfolioSnapshots.totalValue],
                            totalCostBasis = row[PortfolioSnapshots.totalCostBasis],
                            totalGainLoss = row[PortfolioSnapshots.totalGainLoss],
                            snapshotType = SnapshotType.valueOf(row[PortfolioSnapshots.snapshotType])
                        )
                    }
            }
        }
        emit(snapshots)
    }

    override suspend fun getLatestPortfolioSnapshot(): PortfolioSnapshot? = withContext(Dispatchers.IO) {
        transaction(database) {
            PortfolioSnapshots
                .selectAll()
                .orderBy(PortfolioSnapshots.date to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.let { row ->
                    PortfolioSnapshot(
                        id = row[PortfolioSnapshots.id].value.toLong(),
                        date = row[PortfolioSnapshots.date],
                        totalValue = row[PortfolioSnapshots.totalValue],
                        totalCostBasis = row[PortfolioSnapshots.totalCostBasis],
                        totalGainLoss = row[PortfolioSnapshots.totalGainLoss],
                        snapshotType = SnapshotType.valueOf(row[PortfolioSnapshots.snapshotType])
                    )
                }
        }
    }

    override suspend fun deleteSnapshotsBefore(date: Long): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            val snapshotIds = PortfolioSnapshots
                .selectAll().where { PortfolioSnapshots.date less date }
                .map { it[PortfolioSnapshots.id] }

            if (snapshotIds.isNotEmpty()) {
                HoldingSnapshots.deleteWhere { portfolioSnapshotId inList snapshotIds.map { it.value } }
            }
            PortfolioSnapshots.deleteWhere { PortfolioSnapshots.date less date }
        }
    }

    override suspend fun createHoldingSnapshots(snapshotType: SnapshotType) {
        createPortfolioSnapshot(snapshotType)
    }

    override fun getHoldingSnapshots(holdingId: Long, startDate: Long, endDate: Long): Flow<List<HoldingSnapshot>> = flow {
        val snapshots = withContext(Dispatchers.IO) {
            transaction(database) {
                (HoldingSnapshots innerJoin PortfolioSnapshots)
                    .selectAll().where {
                        (HoldingSnapshots.holdingId eq holdingId.toInt()) and
                        (PortfolioSnapshots.date greaterEq startDate) and
                        (PortfolioSnapshots.date lessEq endDate)
                    }
                    .orderBy(PortfolioSnapshots.date to SortOrder.ASC)
                    .map { row ->
                        val costBasis = row[HoldingSnapshots.costBasis]
                        val marketValue = row[HoldingSnapshots.marketValue]
                        HoldingSnapshot(
                            id = row[HoldingSnapshots.id].value.toLong(),
                            holdingId = holdingId,
                            date = row[PortfolioSnapshots.date],
                            quantity = row[HoldingSnapshots.shares],
                            price = row[HoldingSnapshots.price],
                            value = marketValue,
                            costBasis = costBasis,
                            gainLoss = marketValue - costBasis,
                            snapshotType = SnapshotType.valueOf(row[PortfolioSnapshots.snapshotType])
                        )
                    }
            }
        }
        emit(snapshots)
    }

    override suspend fun getHoldingSnapshotsForDate(date: Long): List<HoldingSnapshot> = withContext(Dispatchers.IO) {
        transaction(database) {
            (HoldingSnapshots innerJoin PortfolioSnapshots)
                .selectAll().where { PortfolioSnapshots.date eq date }
                .map { row ->
                    val costBasis = row[HoldingSnapshots.costBasis]
                    val marketValue = row[HoldingSnapshots.marketValue]
                    HoldingSnapshot(
                        id = row[HoldingSnapshots.id].value.toLong(),
                        holdingId = row[HoldingSnapshots.holdingId]?.value?.toLong() ?: 0L,
                        date = row[PortfolioSnapshots.date],
                        quantity = row[HoldingSnapshots.shares],
                        price = row[HoldingSnapshots.price],
                        value = marketValue,
                        costBasis = costBasis,
                        gainLoss = marketValue - costBasis,
                        snapshotType = SnapshotType.valueOf(row[PortfolioSnapshots.snapshotType])
                    )
                }
        }
    }

    override suspend fun calculatePerformanceMetrics(timeRange: TimeRange): PerformanceMetrics? = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val startDate = if (timeRange == TimeRange.ALL_TIME) {
            0L
        } else {
            now - (timeRange.days * 24 * 60 * 60 * 1000L)
        }

        transaction(database) {
            val snapshots = PortfolioSnapshots
                .selectAll().where { (PortfolioSnapshots.date greaterEq startDate) and (PortfolioSnapshots.date lessEq now) }
                .orderBy(PortfolioSnapshots.date to SortOrder.ASC)
                .map { row ->
                    PortfolioSnapshot(
                        id = row[PortfolioSnapshots.id].value.toLong(),
                        date = row[PortfolioSnapshots.date],
                        totalValue = row[PortfolioSnapshots.totalValue],
                        totalCostBasis = row[PortfolioSnapshots.totalCostBasis],
                        totalGainLoss = row[PortfolioSnapshots.totalGainLoss],
                        snapshotType = SnapshotType.valueOf(row[PortfolioSnapshots.snapshotType])
                    )
                }

            if (snapshots.isEmpty()) return@transaction null

            val startSnapshot = snapshots.first()
            val endSnapshot = snapshots.last()

            val totalReturn = endSnapshot.totalValue - startSnapshot.totalValue
            val totalReturnPercent = if (startSnapshot.totalValue > 0) {
                (totalReturn.toDouble() / startSnapshot.totalValue.toDouble()) * 100.0
            } else {
                0.0
            }

            // Calculate time-weighted return (simplified version)
            val days = ((endSnapshot.date - startSnapshot.date) / (24 * 60 * 60 * 1000L)).toInt()
            val timeWeightedReturn = if (days > 0 && startSnapshot.totalValue > 0) {
                val dailyReturn = totalReturnPercent / 100.0
                val annualizationFactor = 365.0 / days.toDouble()
                ((1.0 + dailyReturn).pow(annualizationFactor) - 1.0) * 100.0
            } else {
                0.0
            }

            // Calculate high and low water marks
            val highWaterMark = snapshots.maxOf { it.totalValue }
            val lowWaterMark = snapshots.minOf { it.totalValue }

            // Calculate volatility (standard deviation of returns)
            val returns = snapshots.zipWithNext { a, b ->
                if (a.totalValue > 0) {
                    (b.totalValue.toDouble() - a.totalValue.toDouble()) / a.totalValue.toDouble()
                } else {
                    0.0
                }
            }

            val volatility = if (returns.isNotEmpty()) {
                val mean = returns.average()
                val variance = returns.map { (it - mean).pow(2) }.average()
                sqrt(variance) * 100.0
            } else {
                0.0
            }

            PerformanceMetrics(
                timeRange = timeRange,
                startValue = startSnapshot.totalValue,
                endValue = endSnapshot.totalValue,
                totalReturn = totalReturn,
                totalReturnPercent = totalReturnPercent,
                timeWeightedReturn = timeWeightedReturn,
                highWaterMark = highWaterMark,
                lowWaterMark = lowWaterMark,
                volatility = volatility
            )
        }
    }

    override fun getAllHoldingPerformance(): Flow<List<HoldingPerformance>> = flow {
        val holdings = investmentRepository.getAllHoldings()
        val performances = holdings.map { holding ->
            calculateHoldingPerformance(holding)
        }
        emit(performances)
    }

    override suspend fun getHoldingPerformance(holdingId: Long): HoldingPerformance? = withContext(Dispatchers.IO) {
        val holding = investmentRepository.getHoldingById(holdingId) ?: return@withContext null
        calculateHoldingPerformance(holding)
    }

    private suspend fun calculateHoldingPerformance(holding: Holding): HoldingPerformance {
        val quote = investmentRepository.getLatestPrice(holding.symbol)
        val currentPrice = quote?.price ?: 0L
        // Get previous price from history (we don't have previousClose in SecurityPrice model)
        val priceHistory = investmentRepository.getPriceHistory(holding.symbol, 2)
        val previousPrice = if (priceHistory.size > 1) priceHistory[1].price else currentPrice

        val currentValue = (holding.shares * currentPrice).toLong()
        val costBasisTotal = holding.costBasis
        val gainLoss = currentValue - costBasisTotal
        val gainLossPercent = if (costBasisTotal > 0) {
            (gainLoss.toDouble() / costBasisTotal.toDouble()) * 100.0
        } else {
            0.0
        }

        val dayChange = (holding.shares * (currentPrice - previousPrice)).toLong()
        val dayChangePercent = if (previousPrice > 0) {
            ((currentPrice - previousPrice).toDouble() / previousPrice.toDouble()) * 100.0
        } else {
            0.0
        }

        // Calculate allocation
        val totalPortfolioValue = investmentRepository.getAllHoldings()
            .sumOf { h ->
                val q = investmentRepository.getLatestPrice(h.symbol)
                val price = q?.price ?: 0L
                (h.shares * price).toLong()
            }

        val allocation = if (totalPortfolioValue > 0) {
            (currentValue.toDouble() / totalPortfolioValue.toDouble()) * 100.0
        } else {
            0.0
        }

        return HoldingPerformance(
            holdingId = holding.id,
            symbol = holding.symbol,
            name = holding.name ?: "",
            quantity = holding.shares,
            costBasis = costBasisTotal,
            currentPrice = currentPrice,
            currentValue = currentValue,
            gainLoss = gainLoss,
            gainLossPercent = gainLossPercent,
            dayChange = dayChange,
            dayChangePercent = dayChangePercent,
            allocation = allocation
        )
    }

    override suspend fun getPerformanceSummary(): PerformanceSummary = withContext(Dispatchers.IO) {
        val holdings = investmentRepository.getAllHoldings()
        val holdingPerformances = holdings.map { calculateHoldingPerformance(it) }

        val totalValue = holdingPerformances.sumOf { it.currentValue }
        val totalCostBasis = holdingPerformances.sumOf { it.costBasis }
        val totalGainLoss = totalValue - totalCostBasis
        val totalGainLossPercent = if (totalCostBasis > 0) {
            (totalGainLoss.toDouble() / totalCostBasis.toDouble()) * 100.0
        } else {
            0.0
        }

        val dayChange = holdingPerformances.sumOf { it.dayChange }
        val previousValue = totalValue - dayChange
        val dayChangePercent = if (previousValue > 0) {
            (dayChange.toDouble() / previousValue.toDouble()) * 100.0
        } else {
            0.0
        }

        val bestPerformer = holdingPerformances.maxByOrNull { it.gainLossPercent }
        val worstPerformer = holdingPerformances.minByOrNull { it.gainLossPercent }
        val topHoldings = holdingPerformances.sortedByDescending { it.allocation }.take(5)

        PerformanceSummary(
            totalValue = totalValue,
            totalCostBasis = totalCostBasis,
            totalGainLoss = totalGainLoss,
            totalGainLossPercent = totalGainLossPercent,
            dayChange = dayChange,
            dayChangePercent = dayChangePercent,
            bestPerformer = bestPerformer,
            worstPerformer = worstPerformer,
            topHoldings = topHoldings
        )
    }

    override suspend fun getPerformanceChartData(timeRange: TimeRange): PerformanceChartData = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val startDate = if (timeRange == TimeRange.ALL_TIME) {
            0L
        } else {
            now - (timeRange.days * 24 * 60 * 60 * 1000L)
        }

        transaction(database) {
            val rows = PortfolioSnapshots
                .selectAll().where { (PortfolioSnapshots.date greaterEq startDate) and (PortfolioSnapshots.date lessEq now) }
                .orderBy(PortfolioSnapshots.date to SortOrder.ASC)
                .toList()

            var previousValue: Long? = null
            val snapshots = rows.map { row ->
                val value = row[PortfolioSnapshots.totalValue]
                val baseValue = previousValue ?: row[PortfolioSnapshots.totalCostBasis]

                val gainLoss = value - baseValue
                val gainLossPercent = if (baseValue > 0) {
                    (gainLoss.toDouble() / baseValue.toDouble()) * 100.0
                } else {
                    0.0
                }

                previousValue = value

                PerformanceDataPoint(
                    date = row[PortfolioSnapshots.date],
                    value = value,
                    gainLoss = gainLoss,
                    gainLossPercent = gainLossPercent
                )
            }

            PerformanceChartData(
                timeRange = timeRange,
                dataPoints = snapshots
            )
        }
    }

    override suspend fun getHoldingChartData(holdingId: Long, timeRange: TimeRange): PerformanceChartData = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val startDate = if (timeRange == TimeRange.ALL_TIME) {
            0L
        } else {
            now - (timeRange.days * 24 * 60 * 60 * 1000L)
        }

        transaction(database) {
            val rows = (HoldingSnapshots innerJoin PortfolioSnapshots)
                .selectAll().where {
                    (HoldingSnapshots.holdingId eq holdingId.toInt()) and
                    (PortfolioSnapshots.date greaterEq startDate) and
                    (PortfolioSnapshots.date lessEq now)
                }
                .orderBy(PortfolioSnapshots.date to SortOrder.ASC)
                .toList()

            var previousValue: Long? = null
            val snapshots = rows.map { row ->
                val value = row[HoldingSnapshots.marketValue]
                val baseValue = previousValue ?: row[HoldingSnapshots.costBasis]

                val gainLoss = value - baseValue
                val gainLossPercent = if (baseValue > 0) {
                    (gainLoss.toDouble() / baseValue.toDouble()) * 100.0
                } else {
                    0.0
                }

                previousValue = value

                PerformanceDataPoint(
                    date = row[PortfolioSnapshots.date],
                    value = value,
                    gainLoss = gainLoss,
                    gainLossPercent = gainLossPercent
                )
            }

            PerformanceChartData(
                timeRange = timeRange,
                dataPoints = snapshots
            )
        }
    }

    override suspend fun recordDividend(dividend: DividendEvent): Long = withContext(Dispatchers.IO) {
        transaction(database) {
            DividendEvents.insertAndGetId {
                it[holdingId] = dividend.holdingId.toInt()
                it[symbol] = dividend.symbol
                it[paymentDate] = dividend.paymentDate
                it[amount] = dividend.amount
                it[perShare] = dividend.perShare
                it[shares] = dividend.shares
                it[isReinvested] = dividend.isReinvested
            }.value.toLong()
        }
    }

    override fun getHoldingDividends(holdingId: Long): Flow<List<DividendEvent>> = flow {
        val dividends = withContext(Dispatchers.IO) {
            transaction(database) {
                DividendEvents
                    .selectAll().where { DividendEvents.holdingId eq holdingId.toInt() }
                    .orderBy(DividendEvents.paymentDate to SortOrder.DESC)
                    .map { row ->
                        DividendEvent(
                            id = row[DividendEvents.id].value.toLong(),
                            holdingId = row[DividendEvents.holdingId].value.toLong(),
                            symbol = row[DividendEvents.symbol],
                            paymentDate = row[DividendEvents.paymentDate],
                            amount = row[DividendEvents.amount],
                            perShare = row[DividendEvents.perShare],
                            shares = row[DividendEvents.shares],
                            isReinvested = row[DividendEvents.isReinvested]
                        )
                    }
            }
        }
        emit(dividends)
    }

    override fun getDividends(startDate: Long, endDate: Long): Flow<List<DividendEvent>> = flow {
        val dividends = withContext(Dispatchers.IO) {
            transaction(database) {
                DividendEvents
                    .selectAll().where { (DividendEvents.paymentDate greaterEq startDate) and (DividendEvents.paymentDate lessEq endDate) }
                    .orderBy(DividendEvents.paymentDate to SortOrder.DESC)
                    .map { row ->
                        DividendEvent(
                            id = row[DividendEvents.id].value.toLong(),
                            holdingId = row[DividendEvents.holdingId].value.toLong(),
                            symbol = row[DividendEvents.symbol],
                            paymentDate = row[DividendEvents.paymentDate],
                            amount = row[DividendEvents.amount],
                            perShare = row[DividendEvents.perShare],
                            shares = row[DividendEvents.shares],
                            isReinvested = row[DividendEvents.isReinvested]
                        )
                    }
            }
        }
        emit(dividends)
    }

    override suspend fun getTotalDividends(holdingId: Long): Long = withContext(Dispatchers.IO) {
        transaction(database) {
            DividendEvents
                .select(DividendEvents.amount.sum())
                .where { DividendEvents.holdingId eq holdingId.toInt() }
                .singleOrNull()
                ?.get(DividendEvents.amount.sum()) ?: 0L
        }
    }
}
