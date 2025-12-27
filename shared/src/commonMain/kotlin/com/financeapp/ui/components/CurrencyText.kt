package com.financeapp.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.financeapp.ui.theme.FinanceTypography
import kotlin.math.abs

/**
 * Centralized currency formatting and display component.
 * Replaces 10+ duplicate formatCurrency() functions across the codebase.
 *
 * Features:
 * - Consistent formatting: $1,234.56 or -$1,234.56
 * - Tabular numbers for proper alignment
 * - Color coding (green for positive, red for negative, or custom)
 * - Multiple size variants
 *
 * @param amountCents Amount in cents (e.g., 123456 = $1,234.56)
 * @param modifier Modifier for the Text composable
 * @param style Text style (defaults to currency medium with tabular numbers)
 * @param color Optional color override (defaults to green/red based on amount)
 * @param showSign Whether to show + for positive amounts
 * @param fontWeight Optional font weight override
 * @param textAlign Text alignment
 * @param overflow Text overflow behavior
 * @param maxLines Maximum number of lines
 */
@Composable
fun CurrencyText(
    amountCents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = FinanceTypography.currencyMedium,
    color: Color? = null,
    showSign: Boolean = false,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE
) {
    val displayColor = color ?: when {
        amountCents > 0 -> MaterialTheme.colorScheme.primary
        amountCents < 0 -> MaterialTheme.colorScheme.error
        else -> LocalTextStyle.current.color
    }

    val formattedText = formatCurrency(amountCents, showSign)

    Text(
        text = formattedText,
        modifier = modifier,
        style = style,
        color = displayColor,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines
    )
}

/**
 * Large currency display (e.g., account balance header)
 */
@Composable
fun CurrencyTextLarge(
    amountCents: Long,
    modifier: Modifier = Modifier,
    color: Color? = null,
    showSign: Boolean = false
) {
    CurrencyText(
        amountCents = amountCents,
        modifier = modifier,
        style = FinanceTypography.currencyLarge,
        color = color,
        showSign = showSign,
        fontWeight = FontWeight.Bold
    )
}

/**
 * Medium currency display (default, e.g., transaction amounts)
 */
@Composable
fun CurrencyTextMedium(
    amountCents: Long,
    modifier: Modifier = Modifier,
    color: Color? = null,
    showSign: Boolean = false
) {
    CurrencyText(
        amountCents = amountCents,
        modifier = modifier,
        style = FinanceTypography.currencyMedium,
        color = color,
        showSign = showSign
    )
}

/**
 * Small currency display (e.g., running balances, secondary amounts)
 */
@Composable
fun CurrencyTextSmall(
    amountCents: Long,
    modifier: Modifier = Modifier,
    color: Color? = null,
    showSign: Boolean = false
) {
    CurrencyText(
        amountCents = amountCents,
        modifier = modifier,
        style = FinanceTypography.currencySmall,
        color = color,
        showSign = showSign
    )
}

/**
 * Balance display (larger, bold, for account balances)
 */
@Composable
fun BalanceText(
    amountCents: Long,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    CurrencyText(
        amountCents = amountCents,
        modifier = modifier,
        style = FinanceTypography.balance,
        color = color
    )
}

/**
 * Transaction amount display (with color coding)
 */
@Composable
fun TransactionAmountText(
    amountCents: Long,
    modifier: Modifier = Modifier,
    showSign: Boolean = true
) {
    CurrencyText(
        amountCents = amountCents,
        modifier = modifier,
        style = FinanceTypography.transactionAmount,
        showSign = showSign,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * Percentage display (e.g., budget usage, portfolio returns)
 */
@Composable
fun PercentageText(
    percentage: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = FinanceTypography.percentage,
    color: Color? = null,
    showSign: Boolean = true,
    decimals: Int = 1
) {
    val displayColor = color ?: when {
        percentage > 0 -> MaterialTheme.colorScheme.primary
        percentage < 0 -> MaterialTheme.colorScheme.error
        else -> LocalTextStyle.current.color
    }

    val sign = when {
        percentage > 0 && showSign -> "+"
        percentage < 0 -> "-"
        else -> ""
    }

    val formattedValue = String.format("%.${decimals}f", abs(percentage))
    val formattedText = "$sign$formattedValue%"

    Text(
        text = formattedText,
        modifier = modifier,
        style = style,
        color = displayColor
    )
}

/**
 * Core currency formatting function.
 * Converts cents to dollars and formats as currency string.
 *
 * @param amountCents Amount in cents (e.g., 123456 = $1,234.56)
 * @param showSign Whether to show + for positive amounts
 * @return Formatted currency string (e.g., "$1,234.56" or "-$1,234.56")
 */
fun formatCurrency(amountCents: Long, showSign: Boolean = false): String {
    val dollars = amountCents / 100.0
    val absValue = abs(dollars)

    val sign = when {
        amountCents > 0 && showSign -> "+"
        amountCents < 0 -> "-"
        else -> ""
    }

    // Format with thousands separator and 2 decimal places
    val formatted = String.format("%,.2f", absValue)

    return "$sign$$$formatted"
}

/**
 * Format cents to dollars without currency symbol (for input fields)
 */
fun formatCentsToDecimal(amountCents: Long): String {
    val dollars = amountCents / 100.0
    return String.format("%.2f", abs(dollars))
}

/**
 * Parse decimal string to cents (for input processing)
 */
fun parseDecimalToCents(decimalString: String): Long? {
    return try {
        val cleaned = decimalString.replace(",", "").trim()
        val dollars = cleaned.toDoubleOrNull() ?: return null
        (dollars * 100).toLong()
    } catch (e: Exception) {
        null
    }
}
