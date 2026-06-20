package com.financeapp.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.financeapp.security.vault.PasswordStrength
import com.financeapp.security.vault.RecoveryKey

// ---------------------------------------------------------------------------
// Shared private form used by VaultSetupScreen and VaultMigrateScreen
// ---------------------------------------------------------------------------

@Composable
private fun MasterPasswordForm(
    title: String,
    subtitle: String,
    checkStrength: (CharArray) -> PasswordStrength.Result,
    confirmLabel: String,
    onSubmit: (CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    val result = remember(password) { checkStrength(password.toCharArray()) }
    val passwordsMatch = password == confirm
    val canSubmit = result.acceptable && passwordsMatch && confirm.isNotEmpty()

    val focusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(bottom = 8.dp)
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(bottom = 28.dp)
        )

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { confirmFocusRequester.requestFocus() }
            ),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            },
            supportingText = {
                when {
                    password.isEmpty() -> Unit
                    !result.acceptable -> Text(
                        text = result.reason ?: "Password is too weak.",
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Text(
                        text = "Looks good",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            isError = password.isNotEmpty() && !result.acceptable,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Confirm password field
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm password") },
            singleLine = true,
            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (canSubmit) onSubmit(password.toCharArray())
                }
            ),
            trailingIcon = {
                TextButton(onClick = { confirmVisible = !confirmVisible }) {
                    Text(if (confirmVisible) "Hide" else "Show")
                }
            },
            supportingText = {
                if (confirm.isNotEmpty() && !passwordsMatch) {
                    Text(
                        text = "Passwords don't match.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            isError = confirm.isNotEmpty() && !passwordsMatch,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .focusRequester(confirmFocusRequester)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onSubmit(password.toCharArray()) },
            enabled = canSubmit
        ) {
            Text(confirmLabel)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Tip: also turn on full-disk encryption (FileVault on macOS, BitLocker on Windows) for defense in depth.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Public composables — signatures are fixed and must not change
// ---------------------------------------------------------------------------

@Composable
fun VaultSetupScreen(
    checkStrength: (CharArray) -> PasswordStrength.Result,
    onCreate: (CharArray) -> Unit
) = MasterPasswordForm(
    title = "Create your master password",
    subtitle = "This password encrypts your financial data. It can't be recovered if you forget it — you'll get a recovery key next.",
    checkStrength = checkStrength,
    confirmLabel = "Create",
    onSubmit = onCreate
)

@Composable
fun VaultMigrateScreen(
    checkStrength: (CharArray) -> PasswordStrength.Result,
    onMigrate: (CharArray) -> Unit
) = MasterPasswordForm(
    title = "Secure your data",
    subtitle = "We're upgrading your encryption. Set a master password — from now on it's required each time you open the app.",
    checkStrength = checkStrength,
    confirmLabel = "Secure",
    onSubmit = onMigrate
)

// ---------------------------------------------------------------------------
// VaultUnlockScreen — production-grade unlock with optional recovery sub-section
// ---------------------------------------------------------------------------

@Composable
fun VaultUnlockScreen(
    error: String?,
    onUnlock: (CharArray) -> Unit,
    onRecover: (String, CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }

    // Recovery sub-section state
    var recoveryKey by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPassword by remember { mutableStateOf("") }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val newPasswordResult = remember(newPassword) {
        PasswordStrength.evaluate(newPassword.toCharArray())
    }
    val passwordsMatch = newPassword == confirmPassword
    val canRecover = recoveryKey.isNotBlank() &&
        newPasswordResult.acceptable &&
        confirmPassword.isNotEmpty() &&
        passwordsMatch

    val focusRequester = remember { FocusRequester() }
    val recoveryKeyFocus = remember { FocusRequester() }
    val newPasswordFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Header
        Text(
            text = "Finance App",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(bottom = 8.dp)
        )
        Text(
            text = "Enter your master password",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(bottom = 28.dp)
        )

        // Error banner
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(bottom = 12.dp)
            )
        }

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (password.isNotEmpty()) onUnlock(password.toCharArray())
                }
            ),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            },
            isError = error != null,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onUnlock(password.toCharArray()) },
            enabled = password.isNotEmpty(),
            modifier = Modifier.widthIn(min = 160.dp)
        ) {
            Text("Unlock")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Forgot password toggle
        TextButton(onClick = { showRecovery = !showRecovery }) {
            Text(if (showRecovery) "Cancel recovery" else "Forgot password?")
        }

        // Recovery sub-section
        if (showRecovery) {
            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(modifier = Modifier.widthIn(max = 360.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Recover with your recovery key",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(bottom = 12.dp)
            )

            // Recovery key field
            OutlinedTextField(
                value = recoveryKey,
                onValueChange = { recoveryKey = it },
                label = { Text("Recovery key (XXXX-XXXX-…)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { newPasswordFocus.requestFocus() }
                ),
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .focusRequester(recoveryKeyFocus)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // New password field
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New master password") },
                singleLine = true,
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { confirmFocus.requestFocus() }
                ),
                trailingIcon = {
                    TextButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                        Text(if (newPasswordVisible) "Hide" else "Show")
                    }
                },
                supportingText = {
                    when {
                        newPassword.isEmpty() -> Unit
                        !newPasswordResult.acceptable -> Text(
                            text = newPasswordResult.reason ?: "Password is too weak.",
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Text(
                            text = "Looks good",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                isError = newPassword.isNotEmpty() && !newPasswordResult.acceptable,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .focusRequester(newPasswordFocus)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Confirm new password field
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm new password") },
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (canRecover) onRecover(recoveryKey, newPassword.toCharArray())
                    }
                ),
                trailingIcon = {
                    TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Text(if (confirmPasswordVisible) "Hide" else "Show")
                    }
                },
                supportingText = {
                    if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                        Text(
                            text = "Passwords don't match.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .focusRequester(confirmFocus)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onRecover(recoveryKey, newPassword.toCharArray()) },
                enabled = canRecover,
                modifier = Modifier.widthIn(min = 200.dp)
            ) {
                Text("Reset password & unlock")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// RecoveryKeyDialog — modal, forces acknowledgement before dismissal
// ---------------------------------------------------------------------------

@Composable
fun RecoveryKeyDialog(
    recoveryKey: RecoveryKey,
    onDismiss: () -> Unit
) {
    var confirmed by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = {},   // No dismiss on outside click — user must tick the checkbox
        title = {
            Text(
                text = "Save your recovery key",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Key displayed prominently in selectable monospace text
                SelectionContainer {
                    Text(
                        text = recoveryKey.display,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Copy button
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(recoveryKey.display))
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Copy")
                }

                // Warning text
                Text(
                    text = "This is the only time you'll see this key. " +
                        "Store it somewhere safe — anyone who has it can open your data.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Acknowledgement checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = { confirmed = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I've saved my recovery key",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                enabled = confirmed
            ) {
                Text("Done")
            }
        }
    )
}
