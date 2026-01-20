package com.financeapp.data.fileimport

import kotlinx.datetime.LocalDate

class QifParser {

    fun parse(content: String): Result<List<ImportedTransaction>> {
        return try {
            val lines = content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (lines.isEmpty()) {
                return Result.failure(ImportError.ParseError("Empty QIF file"))
            }

            val transactions = mutableListOf<ImportedTransaction>()
            var currentTransaction = QifTransaction()

            for (line in lines) {
                when {
                    line.startsWith("!Type:") -> {
                        // Account type header, skip
                    }
                    line == "^" -> {
                        // End of transaction
                        val imported = currentTransaction.toImportedTransaction()
                        if (imported != null) {
                            transactions.add(imported)
                        }
                        currentTransaction = QifTransaction()
                    }
                    line.startsWith("D") -> {
                        currentTransaction.date = parseQifDate(line.substring(1))
                    }
                    line.startsWith("T") -> {
                        currentTransaction.amountT = parseQifAmount(line.substring(1))
                    }
                    line.startsWith("U") -> {
                        // U field is typically higher precision, prefer it over T
                        currentTransaction.amountU = parseQifAmount(line.substring(1))
                    }
                    line.startsWith("P") -> {
                        currentTransaction.payee = line.substring(1).trim()
                    }
                    line.startsWith("M") -> {
                        currentTransaction.memo = line.substring(1).trim()
                    }
                    line.startsWith("N") -> {
                        currentTransaction.checkNumber = line.substring(1).trim()
                    }
                    line.startsWith("C") -> {
                        currentTransaction.cleared = line.substring(1).trim()
                    }
                    line.startsWith("L") -> {
                        currentTransaction.category = line.substring(1).trim()
                    }
                }
            }

            // Handle last transaction if file doesn't end with ^
            if (currentTransaction.date != null && (currentTransaction.amountT != null || currentTransaction.amountU != null)) {
                val imported = currentTransaction.toImportedTransaction()
                if (imported != null) {
                    transactions.add(imported)
                }
            }

            Result.success(transactions)
        } catch (e: Exception) {
            Result.failure(ImportError.ParseError("Failed to parse QIF: ${e.message}"))
        }
    }

    private fun parseQifDate(dateStr: String): LocalDate? {
        val cleaned = dateStr.trim()

        return try {
            // QIF dates can be in various formats:
            // MM/DD/YY, MM/DD/YYYY, MM/DD'YY, MM-DD-YY, etc.
            val normalized = cleaned
                .replace("'", "/")
                .replace("-", "/")

            val parts = normalized.split("/")
            if (parts.size < 3) return null

            val month = parts[0].toInt()
            val day = parts[1].toInt()
            var year = parts[2].toInt()

            // Handle 2-digit years
            if (year < 100) {
                year += if (year > 50) 1900 else 2000
            }

            LocalDate(year, month, day)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseQifAmount(amountStr: String): Long? {
        return AmountParser.parseToCents(amountStr)
    }

    private data class QifTransaction(
        var date: LocalDate? = null,
        var amountT: Long? = null,  // T field - standard amount
        var amountU: Long? = null,  // U field - higher precision amount
        var payee: String? = null,
        var memo: String? = null,
        var checkNumber: String? = null,
        var cleared: String? = null,
        var category: String? = null
    ) {
        fun toImportedTransaction(): ImportedTransaction? {
            val d = date ?: return null
            // Prefer U (higher precision) over T when both are present
            val a = amountU ?: amountT ?: return null
            val name = payee ?: memo ?: "Unknown"

            // Generate unique ID
            val fitId = "QIF_${d}_${a}_${name.hashCode()}"

            val type = if (a >= 0) TransactionType.CREDIT else TransactionType.DEBIT

            return ImportedTransaction(
                fitId = fitId,
                date = d,
                amount = a,
                name = name,
                memo = if (payee != null) memo else null,
                checkNumber = checkNumber,
                type = type
            )
        }
    }
}
