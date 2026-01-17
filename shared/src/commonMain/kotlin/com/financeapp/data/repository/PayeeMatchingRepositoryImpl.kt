package com.financeapp.data.repository

import com.financeapp.db.schema.PayeeAliases
import com.financeapp.domain.matching.PayeeMatcher
import com.financeapp.domain.model.MatchType
import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.PayeeAlias
import com.financeapp.domain.model.PayeeResolutionResult
import com.financeapp.domain.model.UnresolvedPayee
import com.financeapp.domain.repository.PayeeMatchingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class PayeeMatchingRepositoryImpl(
    private val database: Database,
    private val payeeMatcher: PayeeMatcher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PayeeMatchingRepository {

    override suspend fun getAliasByName(aliasName: String): PayeeAlias? = withContext(ioDispatcher) {
        transaction(database) {
            PayeeAliases
                .select { PayeeAliases.aliasName eq payeeMatcher.normalize(aliasName) }
                .singleOrNull()
                ?.toDomain()
        }
    }

    override suspend fun getAliasesByNames(aliasNames: List<String>): Map<String, PayeeAlias> = withContext(ioDispatcher) {
        if (aliasNames.isEmpty()) {
            return@withContext emptyMap()
        }

        transaction(database) {
            val normalizedNames = aliasNames.map { payeeMatcher.normalize(it) }
            PayeeAliases
                .select { PayeeAliases.aliasName inList normalizedNames }
                .associate { row ->
                    row[PayeeAliases.aliasName] to row.toDomain()
                }
        }
    }

    override suspend fun getAliasesByPayeeId(payeeId: Long): List<PayeeAlias> = withContext(ioDispatcher) {
        transaction(database) {
            PayeeAliases
                .select { PayeeAliases.canonicalPayeeId eq payeeId.toInt() }
                .map { it.toDomain() }
        }
    }

    override suspend fun batchInsertAliases(aliases: List<PayeeAlias>): List<Long> = withContext(ioDispatcher) {
        transaction(database) {
            aliases.map { alias ->
                PayeeAliases.insertAndGetId {
                    it[aliasName] = payeeMatcher.normalize(alias.aliasName)
                    it[canonicalPayeeId] = alias.canonicalPayeeId.toInt()
                    it[matchType] = alias.matchType.name
                    it[confidence] = alias.confidence
                    it[createdAt] = alias.createdAt.toEpochMilliseconds()
                }.value.toLong()
            }
        }
    }

    override suspend fun deleteAlias(id: Long): Unit = withContext(ioDispatcher) {
        transaction(database) {
            PayeeAliases.deleteWhere { PayeeAliases.id eq id.toInt() }
        }
    }

    override suspend fun resolvePayeeNames(
        importedNames: List<String>,
        existingPayees: List<Payee>,
        threshold: Double
    ): PayeeResolutionResult = withContext(ioDispatcher) {
        val autoResolved = mutableMapOf<String, Long>()
        val needsReview = mutableListOf<UnresolvedPayee>()

        // Group imported names by count (how many transactions have each name)
        val nameCounts = importedNames.groupingBy { it }.eachCount()

        // Batch lookup all aliases in a single query
        val uniqueNames = nameCounts.keys.filter { it.isNotBlank() }
        val aliasMap = getAliasesByNames(uniqueNames)

        // Sort names alphabetically for consistent ordering
        val sortedNames = uniqueNames.sortedBy { it.lowercase() }

        // Track previously processed payee names (for checking intra-import similarity)
        val processedPayeeNames = mutableListOf<String>()

        // Process each unique name individually (no grouping!)
        for ((index, importedName) in sortedNames.withIndex()) {
            val count = nameCounts[importedName] ?: 0

            // Check if we have a saved alias for this name
            val normalizedName = payeeMatcher.normalize(importedName)
            val alias = aliasMap[normalizedName]
            if (alias != null) {
                autoResolved[importedName] = alias.canonicalPayeeId
                // Don't add to processedPayeeNames since it's auto-resolved
                continue
            }

            // No saved alias - find similar existing payees in database
            val matches = payeeMatcher.findSimilarPayees(importedName, existingPayees, threshold)

            // Also check for similar payees that were processed earlier in THIS import
            val similarInImport = if (processedPayeeNames.isNotEmpty()) {
                payeeMatcher.findSimilarNames(importedName, processedPayeeNames, threshold)
                    .also { similar ->
                    }
            } else {
                emptyList()
            }

            // Create an unresolved payee entry
            val unresolvedPayee = UnresolvedPayee(
                importedName = importedName,
                transactionCount = count,
                suggestedMatches = matches,
                variantNames = listOf(importedName), // Only this name
                similarInImport = similarInImport  // Names from this import that are similar
            )

            needsReview.add(unresolvedPayee)

            // Add this name to the list of processed names for future comparisons
            processedPayeeNames.add(importedName)
        }

        PayeeResolutionResult(
            autoResolved = autoResolved,
            needsReview = needsReview
        )
    }

    /**
     * Extension function to convert database row to PayeeAlias domain model
     */
    private fun ResultRow.toDomain() = PayeeAlias(
        id = this[PayeeAliases.id].value.toLong(),
        aliasName = this[PayeeAliases.aliasName],
        canonicalPayeeId = this[PayeeAliases.canonicalPayeeId].value.toLong(),
        matchType = MatchType.valueOf(this[PayeeAliases.matchType]),
        confidence = this[PayeeAliases.confidence],
        createdAt = Instant.fromEpochMilliseconds(this[PayeeAliases.createdAt])
    )
}
