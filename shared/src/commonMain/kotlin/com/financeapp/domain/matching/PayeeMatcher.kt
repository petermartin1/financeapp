package com.financeapp.domain.matching

import com.financeapp.domain.model.MatchType
import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.PayeeMatch
import kotlin.math.min

/**
 * Service for finding similar payees using string similarity algorithms
 */
class PayeeMatcher {

    /**
     * Normalize a payee name for comparison
     * - Convert to lowercase
     * - Trim whitespace
     * - Remove special characters except spaces
     * - Collapse multiple spaces to single space
     */
    fun normalize(name: String): String {
        return name
            .lowercase()
            .trim()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Find similar payees for an imported name
     *
     * Matching pipeline:
     * 1. Exact match after normalization
     * 2. Substring match
     * 3. Token/word-based prefix match (for credit card transactions with suffixes)
     * 4. Fuzzy match using Jaro-Winkler
     *
     * @param importedName The payee name from the imported transaction
     * @param existingPayees List of existing payees to match against
     * @param threshold Minimum similarity threshold (0.0 to 1.0), default 0.75
     * @return List of PayeeMatch objects sorted by similarity (highest first)
     */
    fun findSimilarPayees(
        importedName: String,
        existingPayees: List<Payee>,
        threshold: Double = 0.75
    ): List<PayeeMatch> {
        val normalizedImported = normalize(importedName)
        val matches = mutableListOf<PayeeMatch>()

        for (payee in existingPayees) {
            val normalizedPayee = normalize(payee.name)

            // Check exact match
            if (normalizedImported == normalizedPayee) {
                matches.add(PayeeMatch(payee, 1.0, MatchType.EXACT))
                continue
            }

            // Check substring match
            if (normalizedImported.contains(normalizedPayee) || normalizedPayee.contains(normalizedImported)) {
                val similarity = 0.95 // High but not perfect
                matches.add(PayeeMatch(payee, similarity, MatchType.SUBSTRING))
                continue
            }

            // Check token-based prefix match (for "ARLINGTON HEIGHTS ANIM 847" vs "ARLINGTON HEIGHTS ANIM ARLI")
            val tokenSimilarity = tokenPrefixSimilarity(normalizedImported, normalizedPayee)
            if (tokenSimilarity >= 0.85) {
                matches.add(PayeeMatch(payee, tokenSimilarity, MatchType.SUBSTRING))
                continue
            }

            // Check fuzzy match using Jaro-Winkler (lowered threshold from 0.85 to 0.75)
            val similarity = jaroWinklerSimilarity(normalizedImported, normalizedPayee)
            if (similarity >= threshold) {
                matches.add(PayeeMatch(payee, similarity, MatchType.FUZZY))
            }
        }

        // Sort by similarity (highest first), then by payee name for stability
        return matches.sortedWith(compareByDescending<PayeeMatch> { it.similarity }.thenBy { it.payee.name })
    }

    /**
     * Find similar names among a list of strings
     * Used to find similar payee names within the same import batch
     *
     * @param targetName The name to find matches for
     * @param candidateNames List of candidate names to match against
     * @param threshold Minimum similarity threshold (0.0 to 1.0)
     * @return List of similar names sorted by similarity (highest first)
     */
    fun findSimilarNames(
        targetName: String,
        candidateNames: List<String>,
        threshold: Double = 0.75
    ): List<String> {
        val normalizedTarget = normalize(targetName)
        val similarities = mutableListOf<Pair<String, Double>>()

        for (candidateName in candidateNames) {
            val normalizedCandidate = normalize(candidateName)

            // Check exact match
            if (normalizedTarget == normalizedCandidate) {
                similarities.add(candidateName to 1.0)
                continue
            }

            // Check substring match
            if (normalizedTarget.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedTarget)) {
                similarities.add(candidateName to 0.95)
                continue
            }

            // Check token-based prefix match
            val tokenSim = tokenPrefixSimilarity(normalizedTarget, normalizedCandidate)
            if (tokenSim >= 0.85) {
                similarities.add(candidateName to tokenSim)
                continue
            }

            // Check fuzzy match using Jaro-Winkler
            val similarity = jaroWinklerSimilarity(normalizedTarget, normalizedCandidate)
            if (similarity >= threshold) {
                similarities.add(candidateName to similarity)
            }
        }

        // Sort by similarity (highest first)
        return similarities.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * Calculate similarity based on matching word tokens and prefixes
     * Useful for credit card transactions like "ARLINGTON HEIGHTS ANIM 847" vs "ARLINGTON HEIGHTS ANIM ARLI"
     *
     * @param s1 First normalized string
     * @param s2 Second normalized string
     * @return Similarity score between 0.0 and 1.0
     */
    private fun tokenPrefixSimilarity(s1: String, s2: String): Double {
        val tokens1 = s1.split(" ").filter { it.isNotEmpty() }
        val tokens2 = s2.split(" ").filter { it.isNotEmpty() }

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        // Count matching tokens (either exact or prefix match with min length 3)
        var matchingTokens = 0
        val minTokens = min(tokens1.size, tokens2.size)

        for (i in 0 until minTokens) {
            val t1 = tokens1[i]
            val t2 = tokens2[i]

            // Exact match
            if (t1 == t2) {
                matchingTokens++
            }
            // Prefix match (min 3 chars to avoid false positives)
            else if (t1.length >= 3 && t2.length >= 3) {
                val prefixLen = min(t1.length, t2.length)
                if (t1.substring(0, min(prefixLen, 4)) == t2.substring(0, min(prefixLen, 4))) {
                    matchingTokens++
                }
            }
        }

        // Calculate similarity as ratio of matching tokens to total unique tokens
        val totalTokens = maxOf(tokens1.size, tokens2.size)
        return matchingTokens.toDouble() / totalTokens
    }

    /**
     * Calculate Jaro-Winkler similarity between two strings
     *
     * The Jaro-Winkler similarity is a variant of the Jaro distance metric designed
     * to give more favorable ratings to strings with common prefixes.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Similarity score between 0.0 (no match) and 1.0 (exact match)
     */
    fun jaroWinklerSimilarity(s1: String, s2: String): Double {
        val jaroSim = jaroSimilarity(s1, s2)

        if (jaroSim < 0.7) {
            return jaroSim
        }

        // Find common prefix up to 4 characters
        val prefixLength = min(4, min(s1.length, s2.length))
        var commonPrefix = 0
        for (i in 0 until prefixLength) {
            if (s1[i] == s2[i]) {
                commonPrefix++
            } else {
                break
            }
        }

        // Jaro-Winkler formula: jw = j + (p * prefix * (1 - j))
        // where p is the scaling factor (typically 0.1)
        val p = 0.1
        return jaroSim + (commonPrefix * p * (1 - jaroSim))
    }

    /**
     * Calculate Jaro similarity between two strings
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Similarity score between 0.0 (no match) and 1.0 (exact match)
     */
    private fun jaroSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        // Maximum allowed distance for matching characters
        val matchDistance = (maxOf(s1.length, s2.length) / 2) - 1

        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        var transpositions = 0

        // Find matches
        for (i in s1.indices) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, s2.length)

            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        // Count transpositions
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        // Jaro formula: (matches/len1 + matches/len2 + (matches-transpositions/2)/matches) / 3
        return (matches.toDouble() / s1.length +
                matches.toDouble() / s2.length +
                (matches - transpositions / 2.0) / matches) / 3.0
    }
}
