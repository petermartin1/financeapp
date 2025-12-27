package com.financeapp.data.fileimport

import com.financeapp.domain.model.Transaction
import com.financeapp.domain.repository.AccountRepository
import com.financeapp.domain.repository.PayeeRepository
import com.financeapp.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class ImportRepository(
    private val transactionRepository: TransactionRepository,
    private val payeeRepository: PayeeRepository,
    private val accountRepository: AccountRepository,
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
