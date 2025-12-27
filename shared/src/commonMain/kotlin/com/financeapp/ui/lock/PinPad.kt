package com.financeapp.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PinPad(
    onPinEntered: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Request focus on first composition to enable keyboard input
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        event.key == Key.Backspace -> {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                            }
                            true
                        }
                        event.key in listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four,
                            Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine) -> {
                            if (pin.length < 4) {
                                val digit = when (event.key) {
                                    Key.Zero -> "0"
                                    Key.One -> "1"
                                    Key.Two -> "2"
                                    Key.Three -> "3"
                                    Key.Four -> "4"
                                    Key.Five -> "5"
                                    Key.Six -> "6"
                                    Key.Seven -> "7"
                                    Key.Eight -> "8"
                                    Key.Nine -> "9"
                                    else -> ""
                                }
                                pin += digit
                                if (pin.length == 4) {
                                    onPinEntered(pin)
                                    pin = ""
                                }
                            }
                            true
                        }
                        // Also support numpad
                        event.key in listOf(Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3,
                            Key.NumPad4, Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9) -> {
                            if (pin.length < 4) {
                                val digit = when (event.key) {
                                    Key.NumPad0 -> "0"
                                    Key.NumPad1 -> "1"
                                    Key.NumPad2 -> "2"
                                    Key.NumPad3 -> "3"
                                    Key.NumPad4 -> "4"
                                    Key.NumPad5 -> "5"
                                    Key.NumPad6 -> "6"
                                    Key.NumPad7 -> "7"
                                    Key.NumPad8 -> "8"
                                    Key.NumPad9 -> "9"
                                    else -> ""
                                }
                                pin += digit
                                if (pin.length == 4) {
                                    onPinEntered(pin)
                                    pin = ""
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // PIN dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pin.length)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                )
            }
        }

        // Number pad
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            ).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    row.forEach { key ->
                        PinKey(
                            key = key,
                            onClick = {
                                when (key) {
                                    "⌫" -> {
                                        if (pin.isNotEmpty()) {
                                            pin = pin.dropLast(1)
                                        }
                                    }
                                    "" -> { /* empty space */ }
                                    else -> {
                                        if (pin.length < 4) {
                                            pin += key
                                            if (pin.length == 4) {
                                                onPinEntered(pin)
                                                pin = ""
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(
    key: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (key.isNotEmpty())
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.surface
            )
            .clickable(enabled = key.isNotEmpty()) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
    }
}
