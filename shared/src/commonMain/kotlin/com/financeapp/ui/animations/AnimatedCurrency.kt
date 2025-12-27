package com.financeapp.ui.animations

import androidx.compose.animation.core.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToLong

/**
 * Animated currency text that counts up/down when value changes.
 *
 * @param amountCents Current amount in cents
 * @param modifier Modifier for styling
 * @param style Text style
 * @param color Text color (defaults to income/expense color based on amount)
 * @param animationDuration Duration of the counting animation in milliseconds
 * @param animationSpec Custom animation spec (defaults to spring animation)
 */
@Composable
fun AnimatedCurrency(
    amountCents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color? = null,
    animationDuration: Int = 800,
    animationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
) {
    var previousAmount by remember { mutableStateOf(amountCents) }
    val animatedAmount by animateFloatAsState(
        targetValue = amountCents.toFloat(),
        animationSpec = animationSpec,
        label = "currency_animation"
    )

    LaunchedEffect(amountCents) {
        previousAmount = animatedAmount.roundToLong()
    }

    val displayAmount = animatedAmount.roundToLong()
    val formattedAmount = formatCurrency(displayAmount)

    val textColor = color ?: when {
        displayAmount > 0 -> MaterialTheme.colorScheme.primary
        displayAmount < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = formattedAmount,
        modifier = modifier,
        style = style,
        color = textColor
    )
}

/**
 * Large animated currency display for balances
 */
@Composable
fun AnimatedCurrencyLarge(
    amountCents: Long,
    modifier: Modifier = Modifier,
    showSign: Boolean = false
) {
    AnimatedCurrency(
        amountCents = amountCents,
        modifier = modifier,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum"
        )
    )
}

/**
 * Balance text with animated counting
 */
@Composable
fun AnimatedBalanceText(
    amountCents: Long,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    AnimatedCurrency(
        amountCents = amountCents,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium.copy(
            fontFeatureSettings = "tnum"
        ),
        color = when {
            amountCents >= 0 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        }
    )
}

/**
 * Transaction amount with animated counting (income/expense colors)
 */
@Composable
fun AnimatedTransactionAmount(
    amountCents: Long,
    modifier: Modifier = Modifier
) {
    AnimatedCurrency(
        amountCents = amountCents,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "tnum"
        ),
        color = when {
            amountCents > 0 -> MaterialTheme.colorScheme.primary
            amountCents < 0 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }
    )
}

/**
 * Animated percentage display with counting animation
 */
@Composable
fun AnimatedPercentage(
    percentage: Float,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    decimals: Int = 1
) {
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "percentage_animation"
    )

    val formattedPercentage = "%.${decimals}f%%".format(animatedPercentage)

    Text(
        text = formattedPercentage,
        modifier = modifier,
        style = style,
        color = color
    )
}

private fun formatCurrency(cents: Long): String {
    val dollars = cents / 100.0
    val sign = if (cents < 0) "-" else ""
    return "$sign$${String.format("%.2f", kotlin.math.abs(dollars))}"
}
