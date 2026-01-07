package com.financeapp.data.repository

import com.financeapp.db.schema.Accounts
import com.financeapp.db.schema.HoldingLots
import com.financeapp.db.schema.Holdings
import com.financeapp.db.schema.SecurityPrices
import com.financeapp.domain.model.Holding
import com.financeapp.domain.model.HoldingLot
import com.financeapp.domain.model.HoldingWithPrice
import com.financeapp.domain.model.SecurityPrice
import com.financeapp.domain.repository.InvestmentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class InvestmentRepositoryImpl(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : InvestmentRepository {

    // Triggers for reactive updates
    private val holdingsRefreshTrigger = MutableStateFlow(0L)
    private val pricesRefreshTrigger = MutableStateFlow(0L)
    private val lotsRefreshTrigger = MutableStateFlow(0L)

    override fun notifyHoldingsChanged() {
        holdingsRefreshTrigger.value += 1
    }

    override fun notifyPricesChanged() {
        pricesRefreshTrigger.value += 1
    }

    private fun notifyLotsChanged() {
        lotsRefreshTrigger.value += 1
    }

    override fun getPortfolio(): Flow<List<HoldingWithPrice>> =
        combine(holdingsRefreshTrigger, pricesRefreshTrigger) { _, _ -> Unit }
            .map { _ ->
                withContext(ioDispatcher) {
                    transaction(database) {
                        // Join Holdings with latest SecurityPrices and Accounts
                        Holdings
                            .leftJoin(Accounts, { accountId }, { Accounts.id })
                            .selectAll()
                            .map { row ->
                                val holding = row.toHoldingDomain()

                                // Get latest price for this symbol
                                val latestPrice = SecurityPrices
                                    .selectAll().where { SecurityPrices.symbol eq holding.symbol }
                                    .orderBy(SecurityPrices.date to SortOrder.DESC)
                                    .limit(1)
                                    .singleOrNull()
                                    ?.get(SecurityPrices.price)

                                HoldingWithPrice(
                                    holding = holding,
                                    currentPrice = latestPrice,
                                    accountName = row[Accounts.name]
                                )
                            }
                    }
                }
            }

    override fun getHoldingsByAccount(accountId: Long): Flow<List<Holding>> =
        holdingsRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    Holdings
                        .selectAll().where { Holdings.accountId eq accountId.toInt() }
                        .map { it.toHoldingDomain() }
                }
            }
        }

    override fun getLots(holdingId: Long): Flow<List<HoldingLot>> =
        lotsRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    ensureLotsExist(holdingId.toInt())
                    HoldingLots
                        .select { HoldingLots.holdingId eq holdingId.toInt() }
                        .orderBy(HoldingLots.acquiredDate to SortOrder.ASC, HoldingLots.id to SortOrder.ASC)
                        .map { it.toLotDomain() }
                }
            }
        }

    override suspend fun getHoldingById(id: Long): Holding? = withContext(Dispatchers.IO) {
        transaction(database) {
            Holdings.selectAll().where { Holdings.id eq id.toInt() }
                .singleOrNull()
                ?.toHoldingDomain()
        }
    }

    override suspend fun getAllHoldings(): List<Holding> = withContext(Dispatchers.IO) {
        transaction(database) {
            Holdings.selectAll().map { it.toHoldingDomain() }
        }
    }

    override suspend fun insertHolding(holding: Holding): Long = withContext(ioDispatcher) {
        val id = transaction(database) {
            Holdings.insert {
                it[accountId] = holding.accountId.toInt()
                it[symbol] = holding.symbol
                it[name] = holding.name
                it[shares] = holding.shares
                it[costBasis] = holding.costBasis
            }[Holdings.id].value.toLong()
        }
        notifyHoldingsChanged()
        id
    }

    override suspend fun updateHolding(holding: Holding): Unit = withContext(ioDispatcher) {
        transaction(database) {
            Holdings.update({ Holdings.id eq holding.id.toInt() }) {
                it[symbol] = holding.symbol
                it[name] = holding.name
                it[shares] = holding.shares
                it[costBasis] = holding.costBasis
            }
        }
        notifyHoldingsChanged()
    }

    override suspend fun deleteHolding(id: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            Holdings.deleteWhere { Holdings.id eq id.toInt() }
        }
        notifyHoldingsChanged()
    }

    override suspend fun insertHoldingLot(lot: HoldingLot): Long = withContext(ioDispatcher) {
        val lotId = transaction(database) {
            val insertedId = HoldingLots.insert {
                it[holdingId] = lot.holdingId.toInt()
                it[acquiredDate] = lot.acquiredDate
                it[purpose] = lot.purpose
                it[shares] = lot.shares
                it[costBasis] = lot.costBasis
                it[notes] = lot.notes
            }[HoldingLots.id].value.toLong()

            recalculateHoldingTotals(lot.holdingId.toInt())
            insertedId
        }
        notifyLotsChanged()
        notifyHoldingsChanged()
        lotId
    }

    override suspend fun updateHoldingLot(lot: HoldingLot): Unit = withContext(ioDispatcher) {
        transaction(database) {
            HoldingLots.update({ HoldingLots.id eq lot.id.toInt() }) {
                it[acquiredDate] = lot.acquiredDate
                it[purpose] = lot.purpose
                it[shares] = lot.shares
                it[costBasis] = lot.costBasis
                it[notes] = lot.notes
            }
            recalculateHoldingTotals(lot.holdingId.toInt())
        }
        notifyLotsChanged()
        notifyHoldingsChanged()
    }

    override suspend fun deleteHoldingLot(id: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            val holdingId = HoldingLots
                .select { HoldingLots.id eq id.toInt() }
                .singleOrNull()
                ?.get(HoldingLots.holdingId)
                ?.value

            HoldingLots.deleteWhere { HoldingLots.id eq id.toInt() }

            holdingId?.let { recalculateHoldingTotals(it) }
        }
        notifyLotsChanged()
        notifyHoldingsChanged()
    }

    override suspend fun getLatestPrice(symbol: String): SecurityPrice? = withContext(Dispatchers.IO) {
        transaction(database) {
            SecurityPrices
                .selectAll().where { SecurityPrices.symbol eq symbol }
                .orderBy(SecurityPrices.date to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toPriceDomain()
        }
    }

    override suspend fun updatePrice(symbol: String, price: Long, date: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            // Insert or update security price
            val existing = SecurityPrices
                .selectAll().where { (SecurityPrices.symbol eq symbol) and (SecurityPrices.date eq date) }
                .singleOrNull()

            if (existing != null) {
                SecurityPrices.update({
                    (SecurityPrices.symbol eq symbol) and (SecurityPrices.date eq date)
                }) {
                    it[SecurityPrices.price] = price
                }
            } else {
                SecurityPrices.insert {
                    it[SecurityPrices.symbol] = symbol
                    it[SecurityPrices.date] = date
                    it[SecurityPrices.price] = price
                }
            }
        }
        notifyPricesChanged()
    }

    override suspend fun getPriceHistory(symbol: String, limit: Int): List<SecurityPrice> = withContext(Dispatchers.IO) {
        transaction(database) {
            SecurityPrices
                .selectAll().where { SecurityPrices.symbol eq symbol }
                .orderBy(SecurityPrices.date to SortOrder.DESC)
                .limit(limit)
                .map { it.toPriceDomain() }
        }
    }

    private fun ResultRow.toHoldingDomain(): Holding {
        return Holding(
            id = this[Holdings.id].value.toLong(),
            accountId = this[Holdings.accountId].value.toLong(),
            symbol = this[Holdings.symbol],
            name = this[Holdings.name],
            shares = this[Holdings.shares],
            costBasis = this[Holdings.costBasis]
        )
    }

    private fun ResultRow.toLotDomain(): HoldingLot {
        return HoldingLot(
            id = this[HoldingLots.id].value.toLong(),
            holdingId = this[HoldingLots.holdingId].value.toLong(),
            acquiredDate = this[HoldingLots.acquiredDate],
            purpose = this[HoldingLots.purpose],
            shares = this[HoldingLots.shares],
            costBasis = this[HoldingLots.costBasis],
            notes = this[HoldingLots.notes]
        )
    }

    private fun ResultRow.toPriceDomain(): SecurityPrice {
        return SecurityPrice(
            id = this[SecurityPrices.id].value.toLong(),
            symbol = this[SecurityPrices.symbol],
            date = this[SecurityPrices.date],
            price = this[SecurityPrices.price]
        )
    }

    private fun Transaction.ensureLotsExist(holdingId: Int) {
        val hasLots = HoldingLots
            .select { HoldingLots.holdingId eq holdingId }
            .limit(1)
            .any()

        if (hasLots) return

        val holdingRow = Holdings
            .select { Holdings.id eq holdingId }
            .singleOrNull() ?: return

        val sharesValue = holdingRow[Holdings.shares]
        val costBasisValue = holdingRow[Holdings.costBasis]

        if (sharesValue == 0.0 && costBasisValue == 0L) {
            return
        }

        HoldingLots.insert {
            it[this.holdingId] = holdingId
            it[acquiredDate] = Clock.System.now().toEpochMilliseconds()
            it[purpose] = "Migrated lot"
            it[shares] = sharesValue
            it[costBasis] = costBasisValue
            it[notes] = "Auto-created from legacy holding data"
        }
        recalculateHoldingTotals(holdingId)
    }

    private fun Transaction.recalculateHoldingTotals(holdingId: Int) {
        val shareSum = HoldingLots.shares.sum()
        val costSum = HoldingLots.costBasis.sum()

        val totals = HoldingLots
            .slice(shareSum, costSum)
            .select { HoldingLots.holdingId eq holdingId }
            .singleOrNull()

        val totalShares = totals?.get(shareSum) ?: 0.0
        val totalCostBasis = totals?.get(costSum) ?: 0L

        Holdings.update({ Holdings.id eq holdingId }) {
            it[shares] = totalShares
            it[costBasis] = totalCostBasis
        }
    }
}
