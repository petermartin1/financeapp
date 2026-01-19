package com.financeapp.domain.model

/**
 * Represents a user's decision on how to map an imported payee name
 */
data class PayeeMapping(
    val importedName: String,
    val resolvedPayeeId: Long?,
    val createNew: Boolean,
    val newPayeeName: String? = null,
    val categoryId: Long? = null,
    val tagIds: List<Long> = emptyList(),
    val applyCategory: Boolean = true,
    val rememberMapping: Boolean = true
)

/**
 * Represents a potential match between an imported name and an existing payee
 */
data class PayeeMatch(
    val payee: Payee,
    val similarity: Double,
    val matchType: MatchType
)

/**
 * Represents an imported payee name that needs user review
 */
data class UnresolvedPayee(
    val importedName: String,
    val transactionCount: Int,
    val suggestedMatches: List<PayeeMatch>,
    val variantNames: List<String> = listOf(importedName), // For grouped similar names
    val similarInImport: List<String> = emptyList()  // Similar payee names found earlier in this import batch
)

/**
 * Auto-resolved alias information
 */
data class ResolvedAlias(
    val payeeId: Long,
    val preferredCategoryId: Long? = null // Category from previous import preference
)

/**
 * Result of analyzing imported payee names
 */
data class PayeeResolutionResult(
    val autoResolved: Map<String, ResolvedAlias>, // importedName → resolved alias info
    val needsReview: List<UnresolvedPayee> // names that need user decision
)
