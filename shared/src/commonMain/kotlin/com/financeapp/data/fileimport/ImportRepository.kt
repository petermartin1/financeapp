package com.financeapp.data.fileimport

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
import kotlinx.datetime.Clock

class ImportRepository(
    private val transactionRepository: TransactionRepository,
    private val payeeRepository: PayeeRepository,
    private val accountRepository: AccountRepository,
    private val payeeMatchingRepository: PayeeMatchingRepository,
    private val tagRepository: TagRepository,
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

            // Extract unique payee names
            val payeeNames = transactions.map { it.name }.distinct()

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
            println("Payee analysis error: ${e.message}")
            e.printStackTrace()
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
            val now = Clock.System.now()

            // Step 1: Check for existing import IDs
            val importIds = transactions.map { it.fitId }
            val existingIds = transactionRepository.getExistingImportIds(importIds)
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

            // Step 2: Create new payees from mappings
            // Deduplicate by name (case-insensitive) to avoid unique constraint violations
            val aliasesToCreate = mutableListOf<PayeeAlias>()

            // Get all existing payees to check for duplicates
            val existingPayees = payeeRepository.getAllPayees().first()
            val existingPayeesByName = existingPayees.associateBy { it.name.lowercase() }

            val newPayeesToCreate = payeeMappings.values
                .filter { it.createNew && it.newPayeeName != null }
                .groupBy { it.newPayeeName!!.lowercase() }
                .mapNotNull { (normalizedName, mappings) ->
                    // Skip if a payee with this name already exists in the database
                    if (normalizedName in existingPayeesByName) {
                        null
                    } else {
                        // Use the first mapping's data for each unique payee name
                        val firstMapping = mappings.first()
                        Payee(
                            name = firstMapping.newPayeeName!!,
                            defaultCategoryId = firstMapping.categoryId
                        )
                    }
                }

            val newPayeeIds = if (newPayeesToCreate.isNotEmpty()) {
                payeeRepository.batchInsertPayees(newPayeesToCreate)
            } else {
                emptyMap()
            }

            // Step 3: Build complete payee name -> ID map
            val payeeMap = mutableMapOf<String, Long>()
            for ((importedName, mapping) in payeeMappings) {
                val payeeId = when {
                    mapping.createNew && mapping.newPayeeName != null -> {
                        // Check if we created a new payee with this name
                        newPayeeIds[mapping.newPayeeName.lowercase()]
                            // Or if a payee with this name already existed
                            ?: existingPayeesByName[mapping.newPayeeName.lowercase()]?.id
                    }
                    mapping.resolvedPayeeId != null -> {
                        mapping.resolvedPayeeId
                    }
                    else -> null
                }

                if (payeeId != null) {
                    payeeMap[importedName.lowercase()] = payeeId

                    // Save alias if user wants to remember this mapping
                    if (mapping.rememberMapping) {
                        aliasesToCreate.add(
                            PayeeAlias(
                                aliasName = importedName,
                                canonicalPayeeId = payeeId,
                                matchType = MatchType.MANUAL,
                                confidence = null,
                                createdAt = now
                            )
                        )
                    }
                }
            }

            // Step 4: Batch insert aliases
            if (aliasesToCreate.isNotEmpty()) {
                payeeMatchingRepository.batchInsertAliases(aliasesToCreate)
            }

            // Step 5: Create transactions
            val transactionsToInsert = newTransactions.map { importedTxn ->
                val mapping = payeeMappings[importedTxn.name]
                Transaction(
                    accountId = accountId,
                    date = importedTxn.date,
                    amount = importedTxn.amount,
                    payeeId = payeeMap[importedTxn.name.lowercase()],
                    categoryId = if (mapping?.applyCategory == true) mapping.categoryId else null,
                    memo = importedTxn.memo,
                    checkNumber = importedTxn.checkNumber,
                    isCleared = true,
                    importId = importedTxn.fitId,
                    transactionType = importedTxn.type.name,
                    sic = importedTxn.sic,
                    createdAt = now,
                    updatedAt = now
                )
            }

            // Step 6: Batch insert transactions
            val insertedTransactionIds = transactionRepository.batchInsertTransactions(transactionsToInsert)

            // Step 7: Apply tags from mappings
            for ((index, importedTxn) in newTransactions.withIndex()) {
                val mapping = payeeMappings[importedTxn.name]
                if (mapping != null && mapping.tagIds.isNotEmpty()) {
                    val transactionId = insertedTransactionIds[index]
                    tagRepository.setTransactionTags(transactionId, mapping.tagIds)
                }
            }

            // Step 8: Notify that balances have changed
            accountRepository.notifyBalancesChanged()
            payeeRepository.notifyPayeesChanged()

            return@withContext Result.success(ImportSummary(
                totalInFile = transactions.size,
                imported = newTransactions.size,
                duplicates = duplicates,
                errors = 0
            ))
        } catch (e: Exception) {
            println("Import with mappings error: ${e.message}")
            e.printStackTrace()
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

            // Step 1: Check for existing import IDs in a single query
            val importIds = transactions.map { it.fitId }
            val existingIds = transactionRepository.getExistingImportIds(importIds)

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

            // Step 2: Get all unique payee names
            val payeeNames = newTransactions.map { it.name }.distinct()

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
                    payeeId = payeeMap[importedTxn.name.lowercase()],
                    categoryId = null,
                    memo = importedTxn.memo,
                    checkNumber = importedTxn.checkNumber,
                    isCleared = true,
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
            println("Import error: ${e.message}")
            e.printStackTrace()
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
