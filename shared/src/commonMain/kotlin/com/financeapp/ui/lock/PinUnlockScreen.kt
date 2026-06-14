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
import kotlinx.coroutines.delay
import kotlin.time.Clock

@Composable
fun PinUnlockScreen(
    onPinEntered: (String) -> Unit,
    failedAttempts: Int,
    lockedUntilEpochMs: Long? = null,
    biometricAvailable: Boolean = false,
    biometricType: BiometricType = BiometricType.NONE,
    onBiometricClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Tick down the lockout so the UI re-enables itself the moment it expires.
    var remainingLockoutSeconds by remember(lockedUntilEpochMs) { mutableStateOf(0L) }
    LaunchedEffect(lockedUntilEpochMs) {
        if (lockedUntilEpochMs == null) {
            remainingLockoutSeconds = 0L
            return@LaunchedEffect
        }
        while (true) {
            val remainingMs = lockedUntilEpochMs - Clock.System.now().toEpochMilliseconds()
            if (remainingMs <= 0) {
                remainingLockoutSeconds = 0L
                break
            }
            remainingLockoutSeconds = (remainingMs + 999) / 1000
            delay(500)
        }
    }
    val isLockedOut = remainingLockoutSeconds > 0

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

        if (isLockedOut) {
            Text(
                text = "Too many attempts. Try again in ${remainingLockoutSeconds}s.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else if (failedAttempts > 0) {
            Text(
                text = if (failedAttempts >= 5)
                    "Incorrect password."
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
            enabled = !isLockedOut,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (password.isNotEmpty() && !isLockedOut) {
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
                if (password.isNotEmpty() && !isLockedOut) {
                    onPinEntered(password)
                    password = ""
                }
            },
            enabled = password.isNotEmpty() && !isLockedOut
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
