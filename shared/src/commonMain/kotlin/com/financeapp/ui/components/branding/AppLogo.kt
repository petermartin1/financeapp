package com.financeapp.ui.components.branding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * App logo - circular icon with currency symbol
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = modifier.size(size)
    ) {
        val canvasSize = this.size.minDimension
        val center = Offset(canvasSize / 2, canvasSize / 2)
        val radius = canvasSize / 2 * 0.9f

        // Outer circle
        drawCircle(
            color = primaryColor,
            radius = radius,
            center = center,
            style = Stroke(width = canvasSize * 0.08f)
        )

        // Inner gradient circle
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.3f),
                    primaryColor.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * 0.8f
            ),
            radius = radius * 0.8f,
            center = center
        )

        // Draw dollar sign
        val dollarSignPath = Path().apply {
            val symbolRadius = radius * 0.5f
            val strokeWidth = canvasSize * 0.06f

            // Vertical line of $
            moveTo(center.x, center.y - symbolRadius)
            lineTo(center.x, center.y + symbolRadius)

            // S curve
            val topStart = Offset(center.x + symbolRadius * 0.4f, center.y - symbolRadius * 0.5f)
            val topEnd = Offset(center.x - symbolRadius * 0.4f, center.y - symbolRadius * 0.5f)
            val midStart = Offset(center.x - symbolRadius * 0.4f, center.y)
            val midEnd = Offset(center.x + symbolRadius * 0.4f, center.y)
            val bottomStart = Offset(center.x + symbolRadius * 0.4f, center.y + symbolRadius * 0.5f)
            val bottomEnd = Offset(center.x - symbolRadius * 0.4f, center.y + symbolRadius * 0.5f)

            // Top arc
            moveTo(topEnd.x, topEnd.y)
            cubicTo(
                topEnd.x, topEnd.y - symbolRadius * 0.3f,
                topStart.x, topStart.y - symbolRadius * 0.3f,
                topStart.x, topStart.y
            )

            // Middle transition
            cubicTo(
                topStart.x, topStart.y + symbolRadius * 0.15f,
                midStart.x, midStart.y - symbolRadius * 0.15f,
                midStart.x, midStart.y
            )

            // Bottom arc
            cubicTo(
                midEnd.x, midEnd.y + symbolRadius * 0.15f,
                bottomEnd.x, bottomEnd.y - symbolRadius * 0.15f,
                bottomEnd.x, bottomEnd.y
            )

            cubicTo(
                bottomEnd.x, bottomEnd.y + symbolRadius * 0.3f,
                bottomStart.x, bottomStart.y + symbolRadius * 0.3f,
                bottomStart.x, bottomStart.y
            )
        }

        drawPath(
            path = dollarSignPath,
            color = primaryColor,
            style = Stroke(
                width = canvasSize * 0.06f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * App logo with name
 */
@Composable
fun AppLogoWithName(
    modifier: Modifier = Modifier,
    logoSize: Dp = 48.dp,
    showTagline: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppLogo(size = logoSize)

        Text(
            text = "FinanceApp",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (showTagline) {
            Text(
                text = "Personal Finance Management",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Small branded icon for navigation or toolbar
 */
@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(
        modifier = modifier.size(size)
    ) {
        val canvasSize = this.size.minDimension
        val center = Offset(canvasSize / 2, canvasSize / 2)
        val radius = canvasSize / 2 * 0.85f

        // Simple circle with $
        drawCircle(
            color = tint,
            radius = radius,
            center = center
        )

        // Draw simplified $ symbol
        val symbolRadius = radius * 0.5f
        val strokeWidth = canvasSize * 0.12f

        // Vertical line
        drawLine(
            color = Color.White,
            start = Offset(center.x, center.y - symbolRadius),
            end = Offset(center.x, center.y + symbolRadius),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // S shape (simplified)
        drawLine(
            color = Color.White,
            start = Offset(center.x + symbolRadius * 0.3f, center.y - symbolRadius * 0.4f),
            end = Offset(center.x - symbolRadius * 0.3f, center.y - symbolRadius * 0.2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = Color.White,
            start = Offset(center.x - symbolRadius * 0.3f, center.y + symbolRadius * 0.4f),
            end = Offset(center.x + symbolRadius * 0.3f, center.y + symbolRadius * 0.2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
