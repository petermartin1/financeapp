package com.financeapp.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.Category
import com.financeapp.ui.categories.CategoriesViewModel
import org.koin.compose.koinInject
import com.financeapp.ui.components.parseDecimalToCents
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFilterSheet(
    currentFilter: TransactionFilter,
    onApply: (TransactionFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val categoriesViewModel: CategoriesViewModel = koinInject()
    val categoriesState by categoriesViewModel.uiState.collectAsState()

    var showCleared by remember { mutableStateOf(currentFilter.showCleared) }
    var showUncleared by remember { mutableStateOf(currentFilter.showUncleared) }
    var selectedCategoryId by remember { mutableStateOf(currentFilter.categoryId) }
    var minAmountText by remember {
        mutableStateOf(currentFilter.minAmount?.let { (it / 100.0).toString() } ?: "")
    }
    var maxAmountText by remember {
        mutableStateOf(currentFilter.maxAmount?.let { (it / 100.0).toString() } ?: "")
    }
    var categoryExpanded by remember { mutableStateOf(false) }

    val selectedCategory = categoriesState.categories.find { it.id == selectedCategoryId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Filter Transactions",
            style = MaterialTheme.typography.titleLarge
        )

        // Cleared status
        Text(
            text = "Status",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = showCleared,
                onClick = { showCleared = !showCleared },
                label = { Text("Cleared") }
            )
            FilterChip(
                selected = showUncleared,
                onClick = { showUncleared = !showUncleared },
                label = { Text("Uncleared") }
            )
        }

        // Category filter
        if (categoriesState.categories.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "All categories",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All categories") },
                        onClick = {
                            selectedCategoryId = null
                            categoryExpanded = false
                        }
                    )
                    categoriesState.categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                selectedCategoryId = category.id
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Amount range
        Text(
            text = "Amount Range",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = minAmountText,
                onValueChange = { value ->
                    if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        minAmountText = value
                    }
                },
                label = { Text("Min") },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = maxAmountText,
                onValueChange = { value ->
                    if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        maxAmountText = value
                    }
                },
                label = { Text("Max") },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear")
            }
            Button(
                onClick = {
                    val minAmount = if (minAmountText.isNotBlank()) parseDecimalToCents(minAmountText) else null
                    val maxAmount = if (maxAmountText.isNotBlank()) parseDecimalToCents(maxAmountText) else null

                    onApply(
                        currentFilter.copy(
                            showCleared = showCleared,
                            showUncleared = showUncleared,
                            categoryId = selectedCategoryId,
                            minAmount = minAmount,
                            maxAmount = maxAmount
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Apply")
            }
        }
    }
}
