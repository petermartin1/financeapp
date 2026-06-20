package com.financeapp.ui.fileimport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.PayeeMatch
import com.financeapp.domain.model.Tag
import com.financeapp.domain.model.UnresolvedPayee
import com.financeapp.ui.components.forms.CategoryPicker
import kotlin.math.roundToInt

@Composable
fun PayeeMappingDialog(
    unresolvedPayees: List<UnresolvedPayee>,
    currentIndex: Int,
    allPayees: List<Payee>,
    allCategories: List<Category>,
    allTags: List<Tag>,
    similarRecentlyCreated: List<Payee>,
    onMapToExisting: (Long, Long?, List<Long>, Boolean) -> Unit,
    onCreateNew: (String, Long?, List<Long>, Boolean) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onSkipAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPayee = unresolvedPayees.getOrNull(currentIndex)

    // Combined suggested matches from database + recently created
    // similarRecentlyCreated is pre-computed in ViewModel using proper PayeeMatcher
    val hasAnySuggestions = (currentPayee?.suggestedMatches?.isNotEmpty() == true) || similarRecentlyCreated.isNotEmpty()

    // State for user selections
    // Default to MAP_TO_EXISTING if we found suggestions, otherwise CREATE_NEW
    var mappingMode by remember(currentIndex) {
        mutableStateOf(
            if (hasAnySuggestions) MappingMode.MAP_TO_EXISTING else MappingMode.CREATE_NEW
        )
    }
    var selectedPayeeId by remember(currentIndex) {
        mutableLongStateOf(
            currentPayee?.suggestedMatches?.firstOrNull()?.payee?.id
                ?: similarRecentlyCreated.firstOrNull()?.id
                ?: 0L
        )
    }
    var newPayeeName by remember(currentIndex) { mutableStateOf(currentPayee?.importedName ?: "") }
    var selectedCategory by remember(currentIndex) { mutableStateOf<Category?>(null) }
    var selectedTagIds by remember(currentIndex) { mutableStateOf(emptyList<Long>()) }
    var rememberMapping by remember(currentIndex) { mutableStateOf(true) }

    if (currentPayee == null) {
        // No more payees to resolve - shouldn't happen
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with progress
                PayeeMappingHeader(
                    currentIndex = currentIndex,
                    totalCount = unresolvedPayees.size,
                    onDismiss = onDismiss,
                    onSkipAll = onSkipAll
                )

                HorizontalDivider()

                // Payee info
                PayeeInfoSection(
                    importedName = currentPayee.importedName,
                    transactionCount = currentPayee.transactionCount,
                    variantNames = currentPayee.variantNames
                )

                HorizontalDivider()

                // Main content area
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Surface names found earlier in THIS file that look like the current one,
                    // so the user can map them to the same payee consistently. (Computed in the
                    // resolver as UnresolvedPayee.similarInImport.)
                    if (currentPayee.similarInImport.isNotEmpty()) {
                        item {
                            SimilarInFileSection(similarNames = currentPayee.similarInImport)
                        }
                    }

                    // Always show mode selection - let user choose
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "What would you like to do?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                MappingModeSelector(
                                    selectedMode = mappingMode,
                                    onModeSelected = { mappingMode = it },
                                    showMapToExisting = hasAnySuggestions,
                                    hasAnySuggestions = hasAnySuggestions,
                                    suggestionCount = (currentPayee.suggestedMatches.size + similarRecentlyCreated.size)
                                )
                            }
                        }
                    }

                    // Content based on mode
                    when {
                        // Only show MAP_TO_EXISTING content if there are suggested matches
                        mappingMode == MappingMode.MAP_TO_EXISTING && hasAnySuggestions -> {
                            // Show database matches if any
                            if (currentPayee.suggestedMatches.isNotEmpty()) {
                                item {
                                    Text(
                                        "Similar payees in database:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                items(currentPayee.suggestedMatches) { match ->
                                    SimilarPayeeItem(
                                        match = match,
                                        isSelected = selectedPayeeId == match.payee.id,
                                        onSelect = { selectedPayeeId = match.payee.id },
                                        allCategories = allCategories
                                    )
                                }
                            }

                            // Show recently created similar payees if any
                            if (similarRecentlyCreated.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Recently created in this import:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }

                                items(similarRecentlyCreated) { recentPayee ->
                                    RecentlyCreatedPayeeItem(
                                        payee = recentPayee,
                                        isSelected = selectedPayeeId == recentPayee.id,
                                        onSelect = { selectedPayeeId = recentPayee.id },
                                        allCategories = allCategories
                                    )
                                }
                            }
                        }

                        // CREATE_NEW mode or when no existing payees to map to
                        else -> {
                            item {
                                OutlinedTextField(
                                    value = newPayeeName,
                                    onValueChange = { newPayeeName = it },
                                    label = { Text("Payee Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    // Category and tags selection (for both modes)
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Optional: Assign category and tags",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        CategoryPicker(
                            selectedCategory = selectedCategory,
                            categories = allCategories,
                            onCategorySelected = { selectedCategory = it },
                            label = "Default Category",
                            showNone = true
                        )
                    }

                    item {
                        TagSelector(
                            selectedTagIds = selectedTagIds,
                            allTags = allTags,
                            onTagsChanged = { selectedTagIds = it }
                        )
                    }

                    // Options
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (selectedCategory != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Category will be applied to all ${currentPayee.transactionCount} transactions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = rememberMapping,
                                    onCheckedChange = { rememberMapping = it }
                                )
                                Text(
                                    "Remember this mapping for future imports",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                HorizontalDivider()

                // Footer with navigation
                PayeeMappingFooter(
                    currentIndex = currentIndex,
                    totalCount = unresolvedPayees.size,
                    canGoBack = currentIndex > 0,
                    onPrevious = onPrevious,
                    onSkip = onSkip,
                    onNext = {
                        // Save the mapping before moving to next
                        when (mappingMode) {
                            MappingMode.MAP_TO_EXISTING -> {
                                if (selectedPayeeId != 0L) {
                                    onMapToExisting(
                                        selectedPayeeId,
                                        selectedCategory?.id,
                                        selectedTagIds,
                                        rememberMapping
                                    )
                                }
                            }
                            MappingMode.CREATE_NEW -> {
                                if (newPayeeName.isNotBlank()) {
                                    onCreateNew(
                                        newPayeeName,
                                        selectedCategory?.id,
                                        selectedTagIds,
                                        rememberMapping
                                    )
                                }
                            }
                        }
                        onNext()
                    }
                )
            }
        }
    }
}

@Composable
private fun PayeeMappingHeader(
    currentIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onSkipAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Cancel")
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Payee Mapping",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Review ${currentIndex + 1} of $totalCount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = onSkipAll) {
            Text("Skip All")
        }
    }
}

