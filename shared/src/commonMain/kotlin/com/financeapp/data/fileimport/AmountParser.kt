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
            // Clean the string: keep only digits, separators and sign/paren markers. This
            // drops currency symbols ($, €, £, "USD", …) and any thousands spaces.
            var cleaned = amountStr.filter { it.isDigit() || it in ".,-+()" }

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

            // Handle trailing minus sign (e.g. "123.45-" used by some accounting/mainframe exports)
            if (cleaned.endsWith("-")) {
                isNegative = true
                cleaned = cleaned.dropLast(1).trim()
            }

            if (cleaned.isEmpty()) return null

            // Normalize US ("1,234.56") and European ("1.234,56") separators to a canonical
            // form using "." as the decimal point and no thousands separators.
            cleaned = normalizeSeparators(cleaned)

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
     * Resolves "." vs "," as decimal/thousands separators, returning a string that uses "."
     * as the decimal point with no thousands separators.
     *
     * Rules:
     * - Both separators present: the right-most one is the decimal point; the other is the
     *   thousands separator. (e.g. "1.234,56" -> "1234.56", "1,234.56" -> "1234.56")
     * - One separator type, appearing more than once: it is a thousands separator
     *   (e.g. "1.234.567" -> "1234567").
     * - A single "." is the decimal point (US default: "12.345" -> "12.345").
     * - A single "," with exactly 3 trailing digits is a US thousands separator
     *   ("1,234" -> "1234"); otherwise it is a European decimal comma
     *   ("1234,56" -> "1234.56", "1,5" -> "1.5").
     */
    private fun normalizeSeparators(value: String): String {
        val hasDot = value.contains('.')
        val hasComma = value.contains(',')

        return when {
            hasDot && hasComma -> {
                val decimalSep = if (value.lastIndexOf('.') > value.lastIndexOf(',')) '.' else ','
                val thousandsSep = if (decimalSep == '.') ',' else '.'
                value.replace(thousandsSep.toString(), "").replace(decimalSep, '.')
            }
            hasDot || hasComma -> {
                val sep = if (hasDot) '.' else ','
                val occurrences = value.count { it == sep }
                val digitsAfter = value.length - value.lastIndexOf(sep) - 1
                // Repeated separators are always thousands; a single comma grouping 3 digits
                // is a US thousands separator. Everything else is the decimal point.
                if (occurrences > 1 || (sep == ',' && digitsAfter == 3)) {
                    value.replace(sep.toString(), "")
                } else {
                    value.replace(sep, '.')
                }
            }
            else -> value
        }
    }

    /**
     * Parses an amount string to cents, returning 0 if parsing fails.
     */
    fun parseToCentsOrZero(amountStr: String): Long {
        return parseToCents(amountStr) ?: 0L
    }
}
