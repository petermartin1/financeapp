package com.financeapp.data.fileimport

import kotlinx.datetime.LocalDate

class OfxParser {

    fun parse(content: String): Result<ImportResult> {
        return try {
            // Normalize SGML to be more parseable
            val normalized = normalizeOfx(content)

            // Determine if this is a credit card or bank statement
            val isCreditCard = normalized.contains("<CCSTMTRS>") || normalized.contains("<CCACCTFROM>")

            val account = if (isCreditCard) {
                parseCreditCardAccount(normalized)
            } else {
                parseBankAccount(normalized)
            }

            val transactions = parseTransactions(normalized)
            val (startDate, endDate) = parseDateRange(normalized)

            Result.success(ImportResult(
                account = account,
                transactions = transactions,
                startDate = startDate,
                endDate = endDate
            ))
        } catch (e: Exception) {
            Result.failure(ImportError.ParseError("Failed to parse OFX: ${e.message}"))
        }
    }

    private fun normalizeOfx(content: String): String {
        // Remove headers (everything before <OFX>)
        val ofxStart = content.indexOf("<OFX>")
        if (ofxStart == -1) {
            throw ImportError.ParseError("No <OFX> tag found")
        }

        var ofx = content.substring(ofxStart)

        // SGML allows unclosed tags - convert to self-closing or add closing tags
        // This is a simplified approach for common OFX patterns
        val tagsToClose = listOf(
            "DTSERVER", "LANGUAGE", "DTSTART", "DTEND", "DTPOSTED", "DTUSER",
            "TRNAMT", "FITID", "NAME", "MEMO", "CHECKNUM", "TRNTYPE", "SIC",
            "BANKID", "ACCTID", "ACCTTYPE", "CURDEF", "BALAMT", "DTASOF",
            "ACCTID", "SEVERITY", "CODE", "MESSAGE"
        )

        for (tag in tagsToClose) {
            // Match <TAG>value without closing tag
            // Lookahead for next tag (any case) or closing tag
            val regex = Regex("<$tag>([^<]*?)(?=<[A-Za-z/])", RegexOption.IGNORE_CASE)
            ofx = ofx.replace(regex) { match ->
                "<$tag>${match.groupValues[1]}</$tag>"
            }
        }

        return ofx
    }

    private fun parseBankAccount(content: String): ImportedAccount {
        val bankId = extractTag(content, "BANKID")
        val accountId = extractTag(content, "ACCTID")
            ?: throw ImportError.ParseError("No account ID found")
        val accountTypeStr = extractTag(content, "ACCTTYPE") ?: "CHECKING"
        val currency = extractTag(content, "CURDEF") ?: "USD"

        val accountType = when (accountTypeStr.uppercase()) {
            "CHECKING" -> ImportedAccountType.CHECKING
            "SAVINGS" -> ImportedAccountType.SAVINGS
            else -> ImportedAccountType.OTHER
        }

        return ImportedAccount(
            bankId = bankId,
            accountId = accountId,
            accountType = accountType,
            currency = currency
        )
    }

    private fun parseCreditCardAccount(content: String): ImportedAccount {
        val accountId = extractTag(content, "ACCTID")
            ?: throw ImportError.ParseError("No account ID found")
        val currency = extractTag(content, "CURDEF") ?: "USD"

        return ImportedAccount(
            bankId = null,
            accountId = accountId,
            accountType = ImportedAccountType.CREDIT_CARD,
            currency = currency
        )
    }

    private fun parseTransactions(content: String): List<ImportedTransaction> {
        val transactions = mutableListOf<ImportedTransaction>()

        // Find all STMTTRN blocks
        val stmtTrnRegex = Regex("<STMTTRN>(.*?)</STMTTRN>", RegexOption.DOT_MATCHES_ALL)
        val matches = stmtTrnRegex.findAll(content)

        for (match in matches) {
            val block = match.groupValues[1]

            val fitId = extractTag(block, "FITID") ?: continue
            val dateStr = extractTag(block, "DTPOSTED") ?: continue
            val amountStr = extractTag(block, "TRNAMT") ?: continue
            val name = extractTag(block, "NAME") ?: extractTag(block, "MEMO") ?: "Unknown"
            val memo = extractTag(block, "MEMO")
            val checkNum = extractTag(block, "CHECKNUM")
            val typeStr = extractTag(block, "TRNTYPE") ?: "OTHER"
            val sic = extractTag(block, "SIC")

            val date = parseOfxDate(dateStr)
            val amount = parseAmount(amountStr)
            val type = parseTransactionType(typeStr)

            transactions.add(ImportedTransaction(
                fitId = fitId,
                date = date,
                amount = amount,
                name = name.trim(),
                memo = memo?.trim(),
                checkNumber = checkNum,
                type = type,
                sic = sic
            ))
        }

        return transactions
    }

    private fun parseDateRange(content: String): Pair<LocalDate?, LocalDate?> {
        val startStr = extractTag(content, "DTSTART")
        val endStr = extractTag(content, "DTEND")

        return Pair(
            startStr?.let { parseOfxDate(it) },
            endStr?.let { parseOfxDate(it) }
        )
    }

    private fun extractTag(content: String, tag: String): String? {
        val regex = Regex("<$tag>([^<]*)</$tag>", RegexOption.IGNORE_CASE)
        return regex.find(content)?.groupValues?.get(1)?.trim()
    }

    private fun parseOfxDate(dateStr: String): LocalDate {
        // OFX dates are YYYYMMDD or YYYYMMDDHHMMSS
        val cleaned = dateStr.trim().take(8)
        if (cleaned.length < 8) {
            throw ImportError.InvalidData("Invalid date: $dateStr")
        }

        val year = cleaned.substring(0, 4).toInt()
        val month = cleaned.substring(4, 6).toInt()
        val day = cleaned.substring(6, 8).toInt()

        return LocalDate(year, month, day)
    }

    private fun parseAmount(amountStr: String): Long {
        return AmountParser.parseToCentsOrZero(amountStr)
    }

    private fun parseTransactionType(typeStr: String): TransactionType {
        return when (typeStr.uppercase()) {
            "CREDIT" -> TransactionType.CREDIT
            "DEBIT" -> TransactionType.DEBIT
            "CHECK" -> TransactionType.CHECK
            "ATM" -> TransactionType.ATM
            "XFER" -> TransactionType.TRANSFER
            else -> TransactionType.OTHER
        }
    }
}
