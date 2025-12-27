package com.financeapp.ui.keyboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Dialog showing all available keyboard shortcuts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsHelpDialog(
    shortcuts: List<KeyboardShortcut>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    useMacStyle: Boolean = true
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
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
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Keyboard Shortcuts",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Shortcuts list grouped by category
                val groupedShortcuts = shortcuts.groupBy { it.category }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    groupedShortcuts.forEach { (category, categoryShortcuts) ->
                        item {
                            ShortcutCategorySection(
                                category = category,
                                shortcuts = categoryShortcuts,
                                useMacStyle = useMacStyle
                            )
                        }
                    }

                    // Footer tip
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Press ${if (useMacStyle) "⌘⇧/" else "Ctrl+Shift+/"} anytime to show this help",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
 * Section for a category of shortcuts
 */
@Composable
private fun ShortcutCategorySection(
    category: ShortcutCategory,
    shortcuts: List<KeyboardShortcut>,
    useMacStyle: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            shortcuts.forEach { shortcut ->
                ShortcutRow(
                    shortcut = shortcut,
                    useMacStyle = useMacStyle
                )
                if (shortcut != shortcuts.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * Single shortcut row showing key combination and description
 */
@Composable
private fun ShortcutRow(
    shortcut: KeyboardShortcut,
    useMacStyle: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = shortcut.description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Text(
                text = shortcut.formatForDisplay(useMacStyle),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Composable to register and handle the help shortcut
 */
@Composable
fun rememberShortcutsHelp(
    shortcuts: List<KeyboardShortcut>
): Pair<Boolean, (Boolean) -> Unit> {
    var showHelp by remember { mutableStateOf(false) }

    // Register help shortcut
    val helpShortcut = remember {
        CommonShortcuts.help { showHelp = true }
    }

    return Pair(showHelp) { showHelp = it }
}
