package com.financeapp.ui.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.ui.theme.Spacing

/**
 * Reusable chart legend item
 */
@Composable
fun ChartLegendItem(
    label: String,
    color: Color,
    value: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Empty state for charts with no data
 */
@Composable
fun EmptyChartState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Chart container with title
 */
@Composable
fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.md)
        )
        content()
    }
}

/**
 * Predefined color palettes for charts
 */
object ChartColors {
    val DefaultPalette = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFFC107), // Amber
        Color(0xFFFF5722), // Deep Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFF00BCD4), // Cyan
        Color(0xFFFF9800), // Orange
        Color(0xFFE91E63), // Pink
        Color(0xFF3F51B5), // Indigo
        Color(0xFF8BC34A), // Light Green
    )

    val FinancePalette = listOf(
        Color(0xFF4CAF50), // Income - Green
        Color(0xFFE53935), // Expense - Red
        Color(0xFF2196F3), // Transfer - Blue
        Color(0xFFFFC107), // Pending - Amber
        Color(0xFF9C27B0), // Investment - Purple
    )

    val CategoryPalette = listOf(
        Color(0xFFE57373), // Red
        Color(0xFFBA68C8), // Purple
        Color(0xFF64B5F6), // Blue
        Color(0xFF4DD0E1), // Cyan
        Color(0xFF81C784), // Green
        Color(0xFFFFD54F), // Yellow
        Color(0xFFFFB74D), // Orange
        Color(0xFFA1887F), // Brown
        Color(0xFF90A4AE), // Blue Grey
        Color(0xFFF06292), // Pink
    )
}
