package com.financeapp.domain.model

import kotlinx.datetime.Instant

/**
 * Represents a mapping from an imported payee name to a canonical payee
 * Used to automatically resolve similar payee names during import
 */
data class PayeeAlias(
    val id: Long = 0,
    val aliasName: String,
    val canonicalPayeeId: Long,
    val matchType: MatchType,
    val confidence: Double?,
    val createdAt: Instant
)

enum class MatchType {
    EXACT,      // Exact match after normalization
    SUBSTRING,  // One name contains the other
    FUZZY,      // Jaro-Winkler similarity match
    MANUAL      // User manually created this mapping
}
