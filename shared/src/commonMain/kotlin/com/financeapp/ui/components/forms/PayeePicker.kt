package com.financeapp.ui.components.forms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.Payee

/**
 * Payee picker with autocomplete suggestions
 *
 * @param selectedPayee Currently selected payee (null if none)
 * @param payees Available payees to choose from
 * @param onPayeeSelected Callback when payee is selected
 * @param onNewPayee Callback to create new payee from text
 * @param label Field label
 * @param modifier Modifier for styling
 * @param enabled Whether picker is enabled
 * @param allowNewPayee Whether to allow creating new payees
 */
@Composable
fun PayeePicker(
    selectedPayee: Payee?,
    payees: List<Payee>,
    onPayeeSelected: (Payee?) -> Unit,
    onNewPayee: (String) -> Unit = {},
    label: String = "Payee",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowNewPayee: Boolean = true
) {
    var textValue by remember(selectedPayee) {
        mutableStateOf(selectedPayee?.name ?: "")
    }
    var showSuggestions by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }

    val filteredPayees = remember(payees, textValue) {
        if (textValue.isBlank()) {
            payees.sortedBy { it.name }
        } else {
            payees.filter {
                it.name.contains(textValue, ignoreCase = true)
            }.sortedBy { it.name }
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { newValue ->
                textValue = newValue
                showSuggestions = newValue.isNotEmpty()
                // Clear selected payee if text doesn't match
                if (selectedPayee != null && newValue != selectedPayee.name) {
                    onPayeeSelected(null)
                }
            },
            label = { Text(label) },
            enabled = enabled,
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null)
            },
            trailingIcon = {
                Row {
                    if (textValue.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                textValue = ""
                                onPayeeSelected(null)
                                showSuggestions = false
                            },
                            enabled = enabled
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                    IconButton(
                        onClick = { showSuggestions = !showSuggestions },
                        enabled = enabled
                    ) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Show suggestions")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    hasFocus = focusState.isFocused
                    if (focusState.isFocused && textValue.isNotEmpty()) {
                        showSuggestions = true
                    }
                }
        )

        // Autocomplete suggestions dropdown
        if (showSuggestions && (hasFocus || filteredPayees.isNotEmpty())) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Show filtered payees
                    items(filteredPayees.take(10)) { payee ->
                        PayeeSuggestionItem(
                            payee = payee,
                            query = textValue,
                            onClick = {
                                textValue = payee.name
                                onPayeeSelected(payee)
                                showSuggestions = false
                            }
                        )
                    }

                    // Show "Create new payee" option if enabled
                    if (allowNewPayee && textValue.isNotBlank()) {
                        val exactMatch = filteredPayees.any {
                            it.name.equals(textValue, ignoreCase = true)
                        }
                        if (!exactMatch) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onNewPayee(textValue)
                                            showSuggestions = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = "Create \"$textValue\"",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "New payee",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }

                    // Empty state
                    if (filteredPayees.isEmpty() && !allowNewPayee) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No payees found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual payee suggestion item
 */
@Composable
private fun PayeeSuggestionItem(
    payee: Payee,
    query: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = payee.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (payee.defaultCategoryId != null) {
                            Text(
                                text = "Auto-categorized",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider()
}

/**
 * Quick payee chips for recent/frequent payees
 */
@Composable
fun QuickPayeeChips(
    payees: List<Payee>,
    selectedPayee: Payee?,
    onPayeeSelected: (Payee?) -> Unit,
    modifier: Modifier = Modifier,
    maxChips: Int = 5
) {
    val recentPayees = remember(payees) {
        payees.sortedBy { it.name }.take(maxChips)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        recentPayees.forEach { payee ->
            FilterChip(
                selected = payee.id == selectedPayee?.id,
                onClick = {
                    onPayeeSelected(
                        if (payee.id == selectedPayee?.id) null else payee
                    )
                },
                label = { Text(payee.name) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}
