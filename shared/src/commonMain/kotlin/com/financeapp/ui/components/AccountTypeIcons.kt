package com.financeapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.financeapp.domain.model.AccountType

/**
 * Icon and color mappings for account types.
 * Provides visual differentiation between different account types.
 *
 * Usage:
 * ```
 * val icon = AccountTypeIcons.getIcon(AccountType.CHECKING)
 * val color = AccountTypeIcons.getColor(AccountType.CHECKING)
 * ```
 */
object AccountTypeIcons {

    /**
     * Get icon for account type (using only available Material icons)
     */
    fun getIcon(accountType: AccountType): ImageVector = when (accountType) {
        AccountType.CHECKING -> Icons.Default.AccountCircle
        AccountType.SAVINGS -> Icons.Default.Home
        AccountType.CREDIT_CARD -> Icons.Default.Warning
        AccountType.CASH -> Icons.Default.Star
        AccountType.INVESTMENT -> Icons.Default.Add
    }

    /**
     * Get color for account type (Material 3 semantic colors)
     */
    fun getColor(accountType: AccountType): Color = when (accountType) {
        AccountType.CHECKING -> Color(0xFF1976D2)      // Blue
        AccountType.SAVINGS -> Color(0xFF388E3C)       // Green
        AccountType.CREDIT_CARD -> Color(0xFFD32F2F)   // Red
        AccountType.CASH -> Color(0xFF689F38)          // Light Green
        AccountType.INVESTMENT -> Color(0xFF7B1FA2)    // Purple
    }

    /**
     * Get background color for account type (lighter tint for badges)
     */
    fun getBackgroundColor(accountType: AccountType): Color = when (accountType) {
        AccountType.CHECKING -> Color(0xFFBBDEFB)      // Light Blue
        AccountType.SAVINGS -> Color(0xFFC8E6C9)       // Light Green
        AccountType.CREDIT_CARD -> Color(0xFFFFCDD2)   // Light Red
        AccountType.CASH -> Color(0xFFDCEDC8)          // Very Light Green
        AccountType.INVESTMENT -> Color(0xFFE1BEE7)    // Light Purple
    }

    /**
     * Get display label for account type
     */
    fun getLabel(accountType: AccountType): String = when (accountType) {
        AccountType.CHECKING -> "Checking"
        AccountType.SAVINGS -> "Savings"
        AccountType.CREDIT_CARD -> "Credit Card"
        AccountType.CASH -> "Cash"
        AccountType.INVESTMENT -> "Investment"
    }

    /**
     * Get short label for account type (for badges)
     */
    fun getShortLabel(accountType: AccountType): String = when (accountType) {
        AccountType.CHECKING -> "CHK"
        AccountType.SAVINGS -> "SAV"
        AccountType.CREDIT_CARD -> "CC"
        AccountType.CASH -> "CASH"
        AccountType.INVESTMENT -> "INV"
    }
}
