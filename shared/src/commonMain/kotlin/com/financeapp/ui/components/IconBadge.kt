package com.financeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.AccountType
import com.financeapp.ui.theme.Spacing

/**
 * Icon badge component with colored background.
 * Used for category icons, account type indicators, etc.
 *
 * Variants:
 * - Circular badge
 * - Rounded square badge
 * - With label
 * - Account type badge
 * - Category badge
 */

/**
 * Circular icon badge with colored background
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = backgroundColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = iconTint
        )
    }
}

/**
 * Rounded square icon badge
 */
@Composable
fun IconBadgeSquare(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    cornerRadius: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(cornerRadius)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = iconTint
        )
    }
}

/**
 * Icon badge with text label below
 */
@Composable
fun IconBadgeWithLabel(
    icon: ImageVector,
    label: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 48.dp,
    iconSize: Dp = 28.dp
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        IconBadge(
            icon = icon,
            contentDescription = contentDescription,
            backgroundColor = backgroundColor,
            iconTint = iconTint,
            size = size,
            iconSize = iconSize
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Account type badge with icon and label
 */
@Composable
fun AccountTypeBadge(
    accountType: AccountType,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    size: Dp = 40.dp
) {
    val icon = AccountTypeIcons.getIcon(accountType)
    val backgroundColor = AccountTypeIcons.getBackgroundColor(accountType)
    val iconColor = AccountTypeIcons.getColor(accountType)
    val label = AccountTypeIcons.getShortLabel(accountType)

    if (showLabel) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = icon,
                contentDescription = AccountTypeIcons.getLabel(accountType),
                backgroundColor = backgroundColor,
                iconTint = iconColor,
                size = size,
                iconSize = size * 0.6f
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        IconBadge(
            icon = icon,
            contentDescription = AccountTypeIcons.getLabel(accountType),
            backgroundColor = backgroundColor,
            iconTint = iconColor,
            size = size,
            iconSize = size * 0.6f,
            modifier = modifier
        )
    }
}

/**
 * Category icon badge
 */
@Composable
fun CategoryBadge(
    categoryName: String?,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    size: Dp = 40.dp,
    backgroundColor: Color? = null,
    iconTint: Color? = null
) {
    val icon = CategoryIcons.getIcon(categoryName)
    val bgColor = backgroundColor ?: MaterialTheme.colorScheme.secondaryContainer
    val iconColor = iconTint ?: MaterialTheme.colorScheme.onSecondaryContainer

    if (showLabel && categoryName != null) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = icon,
                contentDescription = categoryName,
                backgroundColor = bgColor,
                iconTint = iconColor,
                size = size,
                iconSize = size * 0.6f
            )
            Text(
                text = categoryName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    } else {
        IconBadge(
            icon = icon,
            contentDescription = categoryName,
            backgroundColor = bgColor,
            iconTint = iconColor,
            size = size,
            iconSize = size * 0.6f,
            modifier = modifier
        )
    }
}

/**
 * Small badge size variant
 */
object IconBadgeSize {
    val Small = 32.dp
    val Medium = 40.dp
    val Large = 56.dp
    val ExtraLarge = 72.dp
}

/**
 * Compact badge for lists (smaller, inline)
 */
@Composable
fun CompactIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconBadge(
        icon = icon,
        contentDescription = contentDescription,
        backgroundColor = backgroundColor,
        iconTint = iconTint,
        size = IconBadgeSize.Small,
        iconSize = 18.dp,
        modifier = modifier
    )
}
