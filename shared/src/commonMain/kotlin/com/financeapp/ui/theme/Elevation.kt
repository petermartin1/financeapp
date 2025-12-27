package com.financeapp.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Finance App Elevation System
 *
 * Consistent elevation/shadow depths following Material 3 specifications.
 * Use these for Cards, Surfaces, and other elevated components.
 *
 * Desktop-specific: Elevation changes on hover provide visual feedback.
 *
 * Usage:
 * ```
 * Card(
 *     elevation = CardDefaults.cardElevation(
 *         defaultElevation = Elevation.level1,
 *         hoveredElevation = Elevation.level2
 *     )
 * ) {
 *     // Content
 * }
 * ```
 */
object Elevation {
    /**
     * No elevation - flat on surface
     * Use for: Inline content, list items without separation
     */
    val level0: Dp = 0.dp

    /**
     * Level 1 - Subtle elevation
     * Use for: Cards at rest, default elevated state
     */
    val level1: Dp = 1.dp

    /**
     * Level 2 - Medium elevation
     * Use for: Cards on hover (desktop), raised cards, filter chips
     */
    val level2: Dp = 3.dp

    /**
     * Level 3 - High elevation
     * Use for: Dialogs, dropdown menus, tooltips
     */
    val level3: Dp = 6.dp

    /**
     * Level 4 - Higher elevation
     * Use for: Navigation drawer, bottom sheets
     */
    val level4: Dp = 8.dp

    /**
     * Level 5 - Highest elevation
     * Use for: Modal sheets, floating action buttons
     */
    val level5: Dp = 12.dp

    // Semantic elevation tokens

    /**
     * Default card elevation at rest
     */
    val cardDefault: Dp = level1

    /**
     * Card elevation on hover (desktop only)
     */
    val cardHovered: Dp = level2

    /**
     * Card elevation when pressed/focused
     */
    val cardPressed: Dp = level0

    /**
     * Dialog elevation
     */
    val dialog: Dp = level3

    /**
     * Navigation drawer elevation
     */
    val navigationDrawer: Dp = level4

    /**
     * Navigation rail elevation
     */
    val navigationRail: Dp = level0

    /**
     * Dropdown menu elevation
     */
    val menu: Dp = level3

    /**
     * Tooltip elevation
     */
    val tooltip: Dp = level3

    /**
     * Bottom sheet elevation
     */
    val bottomSheet: Dp = level4

    /**
     * Floating action button elevation
     */
    val fab: Dp = level3

    /**
     * Modal backdrop elevation (behind dialogs/sheets)
     */
    val scrim: Dp = level5

    /**
     * App bar elevation (top bar)
     */
    val appBar: Dp = level0  // Material 3 uses level0 for top app bar

    /**
     * Search bar elevation
     */
    val searchBar: Dp = level0

    /**
     * Chart card elevation
     */
    val chartCard: Dp = level1

    /**
     * Transaction card elevation
     */
    val transactionCard: Dp = level1

    /**
     * Account card elevation
     */
    val accountCard: Dp = level1

    /**
     * Dashboard widget elevation
     */
    val dashboardWidget: Dp = level1
}
