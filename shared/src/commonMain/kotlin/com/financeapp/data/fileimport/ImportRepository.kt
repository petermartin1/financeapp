package com.financeapp.data.fileimport

import com.financeapp.db.schema.PayeeAliases
import com.financeapp.db.schema.Payees
import com.financeapp.db.schema.TransactionTags
import com.financeapp.db.schema.Transactions
import com.financeapp.domain.matching.PayeeMatcher
import com.financeapp.domain.model.MatchType
import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.PayeeAlias
import com.financeapp.domain.model.PayeeMapping
import com.financeapp.domain.model.PayeeResolutionResult
import com.financeapp.domain.model.Transaction
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.PayeeMatchingRepository
import com.financeapp.domain.repository.PayeeRepository
import com.financeapp.domain.repository.TagRepository
import com.financeapp.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ImportRepository(
    private val transactionRepository: TransactionRepository,
    private val payeeRepository: PayeeRepository,
    private val accountRepository: AccountRepository,
    private val payeeMatchingRepository: PayeeMatchingRepository,
    private val tagRepository: TagRepository,
    private val database: Database,
    private val payeeMatcher: PayeeMatcher = PayeeMatcher(),
    private val ofxParser: OfxParser = OfxParser(),
    private val csvParser: CsvParser = CsvParser(),
    private val qifParser: QifParser = QifParser()
) {

    // Preview functions - parse without importing
    suspend fun previewOfxFile(content: String): Result<List<ImportedTransaction>> = withContext(Dispatchers.IO) {
        val parseResult = ofxParser.parse(content)
        if (parseResult.isFailure) {
            return@withContext Result.failure(parseResult.exceptionOrNull()!!)
        }
        Result.success(parseResult.getOrThrow().transactions)
    }

    suspend fun previewCsvFile(content: String, config: CsvImportConfig): Result<List<ImportedTransaction>> = withContext(Dispatchers.IO) {
        csvParser.parse(content, config)
    }

    suspend fun previewQifFile(content: String): Result<List<ImportedTransaction>> = withContext(Dispatchers.IO) {
        qifParser.parse(content)
    }

    // Import with preview data
    suspend fun importPreviewedTransactions(
        transactions: List<ImportedTransaction>,
        accountId: Long
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        importTransactions(transactions, accountId)
    }

    // QIF import
    suspend fun importQifFile(
        content: String,
        accountId: Long
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        val parseResult = qifParser.parse(content)

        if (parseResult.isFailure) {
            return@withContext Result.failure(parseResult.exceptionOrNull()!!)
        }

        val transactions = parseResult.getOrThrow()
        importTransactions(transactions, accountId)
    }


    suspend fun importOfxFile(
        content: String,
        accountId: Long
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        val parseResult = ofxParser.parse(content)

        if (parseResult.isFailure) {
            return@withContext Result.failure(parseResult.exceptionOrNull()!!)
        }

        val importResult = parseResult.getOrThrow()
        importTransactions(importResult.transactions, accountId)
    }

    suspend fun importCsvFile(
        content: String,
        accountId: Long,
        config: CsvImportConfig
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        val parseResult = csvParser.parse(content, config)

        if (parseResult.isFailure) {
            return@withContext Result.failure(parseResult.exceptionOrNull()!!)
        }

        val transactions = parseResult.getOrThrow()
        importTransactions(transactions, accountId)
    }

    /**
     * Analyze imported payee names to find matches with existing payees
     * Returns auto-resolved mappings and payees that need user review
     */
    suspend fun analyzeImportPayees(
        transactions: List<ImportedTransaction>
    ): Result<PayeeResolutionResult> = withContext(Dispatchers.IO) {
        try {

            // Extract unique payee names, skipping checks (their name is not a payee).
            val payeeNames = transactions.filterNot { it.isCheck }.map { it.name }.distinct()

            // Get all existing payees
            val payeeList = payeeRepository.getAllPayees().first()

            // Resolve payee names using matching repository
            val result = payeeMatchingRepository.resolvePayeeNames(
                importedNames = payeeNames,
                existingPayees = payeeList,
                threshold = 0.75
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import transactions with user-defined payee mappings
     * Applies categories and tags based on mappings
     */
    suspend fun importWithMappings(
        transactions: List<ImportedTransaction>,
        accountId: Long,
        payeeMappings: Map<String, PayeeMapping>
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        if (transactions.isEmpty()) {
            return@withContext Result.success(ImportSummary(0, 0, 0, 0))
        }

        try {
            val nowMillis = Clock.System.now().toEpochMilliseconds()

            // --- Reads / dedup precomputation (no writes) ---
            val importIds = transactions.map { it.fitId }
            val existingIds = transactionRepository.getExistingImportIds(accountId, importIds)
            val newTransactions = transactions.filter { it.fitId !in existingIds }
            val duplicates = transactions.size - newTransactions.size

            if (newTransactions.isEmpty()) {
                return@withContext Result.success(ImportSummary(
                    totalInFile = transactions.size,
                    imported = 0,
                    duplicates = duplicates,
                    errors = 0
                ))
            }

            val existingPayeesByName = payeeRepository.getAllPayees().first()
                .associateBy { it.name.lowercase() }

            // New payees to create (deduplicated by name, skipping ones that already exist)
            val newPayeesToCreate = payeeMappings.values
                .filter { it.createNew && it.newPayeeName != null }
                .groupBy { it.newPayeeName!!.lowercase() }
                .mapNotNull { (normalizedName, mappings) ->
                    if (normalizedName in existingPayeesByName) null
                    else Payee(name = mappings.first().newPayeeName!!, defaultCategoryId = mappings.first().categoryId)
                }

            // --- All writes in ONE transaction so a mid-way failure leaves nothing behind (N9) ---
            transaction(database) {
                // Payees
                val newPayeeIds = newPayeesToCreate.associate { payee ->
                    val id = Payees.insert {
                        it[Payees.name] = payee.name
                        it[Payees.defaultCategoryId] = payee.defaultCategoryId?.toInt()
                    }[Payees.id].value.toLong()
                    payee.name.lowercase() to id
                }

                // Resolve payee ids per imported name, collecting alias candidates to remember
                val payeeMap = mutableMapOf<String, Long>()
                // normalized alias name -> (canonical payee id, preferred category id)
                val aliasByNormalizedName = LinkedHashMap<String, Pair<Long, Long?>>()
                for ((importedName, mapping) in payeeMappings) {
                    val payeeId = when {
                        mapping.createNew && mapping.newPayeeName != null ->
                            newPayeeIds[mapping.newPayeeName.lowercase()]
                                ?: existingPayeesByName[mapping.newPayeeName.lowercase()]?.id
                        mapping.resolvedPayeeId != null -> mapping.resolvedPayeeId
                        else -> null
                    }
                    if (payeeId != null) {
                        payeeMap[importedName.lowercase()] = payeeId
                        if (mapping.rememberMapping) {
                            val normalized = payeeMatcher.normalize(importedName)
                            if (normalized.isNotBlank()) {
                                aliasByNormalizedName[normalized] =
                                    payeeId to (if (mapping.applyCategory) mapping.categoryId else null)
                            }
                        }
                    }
                }

                // Aliases — skip any whose normalized name is already stored (mirrors batchInsertAliases)
                if (aliasByNormalizedName.isNotEmpty()) {
                    val existingAliasNames = PayeeAliases
                        .select(PayeeAliases.aliasName)
                        .where { PayeeAliases.aliasName inList aliasByNormalizedName.keys.toList() }
                        .map { it[PayeeAliases.aliasName] }
                        .toSet()
                    for ((normalized, info) in aliasByNormalizedName) {
                        if (normalized in existingAliasNames) continue
                        val (canonicalPayeeId, preferredCategoryId) = info
                        PayeeAliases.insert {
                            it[PayeeAliases.aliasName] = normalized
                            it[PayeeAliases.canonicalPayeeId] = canonicalPayeeId.toInt()
                            it[PayeeAliases.matchType] = MatchType.MANUAL.name
                            it[PayeeAliases.confidence] = null
                            it[PayeeAliases.preferredCategoryId] = preferredCategoryId?.toInt()
                            it[PayeeAliases.createdAt] = nowMillis
                        }
                    }
                }

                // Transactions and their tags together, so a tagged import never leaves a
                // transaction inserted but its tags missing.
                for (importedTxn in newTransactions) {
                    val mapping = payeeMappings[importedTxn.name]
                    val dateMillis = importedTxn.date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                    val txnId = Transactions.insert {
                        it[Transactions.accountId] = accountId.toInt()
                        it[Transactions.date] = dateMillis
                        it[Transactions.amount] = importedTxn.amount
                        // Checks never carry a payee — leave it unassigned for the user to fill in.
                        it[Transactions.payeeId] = if (importedTxn.isCheck) null
                            else payeeMap[importedTxn.name.lowercase()]?.toInt()
                        it[Transactions.categoryId] = (if (mapping?.applyCategory == true) mapping.categoryId else null)?.toInt()
                        it[Transactions.memo] = importedTxn.memo
                        it[Transactions.checkNumber] = importedTxn.effectiveCheckNumber
                        it[Transactions.importedName] = importedTxn.name
                        it[Transactions.isCleared] = false
                        it[Transactions.importId] = importedTxn.fitId
                        it[Transactions.transactionType] = importedTxn.type.name
                        it[Transactions.sic] = importedTxn.sic
                        it[Transactions.createdAt] = nowMillis
                        it[Transactions.updatedAt] = nowMillis
                    }[Transactions.id].value.toLong()

                    for (tagId in mapping?.tagIds?.distinct().orEmpty()) {
                        TransactionTags.insert {
                            it[TransactionTags.transactionId] = txnId.toInt()
                            it[TransactionTags.tagId] = tagId.toInt()
                        }
                    }
                }
            }

            accountRepository.notifyBalancesChanged()
            payeeRepository.notifyPayeesChanged()
            transactionRepository.notifyTransactionsChanged()

            return@withContext Result.success(ImportSummary(
                totalInFile = transactions.size,
                imported = newTransactions.size,
                duplicates = duplicates,
                errors = 0
            ))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private suspend fun importTransactions(
        transactions: List<ImportedTransaction>,
        accountId: Long
    ): Result<ImportSummary> {
        if (transactions.isEmpty()) {
            return Result.success(ImportSummary(0, 0, 0, 0))
        }

        try {
            val now = Clock.System.now()

            // Step 1: Check for existing import IDs in a single query (scoped to this account)
            val importIds = transactions.map { it.fitId }
            val existingIds = transactionRepository.getExistingImportIds(accountId, importIds)

            // Filter out duplicates
            val newTransactions = transactions.filter { it.fitId !in existingIds }
            val duplicates = transactions.size - newTransactions.size

            if (newTransactions.isEmpty()) {
                return Result.success(ImportSummary(
                    totalInFile = transactions.size,
                    imported = 0,
                    duplicates = duplicates,
                    errors = 0
                ))
            }

            // Step 2: Get all unique payee names, skipping checks (their name is not a payee).
            val payeeNames = newTransactions.filterNot { it.isCheck }.map { it.name }.distinct()

            // Step 3: Get existing payees in a single query
            val existingPayees = payeeRepository.getPayeesByNames(payeeNames)

            // Step 4: Create new payees for names that don't exist
            val newPayeeNames = payeeNames.filter { it.lowercase() !in existingPayees.keys }
            val newPayees = newPayeeNames.map { com.financeapp.domain.model.Payee(name = it) }

            val newPayeeIds = if (newPayees.isNotEmpty()) {
                payeeRepository.batchInsertPayees(newPayees)
            } else {
                emptyMap()
            }

            // Step 5: Build payee name -> ID map
            val payeeMap = existingPayees.mapValues { it.value.id } + newPayeeIds

            // Step 6: Create all transactions
            val transactionsToInsert = newTransactions.map { importedTxn ->
                Transaction(
                    accountId = accountId,
                    date = importedTxn.date,
                    amount = importedTxn.amount,
                    // Checks never carry a payee — leave it unassigned for the user to fill in.
                    payeeId = if (importedTxn.isCheck) null else payeeMap[importedTxn.name.lowercase()],
                    categoryId = null,
                    memo = importedTxn.memo,
                    checkNumber = importedTxn.effectiveCheckNumber,
                    importedName = importedTxn.name,
                    isCleared = false,
                    importId = importedTxn.fitId,
                    transactionType = importedTxn.type.name,
                    sic = importedTxn.sic,
                    createdAt = now,
                    updatedAt = now
                )
            }

            // Step 7: Batch insert all transactions in a single transaction
            transactionRepository.batchInsertTransactions(transactionsToInsert)

            // Step 8: Notify that balances have changed so UI can update
            accountRepository.notifyBalancesChanged()

            return Result.success(ImportSummary(
                totalInFile = transactions.size,
                imported = newTransactions.size,
                duplicates = duplicates,
                errors = 0
            ))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}

data class ImportSummary(
    val totalInFile: Int,
    val imported: Int,
    val duplicates: Int,
    val errors: Int
)
