package com.financeapp.data.repository

import com.financeapp.db.schema.*
import com.financeapp.domain.model.ScheduledTransaction
import com.financeapp.domain.model.ScheduledTransactionWithDetails
import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.repository.ScheduledTransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ScheduledTransactionRepositoryImpl(
    private val database: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ScheduledTransactionRepository {

    // Trigger for reactive updates
    private val scheduledTransactionRefreshTrigger = MutableStateFlow(0L)

    override fun notifyScheduledTransactionsChanged() {
        scheduledTransactionRefreshTrigger.value += 1
    }

    override fun getAllScheduledTransactions(): Flow<List<ScheduledTransactionWithDetails>> =
        scheduledTransactionRefreshTrigger.map { _ ->
            withContext(ioDispatcher) {
                transaction(database) {
                    // Load all active scheduled transactions with account names
                    val scheduledRows = ScheduledTransactions
                        .join(Accounts, JoinType.INNER, ScheduledTransactions.accountId, Accounts.id)
                        .selectAll().where { ScheduledTransactions.isActive eq true }
                        .toList()

                    // Batch load all payees and categories once
                    val payees = Payees.selectAll().associate { it[Payees.id].value.toLong() to it[Payees.name] }
                    val categories = Categories.selectAll().associate { it[Categories.id].value.toLong() to it[Categories.name] }

                    // Map results using lookup tables (no nested transactions)
                    scheduledRows.map { row ->
                        val scheduled = row.toScheduledTransaction()
                        ScheduledTransactionWithDetails(
                            scheduled = scheduled,
                            accountName = row[Accounts.name],
                            payeeName = scheduled.payeeId?.let { payees[it] },
                            categoryName = scheduled.categoryId?.let { categories[it] }
                        )
                    }
                }
            }
        }

    override suspend fun getScheduledTransactionById(id: Long): ScheduledTransaction? = withContext(Dispatchers.IO) {
        transaction(database) {
            ScheduledTransactions
                .selectAll().where { ScheduledTransactions.id eq id.toInt() }
                .singleOrNull()
                ?.toScheduledTransaction()
        }
    }

    override suspend fun getDueScheduledTransactions(currentDateMillis: Long): List<ScheduledTransaction> = withContext(Dispatchers.IO) {
        transaction(database) {
            ScheduledTransactions
                .selectAll().where {
                    (ScheduledTransactions.isActive eq true) and
                    (ScheduledTransactions.nextDate lessEq currentDateMillis) and
                    (ScheduledTransactions.endDate.isNull() or (ScheduledTransactions.nextDate lessEq ScheduledTransactions.endDate))
                }
                .map { it.toScheduledTransaction() }
        }
    }

    override suspend fun insertScheduledTransaction(scheduledTransaction: ScheduledTransaction): Long = withContext(ioDispatcher) {
        val id = transaction(database) {
            ScheduledTransactions.insert {
                it[accountId] = scheduledTransaction.accountId.toInt()
                it[payeeId] = scheduledTransaction.payeeId?.toInt()
                it[categoryId] = scheduledTransaction.categoryId?.toInt()
                it[amount] = scheduledTransaction.amount
                it[memo] = scheduledTransaction.memo
                it[frequency] = scheduledTransaction.frequency.name
                it[nextDate] = scheduledTransaction.nextDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                it[endDate] = scheduledTransaction.endDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
                it[isActive] = scheduledTransaction.isActive
                // Anchor the day-of-month so MONTHLY/YEARLY schedules don't drift; default it from
                // the start date when the caller didn't specify one.
                it[dayOfMonth] = scheduledTransaction.dayOfMonth ?: scheduledTransaction.nextDate.dayOfMonth
            }[ScheduledTransactions.id].value.toLong()
        }
        notifyScheduledTransactionsChanged()
        id
    }

    override suspend fun updateScheduledTransactionNextDate(id: Long, nextDateMillis: Long): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            ScheduledTransactions.update({ ScheduledTransactions.id eq id.toInt() }) {
                it[nextDate] = nextDateMillis
            }
        }
        notifyScheduledTransactionsChanged()
    }

    override suspend fun updateScheduledTransactionActive(id: Long, isActive: Boolean): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            ScheduledTransactions.update({ ScheduledTransactions.id eq id.toInt() }) {
                it[ScheduledTransactions.isActive] = isActive
            }
        }
        notifyScheduledTransactionsChanged()
    }

    override suspend fun deleteScheduledTransaction(id: Long): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            ScheduledTransactions.deleteWhere { ScheduledTransactions.id eq id.toInt() }
        }
        notifyScheduledTransactionsChanged()
    }

    private fun ResultRow.toScheduledTransaction(): ScheduledTransaction {
        val nextDateMillis = this[ScheduledTransactions.nextDate]
        val endDateMillis = this[ScheduledTransactions.endDate]

        return ScheduledTransaction(
            id = this[ScheduledTransactions.id].value.toLong(),
            accountId = this[ScheduledTransactions.accountId].value.toLong(),
            payeeId = this[ScheduledTransactions.payeeId]?.value?.toLong(),
            categoryId = this[ScheduledTransactions.categoryId]?.value?.toLong(),
            amount = this[ScheduledTransactions.amount],
            memo = this[ScheduledTransactions.memo],
            frequency = TransactionFrequency.valueOf(this[ScheduledTransactions.frequency]),
            nextDate = Instant.fromEpochMilliseconds(nextDateMillis).toLocalDateTime(TimeZone.UTC).date,
            endDate = endDateMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date },
            isActive = this[ScheduledTransactions.isActive],
            dayOfMonth = this[ScheduledTransactions.dayOfMonth]
        )
    }

}
