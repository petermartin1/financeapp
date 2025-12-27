package com.financeapp.ui.accessibility

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*

/**
 * Accessibility utilities and helpers for better screen reader support
 */

/**
 * Add semantic content description to any composable
 */
fun Modifier.contentDescription(description: String): Modifier = this.semantics {
    contentDescription = description
}

/**
 * Mark composable as a heading for navigation
 */
fun Modifier.heading(): Modifier = this.semantics {
    heading()
}

/**
 * Add custom accessibility action
 */
fun Modifier.accessibilityAction(
    label: String,
    action: () -> Boolean
): Modifier = this.semantics {
    customActions = listOf(
        CustomAccessibilityAction(label) { action() }
    )
}

/**
 * Mark composable as clickable with proper semantics
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.accessibleClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier = this.combinedClickable(
    enabled = enabled,
    onClickLabel = onClickLabel,
    onClick = onClick
).semantics {
    if (!enabled) {
        disabled()
    }
}

/**
 * Format currency amount for screen readers
 */
fun formatCurrencyForScreenReader(cents: Long): String {
    val dollars = cents / 100.0
    val absDollars = kotlin.math.abs(dollars)

    return when {
        cents == 0L -> "zero dollars"
        cents > 0 -> "${"%.2f".format(absDollars)} dollars"
        else -> "negative ${"%.2f".format(absDollars)} dollars"
    }
}

/**
 * Format date for screen readers
 */
fun formatDateForScreenReader(dateString: String): String {
    // Convert "Jan 15, 2025" to "January 15th, 2025"
    return dateString // TODO: Improve formatting
}

/**
 * Announce content change to screen readers
 */
fun Modifier.announceForAccessibility(message: String): Modifier = this.semantics {
    liveRegion = LiveRegionMode.Polite
    contentDescription = message
}

/**
 * Mark as disabled with proper semantics
 */
fun Modifier.accessibilityDisabled(): Modifier = this.semantics {
    disabled()
}

/**
 * Set role for semantic understanding
 */
fun Modifier.accessibilityRole(role: Role): Modifier = this.semantics {
    this.role = role
}

/**
 * Group related content together
 */
fun Modifier.accessibilityGroup(): Modifier = this.semantics(mergeDescendants = true) {}

/**
 * Add state description (e.g., "selected", "checked")
 */
fun Modifier.stateDescription(state: String): Modifier = this.semantics {
    stateDescription = state
}

/**
 * Clickable button with proper accessibility
 */
@Composable
fun Modifier.accessibleButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }

    return this
        .semantics {
            role = Role.Button
            contentDescription = label
            if (!enabled) disabled()
        }
        .accessibleClickable(
            enabled = enabled,
            onClickLabel = label,
            onClick = onClick
        )
}

/**
 * Common content descriptions for financial UI
 */
object FinanceAccessibility {
    fun accountBalance(accountName: String, balance: Long): String {
        return "$accountName balance: ${formatCurrencyForScreenReader(balance)}"
    }

    fun transactionAmount(payee: String?, amount: Long, isIncome: Boolean): String {
        val type = if (isIncome) "income" else "expense"
        val payeeText = payee?.let { "from $it" } ?: ""
        return "$type of ${formatCurrencyForScreenReader(amount)} $payeeText"
    }

    fun categorySelection(categoryName: String?): String {
        return if (categoryName != null) {
            "Category: $categoryName"
        } else {
            "No category selected"
        }
    }

    fun dateSelection(date: String): String {
        return "Date: $date"
    }

    fun filterActive(filterCount: Int): String {
        return if (filterCount > 0) {
            "$filterCount filters active"
        } else {
            "No filters active"
        }
    }

    fun searchResults(count: Int, total: Int): String {
        return "Showing $count of $total results"
    }
}

/**
 * Focus management utilities
 */
object FocusManagement {
    /**
     * Announce when focus enters a new section
     */
    fun sectionAnnouncement(sectionName: String): String {
        return "Entered $sectionName section"
    }

    /**
     * Announce list size
     */
    fun listAnnouncement(itemCount: Int, itemType: String = "items"): String {
        return "List with $itemCount $itemType"
    }

    /**
     * Announce current position in list
     */
    fun listPositionAnnouncement(position: Int, total: Int): String {
        return "Item ${position + 1} of $total"
    }
}

/**
 * High contrast mode detection and utilities
 */
object HighContrastSupport {
    /**
     * Check if high contrast mode should be used
     * In a real app, this would check system settings
     */
    fun isHighContrastMode(): Boolean {
        return false // TODO: Implement system check
    }

    /**
     * Adjust alpha for high contrast mode
     */
    fun adjustAlpha(normalAlpha: Float, highContrastAlpha: Float = 1.0f): Float {
        return if (isHighContrastMode()) highContrastAlpha else normalAlpha
    }
}
