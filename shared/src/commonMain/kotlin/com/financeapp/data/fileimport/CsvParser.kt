package com.financeapp.data.fileimport

import kotlinx.datetime.LocalDate

class CsvParser {

    fun parse(
        content: String,
        config: CsvImportConfig
    ): Result<List<ImportedTransaction>> {
        return try {
            val lines = content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (lines.isEmpty()) {
                return Result.failure(ImportError.ParseError("Empty CSV file"))
            }

            // Skip header rows
            val dataLines = lines.drop(config.headerRows)

            val transactions = dataLines.mapNotNull { line ->
                parseLine(line, config)
            }

            Result.success(transactions)
        } catch (e: Exception) {
            Result.failure(ImportError.ParseError("Failed to parse CSV: ${e.message}"))
        }
    }

    private fun parseLine(line: String, config: CsvImportConfig): ImportedTransaction? {
        val columns = parseCsvLine(line)

        if (columns.size <= maxOf(
                config.dateColumn,
                config.amountColumn,
                config.descriptionColumn,
                config.memoColumn ?: -1
            )) {
            return null
        }

        val dateStr = columns.getOrNull(config.dateColumn) ?: return null
        val amountStr = columns.getOrNull(config.amountColumn) ?: return null
        val description = columns.getOrNull(config.descriptionColumn) ?: return null
        val memo = config.memoColumn?.let { columns.getOrNull(it) }

        val date = parseDate(dateStr, config.dateFormat) ?: return null
        val amount = parseAmount(amountStr, config)

        // Generate a unique ID from date + amount + description
        val fitId = "${date}_${amount}_${description.hashCode()}"

        val type = if (amount >= 0) TransactionType.CREDIT else TransactionType.DEBIT

        return ImportedTransaction(
            fitId = fitId,
            date = date,
            amount = amount,
            name = description.trim(),
            memo = memo?.trim()?.takeIf { it.isNotEmpty() },
            checkNumber = null,
            type = type
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    // Escaped quote ("") inside quoted field - convert to single quote
                    current.append('"')
                    i++ // Skip the second quote
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString().trim())

        return result
    }

    private fun parseDate(dateStr: String, format: DateFormat): LocalDate? {
        val cleaned = dateStr.trim()
        return try {
            when (format) {
                DateFormat.MM_DD_YYYY -> {
                    val parts = cleaned.split("/", "-")
                    if (parts.size != 3) return null
                    LocalDate(parts[2].toInt(), parts[0].toInt(), parts[1].toInt())
                }
                DateFormat.DD_MM_YYYY -> {
                    val parts = cleaned.split("/", "-")
                    if (parts.size != 3) return null
                    LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                }
                DateFormat.YYYY_MM_DD -> {
                    val parts = cleaned.split("/", "-")
                    if (parts.size != 3) return null
                    LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAmount(amountStr: String, config: CsvImportConfig): Long {
        var amount = AmountParser.parseToCentsOrZero(amountStr)

        // Apply sign inversion if configured
        if (config.invertAmount) {
            amount = -amount
        }

        return amount
    }
}

data class CsvImportConfig(
    val headerRows: Int = 1,
    val dateColumn: Int,
    val amountColumn: Int,
    val descriptionColumn: Int,
    val memoColumn: Int? = null,
    val dateFormat: DateFormat = DateFormat.MM_DD_YYYY,
    val invertAmount: Boolean = false  // Some banks show debits as positive
)

enum class DateFormat {
    MM_DD_YYYY,  // 12/31/2024
    DD_MM_YYYY,  // 31/12/2024
    YYYY_MM_DD   // 2024-12-31
}

// Preset configurations for common banks
object CsvPresets {
    val CHASE_CREDIT = CsvImportConfig(
        headerRows = 1,
        dateColumn = 0,
        descriptionColumn = 2,
        amountColumn = 5,
        dateFormat = DateFormat.MM_DD_YYYY,
        invertAmount = true  // Chase shows debits as positive
    )

    val CITI_CREDIT = CsvImportConfig(
        headerRows = 1,
        dateColumn = 1,
        descriptionColumn = 2,
        amountColumn = 3,  // Debit column
        dateFormat = DateFormat.MM_DD_YYYY,
        invertAmount = true
    )

    val GENERIC = CsvImportConfig(
        headerRows = 1,
        dateColumn = 0,
        amountColumn = 1,
        descriptionColumn = 2,
        dateFormat = DateFormat.MM_DD_YYYY
    )
}
