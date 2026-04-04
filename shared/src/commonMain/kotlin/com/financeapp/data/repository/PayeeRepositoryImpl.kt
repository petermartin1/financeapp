package com.financeapp.data.repository

import com.financeapp.db.schema.PayeeAliases
import com.financeapp.db.schema.Payees
import com.financeapp.db.schema.ScheduledTransactions
import com.financeapp.db.schema.TransactionTemplates
import com.financeapp.db.schema.Transactions
import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.PayeeWithStats
import com.financeapp.domain.repository.PayeeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class PayeeRepositoryImpl(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PayeeRepository {

    // Trigger for reactive payee updates
    private val payeeRefreshTrigger = MutableStateFlow(0L)

    override fun notifyPayeesChanged() {
        payeeRefreshTrigger.value += 1
    }

    override fun getAllPayees(): Flow<List<Payee>> =
        payeeRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    Payees.selectAll()
                        .orderBy(Payees.name to SortOrder.ASC)
                        .map { it.toDomain() }
                }
            }
        }

    override fun getPayeesWithStats(): Flow<List<PayeeWithStats>> =
        payeeRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    val transactionCount = Transactions.id.count()

                    Payees
                        .leftJoin(Transactions, { Payees.id }, { Transactions.payeeId })
                        .select(Payees.columns + transactionCount)
                        .groupBy(Payees.id)
                        .orderBy(Payees.name to SortOrder.ASC)
                        .map { row ->
                            PayeeWithStats(
                                payee = Payee(
                                    id = row[Payees.id].value.toLong(),
                                    name = row[Payees.name],
                                    defaultCategoryId = row[Payees.defaultCategoryId]?.value?.toLong()
                                ),
                                transactionCount = row[transactionCount]
                            )
                        }
                }
            }
        }

    override suspend fun getPayeeById(id: Long): Payee? = withContext(ioDispatcher) {
        transaction(database) {
            Payees.selectAll().where { Payees.id eq id.toInt() }
                .singleOrNull()
                ?.toDomain()
        }
    }

    override suspend fun getPayeeByName(name: String): Payee? = withContext(ioDispatcher) {
        transaction(database) {
            Payees.selectAll().where { Payees.name.lowerCase() eq name.lowercase() }
                .singleOrNull()
                ?.toDomain()
        }
    }

    override suspend fun getPayeesByNames(names: List<String>): Map<String, Payee> = withContext(ioDispatcher) {
        if (names.isEmpty()) return@withContext emptyMap()

        transaction(database) {
            val lowercaseNames = names.map { it.lowercase() }
            Payees
                .selectAll().where { Payees.name.lowerCase() inList lowercaseNames }
                .map { it.toDomain() }
                .associateBy { it.name.lowercase() }
        }
    }

    override suspend fun insertPayee(payee: Payee): Long = withContext(ioDispatcher) {
        val id = transaction(database) {
            Payees.insert {
                it[name] = payee.name
                it[defaultCategoryId] = payee.defaultCategoryId?.toInt()
            }[Payees.id].value.toLong()
        }
        notifyPayeesChanged()
        id
    }

    override suspend fun batchInsertPayees(payees: List<Payee>): Map<String, Long> = withContext(ioDispatcher) {
        if (payees.isEmpty()) return@withContext emptyMap()

        val result = transaction(database) {
            payees.associate { payee ->
                val id = Payees.insert {
                    it[name] = payee.name
                    it[defaultCategoryId] = payee.defaultCategoryId?.toInt()
                }[Payees.id].value.toLong()
                payee.name.lowercase() to id
            }
        }
        notifyPayeesChanged()
        result
    }

    override suspend fun updatePayee(payee: Payee): Unit = withContext(ioDispatcher) {
        transaction(database) {
            Payees.update({ Payees.id eq payee.id.toInt() }) {
                it[name] = payee.name
                it[defaultCategoryId] = payee.defaultCategoryId?.toInt()
            }
        }
        notifyPayeesChanged()
    }

    override suspend fun deletePayee(id: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            // Nullify payee references in transactions
            Transactions.update({ Transactions.payeeId eq id.toInt() }) {
                it[payeeId] = null
            }
            // Nullify payee references in scheduled transactions
            ScheduledTransactions.update({ ScheduledTransactions.payeeId eq id.toInt() }) {
                it[payeeId] = null
            }
            // Nullify payee references in templates
            TransactionTemplates.update({ TransactionTemplates.payeeId eq id.toInt() }) {
                it[payeeId] = null
            }
            // Delete payee aliases
            PayeeAliases.deleteWhere { PayeeAliases.canonicalPayeeId eq id.toInt() }
            // Delete the payee
            Payees.deleteWhere { Payees.id eq id.toInt() }
        }
        notifyPayeesChanged()
    }

    override suspend fun mergePayees(sourceId: Long, targetId: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            // Update all transactions from source payee to target payee
            Transactions.update({ Transactions.payeeId eq sourceId.toInt() }) {
                it[payeeId] = targetId.toInt()
            }
            // Update scheduled transactions
            ScheduledTransactions.update({ ScheduledTransactions.payeeId eq sourceId.toInt() }) {
                it[payeeId] = targetId.toInt()
            }
            // Update transaction templates
            TransactionTemplates.update({ TransactionTemplates.payeeId eq sourceId.toInt() }) {
                it[payeeId] = targetId.toInt()
            }
            // Update payee aliases to point to target payee
            PayeeAliases.update({ PayeeAliases.canonicalPayeeId eq sourceId.toInt() }) {
                it[canonicalPayeeId] = targetId.toInt()
            }
            // Delete source payee
            Payees.deleteWhere { Payees.id eq sourceId.toInt() }
        }
        notifyPayeesChanged()
    }

    private fun ResultRow.toDomain(): Payee {
        return Payee(
            id = this[Payees.id].value.toLong(),
            name = this[Payees.name],
            defaultCategoryId = this[Payees.defaultCategoryId]?.value?.toLong()
        )
    }
}
