package com.financeapp.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.financeapp.ui.theme.FinanceTypography

/**
 * Reusable currency input field with validation and formatting.
 *
 * Features:
 * - Automatic formatting as user types
 * - Input validation (only allows valid decimal numbers)
 * - Supports positive and negative amounts
 * - Tabular numbers for alignment
 * - Optional prefix/suffix
 *
 * @param value Current amount as decimal string (e.g., "1234.56")
 * @param onValueChange Callback when value changes
 * @param modifier Modifier for the field
 * @param label Label text
 * @param placeholder Placeholder text
 * @param prefix Prefix text (default: "$")
 * @param allowNegative Whether to allow negative amounts
 * @param isError Whether to show error state
 * @param errorMessage Optional error message to display
 * @param singleLine Whether field is single line
 * @param enabled Whether field is enabled
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Amount",
    placeholder: String = "0.00",
    prefix: String = "$",
    allowNegative: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // Only allow digits, one decimal point, and optional negative sign
            val cleaned = newValue.trim()

            val regex = if (allowNegative) {
                Regex("^-?\\d*\\.?\\d{0,2}$")
            } else {
                Regex("^\\d*\\.?\\d{0,2}$")
            }

            if (cleaned.isEmpty() || cleaned.matches(regex)) {
                onValueChange(cleaned)
            }
        },
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        prefix = { Text(prefix) },
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        supportingText = if (errorMessage != null) {
            { Text(errorMessage) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = FinanceTypography.currency
    )
}

/**
 * Amount field that works directly with cents (Long)
 */
@Composable
fun AmountFieldCents(
    amountCents: Long?,
    onAmountChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Amount",
    placeholder: String = "0.00",
    allowNegative: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    // Convert cents to decimal string for display
    var textValue by remember(amountCents) {
        mutableStateOf(
            if (amountCents != null) {
                formatCentsToDecimal(amountCents)
            } else {
                ""
            }
        )
    }

    AmountField(
        value = textValue,
        onValueChange = { newValue ->
            textValue = newValue
            // Convert back to cents
            val cents = if (newValue.isBlank()) {
                null
            } else {
                parseDecimalToCents(newValue)
            }
            onAmountChange(cents)
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        allowNegative = allowNegative,
        isError = isError,
        errorMessage = errorMessage
    )
}

/**
 * Percentage input field
 */
@Composable
fun PercentageField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Percentage",
    placeholder: String = "0.00",
    allowNegative: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val cleaned = newValue.trim()

            val regex = if (allowNegative) {
                Regex("^-?\\d*\\.?\\d{0,2}$")
            } else {
                Regex("^\\d*\\.?\\d{0,2}$")
            }

            if (cleaned.isEmpty() || cleaned.matches(regex)) {
                // Validate range (0-100 or -100 to 100)
                val number = cleaned.toDoubleOrNull()
                if (number == null || (allowNegative && number >= -100 && number <= 100) || (!allowNegative && number >= 0 && number <= 100)) {
                    onValueChange(cleaned)
                }
            }
        },
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        suffix = { Text("%") },
        singleLine = true,
        isError = isError,
        supportingText = if (errorMessage != null) {
            { Text(errorMessage) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = FinanceTypography.currency
    )
}

/**
 * Validates if a string is a valid amount
 */
fun isValidAmount(value: String, allowNegative: Boolean = false): Boolean {
    if (value.isBlank()) return false

    val regex = if (allowNegative) {
        Regex("^-?\\d+(\\.\\d{1,2})?$")
    } else {
        Regex("^\\d+(\\.\\d{1,2})?$")
    }

    if (!value.matches(regex)) return false

    val number = value.toDoubleOrNull() ?: return false

    return if (allowNegative) {
        true // Any number is valid
    } else {
        number >= 0
    }
}

/**
 * Formats amount with commas for thousands (e.g., 1234.56 -> 1,234.56)
 */
fun formatAmountWithCommas(value: String): String {
    val number = value.toDoubleOrNull() ?: return value

    val parts = value.split(".")
    val wholePart = parts[0]
    val decimalPart = if (parts.size > 1) parts[1] else null

    // Strip negative sign before formatting, re-prepend after
    val negative = wholePart.startsWith("-")
    val absWhole = if (negative) wholePart.substring(1) else wholePart

    // Add commas to whole part
    val formatted = absWhole.reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()

    val withSign = if (negative) "-$formatted" else formatted

    return if (decimalPart != null) {
        "$withSign.$decimalPart"
    } else {
        withSign
    }
}
