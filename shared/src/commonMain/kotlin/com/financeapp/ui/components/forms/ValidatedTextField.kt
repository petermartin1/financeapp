package com.financeapp.ui.components.forms

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.financeapp.ui.animations.shake

/**
 * Validation rule for text fields
 */
data class ValidationRule(
    val validate: (String) -> Boolean,
    val errorMessage: String
)

/**
 * Validation state for a field
 */
sealed class ValidationState {
    object Idle : ValidationState()
    object Valid : ValidationState()
    data class Invalid(val message: String) : ValidationState()
}

/**
 * Text field with inline validation and error messages
 *
 * @param value Current text value
 * @param onValueChange Callback when value changes
 * @param label Field label
 * @param validationRules List of validation rules to apply
 * @param validateOnChange Whether to validate on each keystroke (default: false)
 * @param modifier Modifier for styling
 * @param placeholder Placeholder text
 * @param leadingIcon Optional leading icon
 * @param trailingIcon Optional trailing icon (error icon shown when invalid)
 * @param singleLine Whether field is single line
 * @param maxLines Maximum number of lines
 * @param enabled Whether field is enabled
 * @param visualTransformation Visual transformation (e.g., password masking)
 */
@Composable
fun ValidatedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    validationRules: List<ValidationRule> = emptyList(),
    validateOnChange: Boolean = false,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    var validationState by remember { mutableStateOf<ValidationState>(ValidationState.Idle) }
    var hasBeenBlurred by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    // Validate function
    fun validate(text: String): ValidationState {
        if (validationRules.isEmpty()) return ValidationState.Valid

        for (rule in validationRules) {
            if (!rule.validate(text)) {
                return ValidationState.Invalid(rule.errorMessage)
            }
        }
        return ValidationState.Valid
    }

    // Update validation state when value changes
    LaunchedEffect(value) {
        if (validateOnChange || hasBeenBlurred) {
            validationState = validate(value)
            showError = validationState is ValidationState.Invalid
        }
    }

    val isError = validationState is ValidationState.Invalid && showError

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
                if (validateOnChange) {
                    validationState = validate(newValue)
                    showError = validationState is ValidationState.Invalid
                }
            },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            leadingIcon = leadingIcon,
            trailingIcon = {
                when {
                    isError -> Icon(
                        Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                    trailingIcon != null -> trailingIcon()
                }
            },
            isError = isError,
            singleLine = singleLine,
            maxLines = maxLines,
            enabled = enabled,
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .shake(isError),
            supportingText = {
                AnimatedVisibility(
                    visible = isError,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    if (validationState is ValidationState.Invalid) {
                        Text(
                            text = (validationState as ValidationState.Invalid).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        )
    }

    // Trigger validation on blur
    DisposableEffect(Unit) {
        onDispose {
            if (!hasBeenBlurred) {
                hasBeenBlurred = true
                validationState = validate(value)
            }
        }
    }
}

/**
 * Common validation rules
 */
object ValidationRules {
    fun required(fieldName: String = "This field") = ValidationRule(
        validate = { it.isNotBlank() },
        errorMessage = "$fieldName is required"
    )

    fun minLength(length: Int, fieldName: String = "This field") = ValidationRule(
        validate = { it.length >= length },
        errorMessage = "$fieldName must be at least $length characters"
    )

    fun maxLength(length: Int, fieldName: String = "This field") = ValidationRule(
        validate = { it.length <= length },
        errorMessage = "$fieldName must be at most $length characters"
    )

    fun email() = ValidationRule(
        validate = { it.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) },
        errorMessage = "Please enter a valid email address"
    )

    fun numeric(fieldName: String = "This field") = ValidationRule(
        validate = { it.matches(Regex("^\\d+$")) },
        errorMessage = "$fieldName must contain only numbers"
    )

    fun decimal(fieldName: String = "This field") = ValidationRule(
        validate = { it.matches(Regex("^\\d*\\.?\\d+$")) },
        errorMessage = "$fieldName must be a valid number"
    )

    fun positiveNumber(fieldName: String = "Amount") = ValidationRule(
        validate = {
            val num = it.toDoubleOrNull()
            num != null && num > 0
        },
        errorMessage = "$fieldName must be greater than zero"
    )

    fun custom(validate: (String) -> Boolean, errorMessage: String) = ValidationRule(
        validate = validate,
        errorMessage = errorMessage
    )
}

/**
 * Form state manager for multiple validated fields
 */
class FormState {
    private val fields = mutableStateMapOf<String, ValidationState>()

    fun setFieldState(fieldId: String, state: ValidationState) {
        fields[fieldId] = state
    }

    fun isValid(): Boolean {
        return fields.values.all { it is ValidationState.Valid || it is ValidationState.Idle }
    }

    fun hasErrors(): Boolean {
        return fields.values.any { it is ValidationState.Invalid }
    }

    fun reset() {
        fields.clear()
    }
}

@Composable
fun rememberFormState(): FormState {
    return remember { FormState() }
}
