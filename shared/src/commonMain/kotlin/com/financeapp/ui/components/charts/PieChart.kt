package com.financeapp.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.ui.theme.Spacing
import kotlin.math.cos
import kotlin.math.sin

/**
 * Data model for pie chart slices
 */
data class PieChartData(
    val label: String,
    val value: Float,
    val color: Color
)

/**
 * Professional pie chart component with legend and labels
 *
 * Features:
 * - Automatic percentage calculation
 * - Hover effects (future enhancement)
 * - Legend with color indicators
 * - Center hole for donut chart style
 * - Click interactions (future enhancement)
 */
@Composable
fun PieChart(
    data: List<PieChartData>,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
    showLabels: Boolean = true,
    centerHoleRatio: Float = 0f, // 0 = full pie, 0.5 = donut with 50% hole
    totalLabel: String? = null
) {
    if (data.isEmpty()) {
        EmptyChartState(
            message = "No data to display",
            modifier = modifier
        )
        return
    }

    val total = data.sumOf { it.value.toDouble() }.toFloat()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pie chart
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    var startAngle = -90f

                    data.forEach { slice ->
                        val sweepAngle = (slice.value / total) * 360f

                        // Draw slice
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            size = Size(size.minDimension, size.minDimension),
                            topLeft = Offset(
                                (size.width - size.minDimension) / 2,
                                (size.height - size.minDimension) / 2
                            )
                        )

                        startAngle += sweepAngle
                    }

                    // Draw center hole for donut chart
                    if (centerHoleRatio > 0f) {
                        val holeSize = size.minDimension * centerHoleRatio
                        drawCircle(
                            color = Color.White,
                            radius = holeSize / 2,
                            center = center
                        )
                    }
                }

                // Center label for donut charts
                if (centerHoleRatio > 0f && totalLabel != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = totalLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Legend
            if (showLegend) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    data.forEach { slice ->
                        val percentage = (slice.value / total) * 100
                        ChartLegendItem(
                            label = slice.label,
                            color = slice.color,
                            value = String.format("%.1f%%", percentage)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Donut chart variant (pie chart with center hole)
 */
@Composable
fun DonutChart(
    data: List<PieChartData>,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
    totalLabel: String? = null
) {
    PieChart(
        data = data,
        modifier = modifier,
        showLegend = showLegend,
        centerHoleRatio = 0.5f,
        totalLabel = totalLabel
    )
}