@Composable
private fun PayeeInfoSection(
    importedName: String,
    transactionCount: Int,
    variantNames: List<String> = listOf(importedName),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (variantNames.size > 1) "Similar Imported Names:" else "Imported Name:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (variantNames.size > 1) {
                // Show all variant names
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    variantNames.forEach { variant ->
                        Text(
                            "• $variant",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                Text(
                    importedName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                "$transactionCount transaction${if (transactionCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MappingModeSelector(
    selectedMode: MappingMode,
    onModeSelected: (MappingMode) -> Unit,
    showMapToExisting: Boolean = true,
    hasAnySuggestions: Boolean = false,
    suggestionCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (showMapToExisting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedMode == MappingMode.MAP_TO_EXISTING,
                    onClick = { onModeSelected(MappingMode.MAP_TO_EXISTING) }
                )
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Map to existing payee",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selectedMode == MappingMode.MAP_TO_EXISTING) FontWeight.Bold else FontWeight.Normal
                            )
                            if (suggestionCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                AssistChip(
                                    onClick = { onModeSelected(MappingMode.MAP_TO_EXISTING) },
                                    label = { Text("$suggestionCount found") },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    border = null,
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                        Text(
                            if (suggestionCount > 0) {
                                "$suggestionCount similar ${if (suggestionCount == 1) "payee" else "payees"} available"
                            } else {
                                "Use an existing payee from database or recently created"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (suggestionCount > 0) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (suggestionCount > 0) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        } else if (!hasAnySuggestions) {
            // Show disabled option with explanation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = false,
                    onClick = { },
                    enabled = false
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        "Map to existing payee",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "No matching payees found yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedMode == MappingMode.CREATE_NEW,
                onClick = { onModeSelected(MappingMode.CREATE_NEW) }
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    "Create new payee",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selectedMode == MappingMode.CREATE_NEW) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    "Create a new payee with a custom name",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SimilarInFileSection(
    similarNames: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Similar names in this file",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                "Map these to the same payee to keep them grouped:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            similarNames.forEach { name ->
                Text(
                    "• $name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun SimilarPayeeItem(
    match: PayeeMatch,
    isSelected: Boolean,
    onSelect: () -> Unit,
    allCategories: List<Category>,
    modifier: Modifier = Modifier
) {
    val category = match.payee.defaultCategoryId?.let { categoryId ->
        allCategories.find { it.id == categoryId }
    }

    Card(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        match.payee.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Similarity score
                    val percentage = (match.similarity * 100).roundToInt()
                    AssistChip(
                        onClick = { },
                        label = { Text("${percentage}% match") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when {
                                percentage >= 95 -> MaterialTheme.colorScheme.primaryContainer
                                percentage >= 85 -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        border = null
                    )

                    // Default category
                    if (category != null) {
                        AssistChip(
                            onClick = { },
                            label = { Text(category.name) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Category,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyCreatedPayeeItem(
    payee: Payee,
    isSelected: Boolean,
    onSelect: () -> Unit,
    allCategories: List<Category>,
    modifier: Modifier = Modifier
) {
    val category = payee.defaultCategoryId?.let { categoryId ->
        allCategories.find { it.id == categoryId }
    }

    Card(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        payee.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "Recently created" badge
                    AssistChip(
                        onClick = { },
                        label = { Text("Recently created") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        border = null
                    )

                    // Default category if set
                    if (category != null) {
                        AssistChip(
                            onClick = { },
                            label = { Text(category.name) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Category,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagSelector(
    selectedTagIds: List<Long>,
    allTags: List<Tag>,
    onTagsChanged: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Tags",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (allTags.isEmpty()) {
            Text(
                "No tags available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                allTags.take(5).forEach { tag ->
                    FilterChip(
                        selected = tag.id in selectedTagIds,
                        onClick = {
                            val updated = if (tag.id in selectedTagIds) {
                                selectedTagIds - tag.id
                            } else {
                                selectedTagIds + tag.id
                            }
                            onTagsChanged(updated)
                        },
                        label = { Text(tag.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PayeeMappingFooter(
    currentIndex: Int,
    totalCount: Int,
    canGoBack: Boolean,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLastPayee = currentIndex >= totalCount - 1

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Previous button
        OutlinedButton(
            onClick = onPrevious,
            enabled = canGoBack
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Previous")
        }

        // Skip button
        OutlinedButton(onClick = onSkip) {
            Text("Skip")
        }

        // Next/Import button
        Button(onClick = onNext) {
            Text(if (isLastPayee) "Import" else "Next")
            if (!isLastPayee) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

private enum class MappingMode {
    MAP_TO_EXISTING,
    CREATE_NEW
}
