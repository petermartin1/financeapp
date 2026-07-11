package com.financeapp.ui.components.charts

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Maps a tap position on a pie-chart canvas to the index of the slice under it, or null when the
 * tap misses (outside the pie radius, inside the donut hole, or nothing drawable).
 *
 * Mirrors the drawing geometry in PieChart/AnimatedPieChart exactly: the pie is a circle of
 * diameter `min(width, height)` centered in the canvas; slices start at -90° (12 o'clock) and
 * sweep clockwise in list order, each proportional to its share of the total. A tap exactly on a
 * boundary angle belongs to the slice that starts there (pinned by PieGeometryTest).
 */
fun pieSliceAt(
    tapX: Float,
    tapY: Float,
    width: Float,
    height: Float,
    values: List<Float>,
    centerHoleRatio: Float = 0f
): Int? {
    val total = values.sum()
    if (values.isEmpty() || total <= 0f) return null

    val dx = tapX - width / 2f
    val dy = tapY - height / 2f
    val radius = min(width, height) / 2f
    val distance = sqrt(dx * dx + dy * dy)
    if (distance > radius) return null
    // The drawn hole radius is (minDimension * centerHoleRatio) / 2 == radius * centerHoleRatio.
    if (centerHoleRatio > 0f && distance < radius * centerHoleRatio) return null

    // atan2 in screen coordinates (y down) increases clockwise with 0° at 3 o'clock; shift by
    // +90° so 0° is the 12 o'clock drawing origin.
    val degrees = atan2(dy, dx) * (180f / PI.toFloat())
    val fromTop = (degrees + 90f + 360f) % 360f

    var cumulative = 0f
    values.forEachIndexed { index, value ->
        cumulative += (value / total) * 360f
        if (fromTop < cumulative) return index
    }
    return values.lastIndex // float rounding exactly at the 360° wrap
}
