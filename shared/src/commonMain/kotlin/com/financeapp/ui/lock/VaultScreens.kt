package com.financeapp.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
// VaultUnlockScreen and RecoveryKeyDialog — bodies unchanged (later task)
// ---------------------------------------------------------------------------

@Composable
fun VaultUnlockScreen(
    error: String?,
    onUnlock: (CharArray) -> Unit,
    onRecover: (String, CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showRecovery by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Enter your master password")
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(320.dp)
        )
        Button(
            onClick = { onUnlock(password.toCharArray()) },
            enabled = password.isNotEmpty()
        ) {
            Text("Unlock")
        }
        if (error != null) {
            Text(error)
        }
        TextButton(onClick = { showRecovery = !showRecovery }) {
            Text("Forgot password?")
        }
        if (showRecovery) {
            OutlinedTextField(
                value = recoveryCode,
                onValueChange = { recoveryCode = it },
                label = { Text("Recovery key") },
                modifier = Modifier.width(320.dp)
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New master password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(320.dp)
            )
            Button(
                onClick = { onRecover(recoveryCode, newPassword.toCharArray()) },
                enabled = recoveryCode.isNotEmpty() && newPassword.isNotEmpty()
            ) {
                Text("Reset password")
            }
        }
    }
}

@Composable
fun RecoveryKeyDialog(
    recoveryKey: RecoveryKey,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save your recovery key") },
        text = {
            Column {
                Text("Store this somewhere safe. It's the only way to recover your data if you forget your password.")
                Text(recoveryKey.display)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}
