package com.financeapp.ui.components.filters

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*

/**
 * Amount range slider for filtering transactions
 */
@Composable
fun AmountRangeFilter(
    minAmount: Long?,
    maxAmount: Long?,
    absoluteMin: Long = 0L,
    absoluteMax: Long = 1000000L, // $10,000
    onRangeChange: (Long?, Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderRange by remember(minAmount, maxAmount) {
        val min = minAmount?.toFloat() ?: absoluteMin.toFloat()
        val max = maxAmount?.toFloat() ?: absoluteMax.toFloat()
        mutableStateOf(min..max)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Amount Range",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        RangeSlider(
            value = sliderRange,
            onValueChange = { sliderRange = it },
            onValueChangeFinished = {
                onRangeChange(
                    sliderRange.start.toLong(),
                    sliderRange.endInclusive.toLong()
                )
            },
            valueRange = absoluteMin.toFloat()..absoluteMax.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatCurrency(sliderRange.start.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatCurrency(sliderRange.endInclusive.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Date range picker for filtering
 */
@Composable
fun DateRangeFilter(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onDateRangeChange: (LocalDate?, LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Date Range",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Start date
            OutlinedCard(
                modifier = Modifier.weight(1f),
                onClick = { /* Show date picker */ }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = startDate?.toString() ?: "Start",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // End date
            OutlinedCard(
                modifier = Modifier.weight(1f),
                onClick = { /* Show date picker */ }
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = endDate?.toString() ?: "End",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Quick date range presets
        Spacer(modifier = Modifier.height(12.dp))
        QuickDateRangePresets(onDateRangeChange)
    }
}

/**
 * Quick date range preset buttons
 */
@Composable
private fun QuickDateRangePresets(
    onDateRangeChange: (LocalDate?, LocalDate?) -> Unit
) {
    val now = Clock.System.now()
    val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = false,
            onClick = {
                onDateRangeChange(today, today)
            },
            label = { Text("Today", style = MaterialTheme.typography.labelSmall) }
        )

        FilterChip(
            selected = false,
            onClick = {
                val weekAgo = today.minus(7, DateTimeUnit.DAY)
                onDateRangeChange(weekAgo, today)
            },
            label = { Text("Last 7 days", style = MaterialTheme.typography.labelSmall) }
        )

        FilterChip(
            selected = false,
            onClick = {
                val monthAgo = today.minus(30, DateTimeUnit.DAY)
                onDateRangeChange(monthAgo, today)
            },
            label = { Text("Last 30 days", style = MaterialTheme.typography.labelSmall) }
        )
    }
}

/**
 * Filter chips for quick selections
 */
@Composable
fun QuickFilterChips(
    filters: List<FilterChipData>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter.selected,
                onClick = filter.onClick,
                label = { Text(filter.label) },
                leadingIcon = filter.icon?.let {
                    {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }
    }
}

/**
 * Data class for filter chips
 */
data class FilterChipData(
    val label: String,
    val selected: Boolean,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val onClick: () -> Unit
)

/**
 * Saved filter presets
 */
@Composable
fun SavedFilterPresets(
    presets: List<FilterPreset>,
    onPresetSelect: (FilterPreset) -> Unit,
    onPresetDelete: (FilterPreset) -> Unit,
    onPresetSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Filters",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = onPresetSave) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Current")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (presets.isEmpty()) {
            Text(
                text = "No saved filters",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            presets.forEach { preset ->
                FilterPresetItem(
                    preset = preset,
                    onSelect = { onPresetSelect(preset) },
                    onDelete = { onPresetDelete(preset) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Individual filter preset item
 */
@Composable
private fun FilterPresetItem(
    preset: FilterPreset,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete preset",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Filter preset data class
 */
data class FilterPreset(
    val id: Long,
    val name: String,
    val description: String,
    val filterData: Map<String, Any>
)

private fun formatCurrency(cents: Long): String {
    val dollars = cents / 100.0
    return "${'$'}${"%.2f".format(kotlin.math.abs(dollars))}"
}
