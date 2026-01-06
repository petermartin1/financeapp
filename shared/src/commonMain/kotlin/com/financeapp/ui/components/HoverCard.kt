package com.financeapp.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * HoverCard provides enhanced tooltips with rich content when hovering over UI elements.
 *
 * Usage:
 * ```
 * HoverCard(
 *     hoverContent = { Text("This is a helpful tooltip") }
 * ) {
 *     Icon(Icons.Default.Info, contentDescription = "Info")
 * }
 * ```
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HoverCard(
    hoverContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Long = 500,
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = {
            Surface(
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                tonalElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier.padding(12.dp)
                ) {
                    ProvideTextStyle(
                        MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.inverseSurface
                        )
                    ) {
                        hoverContent()
                    }
                }
            }
        },
        delayMillis = delayMillis.toInt(),
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Simple text-only hover tooltip
 */
@Composable
fun HoverTooltip(
    text: String,
    modifier: Modifier = Modifier,
    delayMillis: Long = 500,
    content: @Composable () -> Unit
) {
    HoverCard(
        hoverContent = { Text(text) },
        modifier = modifier,
        delayMillis = delayMillis,
        content = content
    )
}
