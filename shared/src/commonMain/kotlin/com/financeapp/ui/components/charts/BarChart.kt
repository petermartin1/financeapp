package com.financeapp.ui.components.charts

import java.util.Locale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.ui.theme.Spacing

/**
 * Data model for bar chart
 */
data class BarChartData(
    val label: String,
    val value: Float,
    val color: Color
)

/**
 * Data model for grouped bar chart
 */
data class GroupedBarData(
    val label: String,
    val values: List<BarValue>
)

data class BarValue(
    val value: Float,
    val color: Color,
    val legend: String
)

/**
 * Professional bar chart component
 *
 * Features:
 * - Axis labels and grid lines
 * - Grouped or stacked bars
 * - Tooltips (future enhancement)
 * - Horizontal or vertical orientation
 */
@Composable
fun BarChart(
    data: List<BarChartData>,
    modifier: Modifier = Modifier,
    showGrid: Boolean = true,
    showLabels: Boolean = true,
    maxValue: Float? = null
) {
    if (data.isEmpty()) {
        EmptyChartState(
            message = "No data to display",
            modifier = modifier
        )
        return
    }

    val chartMax = maxValue ?: data.maxOf { it.value } * 1.1f
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(vertical = Spacing.md)
        ) {
            val barWidth = size.width / (data.size * 2 + 1)
            val chartHeight = size.height * 0.85f
            val labelHeight = size.height * 0.15f

            // Draw grid lines
            if (showGrid) {
                val gridLines = 5
                for (i in 0..gridLines) {
                    val y = chartHeight * (i.toFloat() / gridLines)
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // Draw bars
            data.forEachIndexed { index, bar ->
                val x = barWidth * (index * 2 + 1)
                val barHeight = (bar.value / chartMax) * chartHeight

                // Bar
                drawRect(
                    color = bar.color,
                    topLeft = Offset(x, chartHeight - barHeight),
                    size = Size(barWidth, barHeight)
                )

                // Label
                if (showLabels) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = bar.label,
                        topLeft = Offset(
                            x + barWidth / 2 - 20.dp.toPx(),
                            chartHeight + 8.dp.toPx()
                        ),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    )
                }
            }
        }
    }
}

/**
 * Grouped bar chart (multiple bars per category)
 */
@Composable
fun GroupedBarChart(
    data: List<GroupedBarData>,
    modifier: Modifier = Modifier,
    showGrid: Boolean = true,
    showLabels: Boolean = true,
    showLegend: Boolean = true
) {
    if (data.isEmpty()) {
        EmptyChartState(
            message = "No data to display",
            modifier = modifier
        )
        return
    }

    val chartMax = data.flatMap { it.values }.maxOf { it.value } * 1.1f
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier) {
        // Legend
        if (showLegend && data.isNotEmpty() && data.first().values.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally)
            ) {
                data.first().values.forEach { barValue ->
                    ChartLegendItem(
                        label = barValue.legend,
                        color = barValue.color,
                        value = null
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(vertical = Spacing.md)
        ) {
            val groupWidth = size.width / (data.size + 1)
            val barsPerGroup = data.firstOrNull()?.values?.size ?: 1
            val barWidth = groupWidth / (barsPerGroup + 1)
            val chartHeight = size.height * 0.85f

            // Draw grid lines
            if (showGrid) {
                val gridLines = 5
                for (i in 0..gridLines) {
                    val y = chartHeight * (i.toFloat() / gridLines)
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // Draw grouped bars
            data.forEachIndexed { groupIndex, group ->
                val groupX = groupWidth * (groupIndex + 0.5f)

                group.values.forEachIndexed { barIndex, barValue ->
                    val x = groupX + barWidth * (barIndex - barsPerGroup / 2f)
                    val barHeight = (barValue.value / chartMax) * chartHeight

                    // Bar
                    drawRect(
                        color = barValue.color,
                        topLeft = Offset(x, chartHeight - barHeight),
                        size = Size(barWidth, barHeight)
                    )
                }

                // Group label
                if (showLabels) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = group.label,
                        topLeft = Offset(
                            groupX - 20.dp.toPx(),
                            chartHeight + 8.dp.toPx()
                        ),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    )
                }
            }
        }
    }
}

/**
 * Horizontal bar chart variant
 */
@Composable
fun HorizontalBarChart(
    data: List<BarChartData>,
    modifier: Modifier = Modifier,
    showGrid: Boolean = true,
    maxValue: Float? = null
) {
    if (data.isEmpty()) {
        EmptyChartState(
            message = "No data to display",
            modifier = modifier
        )
        return
    }

    val chartMax = maxValue ?: data.maxOf { it.value } * 1.1f

    Column(
        modifier = modifier.padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        data.forEach { bar ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Label
                Text(
                    text = bar.label,
                    modifier = Modifier.width(100.dp),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.width(Spacing.sm))

                // Bar
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    ) {
                        val barWidth = (bar.value / chartMax) * size.width

                        // Background
                        if (showGrid) {
                            drawRect(
                                color = Color.LightGray.copy(alpha = 0.1f),
                                size = Size(size.width, size.height)
                            )
                        }

                        // Bar
                        drawRect(
                            color = bar.color,
                            size = Size(barWidth, size.height)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.sm))

                // Value
                Text(
                    text = String.format(Locale.ROOT, "%.0f", bar.value),
                    modifier = Modifier.width(60.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
