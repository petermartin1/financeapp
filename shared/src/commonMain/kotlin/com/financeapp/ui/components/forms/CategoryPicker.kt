package com.financeapp.ui.components.forms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import com.financeapp.ui.components.CategoryIcons

/**
 * Category picker with icons and colors
 *
 * @param selectedCategory Currently selected category (null if none)
 * @param categories Available categories to choose from
 * @param onCategorySelected Callback when category is selected
 * @param label Field label
 * @param modifier Modifier for styling
 * @param enabled Whether picker is enabled
 * @param showNone Whether to show "None" option
 * @param filterType Optional filter to show only specific category types
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(
    selectedCategory: Category?,
    categories: List<Category>,
    onCategorySelected: (Category?) -> Unit,
    label: String = "Category",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showNone: Boolean = true,
    filterType: CategoryType? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    val filteredCategories = remember(categories, filterType) {
        if (filterType != null) {
            categories.filter { it.type == filterType }
        } else {
            categories
        }
    }

    OutlinedTextField(
        value = selectedCategory?.name ?: if (showNone) "None" else "",
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        enabled = enabled,
        leadingIcon = selectedCategory?.let {
            {
                Icon(
                    imageVector = CategoryIcons.getIcon(it.name),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        trailingIcon = {
            IconButton(
                onClick = { if (enabled) showDialog = true },
                enabled = enabled
            ) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select category")
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true }
    )

    if (showDialog) {
        CategoryPickerDialog(
            categories = filteredCategories,
            selectedCategory = selectedCategory,
            onCategorySelected = { category ->
                onCategorySelected(category)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            showNone = showNone
        )
    }
}

/**
 * Category picker dialog with search and grouping
 */
@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category?) -> Unit,
    onDismiss: () -> Unit,
    showNone: Boolean
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredCategories = remember(categories, searchQuery) {
        if (searchQuery.isBlank()) {
            categories
        } else {
            categories.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val groupedCategories = remember(filteredCategories) {
        filteredCategories.groupBy { it.type }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Category") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search categories") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Categories list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // None option
                    if (showNone) {
                        item {
                            CategoryPickerItem(
                                category = null,
                                isSelected = selectedCategory == null,
                                onClick = { onCategorySelected(null) }
                            )
                        }
                    }

                    // Grouped by category type
                    groupedCategories.forEach { (type, categoriesOfType) ->
                        item {
                            Text(
                                text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(categoriesOfType) { category ->
                            CategoryPickerItem(
                                category = category,
                                isSelected = category.id == selectedCategory?.id,
                                onClick = { onCategorySelected(category) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Individual category item in the picker
 */
@Composable
private fun CategoryPickerItem(
    category: Category?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (category != null) {
                Icon(
                    imageVector = CategoryIcons.getIcon(category.name),
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )

                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            } else {
                Text(
                    text = "None",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Quick category chips for common selections
 */
@Composable
fun QuickCategoryChips(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category?) -> Unit,
    modifier: Modifier = Modifier,
    maxChips: Int = 5
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.take(maxChips).forEach { category ->
            FilterChip(
                selected = category.id == selectedCategory?.id,
                onClick = {
                    onCategorySelected(
                        if (category.id == selectedCategory?.id) null else category
                    )
                },
                label = { Text(category.name) },
                leadingIcon = {
                    Icon(
                        imageVector = CategoryIcons.getIcon(category.name),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}
