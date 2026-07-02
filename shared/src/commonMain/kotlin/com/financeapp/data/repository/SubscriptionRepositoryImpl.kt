package com.financeapp.data.repository

import com.financeapp.db.schema.DetectedSubscriptions
import com.financeapp.db.schema.Payees
import com.financeapp.domain.model.DetectedSubscription
import com.financeapp.domain.model.ScheduledTransaction
import com.financeapp.domain.model.SubscriptionOrigin
import com.financeapp.domain.model.SubscriptionStatus
import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.recurrence.nextRecurrenceDate
import com.financeapp.domain.repository.ScheduledTransactionRepository
import com.financeapp.domain.repository.SubscriptionRepository
import com.financeapp.domain.repository.TransactionRepository
import com.financeapp.domain.subscriptions.SubscriptionCandidate
import com.financeapp.domain.subscriptions.SubscriptionDetector
import com.financeapp.domain.subscriptions.SubscriptionSource
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class SubscriptionRepositoryImpl(
    private val database: Database,
    private val transactionRepository: TransactionRepository,
    private val scheduledTransactionRepository: ScheduledTransactionRepository,
    private val detector: SubscriptionDetector,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SubscriptionRepository {

    private val refreshTrigger = MutableStateFlow(0L)

    override fun notifySubscriptionsChanged() {
        refreshTrigger.value += 1
    }

    private data class Src(
        override val payeeId: Long?,
        override val importedName: String?,
        override val amountCents: Long,
        override val date: LocalDate,
        override val transferId: Long?
    ) : SubscriptionSource

    override fun getSubscriptions(): Flow<List<DetectedSubscription>> =
        refreshTrigger.map {
            withContext(ioDispatcher) {
                transaction(database) {
                    val payeeNames = Payees.selectAll()
                        .associate { it[Payees.id].value.toLong() to it[Payees.name] }
                    DetectedSubscriptions.selectAll()
                        .map { it.toDetectedSubscription(payeeNames) }
                        .sortedWith(
                            compareByDescending<DetectedSubscription> { it.isActive }
                                .thenByDescending { it.confidence }
                        )
                }
            }
        }

    override suspend fun rescan() = withContext(ioDispatcher) {
        // Load transactions off any open DB transaction, then reconcile in one DB transaction.
        val sources = loadSources()
        val candidates = detector.detect(sources)
        val now = now()

        transaction(database) {
            val existing = DetectedSubscriptions.selectAll()
                .associateBy { it[DetectedSubscriptions.matchKey] }
            val seenKeys = candidates.map { it.matchKey }.toSet()

            candidates.forEach { c ->
                val row = existing[c.matchKey]
                if (row == null) {
                    DetectedSubscriptions.insert { it.applyStats(c, now, isNew = true) }
                } else {
                    DetectedSubscriptions.update({ DetectedSubscriptions.matchKey eq c.matchKey }) {
                        it.applyStats(c, now, isNew = false)
                    }
                }
            }
            // Anything previously detected but no longer qualifying -> inactive (kept, not deleted).
            // MANUAL rows are user-asserted, so they are never auto-deactivated.
            existing.filterKeys { it !in seenKeys }
                .filterValues { it[DetectedSubscriptions.origin] != SubscriptionOrigin.MANUAL.name }
                .keys.forEach { key ->
                    DetectedSubscriptions.update({ DetectedSubscriptions.matchKey eq key }) {
                        it[isActive] = false
                        it[updatedAt] = now
                    }
                }
        }
        notifySubscriptionsChanged()
    }

    override suspend fun confirm(id: Long) = setStatus(id, SubscriptionStatus.CONFIRMED)
    override suspend fun dismiss(id: Long) = setStatus(id, SubscriptionStatus.DISMISSED)

    private suspend fun setStatus(id: Long, status: SubscriptionStatus) = withContext(ioDispatcher) {
        transaction(database) {
            DetectedSubscriptions.update({ DetectedSubscriptions.id eq id.toInt() }) {
                it[DetectedSubscriptions.status] = status.name
                it[updatedAt] = now()
            }
        }
        notifySubscriptionsChanged()
    }

    override suspend fun markPayeeAsSubscription(payeeId: Long) = withContext(ioDispatcher) {
        val matchKey = "payee:$payeeId"
        val sources = loadSources().filter { it.payeeId == payeeId }
        // Prefer real detector stats if the payee qualifies; otherwise a MONTHLY best-guess.
        val stats = detector.detect(sources).firstOrNull { it.matchKey == matchKey }
            ?: manualStats(matchKey, payeeId, sources)
        val now = now()
        transaction(database) {
            val exists = DetectedSubscriptions
                .selectAll().where { DetectedSubscriptions.matchKey eq matchKey }
                .any()
            if (!exists) {
                DetectedSubscriptions.insert {
                    it.applyStats(stats, now, isNew = true)
                    it[status] = SubscriptionStatus.CONFIRMED.name   // overrides the CANDIDATE default
                    it[origin] = SubscriptionOrigin.MANUAL.name
                }
            } else {
                DetectedSubscriptions.update({ DetectedSubscriptions.matchKey eq matchKey }) {
                    it.applyStats(stats, now, isNew = false)
                    it[status] = SubscriptionStatus.CONFIRMED.name
                    it[origin] = SubscriptionOrigin.MANUAL.name
                }
            }
        }
        notifySubscriptionsChanged()
    }

    override suspend fun createScheduledFromSubscription(id: Long) = withContext(ioDispatcher) {
        val row = transaction(database) {
            DetectedSubscriptions.selectAll().where { DetectedSubscriptions.id eq id.toInt() }.firstOrNull()
        } ?: return@withContext
        val payeeId = row[DetectedSubscriptions.payeeId]?.value?.toLong()
            ?: return@withContext                                   // bridge requires a payee
        if (row[DetectedSubscriptions.scheduledTransactionId] != null) return@withContext  // already bridged

        // Most recent matching transaction supplies account + category.
        val recent = loadSourcesFull()
            .filter { it.transaction.payeeId == payeeId }
            .maxByOrNull { it.transaction.date.toEpochDays() } ?: return@withContext

        // Stored as an outflow (negative), consistent with how the ledger signs scheduled entries.
        val newId = scheduledTransactionRepository.insertScheduledTransaction(
            ScheduledTransaction(
                id = 0,
                accountId = recent.transaction.accountId,
                payeeId = payeeId,
                categoryId = recent.transaction.categoryId,
                amount = -row[DetectedSubscriptions.medianAmount],
                memo = "Detected subscription",
                frequency = TransactionFrequency.valueOf(row[DetectedSubscriptions.cadence]),
                nextDate = row[DetectedSubscriptions.nextExpectedDate].toLocalDate(),
                endDate = null
            )
        )
        transaction(database) {
            DetectedSubscriptions.update({ DetectedSubscriptions.id eq id.toInt() }) {
                it[scheduledTransactionId] = newId.toInt()
                it[updatedAt] = now()
            }
        }
        notifySubscriptionsChanged()
    }

    private suspend fun loadSourcesFull() =
        transactionRepository.getAllTransactionsWithDetails().first()

    private suspend fun loadSources(): List<Src> =
        loadSourcesFull().map {
            Src(
                payeeId = it.transaction.payeeId,
                importedName = it.payeeName ?: it.transaction.importedName,
                amountCents = it.transaction.amount,
                date = it.transaction.date,
                transferId = it.transaction.transferId
            )
        }

    /** Best-guess stats for a manually-marked payee that doesn't meet the auto-detection bar. */
    private fun manualStats(matchKey: String, payeeId: Long, sources: List<Src>): SubscriptionCandidate {
        val outflows = sources.filter { it.amountCents < 0 && it.transferId == null }
        val amounts = outflows.map { abs(it.amountCents) }.ifEmpty { listOf(0L) }
        val dates = outflows.map { it.date }.sortedBy { it.toEpochDays() }
        val today = today()
        val last = dates.lastOrNull() ?: today
        val median = amounts.sorted()[amounts.size / 2]
        return SubscriptionCandidate(
            matchKey = matchKey,
            payeeId = payeeId,
            displayName = outflows.firstOrNull()?.importedName ?: matchKey.removePrefix("payee:"),
            cadence = TransactionFrequency.MONTHLY,                  // fallback cadence
            medianAmountCents = median,
            minAmountCents = amounts.min(),
            maxAmountCents = amounts.max(),
            isVariable = median > 0 && (amounts.max() - amounts.min()).toDouble() / median > 0.15,
            occurrenceCount = dates.size,
            firstSeen = dates.firstOrNull() ?: today,
            lastSeen = last,
            nextExpectedDate = nextRecurrenceDate(last, TransactionFrequency.MONTHLY),
            confidence = 0
        )
    }

    private fun UpdateBuilder<*>.applyStats(c: SubscriptionCandidate, now: Long, isNew: Boolean) {
        this[DetectedSubscriptions.payeeId] = c.payeeId?.toInt()
        this[DetectedSubscriptions.cadence] = c.cadence.name
        this[DetectedSubscriptions.medianAmount] = c.medianAmountCents
        this[DetectedSubscriptions.minAmount] = c.minAmountCents
        this[DetectedSubscriptions.maxAmount] = c.maxAmountCents
        this[DetectedSubscriptions.isVariable] = c.isVariable
        this[DetectedSubscriptions.occurrenceCount] = c.occurrenceCount
        this[DetectedSubscriptions.firstSeen] = c.firstSeen.toMillis()
        this[DetectedSubscriptions.lastSeen] = c.lastSeen.toMillis()
        this[DetectedSubscriptions.nextExpectedDate] = c.nextExpectedDate.toMillis()
        this[DetectedSubscriptions.confidence] = c.confidence
        this[DetectedSubscriptions.isActive] = true
        this[DetectedSubscriptions.updatedAt] = now
        if (isNew) {
            this[DetectedSubscriptions.matchKey] = c.matchKey
            this[DetectedSubscriptions.status] = SubscriptionStatus.CANDIDATE.name
            this[DetectedSubscriptions.createdAt] = now
        }
    }

    private fun ResultRow.toDetectedSubscription(payeeNames: Map<Long, String>): DetectedSubscription {
        val payeeId = this[DetectedSubscriptions.payeeId]?.value?.toLong()
        val matchKey = this[DetectedSubscriptions.matchKey]
        return DetectedSubscription(
            id = this[DetectedSubscriptions.id].value.toLong(),
            payeeId = payeeId,
            displayName = payeeId?.let { payeeNames[it] } ?: matchKey.removePrefix("name:"),
            matchKey = matchKey,
            cadence = TransactionFrequency.valueOf(this[DetectedSubscriptions.cadence]),
            status = SubscriptionStatus.valueOf(this[DetectedSubscriptions.status]),
            medianAmountCents = this[DetectedSubscriptions.medianAmount],
            minAmountCents = this[DetectedSubscriptions.minAmount],
            maxAmountCents = this[DetectedSubscriptions.maxAmount],
            isVariable = this[DetectedSubscriptions.isVariable],
            occurrenceCount = this[DetectedSubscriptions.occurrenceCount],
            firstSeen = this[DetectedSubscriptions.firstSeen].toLocalDate(),
            lastSeen = this[DetectedSubscriptions.lastSeen].toLocalDate(),
            nextExpectedDate = this[DetectedSubscriptions.nextExpectedDate].toLocalDate(),
            confidence = this[DetectedSubscriptions.confidence],
            isActive = this[DetectedSubscriptions.isActive],
            origin = SubscriptionOrigin.valueOf(this[DetectedSubscriptions.origin]),
            scheduledTransactionId = this[DetectedSubscriptions.scheduledTransactionId]?.value?.toLong()
        )
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.UTC).date

    private fun LocalDate.toMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    private fun Long.toLocalDate(): LocalDate =
        Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
}
