package com.financeapp.ui.components.forms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.datetime.*

/**
 * Date picker field with calendar dialog
 *
 * @param selectedDate Currently selected date
 * @param onDateSelected Callback when date is selected
 * @param label Field label
 * @param modifier Modifier for styling
 * @param enabled Whether field is enabled
 * @param minDate Minimum selectable date (optional)
 * @param maxDate Maximum selectable date (optional)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    val formattedDate = remember(selectedDate) {
        formatDateLong(selectedDate)
    }

    OutlinedTextField(
        value = formattedDate,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = enabled,
        trailingIcon = {
            IconButton(
                onClick = { if (enabled) showDialog = true },
                enabled = enabled
            ) {
                Icon(Icons.Default.DateRange, contentDescription = "Select date")
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true }
    )

    if (showDialog) {
        DatePickerDialog(
            initialDate = selectedDate,
            onDateSelected = { date ->
                onDateSelected(date)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            minDate = minDate,
            maxDate = maxDate
        )
    }
}

/**
 * Date picker dialog with month/year navigation
 */
@Composable
private fun DatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null
) {
    var currentMonth by remember { mutableStateOf(initialDate.month) }
    var currentYear by remember { mutableStateOf(initialDate.year) }
    var selectedDate by remember { mutableStateOf(initialDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous month button
                IconButton(onClick = {
                    if (currentMonth == Month.JANUARY) {
                        currentMonth = Month.DECEMBER
                        currentYear--
                    } else {
                        currentMonth = Month.entries[currentMonth.ordinal - 1]
                    }
                }) {
                    Text("<")
                }

                // Month and year display
                Text(
                    text = "${currentMonth.name.lowercase().replaceFirstChar { it.uppercase() }} $currentYear",
                    style = MaterialTheme.typography.titleLarge
                )

                // Next month button
                IconButton(onClick = {
                    if (currentMonth == Month.DECEMBER) {
                        currentMonth = Month.JANUARY
                        currentYear++
                    } else {
                        currentMonth = Month.entries[currentMonth.ordinal + 1]
                    }
                }) {
                    Text(">")
                }
            }
        },
        text = {
            CalendarGrid(
                month = currentMonth,
                year = currentYear,
                selectedDate = selectedDate,
                onDateClick = { date ->
                    val isInRange = (minDate == null || date >= minDate) &&
                            (maxDate == null || date <= maxDate)
                    if (isInRange) {
                        selectedDate = date
                    }
                },
                minDate = minDate,
                maxDate = maxDate
            )
        },
        confirmButton = {
            TextButton(onClick = { onDateSelected(selectedDate) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Calendar grid showing days of the month
 */
@Composable
private fun CalendarGrid(
    month: Month,
    year: Int,
    selectedDate: LocalDate,
    onDateClick: (LocalDate) -> Unit,
    minDate: LocalDate?,
    maxDate: LocalDate?
) {
    val firstDayOfMonth = LocalDate(year, month, 1)
    val isLeapYear = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    val daysInMonth = when (month) {
        Month.JANUARY, Month.MARCH, Month.MAY, Month.JULY,
        Month.AUGUST, Month.OCTOBER, Month.DECEMBER -> 31
        Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
        Month.FEBRUARY -> if (isLeapYear) 29 else 28
        else -> 30
    }
    val firstDayOfWeek = (firstDayOfMonth.dayOfWeek.ordinal + 1) % 7 // 0 = Sunday

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Day of week headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar days grid
        var dayCounter = 1
        repeat(6) { week ->
            if (dayCounter > daysInMonth) return@repeat

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(7) { dayOfWeek ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (week == 0 && dayOfWeek < firstDayOfWeek || dayCounter > daysInMonth) {
                            // Empty cell
                            Spacer(modifier = Modifier.size(40.dp))
                        } else {
                            val currentDate = LocalDate(year, month, dayCounter)
                            val isSelected = currentDate == selectedDate
                            val isDisabled = (minDate != null && currentDate < minDate) ||
                                    (maxDate != null && currentDate > maxDate)

                            val dayNumber = dayCounter
                            dayCounter++

                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable(enabled = !isDisabled) {
                                        onDateClick(currentDate)
                                    },
                                shape = MaterialTheme.shapes.small,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = dayNumber.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            isDisabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Quick date selection buttons
 */
@Composable
fun QuickDateSelector(
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { onDateSelected(today) },
            modifier = Modifier.weight(1f)
        ) {
            Text("Today")
        }

        OutlinedButton(
            onClick = { onDateSelected(today.minus(1, DateTimeUnit.DAY)) },
            modifier = Modifier.weight(1f)
        ) {
            Text("Yesterday")
        }

        OutlinedButton(
            onClick = { onDateSelected(today.minus(7, DateTimeUnit.DAY)) },
            modifier = Modifier.weight(1f)
        ) {
            Text("Last Week")
        }
    }
}

private fun formatDateLong(date: LocalDate): String {
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    return "${months[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"
}
