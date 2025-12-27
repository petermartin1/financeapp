package com.financeapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeapp.ui.theme.Spacing

/**
 * Centralized empty state component.
 * Replaces 6+ duplicate empty state implementations across the codebase.
 *
 * Features:
 * - Consistent layout and styling
 * - Optional icon, title, message, and action button
 * - Multiple presets for common scenarios
 *
 * @param icon Icon to display (default: informational icon)
 * @param title Main heading text
 * @param message Optional descriptive message
 * @param actionText Optional button text
 * @param onAction Optional button click handler
 * @param modifier Modifier for the container
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (message != null) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(onClick = onAction) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(actionText)
            }
        }
    }
}

/**
 * Empty state for no transactions
 */
@Composable
fun EmptyTransactionsState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.AutoMirrored.Filled.List,
        title = "No transactions yet",
        message = "Add your first transaction to get started",
        actionText = "Add Transaction",
        onAction = onAddClick,
        modifier = modifier
    )
}

/**
 * Empty state for no accounts
 */
@Composable
fun EmptyAccountsState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.Default.AccountCircle,
        title = "No accounts yet",
        message = "Create your first account to start tracking finances",
        actionText = "Add Account",
        onAction = onAddClick,
        modifier = modifier
    )
}

/**
 * Empty state for no categories
 */
@Composable
fun EmptyCategoriesState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.Default.Menu,
        title = "No categories yet",
        message = "Create categories to organize your transactions",
        actionText = "Add Category",
        onAction = onAddClick,
        modifier = modifier
    )
}

/**
 * Empty state for no payees
 */
@Composable
fun EmptyPayeesState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.Default.Person,
        title = "No payees yet",
        message = "Add payees to track who you pay or receive from",
        actionText = "Add Payee",
        onAction = onAddClick,
        modifier = modifier
    )
}

/**
 * Empty state for no budgets
 */
@Composable
fun EmptyBudgetsState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.Default.Add,
        title = "No budgets set",
        message = "Create budgets to track your spending goals",
        actionText = "Add Budget",
        onAction = onAddClick,
        modifier = modifier
    )
}

/**
 * Empty state for no tags
 */
@Composable
fun EmptyTagsState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.Default.Star,
        title = "No tags yet",
        message = "Create tags to add custom labels to transactions",
        actionText = "Add Tag",
        onAction = onAddClick,
        modifier = modifier
    )
}

/**
 * Empty state for no scheduled transactions
 */
@Composable
fun EmptyScheduledTransactionsState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.Default.DateRange,
        title = "No scheduled transactions",
        message = "Set up recurring transactions to automate data entry",
        actionText = "Add Scheduled Transaction",
        onAction = onAddClick,
        modifier = modifier
    )
}

/**
 * Empty state for no bank connections
 */
@Composable
fun EmptyConnectionsState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.Default.Share,
        title = "No bank connections",
        message = "Connect to your bank to automatically import transactions",
        actionText = "Add Connection",
        onAction = onAddClick,
        modifier = modifier
    )
}

/**
 * Empty state for no search results
 */
@Composable
fun EmptySearchResultsState(
    searchQuery: String? = null,
    onClearFilter: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val message = if (searchQuery != null) {
        "No results found for \"$searchQuery\""
    } else {
        "No matching items found"
    }

    EmptyState(
        icon = Icons.Default.Search,
        title = "No results",
        message = message,
        actionText = if (onClearFilter != null) "Clear filters" else null,
        onAction = onClearFilter,
        modifier = modifier
    )
}

/**
 * Empty state for filtered results (with clear filter action)
 */
@Composable
fun EmptyFilteredResultsState(
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.Default.Clear,
        title = "No matching items",
        message = "Try adjusting your filters to see more results",
        actionText = "Clear filters",
        onAction = onClearFilter,
        modifier = modifier
    )
}

/**
 * Generic empty state for data that hasn't loaded yet vs truly empty
 */
@Composable
fun EmptyDataState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier
) {
    EmptyState(
        icon = Icons.Default.Info,
        title = title,
        message = message,
        modifier = modifier
    )
}
