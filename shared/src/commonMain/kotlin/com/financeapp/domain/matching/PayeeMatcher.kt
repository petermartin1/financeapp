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
     * 2. Substring match (only if first token passes gate)
     * 3. Token/word-based prefix match (for credit card transactions with suffixes)
     * 4. Weighted token fuzzy match (prioritizes business name over location)
     *
     * The first-token gate prevents false matches where only the location suffix
     * is similar (e.g., "DOMINO'S ARLINGTON HEI" vs "MARIANOS ARLINGTON HEI").
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

            // Check whole-token containment: the stored payee's name appears as a contiguous
            // run of whole tokens inside the imported name (or vice versa). This catches OFX
            // names carrying a leading processor prefix ("SQ *BLUE BOTTLE COFFEE" -> "Blue Bottle")
            // while rejecting raw character substrings ("bp" inside "subprime") and names that
            // merely share a location suffix ("IBJI ARLINGTON" vs "ARLINGTON HEIGHTS ANIM").
            if (tokensContain(normalizedImported, normalizedPayee) ||
                tokensContain(normalizedPayee, normalizedImported)) {
                val similarity = 0.95 // High but not perfect
                matches.add(PayeeMatch(payee, similarity, MatchType.SUBSTRING))
                continue
            }

            // Same identity encoded in a long leading token with a variable trailing suffix,
            // e.g. "BP#1724100ARLINGTONQPS ARLI" vs "BP#1724100ARLINGTON SIMP AR" (same store;
            // the bank crams id + location into the first token and appends differing noise).
            if (firstTokenIsSharedPrefix(normalizedImported, normalizedPayee)) {
                matches.add(PayeeMatch(payee, 0.9, MatchType.FUZZY))
                continue
            }

            // Check token-based prefix match (for "ARLINGTON HEIGHTS ANIM 847" vs "ARLINGTON HEIGHTS ANIM ARLI")
            // Only if first token passes gate
            if (firstTokenPassesGate(normalizedImported, normalizedPayee, 0.6)) {
                val tokenSim = tokenPrefixSimilarity(normalizedImported, normalizedPayee)
                if (tokenSim >= 0.85) {
                    matches.add(PayeeMatch(payee, tokenSim, MatchType.SUBSTRING))
                    continue
                }
            }

            // Check weighted token fuzzy match - requires first token gate
            // This uses weighted similarity that prioritizes business name (first tokens)
            if (firstTokenPassesGate(normalizedImported, normalizedPayee, 0.6)) {
                val similarity = weightedTokenSimilarity(normalizedImported, normalizedPayee)
                if (similarity >= threshold) {
                    matches.add(PayeeMatch(payee, similarity, MatchType.FUZZY))
                }
            }
        }

        // Sort by similarity (highest first), then by payee name for stability
        return matches.sortedWith(compareByDescending<PayeeMatch> { it.similarity }.thenBy { it.payee.name })
    }

    /**
     * Find similar names among a list of strings
     * Used to find similar payee names within the same import batch
     *
     * Uses the same first-token gate and weighted matching as findSimilarPayees
     * to ensure consistent behavior.
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

            // Whole-token containment (see findSimilarPayees for the rationale).
            if (tokensContain(normalizedTarget, normalizedCandidate) ||
                tokensContain(normalizedCandidate, normalizedTarget)) {
                similarities.add(candidateName to 0.95)
                continue
            }

            // Same identity in a long shared leading token (see findSimilarPayees).
            if (firstTokenIsSharedPrefix(normalizedTarget, normalizedCandidate)) {
                similarities.add(candidateName to 0.9)
                continue
            }

            // Check token-based prefix match - only if first token passes gate
            if (firstTokenPassesGate(normalizedTarget, normalizedCandidate, 0.6)) {
                val tokenSim = tokenPrefixSimilarity(normalizedTarget, normalizedCandidate)
                if (tokenSim >= 0.85) {
                    similarities.add(candidateName to tokenSim)
                    continue
                }
            }

            // Check weighted token fuzzy match - requires first token gate
            if (firstTokenPassesGate(normalizedTarget, normalizedCandidate, 0.6)) {
                val similarity = weightedTokenSimilarity(normalizedTarget, normalizedCandidate)
                if (similarity >= threshold) {
                    similarities.add(candidateName to similarity)
                }
            }
        }

        // Sort by similarity (highest first)
        return similarities.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * True if every token of [needle] appears as a contiguous run of whole tokens within
     * [haystack]. Unlike a raw character `contains`, this only matches on token boundaries,
     * so "bp" does not match "subprime" and a shared trailing location does not count as
     * containment.
     */
    private fun tokensContain(haystack: String, needle: String): Boolean {
        val hay = haystack.split(" ").filter { it.isNotEmpty() }
        val need = needle.split(" ").filter { it.isNotEmpty() }
        if (need.isEmpty() || need.size > hay.size) return false
        for (start in 0..hay.size - need.size) {
            if ((need.indices).all { hay[start + it] == need[it] }) return true
        }
        return false
    }

    /**
     * True when the two names share the same long leading token: the shorter first token is
     * an exact prefix of the longer one and is at least [minLen] characters. Banks often pack
     * a store id and location into the first token ("bp1724100arlington...") and append a
     * variable suffix; a long exact-prefix match identifies the same store while a short
     * prefix (e.g. "bp", "dominos") is rejected, so unrelated businesses don't collide.
     */
    private fun firstTokenIsSharedPrefix(s1: String, s2: String, minLen: Int = 8): Boolean {
        val t1 = s1.split(" ").firstOrNull { it.isNotEmpty() } ?: return false
        val t2 = s2.split(" ").firstOrNull { it.isNotEmpty() } ?: return false
        val shorter = if (t1.length <= t2.length) t1 else t2
        val longer = if (t1.length <= t2.length) t2 else t1
        return shorter.length >= minLen && longer.startsWith(shorter)
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
     * Calculate weighted token similarity that prioritizes business name (first tokens)
     * over location suffixes commonly added by banks.
     *
     * Weighting scheme:
     * - First token: 50% weight (the actual business name)
     * - Second token: 25% weight (often part of business name or store number)
     * - Remaining tokens: 25% weight combined (usually location/address noise)
     *
     * This prevents "DOMINO'S 2824 ARLINGTON HEI" from matching "MARIANOS #501 ARLINGTON HEI"
     * just because they share the same location suffix.
     *
     * @param s1 First normalized string
     * @param s2 Second normalized string
     * @return Similarity score between 0.0 and 1.0
     */
    fun weightedTokenSimilarity(s1: String, s2: String): Double {
        val tokens1 = s1.split(" ").filter { it.isNotEmpty() }
        val tokens2 = s2.split(" ").filter { it.isNotEmpty() }

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        // Calculate similarity for first token (business name) - weight 50%
        val firstTokenSim = if (tokens1.isNotEmpty() && tokens2.isNotEmpty()) {
            tokenSimilarity(tokens1[0], tokens2[0])
        } else 0.0

        // Calculate similarity for second token - weight 25%
        val secondTokenSim = if (tokens1.size > 1 && tokens2.size > 1) {
            tokenSimilarity(tokens1[1], tokens2[1])
        } else if (tokens1.size > 1 || tokens2.size > 1) {
            0.0 // Penalize if one has a second token and the other doesn't
        } else {
            1.0 // Both only have one token, don't penalize
        }

        // Calculate similarity for remaining tokens - weight 25%
        val remainingTokensSim = if (tokens1.size > 2 || tokens2.size > 2) {
            val remaining1 = if (tokens1.size > 2) tokens1.subList(2, tokens1.size) else emptyList()
            val remaining2 = if (tokens2.size > 2) tokens2.subList(2, tokens2.size) else emptyList()
            calculateRemainingSimilarity(remaining1, remaining2)
        } else {
            1.0 // No remaining tokens to compare, don't penalize
        }

        // Weighted average: 50% first token, 25% second token, 25% remaining
        return (firstTokenSim * 0.50) + (secondTokenSim * 0.25) + (remainingTokensSim * 0.25)
    }

    /**
     * Calculate similarity between two individual tokens using Jaro-Winkler
     */
    private fun tokenSimilarity(t1: String, t2: String): Double {
        if (t1 == t2) return 1.0
        if (t1.isEmpty() || t2.isEmpty()) return 0.0

        // Use Jaro-Winkler for token comparison
        return jaroWinklerSimilarity(t1, t2)
    }

    /**
     * Calculate similarity for remaining tokens (position 2+)
     * Uses a bag-of-words approach since position matters less for these
     */
    private fun calculateRemainingSimilarity(tokens1: List<String>, tokens2: List<String>): Double {
        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.5 // Partial penalty

        // Count how many tokens from tokens1 have a good match in tokens2
        var matchedCount = 0
        val used = mutableSetOf<Int>()

        for (t1 in tokens1) {
            var bestMatch = 0.0
            var bestIdx = -1
            for ((idx, t2) in tokens2.withIndex()) {
                if (idx in used) continue
                val sim = tokenSimilarity(t1, t2)
                if (sim > bestMatch) {
                    bestMatch = sim
                    bestIdx = idx
                }
            }
            if (bestMatch >= 0.8 && bestIdx >= 0) {
                matchedCount++
                used.add(bestIdx)
            }
        }

        val totalTokens = maxOf(tokens1.size, tokens2.size)
        return matchedCount.toDouble() / totalTokens
    }

    /**
     * Check if the first token (business name) meets a minimum similarity threshold.
     * This acts as a gate - if the business names don't match, we reject the match
     * regardless of how similar the rest of the string is.
     *
     * @param s1 First normalized string
     * @param s2 Second normalized string
     * @param minThreshold Minimum similarity for first token (default 0.6)
     * @return true if first tokens are sufficiently similar
     */
    fun firstTokenPassesGate(s1: String, s2: String, minThreshold: Double = 0.6): Boolean {
        val tokens1 = s1.split(" ").filter { it.isNotEmpty() }
        val tokens2 = s2.split(" ").filter { it.isNotEmpty() }

        if (tokens1.isEmpty() || tokens2.isEmpty()) return false

        val firstTokenSim = tokenSimilarity(tokens1[0], tokens2[0])
        return firstTokenSim >= minThreshold
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
