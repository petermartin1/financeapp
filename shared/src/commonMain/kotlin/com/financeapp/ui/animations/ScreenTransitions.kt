package com.financeapp.ui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Screen transition types
 */
enum class ScreenTransition {
    Slide,      // Slide horizontally (like navigation)
    Fade,       // Simple crossfade
    SlideUp,    // Slide from bottom (for modals)
    Scale,      // Scale + fade (for pop-ups)
    None        // No animation
}

/**
 * Creates appropriate transition spec for screen changes
 */
fun screenTransitionSpec(
    transitionType: ScreenTransition = ScreenTransition.Slide
): ContentTransform {
    return when (transitionType) {
        ScreenTransition.Slide -> {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300, easing = EaseOutCubic)
            ) + fadeIn(tween(300)) togetherWith
                    slideOutHorizontally(
                        targetOffsetX = { -it / 3 },
                        animationSpec = tween(300, easing = EaseInCubic)
                    ) + fadeOut(tween(300))
        }
        ScreenTransition.Fade -> {
            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        }
        ScreenTransition.SlideUp -> {
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300, easing = EaseOutCubic)
            ) + fadeIn(tween(200)) togetherWith
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(300, easing = EaseInCubic)
                    ) + fadeOut(tween(200))
        }
        ScreenTransition.Scale -> {
            scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(250, easing = EaseOutCubic)
            ) + fadeIn(tween(250)) togetherWith
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(250, easing = EaseInCubic)
                    ) + fadeOut(tween(250))
        }
        ScreenTransition.None -> {
            EnterTransition.None togetherWith ExitTransition.None
        }
    }
}

/**
 * Animated content wrapper for screens with automatic transitions
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <T> AnimatedScreen(
    targetState: T,
    modifier: Modifier = Modifier,
    transitionType: ScreenTransition = ScreenTransition.Slide,
    content: @Composable AnimatedVisibilityScope.(T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = { screenTransitionSpec(transitionType) },
        label = "screen_transition",
        content = content
    )
}

/**
 * Dialog/Modal transition animations
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <T> AnimatedDialog(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.(T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(tween(200)) togetherWith
                    scaleOut(
                        targetScale = 0.8f,
                        animationSpec = tween(150)
                    ) + fadeOut(tween(150))
        },
        label = "dialog_transition",
        content = content
    )
}

/**
 * Bottom sheet transition animations
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <T> AnimatedBottomSheet(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.(T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(tween(150)) togetherWith
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(250)
                    ) + fadeOut(tween(150))
        },
        label = "bottom_sheet_transition",
        content = content
    )
}

/**
 * Tab navigation transition with custom direction
 */
fun tabTransitionSpec(forward: Boolean): ContentTransform {
    val slideDirection = if (forward) 1 else -1
    return slideInHorizontally(
        initialOffsetX = { it * slideDirection },
        animationSpec = tween(250, easing = EaseOutCubic)
    ) + fadeIn(tween(250)) togetherWith
            slideOutHorizontally(
                targetOffsetX = { -it * slideDirection / 3 },
                animationSpec = tween(250, easing = EaseInCubic)
            ) + fadeOut(tween(250))
}

/**
 * Expandable content transition
 */
fun expandableContentTransition(): ContentTransform {
    return expandVertically(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    ) + fadeIn(tween(200)) togetherWith
            shrinkVertically(
                animationSpec = tween(200)
            ) + fadeOut(tween(150))
}

// Custom easing curves
private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
