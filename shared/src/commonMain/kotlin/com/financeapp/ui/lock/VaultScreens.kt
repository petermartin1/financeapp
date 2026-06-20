package com.financeapp.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.financeapp.security.vault.PasswordStrength
import com.financeapp.security.vault.RecoveryKey

/**
 * Minimal placeholder vault screens. These are functional enough to drive the unlock flow;
 * polished versions arrive in a later phase.
 */

@Composable
fun VaultSetupScreen(
    checkStrength: (CharArray) -> PasswordStrength.Result,
    onCreate: (CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val strength = remember(password) { checkStrength(password.toCharArray()) }
    val matches = password.isNotEmpty() && password == confirm
    val canCreate = strength.acceptable && matches

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create your master password")
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(320.dp)
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(320.dp)
        )
        if (password.isNotEmpty() && !strength.acceptable) {
            Text(strength.reason ?: "Password is too weak.")
        } else if (confirm.isNotEmpty() && !matches) {
            Text("Passwords don't match.")
        }
        Button(
            onClick = { onCreate(password.toCharArray()) },
            enabled = canCreate
        ) {
            Text("Create")
        }
    }
}

@Composable
fun VaultMigrateScreen(
    checkStrength: (CharArray) -> PasswordStrength.Result,
    onMigrate: (CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val strength = remember(password) { checkStrength(password.toCharArray()) }
    val matches = password.isNotEmpty() && password == confirm
    val canMigrate = strength.acceptable && matches

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Protect your data with a master password")
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(320.dp)
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(320.dp)
        )
        if (password.isNotEmpty() && !strength.acceptable) {
            Text(strength.reason ?: "Password is too weak.")
        } else if (confirm.isNotEmpty() && !matches) {
            Text("Passwords don't match.")
        }
        Button(
            onClick = { onMigrate(password.toCharArray()) },
            enabled = canMigrate
        ) {
            Text("Continue")
        }
    }
}

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
