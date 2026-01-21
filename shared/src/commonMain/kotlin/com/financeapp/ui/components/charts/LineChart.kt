package com.financeapp.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.ui.theme.Spacing

/**
 * Data model for line chart
 */
data class LineChartData(
    val label: String,
    val series: List<LineSeriesData>
)

data class LineSeriesData(
    val name: String,
    val points: List<Float>,
    val color: Color,
    val fillArea: Boolean = false
)

/**
 * Professional line chart component
 *
 * Features:
 * - Multiple series support
 * - Area fill option
 * - Grid lines and axis labels
 * - Crosshair (future enhancement)
 * - Tooltips on hover (future enhancement)
 */
@Composable
fun LineChart(
    labels: List<String>,
    series: List<LineSeriesData>,
    modifier: Modifier = Modifier,
    showGrid: Boolean = true,
    showLabels: Boolean = true,
    showLegend: Boolean = true
) {
    if (series.isEmpty() || labels.isEmpty()) {
        EmptyChartState(
            message = "No data to display",
            modifier = modifier
        )
        return
    }

    val allPoints = series.flatMap { it.points }
    val rawMinValue = allPoints.minOrNull() ?: 0f
    val rawMaxValue = (allPoints.maxOrNull() ?: 100f) * 1.1f
    // Ensure non-zero range to prevent division by zero when all values are equal
    val range = if (rawMaxValue - rawMinValue < 0.01f) 1f else rawMaxValue - rawMinValue
    val minValue = rawMinValue
    val maxValue = rawMinValue + range
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier) {
        // Legend
        if (showLegend) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally)
            ) {
                series.forEach { s ->
                    ChartLegendItem(
                        label = s.name,
                        color = s.color,
                        value = null
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(Spacing.md)
        ) {
            val chartWidth = size.width
            val chartHeight = size.height * 0.85f
            val pointSpacing = chartWidth / (labels.size - 1).coerceAtLeast(1)

            // Draw grid
            if (showGrid) {
                val gridLines = 5
                for (i in 0..gridLines) {
                    val y = chartHeight * (i.toFloat() / gridLines)
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // Draw each series
            series.forEach { seriesData ->
                if (seriesData.points.isEmpty()) return@forEach

                val path = Path()
                val areaPath = Path()

                // Create line path
                seriesData.points.forEachIndexed { index, value ->
                    val x = index * pointSpacing
                    val y = chartHeight - ((value - minValue) / (maxValue - minValue)) * chartHeight

                    if (index == 0) {
                        path.moveTo(x, y)
                        if (seriesData.fillArea) {
                            areaPath.moveTo(x, chartHeight)
                            areaPath.lineTo(x, y)
                        }
                    } else {
                        path.lineTo(x, y)
                        if (seriesData.fillArea) {
                            areaPath.lineTo(x, y)
                        }
                    }

                    // Draw point
                    drawCircle(
                        color = seriesData.color,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }

                // Complete area path
                if (seriesData.fillArea && seriesData.points.isNotEmpty()) {
                    val lastX = (seriesData.points.size - 1) * pointSpacing
                    areaPath.lineTo(lastX, chartHeight)
                    areaPath.close()

                    // Draw filled area
                    drawPath(
                        path = areaPath,
                        color = seriesData.color.copy(alpha = 0.2f)
                    )
                }

                // Draw line
                drawPath(
                    path = path,
                    color = seriesData.color,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Draw labels
            if (showLabels) {
                labels.forEachIndexed { index, label ->
                    val x = index * pointSpacing
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(
                            x - 15.dp.toPx(),
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
 * Simple line chart with single series
 */
@Composable
fun SimpleLineChart(
    labels: List<String>,
    values: List<Float>,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    fillArea: Boolean = true,
    showGrid: Boolean = true
) {
    LineChart(
        labels = labels,
        series = listOf(
            LineSeriesData(
                name = "",
                points = values,
                color = color,
                fillArea = fillArea
            )
        ),
        modifier = modifier,
        showGrid = showGrid,
        showLegend = false
    )
}
