package com.financeapp.data.fileimport

/**
 * Parses monetary amounts from strings to cents (Long) without floating-point precision loss.
 *
 * This avoids the common bug where `"123.45".toDouble() * 100` can produce 12344.999999
 * which then truncates to 12344 when converted to Long.
 */
object AmountParser {

    /**
     * Parses an amount string to cents (Long).
     * Handles various formats:
     * - "123.45" -> 12345
     * - "-123.45" -> -12345
     * - "(123.45)" -> -12345 (accounting format)
     * - "$1,234.56" -> 123456
     * - "1234" -> 123400 (no decimal = dollars)
     * - ".45" -> 45
     *
     * @param amountStr The amount string to parse
     * @return The amount in cents, or null if parsing fails
     */
    fun parseToCents(amountStr: String): Long? {
        if (amountStr.isBlank()) return null

        try {
            // Clean the string: remove currency symbols, commas, spaces
            var cleaned = amountStr
                .replace("$", "")
                .replace(",", "")
                .replace(" ", "")
                .trim()

            if (cleaned.isEmpty()) return null

            // Track if negative
            var isNegative = false

            // Handle parentheses as negative (accounting format)
            if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
                isNegative = true
                cleaned = cleaned.drop(1).dropLast(1).trim()
            }

            // Handle leading minus sign
            if (cleaned.startsWith("-")) {
                isNegative = true
                cleaned = cleaned.drop(1).trim()
            }

            // Handle leading plus sign
            if (cleaned.startsWith("+")) {
                cleaned = cleaned.drop(1).trim()
            }

            if (cleaned.isEmpty()) return null

            // Split on decimal point
            val parts = cleaned.split(".")

            val dollars: Long
            val cents: Long

            when (parts.size) {
                1 -> {
                    // No decimal point - treat as whole dollars
                    dollars = parts[0].toLongOrNull() ?: return null
                    cents = 0
                }
                2 -> {
                    // Has decimal point
                    dollars = if (parts[0].isEmpty()) 0 else parts[0].toLongOrNull() ?: return null

                    // Handle cents part - pad or truncate to 2 digits
                    val centsPart = parts[1]
                    cents = when {
                        centsPart.isEmpty() -> 0
                        centsPart.length == 1 -> {
                            // Single digit like ".5" means 50 cents
                            (centsPart.toLongOrNull() ?: return null) * 10
                        }
                        centsPart.length == 2 -> {
                            centsPart.toLongOrNull() ?: return null
                        }
                        else -> {
                            // More than 2 decimal places - take first 2 and round
                            // Uses half-up rounding (standard for financial calculations)
                            val first3 = centsPart.take(3).toLongOrNull() ?: return null
                            (first3 + 5) / 10  // Round to nearest cent (half-up)
                        }
                    }
                }
                else -> {
                    // Multiple decimal points - invalid
                    return null
                }
            }

            val totalCents = dollars * 100 + cents
            return if (isNegative) -totalCents else totalCents

        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Parses an amount string to cents, returning 0 if parsing fails.
     */
    fun parseToCentsOrZero(amountStr: String): Long {
        return parseToCents(amountStr) ?: 0L
    }
}
