package com.financeapp.data.fileimport

import kotlinx.datetime.LocalDate

class CsvParser {

    fun parse(
        content: String,
        config: CsvImportConfig
    ): Result<List<ImportedTransaction>> {
        return try {
            // Tokenize the whole document so quoted fields may contain commas and newlines
            // (RFC 4180), rather than splitting on physical lines first.
            val rows = parseCsvRows(content)

            if (rows.isEmpty()) {
                return Result.failure(ImportError.ParseError("Empty CSV file"))
            }

            // Skip header rows
            val dataRows = rows.drop(config.headerRows)

            val raw = dataRows.mapNotNull { columns -> parseRow(columns, config) }

            Result.success(raw.withStableFitIds("CSV"))
        } catch (e: Exception) {
            Result.failure(ImportError.ParseError("Failed to parse CSV: ${e.message}"))
        }
    }

    private fun parseRow(columns: List<String>, config: CsvImportConfig): RawImported? {
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

        val type = if (amount >= 0) TransactionType.CREDIT else TransactionType.DEBIT

        return RawImported(
            date = date,
            amount = amount,
            name = description.trim(),
            memo = memo?.trim()?.takeIf { it.isNotEmpty() },
            checkNumber = null,
            type = type
        )
    }

    /**
     * Splits the entire CSV document into rows of fields, honoring quoted fields that contain
     * commas, escaped quotes (""), and embedded newlines. Fully-empty rows are dropped.
     */
    private fun parseCsvRows(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            currentRow.add(field.toString().trim())
            field.clear()
        }
        fun endRow() {
            endField()
            rows.add(currentRow)
            currentRow = mutableListOf()
        }

        while (i < content.length) {
            val char = content[i]
            when {
                inQuotes -> when {
                    char == '"' && i + 1 < content.length && content[i + 1] == '"' -> {
                        field.append('"') // escaped quote
                        i++
                    }
                    char == '"' -> inQuotes = false
                    else -> field.append(char) // commas and newlines are literal inside quotes
                }
                char == '"' -> inQuotes = true
                char == ',' -> endField()
                char == '\n' -> endRow()
                char == '\r' -> { /* part of CRLF; row break handled by \n */ }
                else -> field.append(char)
            }
            i++
        }
        // Flush the trailing field/row (file may not end with a newline).
        endRow()

        return rows.filter { row -> row.any { it.isNotEmpty() } }
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
