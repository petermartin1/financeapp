package com.financeapp.ui.lock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.financeapp.security.BiometricType

@Composable
fun PinUnlockScreen(
    onPinEntered: (String) -> Unit,
    failedAttempts: Int,
    biometricAvailable: Boolean = false,
    biometricType: BiometricType = BiometricType.NONE,
    onBiometricClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Finance App",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Enter your password",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (failedAttempts > 0) {
            Text(
                text = if (failedAttempts >= 5)
                    "Too many attempts. Try again later."
                else
                    "Incorrect password. ${5 - failedAttempts} attempts remaining.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (password.isNotEmpty()) {
                        onPinEntered(password)
                        password = ""
                    }
                }
            ),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier
                .widthIn(max = 300.dp)
                .focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (password.isNotEmpty()) {
                    onPinEntered(password)
                    password = ""
                }
            },
            enabled = password.isNotEmpty() && failedAttempts < 5
        ) {
            Text("Unlock")
        }

        if (biometricAvailable) {
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onBiometricClick
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when (biometricType) {
                        BiometricType.FACE -> "Use Face ID"
                        BiometricType.FINGERPRINT -> "Use Touch ID"
                        else -> "Use Biometrics"
                    }
                )
            }
        }
    }
}
