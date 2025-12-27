package com.financeapp.ui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Creates a staggered enter animation for list items.
 * Each item appears with a slight delay and fade/slide animation.
 *
 * @param index The index of the item in the list
 * @param staggerDelayMillis Delay between each item animation
 * @param itemDurationMillis Duration of each item's animation
 */
@Composable
fun StaggeredListItem(
    index: Int,
    modifier: Modifier = Modifier,
    staggerDelayMillis: Int = 50,
    itemDurationMillis: Int = 300,
    slideDistance: Float = 20f,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(index) {
        delay((index * staggerDelayMillis).toLong())
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = itemDurationMillis, easing = EaseOutCubic),
        label = "stagger_alpha_$index"
    )

    val translateY by animateFloatAsState(
        targetValue = if (visible) 0f else slideDistance,
        animationSpec = tween(durationMillis = itemDurationMillis, easing = EaseOutCubic),
        label = "stagger_translate_$index"
    )

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translateY
        }
    ) {
        content()
    }
}

/**
 * Slide animation variants for different directions
 */
enum class SlideDirection {
    Left, Right, Up, Down
}

/**
 * Creates a slide transition animation for entering/exiting content
 */
fun slideInAnimation(
    direction: SlideDirection = SlideDirection.Right,
    durationMillis: Int = 300
): EnterTransition {
    return when (direction) {
        SlideDirection.Right -> slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis, easing = EaseOutCubic)
        ) + fadeIn(tween(durationMillis))
        SlideDirection.Left -> slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(durationMillis, easing = EaseOutCubic)
        ) + fadeIn(tween(durationMillis))
        SlideDirection.Down -> slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis, easing = EaseOutCubic)
        ) + fadeIn(tween(durationMillis))
        SlideDirection.Up -> slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis, easing = EaseOutCubic)
        ) + fadeIn(tween(durationMillis))
    }
}

fun slideOutAnimation(
    direction: SlideDirection = SlideDirection.Left,
    durationMillis: Int = 300
): ExitTransition {
    return when (direction) {
        SlideDirection.Right -> slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis, easing = EaseInCubic)
        ) + fadeOut(tween(durationMillis))
        SlideDirection.Left -> slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(durationMillis, easing = EaseInCubic)
        ) + fadeOut(tween(durationMillis))
        SlideDirection.Down -> slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis, easing = EaseInCubic)
        ) + fadeOut(tween(durationMillis))
        SlideDirection.Up -> slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis, easing = EaseInCubic)
        ) + fadeOut(tween(durationMillis))
    }
}

/**
 * Standard fade in animation
 */
fun fadeInAnimation(durationMillis: Int = 300): EnterTransition {
    return fadeIn(animationSpec = tween(durationMillis, easing = LinearEasing))
}

/**
 * Standard fade out animation
 */
fun fadeOutAnimation(durationMillis: Int = 300): ExitTransition {
    return fadeOut(animationSpec = tween(durationMillis, easing = LinearEasing))
}

/**
 * Scale + fade animation for dialogs
 */
fun scaleInAnimation(durationMillis: Int = 200): EnterTransition {
    return scaleIn(
        initialScale = 0.8f,
        animationSpec = tween(durationMillis, easing = EaseOutCubic)
    ) + fadeIn(tween(durationMillis))
}

fun scaleOutAnimation(durationMillis: Int = 200): ExitTransition {
    return scaleOut(
        targetScale = 0.8f,
        animationSpec = tween(durationMillis, easing = EaseInCubic)
    ) + fadeOut(tween(durationMillis))
}

/**
 * Expand/collapse animation for expandable content
 */
fun expandAnimation(durationMillis: Int = 300): EnterTransition {
    return expandVertically(
        animationSpec = tween(durationMillis, easing = EaseOutCubic)
    ) + fadeIn(tween(durationMillis))
}

fun collapseAnimation(durationMillis: Int = 300): ExitTransition {
    return shrinkVertically(
        animationSpec = tween(durationMillis, easing = EaseInCubic)
    ) + fadeOut(tween(durationMillis))
}

// Custom easing curves
private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
