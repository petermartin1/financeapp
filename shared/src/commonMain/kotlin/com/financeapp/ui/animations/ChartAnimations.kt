package com.financeapp.ui.animations

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlinx.coroutines.delay

/**
 * Animated progress for chart drawing.
 * Returns a value from 0f to 1f that can be used to animate chart elements.
 *
 * @param durationMillis Animation duration in milliseconds
 * @param delayMillis Initial delay before animation starts
 */
@Composable
fun rememberChartAnimationProgress(
    durationMillis: Int = 800,
    delayMillis: Int = 100
): Float {
    var targetValue by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        targetValue = 1f
    }

    val progress by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = EaseOutCubic
        ),
        label = "chart_progress"
    )

    return progress
}

/**
 * Staggered animation for multiple chart elements (e.g., bars in a bar chart).
 * Each element animates with a slight delay.
 *
 * @param itemCount Total number of items to animate
 * @param itemIndex Current item index
 * @param totalDuration Total duration for all items
 * @param staggerPercent Percentage of time to stagger (0.0 to 1.0)
 */
@Composable
fun rememberStaggeredChartProgress(
    itemCount: Int,
    itemIndex: Int,
    totalDuration: Int = 1000,
    staggerPercent: Float = 0.3f
): Float {
    val staggerDuration = (totalDuration * staggerPercent).toInt()
    val itemDuration = totalDuration - staggerDuration
    val itemDelay = if (itemCount > 1) {
        (staggerDuration * itemIndex) / (itemCount - 1)
    } else {
        0
    }

    var targetValue by remember { mutableStateOf(0f) }

    LaunchedEffect(itemIndex) {
        delay(itemDelay.toLong())
        targetValue = 1f
    }

    val progress by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = tween(
            durationMillis = itemDuration,
            easing = EaseOutCubic
        ),
        label = "stagger_progress_$itemIndex"
    )

    return progress
}

/**
 * Pie chart slice animation with rotation and scale
 */
@Composable
fun rememberPieSliceAnimation(
    sliceIndex: Int,
    sliceCount: Int,
    durationMillis: Int = 800,
    staggerMillis: Int = 50
): PieSliceAnimation {
    val delay = sliceIndex * staggerMillis
    var targetProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(sliceIndex) {
        delay(delay.toLong())
        targetProgress = 1f
    }

    val sweepProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = EaseOutCubic
        ),
        label = "pie_sweep_$sliceIndex"
    )

    val scaleProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pie_scale_$sliceIndex"
    )

    return PieSliceAnimation(sweepProgress, scaleProgress)
}

data class PieSliceAnimation(
    val sweepProgress: Float,
    val scaleProgress: Float
)

/**
 * Line chart path animation - draws the line from left to right
 */
@Composable
fun rememberLineChartAnimation(
    durationMillis: Int = 1000,
    delayMillis: Int = 200
): Float {
    var targetValue by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        targetValue = 1f
    }

    val progress by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = tween(
            durationMillis = durationMillis,
            easing = EaseInOutCubic
        ),
        label = "line_chart_progress"
    )

    return progress
}

/**
 * Bar chart column animation - grows from bottom to top
 */
@Composable
fun rememberBarAnimation(
    barIndex: Int,
    totalBars: Int,
    durationMillis: Int = 600,
    staggerMillis: Int = 40
): Float {
    val delay = barIndex * staggerMillis
    var targetValue by remember { mutableStateOf(0f) }

    LaunchedEffect(barIndex) {
        delay(delay.toLong())
        targetValue = 1f
    }

    val progress by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bar_progress_$barIndex"
    )

    return progress
}

/**
 * Donut chart animation with inner and outer radius
 */
@Composable
fun rememberDonutAnimation(
    durationMillis: Int = 800,
    delayMillis: Int = 100
): Float {
    var targetValue by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        targetValue = 1f
    }

    val progress by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "donut_progress"
    )

    return progress
}

/**
 * Helper function to apply scale animation in DrawScope
 */
fun DrawScope.animatedScale(
    scale: Float,
    pivotX: Float = size.width / 2,
    pivotY: Float = size.height / 2,
    block: DrawScope.() -> Unit
) {
    scale(scale, pivot = androidx.compose.ui.geometry.Offset(pivotX, pivotY)) {
        block()
    }
}

// Custom easing curves for charts
private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
