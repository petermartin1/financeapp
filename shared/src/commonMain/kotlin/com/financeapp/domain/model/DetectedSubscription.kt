package com.financeapp.domain.model

import kotlinx.datetime.LocalDate

enum class SubscriptionStatus { CANDIDATE, CONFIRMED, DISMISSED }

enum class SubscriptionOrigin { DETECTED, MANUAL }

data class DetectedSubscription(
    val id: Long = 0,
    val payeeId: Long?,
    val displayName: String,
    val matchKey: String,
    val cadence: TransactionFrequency,
    val status: SubscriptionStatus,
    val medianAmountCents: Long,
    val minAmountCents: Long,
    val maxAmountCents: Long,
    val isVariable: Boolean,
    val occurrenceCount: Int,
    val firstSeen: LocalDate,
    val lastSeen: LocalDate,
    val nextExpectedDate: LocalDate,
    val confidence: Int,
    val isActive: Boolean,
    val origin: SubscriptionOrigin = SubscriptionOrigin.DETECTED,
    val scheduledTransactionId: Long? = null
)
