package com.financeapp.domain.categorize

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * A trained Complement Naive Bayes classifier over a fixed feature vocabulary. Holds, per leaf
 * category id, the weight-normalized complement log-weights (WCNB; Rennie et al. 2003) for each
 * feature. [scores] returns softmax pseudo-probabilities over the categories.
 *
 * The model [abstains][isEmpty] when there is nothing to discriminate (fewer than two categories),
 * so the cascade falls through to the bundled cold-start signals.
 */
class TransactionCategoryModel internal constructor(
    private val weights: Map<Long, Map<String, Double>>,
    private val temperature: Double = CategorizationThresholds.CONFIDENCE_TEMPERATURE
) {
    val isEmpty: Boolean get() = weights.size < 2

    /**
     * Pseudo-probabilities per category for the given feature tokens. Empty when the model abstains.
     * Complement NB picks the class with the *smallest* weighted sum, so we negate before softmax
     * to get a larger-is-better distribution.
     */
    fun scores(features: List<String>): Map<Long, Double> {
        if (isEmpty) return emptyMap()
        return softmax(logits(features))
    }

    /**
     * Pre-softmax class scores: the negated WCNB weighted sums (larger = better fit). Exposed
     * internally so confidence calibration (the softmax temperature) can be reasoned about and
     * tested directly.
     */
    internal fun logits(features: List<String>): Map<Long, Double> {
        // Term frequency of each feature in this document.
        val tf = HashMap<String, Int>()
        for (f in features) tf[f] = (tf[f] ?: 0) + 1

        val out = HashMap<Long, Double>(weights.size)
        for ((category, w) in weights) {
            var sum = 0.0
            for ((feature, count) in tf) {
                val weight = w[feature] ?: continue
                sum += count * weight
            }
            // Complement NB: smaller weighted sum = better fit, so negate.
            out[category] = -sum
        }
        return out
    }

    private fun softmax(values: Map<Long, Double>): Map<Long, Double> {
        val scaled = values.mapValues { it.value / temperature }
        val max = scaled.values.max()
        val exps = scaled.mapValues { exp(it.value - max) }
        val total = exps.values.sum()
        return exps.mapValues { it.value / total }
    }

    companion object {
        val EMPTY = TransactionCategoryModel(emptyMap())
    }
}

/**
 * Builds a [TransactionCategoryModel] from the user's categorized transactions using
 * weight-normalized Complement Naive Bayes, which is robust to the heavy class imbalance typical of
 * personal spending (lots of groceries/coffee, few of everything else). Training is in-memory and
 * runs in milliseconds over a few thousand rows.
 */
class CategoryModelTrainer(
    private val featureExtractor: FeatureExtractor = FeatureExtractor(),
    private val alpha: Double = 1.0 // Laplace smoothing
) {
    fun train(samples: List<TrainingSample>): TransactionCategoryModel {
        val classes = samples.map { it.categoryId }.distinct()
        if (classes.size < 2) return TransactionCategoryModel.EMPTY

        // Per-class and global feature counts (term frequencies summed over documents).
        val classCounts = HashMap<Long, HashMap<String, Long>>()
        val globalCounts = HashMap<String, Long>()
        for (sample in samples) {
            val perClass = classCounts.getOrPut(sample.categoryId) { HashMap() }
            val features = featureExtractor.extract(sample.merchantName, sample.sic, sample.amountCents)
            for (f in features) {
                perClass[f] = (perClass[f] ?: 0L) + 1L
                globalCounts[f] = (globalCounts[f] ?: 0L) + 1L
            }
        }

        val vocab = globalCounts.keys
        val vocabSize = vocab.size
        val totalAll = globalCounts.values.sum()

        val weights = HashMap<Long, Map<String, Double>>(classes.size)
        for (category in classes) {
            val perClass = classCounts[category] ?: emptyMap()
            val classTotal = perClass.values.sum()
            val complementTotal = totalAll - classTotal

            // Unnormalized complement log-weights for every feature in the vocabulary.
            val rawWeights = HashMap<String, Double>(vocabSize)
            for (f in vocab) {
                val complementCount = (globalCounts[f] ?: 0L) - (perClass[f] ?: 0L)
                rawWeights[f] = ln((complementCount + alpha) / (complementTotal + alpha * vocabSize))
            }

            // Weight normalization (the "W" in WCNB): divide by L1 norm so long, common classes
            // don't dominate purely by accumulating more (negative) weight.
            val l1 = rawWeights.values.sumOf { abs(it) }
            val normalized = if (l1 == 0.0) rawWeights else rawWeights.mapValues { it.value / l1 }
            weights[category] = normalized
        }

        return TransactionCategoryModel(weights)
    }
}
