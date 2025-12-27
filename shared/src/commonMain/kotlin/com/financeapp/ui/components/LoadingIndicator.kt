package com.financeapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeapp.ui.theme.Spacing

/**
 * Centralized loading indicator components.
 * Provides consistent loading states across the application.
 *
 * Variants:
 * - Full screen loading
 * - Inline loading
 * - Loading with message
 * - Progress bar loading
 */

/**
 * Full screen centered loading indicator
 */
@Composable
fun LoadingScreen(
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            CircularProgressIndicator()

            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Inline loading indicator (for embedding in layouts)
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    size: LoadingSize = LoadingSize.Medium
) {
    CircularProgressIndicator(
        modifier = modifier.size(size.dp.dp),
        strokeWidth = when (size) {
            LoadingSize.Small -> 2.dp
            LoadingSize.Medium -> 3.dp
            LoadingSize.Large -> 4.dp
        }
    )
}

/**
 * Loading indicator with message (for inline use)
 */
@Composable
fun LoadingWithMessage(
    message: String,
    modifier: Modifier = Modifier,
    size: LoadingSize = LoadingSize.Medium
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoadingIndicator(size = size)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Progress bar loading indicator (for determinate progress)
 */
@Composable
fun LoadingProgress(
    progress: Float,
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Indeterminate progress bar (for unknown duration tasks)
 */
@Composable
fun LoadingProgressIndeterminate(
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Loading overlay (for showing loading over content)
 */
@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    message: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(modifier),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    CircularProgressIndicator()

                    if (message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Loading size variants
 */
enum class LoadingSize(val dp: Int) {
    Small(20),
    Medium(40),
    Large(56)
}

/**
 * Loading state composable (handles loading, empty, and content states)
 */
@Composable
fun <T> LoadingState(
    isLoading: Boolean,
    data: T?,
    loadingContent: @Composable () -> Unit = { LoadingScreen() },
    emptyContent: @Composable () -> Unit,
    content: @Composable (T) -> Unit
) {
    when {
        isLoading -> loadingContent()
        data == null -> emptyContent()
        else -> content(data)
    }
}

/**
 * Loading state for lists
 */
@Composable
fun <T> LoadingListState(
    isLoading: Boolean,
    items: List<T>,
    loadingContent: @Composable () -> Unit = { LoadingScreen() },
    emptyContent: @Composable () -> Unit,
    content: @Composable (List<T>) -> Unit
) {
    when {
        isLoading -> loadingContent()
        items.isEmpty() -> emptyContent()
        else -> content(items)
    }
}
