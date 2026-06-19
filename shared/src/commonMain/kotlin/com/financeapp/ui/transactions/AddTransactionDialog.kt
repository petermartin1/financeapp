package com.financeapp.ui.transactions

import java.util.Locale

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.Account
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.CategoryType
import com.financeapp.domain.model.Tag
import com.financeapp.domain.model.TransactionWithDetails
import kotlin.math.abs
import com.financeapp.ui.components.parseDecimalToCents
import kotlin.math.roundToLong
import com.financeapp.ui.categories.CategoriesViewModel
import com.financeapp.ui.tags.TagsViewModel
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        amount: Long,
        payee: String?,
        categoryId: Long?,
        memo: String?,
        date: LocalDate,
        isCleared: Boolean,
        tagIds: List<Long>
    ) -> Unit,
    currentAccountId: Long = 0,
    accounts: List<Account> = emptyList(),
    onTransfer: ((amount: Long, toAccountId: Long, memo: String?, date: LocalDate) -> Unit)? = null
) {
    val categoriesViewModel: CategoriesViewModel = koinInject()
    val categoriesState by categoriesViewModel.uiState.collectAsState()
    val tagsViewModel: TagsViewModel = koinInject()
    val tagsState by tagsViewModel.uiState.collectAsState()
    val payeeRepository: com.financeapp.domain.repository.PayeeRepository = koinInject()
    val allPayees by payeeRepository.getAllPayees().collectAsState(initial = emptyList())

    var amountText by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf(0) } // 0=Expense, 1=Income, 2=Transfer
    var payee by remember { mutableStateOf("") }
    var payeeExpanded by remember { mutableStateOf(false) }
    var memo by remember { mutableStateOf("") }
    var isCleared by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedToAccount by remember { mutableStateOf<Account?>(null) }
    var toAccountExpanded by remember { mutableStateOf(false) }
    var selectedTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Default to today
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    var selectedDate by remember { mutableStateOf(today) }

    // Filter categories based on expense/income
    val availableCategories = categoriesState.categories.filter {
        when (transactionType) {
            0 -> it.type == CategoryType.EXPENSE
            1 -> it.type == CategoryType.INCOME
            else -> false
        }
    }

    // Filter out current account from transfer targets
    val transferAccounts = accounts.filter { it.id != currentAccountId }

    // Filter payees based on text input for autocomplete
    val filteredPayees = remember(payee, allPayees) {
        if (payee.isBlank()) {
            allPayees.take(10) // Show first 10 when empty
        } else {
            allPayees.filter { it.name.contains(payee, ignoreCase = true) }.take(10)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Transaction") },
        text = {
            Column(
                modifier = Modifier.width(500.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Transaction type toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = transactionType == 0,
                        onClick = {
                            transactionType = 0
                            selectedCategory = null
                        },
                        label = { Text("Expense") }
                    )
                    FilterChip(
                        selected = transactionType == 1,
                        onClick = {
                            transactionType = 1
                            selectedCategory = null
                        },
                        label = { Text("Income") }
                    )
                    if (onTransfer != null && transferAccounts.isNotEmpty()) {
                        FilterChip(
                            selected = transactionType == 2,
                            onClick = {
                                transactionType = 2
                                selectedCategory = null
                            },
                            label = { Text("Transfer") }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value ->
                        // Only allow digits and one decimal point
                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amountText = value
                        }
                    },
                    label = { Text("Amount") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                if (transactionType == 2) {
                    // Transfer: show account selector
                    ExposedDropdownMenuBox(
                        expanded = toAccountExpanded,
                        onExpandedChange = { toAccountExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedToAccount?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Transfer To") },
                            placeholder = { Text("Select account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toAccountExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )

                        ExposedDropdownMenu(
                            expanded = toAccountExpanded,
                            onDismissRequest = { toAccountExpanded = false }
                        ) {
                            transferAccounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = {
                                        selectedToAccount = account
                                        toAccountExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Regular transaction: show payee (free text with suggestions)
                    Column {
                        OutlinedTextField(
                            value = payee,
                            onValueChange = {
                                payee = it
                                payeeExpanded = it.isNotEmpty() && filteredPayees.isNotEmpty()
                            },
                            label = { Text("Payee") },
                            placeholder = { Text("Type payee name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Show suggestions as chips below the field
                        if (payeeExpanded && filteredPayees.isNotEmpty()) {
                            Text(
                                text = "Suggestions:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                filteredPayees.take(5).forEach { payeeItem ->
                                    SuggestionChip(
                                        onClick = {
                                            payee = payeeItem.name
                                            payeeExpanded = false
                                        },
                                        label = { Text(payeeItem.name) }
                                    )
                                }
                            }
                        }
                    }

                    // Category dropdown
                    if (availableCategories.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedCategory?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category") },
                                placeholder = { Text("Select category") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )

                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                availableCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            selectedCategory = category
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("Memo (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date picker
                OutlinedTextField(
                    value = "${selectedDate.monthNumber}/${selectedDate.dayOfMonth}/${selectedDate.year}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tags selector
                if (tagsState.tags.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Tags",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tagsState.tags) { tag ->
                                FilterChip(
                                    selected = selectedTagIds.contains(tag.id),
                                    onClick = {
                                        selectedTagIds = if (selectedTagIds.contains(tag.id)) {
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
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isCleared,
                        onCheckedChange = { isCleared = it }
                    )
                    Text("Cleared")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cents = parseDecimalToCents(amountText) ?: 0L

                    if (transactionType == 2 && onTransfer != null && selectedToAccount != null) {
                        // Transfer
                        onTransfer(
                            cents,
                            selectedToAccount!!.id,
                            memo.ifBlank { null },
                            selectedDate
                        )
                    } else {
                        // Regular transaction
                        var amount = cents
                        if (transactionType == 0) amount = -amount // Expense is negative

                        onConfirm(
                            amount,
                            payee.ifBlank { null },
                            selectedCategory?.id,
                            memo.ifBlank { null },
                            selectedDate,
                            isCleared,
                            selectedTagIds.toList()
                        )
                    }
                },
                enabled = amountText.isNotBlank() && amountText.toDoubleOrNull() != null &&
                    (transactionType != 2 || selectedToAccount != null)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toEpochDays() * 24 * 60 * 60 * 1000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val days = millis / (24 * 60 * 60 * 1000L)
                        selectedDate = LocalDate.fromEpochDays(days.toInt())
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: TransactionWithDetails,
    onDismiss: () -> Unit,
    onSave: (
        categoryId: Long?,
        memo: String?,
        date: LocalDate,
        isCleared: Boolean,
        tagIds: List<Long>
    ) -> Unit,
    onDelete: () -> Unit,
    initialTagIds: List<Long> = emptyList()
) {
    val categoriesViewModel: CategoriesViewModel = koinInject()
    val categoriesState by categoriesViewModel.uiState.collectAsState()
    val tagsViewModel: TagsViewModel = koinInject()
    val tagsState by tagsViewModel.uiState.collectAsState()

    val txn = transaction.transaction
    val isExpense = txn.amount < 0

    var memo by remember { mutableStateOf(txn.memo ?: "") }
    var isCleared by remember { mutableStateOf(txn.isCleared) }
    var selectedCategory by remember {
        mutableStateOf(categoriesState.categories.find { it.id == txn.categoryId })
    }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedTagIds by remember { mutableStateOf(initialTagIds.toSet()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(txn.date) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Keep the selected category in sync with the live list (R21): resolve it once categories
    // load, and drop it if it was deleted elsewhere while this dialog was open so we never save
    // a stale category id.
    LaunchedEffect(categoriesState.categories) {
        val current = selectedCategory
        if (current != null) {
            if (categoriesState.categories.none { it.id == current.id }) {
                selectedCategory = null
            }
        } else if (txn.categoryId != null) {
            selectedCategory = categoriesState.categories.find { it.id == txn.categoryId }
        }
    }

    // Filter categories based on expense/income
    val availableCategories = categoriesState.categories.filter {
        if (isExpense) it.type == CategoryType.EXPENSE else it.type == CategoryType.INCOME
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Display payee and amount (read-only)
                Text(
                    text = transaction.payeeName ?: txn.memo ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$${String.format(Locale.ROOT, "%.2f", abs(txn.amount) / 100.0)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )

                // Category dropdown
                if (availableCategories.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            placeholder = { Text("Select category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )

                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            availableCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        selectedCategory = category
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("Memo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Date picker
                OutlinedTextField(
                    value = "${selectedDate.monthNumber}/${selectedDate.dayOfMonth}/${selectedDate.year}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tags selector
                if (tagsState.tags.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Tags",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tagsState.tags) { tag ->
                                FilterChip(
                                    selected = selectedTagIds.contains(tag.id),
                                    onClick = {
                                        selectedTagIds = if (selectedTagIds.contains(tag.id)) {
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
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isCleared,
                        onCheckedChange = { isCleared = it }
                    )
                    Text("Cleared")
                }

                // Delete button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Transaction")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        selectedCategory?.id,
                        memo.ifBlank { null },
                        selectedDate,
                        isCleared,
                        selectedTagIds.toList()
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toEpochDays() * 24 * 60 * 60 * 1000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val days = millis / (24 * 60 * 60 * 1000L)
                        selectedDate = LocalDate.fromEpochDays(days.toInt())
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete this transaction? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
