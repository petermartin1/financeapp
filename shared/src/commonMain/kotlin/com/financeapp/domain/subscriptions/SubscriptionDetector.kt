package com.financeapp.domain.subscriptions

import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.recurrence.nextRecurrenceDate
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/** Minimal per-transaction shape the detector needs. Amount is in cents, negative for outflows. */
interface SubscriptionSource {
    val payeeId: Long?
    val importedName: String?
    val amountCents: Long
    val date: LocalDate
    val transferId: Long?
}

data class SubscriptionCandidate(
    val matchKey: String,
    val payeeId: Long?,
    val displayName: String,
    val cadence: TransactionFrequency,
    val medianAmountCents: Long,
    val minAmountCents: Long,
    val maxAmountCents: Long,
    val isVariable: Boolean,
    val occurrenceCount: Int,
    val firstSeen: LocalDate,
    val lastSeen: LocalDate,
    val nextExpectedDate: LocalDate,
    val confidence: Int
)

/**
 * Detects subscription-like recurring charges by clustering each payee's outflow dates into a
 * regular cadence. Pure and total: never throws; non-qualifying groups are simply omitted.
 */
class SubscriptionDetector {

    private companion object {
        const val MIN_OCCURRENCES = 3
        const val CADENCE_TOLERANCE = 0.25          // ±25% of a cadence's nominal day count
        const val MIN_GAP_FIT_FRACTION = 0.6        // majority of gaps must fit the cadence
        const val VARIABLE_THRESHOLD = 0.15         // (max-min)/median above this => variable
        val CANDIDATE_CADENCES = listOf(
            TransactionFrequency.WEEKLY,
            TransactionFrequency.BIWEEKLY,
            TransactionFrequency.MONTHLY,
            TransactionFrequency.YEARLY
        )
    }

    fun detect(sources: List<SubscriptionSource>): List<SubscriptionCandidate> {
        return sources
            .asSequence()
            .filter { it.amountCents < 0 && it.transferId == null }
            .mapNotNull { src -> matchKeyFor(src)?.let { key -> key to src } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (key, group) -> candidateFor(key, group) }
            .sortedByDescending { it.confidence }
    }

    private fun matchKeyFor(src: SubscriptionSource): String? {
        if (src.payeeId != null) return "payee:${src.payeeId}"
        val normalized = src.importedName?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")
        return if (normalized.isNullOrBlank()) null else "name:$normalized"
    }

    private fun candidateFor(matchKey: String, group: List<SubscriptionSource>): SubscriptionCandidate? {
        // Collapse duplicate same-day charges: one entry per date, amount summed for that day.
        val byDate = group.groupBy { it.date }
            .map { (date, sameDay) -> date to sameDay.sumOf { abs(it.amountCents) } }
            .sortedBy { it.first.toEpochDays() }
        if (byDate.size < MIN_OCCURRENCES) return null

        val dates = byDate.map { it.first }
        val amounts = byDate.map { it.second }

        val gaps = dates.zipWithNext { a, b -> (b.toEpochDays() - a.toEpochDays()).toInt() }
        val cadence = classifyCadence(gaps) ?: return null

        val median = amounts.sorted()[amounts.size / 2]
        val min = amounts.min()
        val max = amounts.max()
        val isVariable = median > 0 && (max - min).toDouble() / median > VARIABLE_THRESHOLD

        val firstSeen = dates.first()
        val lastSeen = dates.last()
        val nextExpected = nextRecurrenceDate(lastSeen, cadence, anchorDay = lastSeen.dayOfMonth)
        val confidence = confidenceScore(gaps, cadence, byDate.size)
        val display = group.firstOrNull { it.payeeId != null }?.importedName
            ?: matchKey.removePrefix("name:")

        return SubscriptionCandidate(
            matchKey = matchKey,
            payeeId = group.firstNotNullOfOrNull { it.payeeId },
            displayName = display.ifBlank { matchKey },
            cadence = cadence,
            medianAmountCents = median,
            minAmountCents = min,
            maxAmountCents = max,
            isVariable = isVariable,
            occurrenceCount = byDate.size,
            firstSeen = firstSeen,
            lastSeen = lastSeen,
            nextExpectedDate = nextExpected,
            confidence = confidence
        )
    }

    private fun classifyCadence(gaps: List<Int>): TransactionFrequency? {
        if (gaps.isEmpty()) return null
        val median = gaps.sorted()[gaps.size / 2]
        val best = CANDIDATE_CADENCES.minByOrNull { abs(it.days - median) } ?: return null
        val medianWithin = abs(median - best.days).toDouble() / best.days <= CADENCE_TOLERANCE
        if (!medianWithin) return null
        val fitFraction = gaps.count {
            abs(it - best.days).toDouble() / best.days <= CADENCE_TOLERANCE
        }.toDouble() / gaps.size
        return if (fitFraction >= MIN_GAP_FIT_FRACTION) best else null
    }

    private fun confidenceScore(gaps: List<Int>, cadence: TransactionFrequency, occurrences: Int): Int {
        val occurrenceScore = minOf(1.0, occurrences / 6.0)
        val meanRelDeviation = gaps.map { abs(it - cadence.days).toDouble() / cadence.days }.average()
        val tightnessScore = (1.0 - minOf(1.0, meanRelDeviation / CADENCE_TOLERANCE)).coerceAtLeast(0.0)
        return ((0.5 * occurrenceScore + 0.5 * tightnessScore) * 100).toInt()
    }
}
