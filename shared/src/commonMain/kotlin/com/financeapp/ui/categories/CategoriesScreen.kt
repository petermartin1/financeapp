package com.financeapp.ui.categories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: CategoriesViewModel = koinInject()
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Category")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.categories.isEmpty()) {
            EmptyCategoriesContent(
                onAddClick = { showAddDialog = true },
                modifier = Modifier.padding(padding)
            )
        } else {
            CategoriesList(
                categories = uiState.categories,
                onDeleteCategory = { viewModel.deleteCategory(it) },
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, color ->
                viewModel.addCategory(name, type, null, null, color)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun EmptyCategoriesContent(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No categories yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add categories to organize your transactions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Category")
        }
    }
}

@Composable
private fun CategoriesList(
    categories: List<Category>,
    onDeleteCategory: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedCategories = categories.groupBy { it.type }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryType.entries.forEach { type ->
            val typeCategories = groupedCategories[type] ?: emptyList()
            if (typeCategories.isNotEmpty()) {
                item {
                    Text(
                        text = type.displayName(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(typeCategories) { category ->
                    CategoryCard(
                        category = category,
                        onDelete = { onDeleteCategory(category.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Color indicator
                category.color?.let { color ->
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = MaterialTheme.shapes.small,
                        color = parseColor(color)
                    ) {}
                }
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun CategoryType.displayName(): String = when (this) {
    CategoryType.INCOME -> "Income"
    CategoryType.EXPENSE -> "Expense"
    CategoryType.TRANSFER -> "Transfer"
}

@Composable
private fun parseColor(hex: String): androidx.compose.ui.graphics.Color {
    return try {
        val colorInt = hex.removePrefix("#").toLong(16)
        androidx.compose.ui.graphics.Color(
            red = ((colorInt shr 16) and 0xFF) / 255f,
            green = ((colorInt shr 8) and 0xFF) / 255f,
            blue = (colorInt and 0xFF) / 255f
        )
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }
}
