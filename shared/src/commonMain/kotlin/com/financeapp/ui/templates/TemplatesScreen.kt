package com.financeapp.ui.templates

import java.util.Locale

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.domain.model.TransactionTemplate
import com.financeapp.domain.model.TransactionTemplateWithDetails
import com.financeapp.domain.model.Account
import com.financeapp.domain.model.Category
import com.financeapp.domain.model.Payee
import com.financeapp.ui.components.parseDecimalToCents
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    viewModel: TemplatesViewModel,
    onBack: () -> Unit,
    onUseTemplate: (TransactionTemplate) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<TransactionTemplateWithDetails?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Templates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Template")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.templates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No templates yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Create templates for frequently used transactions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showAddDialog = true }) {
                        Text("Add Template")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(uiState.templates) { template ->
                    TemplateItem(
                        template = template,
                        onUse = { onUseTemplate(template.template) },
                        onEdit = { editingTemplate = template },
                        onDelete = { viewModel.deleteTemplate(template.template.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        TemplateDialog(
            template = null,
            accounts = uiState.accounts,
            categories = uiState.categories,
            payees = uiState.payees,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, accountId, payeeId, categoryId, amount, memo ->
                viewModel.addTemplate(name, accountId, payeeId, categoryId, amount, memo)
                showAddDialog = false
            }
        )
    }

    editingTemplate?.let { template ->
        TemplateDialog(
            template = template,
            accounts = uiState.accounts,
            categories = uiState.categories,
            payees = uiState.payees,
            onDismiss = { editingTemplate = null },
            onConfirm = { name, accountId, payeeId, categoryId, amount, memo ->
                viewModel.updateTemplate(
                    template.template.copy(
                        name = name,
                        accountId = accountId,
                        payeeId = payeeId,
                        categoryId = categoryId,
                        amount = amount,
                        memo = memo
                    )
                )
                editingTemplate = null
            }
        )
    }
}

@Composable
private fun TemplateItem(
    template: TransactionTemplateWithDetails,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                template.template.name,
                fontWeight = FontWeight.Medium
            )
        },
        supportingContent = {
            Column {
                template.payeeName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                template.categoryName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                template.template.amount?.let { amount ->
                    Text(
                        formatCurrency(amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (amount >= 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Use") },
                            onClick = {
                                showMenu = false
                                onUse()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onUse)
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateDialog(
    template: TransactionTemplateWithDetails?,
    accounts: List<Account>,
    categories: List<Category>,
    payees: List<Payee>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?, Long?, Long?, Long?, String?) -> Unit
) {
    var name by remember { mutableStateOf(template?.template?.name ?: "") }
    var selectedAccountId by remember { mutableStateOf(template?.template?.accountId) }
    var selectedPayeeId by remember { mutableStateOf(template?.template?.payeeId) }
    var selectedCategoryId by remember { mutableStateOf(template?.template?.categoryId) }
    var amountText by remember {
        mutableStateOf(template?.template?.amount?.let {
            String.format(Locale.ROOT, "%.2f", kotlin.math.abs(it) / 100.0)
        } ?: "")
    }
    var isExpense by remember { mutableStateOf((template?.template?.amount ?: -1) < 0) }
    var memo by remember { mutableStateOf(template?.template?.memo ?: "") }

    var accountExpanded by remember { mutableStateOf(false) }
    var payeeExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template == null) "Add Template" else "Edit Template") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Template Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Account dropdown
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = it }
                ) {
                    OutlinedTextField(
                        value = accounts.find { it.id == selectedAccountId }?.name ?: "Any Account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = accountExpanded,
                        onDismissRequest = { accountExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Any Account") },
                            onClick = {
                                selectedAccountId = null
                                accountExpanded = false
                            }
                        )
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedAccountId = account.id
                                    accountExpanded = false
                                }
                            )
                        }
                    }
                }

                // Payee dropdown
                ExposedDropdownMenuBox(
                    expanded = payeeExpanded,
                    onExpandedChange = { payeeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = payees.find { it.id == selectedPayeeId }?.name ?: "No Payee",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Payee") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = payeeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = payeeExpanded,
                        onDismissRequest = { payeeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No Payee") },
                            onClick = {
                                selectedPayeeId = null
                                payeeExpanded = false
                            }
                        )
                        payees.forEach { payee ->
                            DropdownMenuItem(
                                text = { Text(payee.name) },
                                onClick = {
                                    selectedPayeeId = payee.id
                                    payeeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = categories.find { it.id == selectedCategoryId }?.name ?: "No Category",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No Category") },
                            onClick = {
                                selectedCategoryId = null
                                categoryExpanded = false
                            }
                        )
                        categories.forEach { category ->
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

                // Amount with type toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isExpense,
                        onClick = { isExpense = true },
                        label = { Text("Expense") }
                    )
                    FilterChip(
                        selected = !isExpense,
                        onClick = { isExpense = false },
                        label = { Text("Income") }
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("Memo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = parseDecimalToCents(amountText)?.let { cents ->
                        if (isExpense) -cents else cents
                    }
                    onConfirm(
                        name,
                        selectedAccountId,
                        selectedPayeeId,
                        selectedCategoryId,
                        amount,
                        memo.ifBlank { null }
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (template == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatCurrency(cents: Long): String {
    val absCents = kotlin.math.abs(cents)
    val wholeDollars = absCents / 100
    val centsPart = absCents % 100
    val sign = if (cents < 0) "-" else ""
    return "$sign$$wholeDollars.${centsPart.toString().padStart(2, '0')}"
}
