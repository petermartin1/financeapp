package com.financeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF001F3F),
    secondary = Color(0xFF4CAF50),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF002204),
    tertiary = Color(0xFFFF9800),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF311B00),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFF410001),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF00390A),
    secondaryContainer = Color(0xFF005313),
    onSecondaryContainer = Color(0xFFC0F0C4),
    tertiary = Color(0xFFFFCC80),
    onTertiary = Color(0xFF4E2600),
    tertiaryContainer = Color(0xFF6F3800),
    onTertiaryContainer = Color(0xFFFFDCC2),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF690003),
    errorContainer = Color(0xFF930006),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Composable
fun FinanceAppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FinanceTypography.default,
        content = content
    )
}

// ========================================
// Finance-Specific Color Extensions
// ========================================

/**
 * Income color (positive transactions, deposits)
 */
val ColorScheme.income: Color
    get() = if (this == LightColorScheme) Color(0xFF4CAF50) else Color(0xFFA5D6A7)

/**
 * Expense color (negative transactions, withdrawals)
 */
val ColorScheme.expense: Color
    get() = error

/**
 * Neutral/Transfer color (transfers between accounts)
 */
val ColorScheme.neutral: Color
    get() = onSurfaceVariant

/**
 * Positive change color (gains, positive percentages)
 */
val ColorScheme.positiveChange: Color
    get() = income

/**
 * Negative change color (losses, negative percentages)
 */
val ColorScheme.negativeChange: Color
    get() = expense

// Account Type Colors

/**
 * Checking account color
 */
val ColorScheme.accountChecking: Color
    get() = if (this == LightColorScheme) Color(0xFF2196F3) else Color(0xFF90CAF9)

/**
 * Savings account color
 */
val ColorScheme.accountSavings: Color
    get() = if (this == LightColorScheme) Color(0xFF4CAF50) else Color(0xFFA5D6A7)

/**
 * Credit card account color
 */
val ColorScheme.accountCredit: Color
    get() = if (this == LightColorScheme) Color(0xFFFF9800) else Color(0xFFFFCC80)

/**
 * Investment account color
 */
val ColorScheme.accountInvestment: Color
    get() = if (this == LightColorScheme) Color(0xFF9C27B0) else Color(0xFFCE93D8)

/**
 * Cash account color
 */
val ColorScheme.accountCash: Color
    get() = if (this == LightColorScheme) Color(0xFF795548) else Color(0xFFBCAAA4)

// Chart Color Palettes

/**
 * Chart colors for categorical data (up to 12 categories)
 */
object ChartColors {
    /**
     * Standard 12-color palette for pie charts and category breakdowns
     */
    val categorical12 = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFFF44336), // Red
        Color(0xFF00BCD4), // Cyan
        Color(0xFFFFEB3B), // Yellow
        Color(0xFF795548), // Brown
        Color(0xFF607D8B), // Blue Grey
        Color(0xFFE91E63), // Pink
        Color(0xFF009688), // Teal
        Color(0xFF673AB7)  // Deep Purple
    )

    /**
     * Sequential color palette for time series and trends
     */
    val sequential = listOf(
        Color(0xFFE3F2FD), // Very light blue
        Color(0xFFBBDEFB), // Light blue
        Color(0xFF90CAF9), // Medium light blue
        Color(0xFF64B5F6), // Medium blue
        Color(0xFF42A5F5), // Medium blue
        Color(0xFF2196F3), // Blue
        Color(0xFF1E88E5), // Dark blue
        Color(0xFF1976D2), // Darker blue
        Color(0xFF1565C0), // Very dark blue
        Color(0xFF0D47A1)  // Darkest blue
    )

    /**
     * Diverging palette for showing positive/negative changes
     */
    val diverging = listOf(
        Color(0xFFD32F2F), // Dark red (very negative)
        Color(0xFFEF5350), // Red
        Color(0xFFE57373), // Light red
        Color(0xFFFFCDD2), // Very light red
        Color(0xFFECEFF1), // Neutral grey
        Color(0xFFC8E6C9), // Very light green
        Color(0xFF81C784), // Light green
        Color(0xFF66BB6A), // Green
        Color(0xFF388E3C)  // Dark green (very positive)
    )

    /**
     * Get color for category index (wraps around if more than 12 categories)
     */
    fun forCategoryIndex(index: Int): Color {
        return categorical12[index % categorical12.size]
    }
}

// Budget Status Colors

/**
 * Budget under 50% used - safe zone
 */
val ColorScheme.budgetSafe: Color
    get() = if (this == LightColorScheme) Color(0xFF4CAF50) else Color(0xFFA5D6A7)

/**
 * Budget 50-80% used - warning zone
 */
val ColorScheme.budgetWarning: Color
    get() = if (this == LightColorScheme) Color(0xFFFF9800) else Color(0xFFFFCC80)

/**
 * Budget 80%+ used - danger zone
 */
val ColorScheme.budgetDanger: Color
    get() = if (this == LightColorScheme) Color(0xFFF44336) else Color(0xFFEF9A9A)

/**
 * Budget exceeded - critical
 */
val ColorScheme.budgetExceeded: Color
    get() = error
