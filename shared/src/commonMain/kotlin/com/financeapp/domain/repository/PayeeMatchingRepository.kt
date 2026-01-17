package com.financeapp.domain.repository

import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.PayeeAlias
import com.financeapp.domain.model.PayeeResolutionResult

/**
 * Repository for payee alias management and matching operations
 */
interface PayeeMatchingRepository {
    /**
     * Get a payee alias by its normalized name
     */
    suspend fun getAliasByName(aliasName: String): PayeeAlias?

    /**
     * Batch lookup payee aliases by normalized names
     * @return Map of alias name to PayeeAlias
     */
    suspend fun getAliasesByNames(aliasNames: List<String>): Map<String, PayeeAlias>

    /**
     * Get all aliases pointing to a specific payee
     */
    suspend fun getAliasesByPayeeId(payeeId: Long): List<PayeeAlias>

    /**
     * Batch insert multiple payee aliases
     * @return List of created alias IDs
     */
    suspend fun batchInsertAliases(aliases: List<PayeeAlias>): List<Long>

    /**
     * Delete a payee alias
     */
    suspend fun deleteAlias(id: Long)

    /**
     * Resolve a list of imported payee names
     * - Checks PayeeAliases table for saved mappings
     * - For unmapped names, finds similar existing payees
     *
     * @param importedNames List of payee names from import
     * @param existingPayees List of all existing payees
     * @param threshold Similarity threshold for fuzzy matching (default 0.75)
     * @return PayeeResolutionResult with auto-resolved and unresolved names
     */
    suspend fun resolvePayeeNames(
        importedNames: List<String>,
        existingPayees: List<Payee>,
        threshold: Double = 0.75
    ): PayeeResolutionResult
}
