package com.financeapp.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeapp.domain.repository.CategoryRepository
import com.financeapp.domain.repository.TagRepository
import com.financeapp.ui.components.forms.CategoryPicker
import org.koin.compose.koinInject

/**
 * Dialog for bulk categorizing selected transactions
 */
@Composable
fun BulkCategorizeDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    val categoryRepository: CategoryRepository = koinInject()
    val categories by categoryRepository.getAllCategories().collectAsState(emptyList())

    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorize $selectedCount Transaction(s)") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select a category to apply to all selected transactions:",
                    style = MaterialTheme.typography.bodyMedium
                )

                CategoryPicker(
                    selectedCategory = selectedCategory,
                    categories = categories,
                    onCategorySelected = { selectedCategory = it },
                    showNone = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCategory?.id) }
            ) {
                Text("Apply")
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
 * Dialog for bulk tagging selected transactions
 */
@Composable
fun BulkTagDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    val tagRepository: TagRepository = koinInject()
    val allTags by tagRepository.getAllTags().collectAsState(emptyList())

    var selectedTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag $selectedCount Transaction(s)") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select tags to add to all selected transactions:",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Tag selection chips
                if (allTags.isEmpty()) {
                    Text(
                        text = "No tags available. Create tags first in the Tags screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in selectedTagIds,
                                onClick = {
                                    selectedTagIds = if (tag.id in selectedTagIds) {
                                        selectedTagIds - tag.id
                                    } else {
                                        selectedTagIds + tag.id
                                    }
                                },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }

                Text(
                    text = "Note: Tags will be added to existing tags on each transaction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedTagIds.toList()) },
                enabled = selectedTagIds.isNotEmpty()
            ) {
                Text("Add Tags")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
