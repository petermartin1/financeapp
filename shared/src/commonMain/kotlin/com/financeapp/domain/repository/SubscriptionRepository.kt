package com.financeapp.domain.repository

import com.financeapp.domain.model.DetectedSubscription
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    /** All detected subscriptions, active first then by confidence desc. */
    fun getSubscriptions(): Flow<List<DetectedSubscription>>

    /** Loads all transactions, runs the detector, and reconciles results (sticky status). */
    suspend fun rescan()

    suspend fun confirm(id: Long)

    suspend fun dismiss(id: Long)

    /**
     * Manual escape hatch for a subscription the detector missed. Builds a row from [payeeId]'s
     * outflow history (best-guess cadence, MONTHLY fallback) and upserts it as CONFIRMED/MANUAL,
     * keyed on `payee:<id>`. Idempotent: a second call just re-promotes the same row.
     */
    suspend fun markPayeeAsSubscription(payeeId: Long)

    /**
     * Action bridge. Creates a [com.financeapp.domain.model.ScheduledTransaction] from the
     * subscription [id] and stores its id on the row. No-op if the subscription already has a
     * linked schedule or has no payee.
     */
    suspend fun createScheduledFromSubscription(id: Long)

    fun notifySubscriptionsChanged()
}
