package com.financeapp.domain.categorize

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory cache of the trained per-user [TransactionCategoryModel]. There is **no table and no
 * migration**: the model is derived from the user's own transactions and retrained lazily.
 *
 * The cache is invalidated whenever transactions are (re)categorized or after an import completes,
 * so the next prediction picks up the latest data. A [Mutex] serializes concurrent access so a
 * burst of predictions during an import triggers at most one training pass.
 */
class CategoryModelStore(
    private val trainingData: suspend () -> List<TrainingSample>,
    private val trainer: CategoryModelTrainer = CategoryModelTrainer()
) {
    private val mutex = Mutex()
    private var cached: TransactionCategoryModel? = null

    /** The current model, training it (once) if the cache is cold or was invalidated. */
    suspend fun model(): TransactionCategoryModel = mutex.withLock {
        cached ?: trainer.train(trainingData()).also { cached = it }
    }

    /** Drop the cached model so the next [model] call retrains from fresh data. */
    fun invalidate() {
        cached = null
    }
}
