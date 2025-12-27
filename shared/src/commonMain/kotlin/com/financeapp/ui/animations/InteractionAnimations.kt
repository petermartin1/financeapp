package com.financeapp.ui.animations

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Adds a press animation to any composable (scales down when pressed).
 *
 * @param pressedScale The scale factor when pressed (e.g., 0.95f for 5% smaller)
 * @param animationDuration Duration of the animation in milliseconds
 */
fun Modifier.pressAnimation(
    pressedScale: Float = 0.95f,
    animationDuration: Int = 100
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                }
            )
        }
}

/**
 * Adds a bounce animation to buttons using InteractionSource
 */
@Composable
fun Modifier.bounceClick(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce_scale"
    )

    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Adds a hover elevation effect for desktop
 */
fun Modifier.hoverElevation(
    hoveredElevation: Float = 8f,
    animationDuration: Int = 150
): Modifier = composed {
    var isHovered by remember { mutableStateOf(false) }

    val elevation by animateFloatAsState(
        targetValue = if (isHovered) hoveredElevation else 0f,
        animationSpec = tween(animationDuration),
        label = "hover_elevation"
    )

    this.graphicsLayer {
        shadowElevation = elevation
    }
}

/**
 * Pulsing animation for drawing attention to elements
 */
@Composable
fun Modifier.pulsing(
    minScale: Float = 0.95f,
    maxScale: Float = 1.05f,
    durationMillis: Int = 1000
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Shimmer loading animation
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    return this.graphicsLayer {
        this.alpha = alpha
    }
}

/**
 * Shake animation for errors or invalid inputs
 */
@Composable
fun Modifier.shake(trigger: Boolean): Modifier {
    var shakeOffset by remember { mutableStateOf(0f) }

    val offset by animateFloatAsState(
        targetValue = if (trigger) shakeOffset else 0f,
        animationSpec = repeatable(
            iterations = 3,
            animation = tween(50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake_offset",
        finishedListener = {
            shakeOffset = if (shakeOffset == 10f) -10f else 10f
        }
    )

    return this.graphicsLayer {
        translationX = if (trigger) offset else 0f
    }
}

/**
 * Rotation animation for loading indicators or icons
 */
@Composable
fun Modifier.rotating(
    durationMillis: Int = 1000
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_degrees"
    )

    return this.graphicsLayer {
        rotationZ = rotation
    }
}
