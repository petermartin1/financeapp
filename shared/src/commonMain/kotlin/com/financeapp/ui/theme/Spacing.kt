package com.financeapp.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Finance App Spacing System
 *
 * Consistent spacing tokens following Material 3's 4dp grid system.
 * Replace all hardcoded .dp values with these semantic tokens.
 *
 * Usage:
 * ```
 * Column(
 *     modifier = Modifier.padding(Spacing.screenPadding),
 *     verticalArrangement = Arrangement.spacedBy(Spacing.md)
 * ) {
 *     Card(modifier = Modifier.padding(Spacing.cardPadding)) {
 *         // Content
 *     }
 * }
 * ```
 */
object Spacing {
    // Base spacing scale (4dp grid)
    val none: Dp = 0.dp
    val xxs: Dp = 2.dp      // Minimal spacing (rarely used)
    val xs: Dp = 4.dp       // Very tight spacing
    val sm: Dp = 8.dp       // Tight spacing (list item internal padding)
    val md: Dp = 16.dp      // Default spacing (most common)
    val lg: Dp = 24.dp      // Comfortable spacing (section gaps)
    val xl: Dp = 32.dp      // Spacious (major sections)
    val xxl: Dp = 48.dp     // Very spacious (screen sections)
    val xxxl: Dp = 64.dp    // Extra spacious (rare, special emphasis)

    // Semantic spacing tokens

    // Screen-level spacing
    val screenPadding: Dp = md           // Standard screen edge padding
    val screenPaddingLarge: Dp = lg      // Larger screens (desktop)
    val screenPaddingSmall: Dp = sm      // Compact screens

    // Card spacing
    val cardPadding: Dp = md             // Internal card content padding
    val cardPaddingCompact: Dp = sm      // Compact cards
    val cardPaddingLarge: Dp = lg        // Spacious cards
    val cardSpacing: Dp = sm             // Gap between cards in list
    val cardSpacingLarge: Dp = md        // Larger gap between card groups

    // List spacing
    val listItemPadding: Dp = md         // Padding inside list items
    val listItemSpacing: Dp = sm         // Vertical gap between list items
    val listItemInternalSpacing: Dp = sm // Spacing within a list item (e.g., icon to text)
    val listSectionSpacing: Dp = lg      // Gap between list sections

    // Form spacing
    val formFieldSpacing: Dp = md        // Vertical gap between form fields
    val formFieldPadding: Dp = md        // Padding inside form fields
    val formSectionSpacing: Dp = lg      // Gap between form sections
    val formLabelSpacing: Dp = xs        // Gap between label and field

    // Button spacing
    val buttonPadding: Dp = md           // Internal button padding
    val buttonSpacing: Dp = sm           // Gap between buttons
    val buttonIconSpacing: Dp = sm       // Gap between button icon and text

    // Dialog spacing
    val dialogPadding: Dp = lg           // Padding inside dialogs
    val dialogContentSpacing: Dp = md    // Spacing between dialog content sections
    val dialogButtonSpacing: Dp = sm     // Spacing between dialog action buttons

    // Navigation spacing
    val navItemPadding: Dp = md          // Padding for navigation items
    val navItemSpacing: Dp = xs          // Gap between nav items
    val navSectionSpacing: Dp = lg       // Gap between nav sections

    // Icon spacing
    val iconTextSpacing: Dp = sm         // Gap between icon and adjacent text
    val iconPadding: Dp = sm             // Padding around standalone icons
    val iconBadgePadding: Dp = sm        // Padding inside icon badges

    // Chart spacing
    val chartPadding: Dp = md            // Padding around charts
    val chartLegendSpacing: Dp = sm      // Gap between chart and legend
    val chartLegendItemSpacing: Dp = sm  // Gap between legend items

    // Table/Grid spacing
    val tableCellPadding: Dp = md        // Padding inside table cells
    val tableRowSpacing: Dp = none       // Gap between table rows (usually 0 for dividers)
    val tableColumnSpacing: Dp = md      // Gap between table columns

    // Divider spacing
    val dividerSpacing: Dp = md          // Vertical space before/after dividers

    // Badge/Chip spacing
    val chipPadding: Dp = sm             // Internal chip padding
    val chipSpacing: Dp = xs             // Gap between chips

    // Dashboard widget spacing
    val widgetPadding: Dp = md           // Padding inside dashboard widgets
    val widgetSpacing: Dp = md           // Gap between dashboard widgets
    val widgetHeaderSpacing: Dp = sm     // Gap between widget header and content

    // Transaction-specific spacing
    val transactionItemPadding: Dp = md      // Padding inside transaction card
    val transactionItemSpacing: Dp = sm      // Gap between transaction items
    val transactionDetailSpacing: Dp = xs    // Gap between transaction details (date, payee, etc.)
    val transactionGroupHeaderSpacing: Dp = md // Space above date group headers

    // Account-specific spacing
    val accountItemPadding: Dp = md          // Padding inside account card
    val accountItemSpacing: Dp = sm          // Gap between account items
    val accountBalanceSpacing: Dp = xs       // Gap between balance label and amount
    val accountTypeGroupSpacing: Dp = lg     // Gap between account type groups

    // Budget-specific spacing
    val budgetItemPadding: Dp = md           // Padding inside budget item
    val budgetItemSpacing: Dp = sm           // Gap between budget items
    val budgetProgressSpacing: Dp = xs       // Gap between label and progress bar
    val budgetCategorySpacing: Dp = md       // Gap between budget categories
}
