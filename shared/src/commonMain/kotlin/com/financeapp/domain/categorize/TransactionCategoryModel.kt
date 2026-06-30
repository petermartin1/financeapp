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
     * The merchant-identifying part of the vocabulary: every known feature except the amount-sign and
     * the "had a digit" placeholder, which are present in almost every transaction and so carry no
     * merchant signal. A prediction is only trustworthy if the input overlaps this set — otherwise the
     * model has genuinely never seen anything like the merchant and must defer (see [scores]).
     */
    private val discriminativeVocab: Set<String> =
        weights.values.firstOrNull()
            ?.keys
            ?.filterTo(HashSet()) { it != FeatureExtractor.NUMBER_TOKEN && !it.startsWith(FeatureExtractor.SIGN_PREFIX) }
            ?: emptySet()

    /**
     * Pseudo-probabilities per category for the given feature tokens. Empty when the model abstains:
     * either it has nothing to discriminate ([isEmpty]) or the input shares too little
     * merchant-identifying evidence with anything the user has categorized. A genuine word match
     * always contributes the word token *plus* its character trigrams, so it overlaps in several
     * features; a lone shared trigram (a coincidental spelling collision) overlaps in just one and
     * must not be trusted — on the standardized confidence scale a single stray feature can otherwise
     * look decisive. Below the evidence floor we defer to the cold-start signals instead.
     */
    fun scores(features: List<String>): Map<Long, Double> {
        if (isEmpty) return emptyMap()
        val overlap = features.toHashSet().count { it in discriminativeVocab }
        if (overlap < MIN_DISCRIMINATIVE_OVERLAP) return emptyMap()
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

    /**
     * Softmax over *standardized* logits. The raw WCNB logits are L1-normalized per class, so their
     * absolute magnitude (and the gap between classes) shrinks with the vocabulary and grows with the
     * input's feature count — a short merchant name and a long one produce wildly different scales for
     * an equally-clean match. Dividing by the per-prediction standard deviation removes that scale, so
     * confidence reflects how many standard deviations the top class sits above the field, stable
     * across name lengths and category counts. When every class scores identically (std == 0, e.g. an
     * input with no known features) we fall back to a uniform distribution.
     */
    private fun softmax(values: Map<Long, Double>): Map<Long, Double> {
        val mean = values.values.average()
        val variance = values.values.sumOf { (it - mean) * (it - mean) } / values.size
        val std = kotlin.math.sqrt(variance)
        if (std == 0.0) return values.mapValues { 1.0 / values.size }

        val scaled = values.mapValues { (it.value - mean) / std / temperature }
        val max = scaled.values.max()
        val exps = scaled.mapValues { exp(it.value - max) }
        val total = exps.values.sum()
        return exps.mapValues { it.value / total }
    }

    companion object {
        val EMPTY = TransactionCategoryModel(emptyMap())

        /**
         * Minimum number of distinct merchant-identifying features (word tokens / character trigrams /
         * SIC) the input must share with the model before its prediction is trusted. A real word match
         * always clears this (word token + at least one trigram); a single coincidental trigram does
         * not, so it can't masquerade as a confident match. See [scores].
         */
        private const val MIN_DISCRIMINATIVE_OVERLAP = 2
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
