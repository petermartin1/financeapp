package com.financeapp.data.repository

import com.financeapp.db.schema.Accounts
import com.financeapp.db.schema.Holdings
import com.financeapp.db.schema.SecurityPrices
import com.financeapp.domain.model.Holding
import com.financeapp.domain.model.HoldingWithPrice
import com.financeapp.domain.model.SecurityPrice
import com.financeapp.domain.repository.InvestmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class InvestmentRepositoryImpl(
    private val database: Database
) : InvestmentRepository {

    override fun getPortfolio(): Flow<List<HoldingWithPrice>> = flow {
        val portfolio = withContext(Dispatchers.IO) {
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
        emit(portfolio)
    }

    override fun getHoldingsByAccount(accountId: Long): Flow<List<Holding>> = flow {
        val holdings = withContext(Dispatchers.IO) {
            transaction(database) {
                Holdings
                    .selectAll().where { Holdings.accountId eq accountId.toInt() }
                    .map { it.toHoldingDomain() }
            }
        }
        emit(holdings)
    }

    override suspend fun getHoldingById(id: Long): Holding? = withContext(Dispatchers.IO) {
        transaction(database) {
            Holdings.selectAll().where { Holdings.id eq id.toInt() }
                .singleOrNull()
                ?.toHoldingDomain()
        }
    }

    override suspend fun insertHolding(holding: Holding): Long = withContext(Dispatchers.IO) {
        transaction(database) {
            Holdings.insert {
                it[accountId] = holding.accountId.toInt()
                it[symbol] = holding.symbol
                it[name] = holding.name
                it[shares] = holding.shares
                it[costBasis] = holding.costBasis
            }[Holdings.id].value.toLong()
        }
    }

    override suspend fun updateHolding(holding: Holding): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            Holdings.update({ Holdings.id eq holding.id.toInt() }) {
                it[symbol] = holding.symbol
                it[name] = holding.name
                it[shares] = holding.shares
                it[costBasis] = holding.costBasis
            }
        }
    }

    override suspend fun deleteHolding(id: Long): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            Holdings.deleteWhere { Holdings.id eq id.toInt() }
        }
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

    override suspend fun updatePrice(symbol: String, price: Long, date: Long): Unit = withContext(Dispatchers.IO) {
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

    private fun ResultRow.toPriceDomain(): SecurityPrice {
        return SecurityPrice(
            id = this[SecurityPrices.id].value.toLong(),
            symbol = this[SecurityPrices.symbol],
            date = this[SecurityPrices.date],
            price = this[SecurityPrices.price]
        )
    }
}
