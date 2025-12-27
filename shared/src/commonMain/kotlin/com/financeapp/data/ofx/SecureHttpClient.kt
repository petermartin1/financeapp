package com.financeapp.data.ofx

import io.ktor.client.*

/**
 * Creates a secure HTTP client with certificate pinning support.
 * Platform implementations use OkHttp (JVM) or URLSession (iOS) with certificate validation.
 */
expect fun createSecureHttpClient(certificatePins: Map<String, List<String>>): HttpClient

/**
 * Validates that a string contains only safe characters for use in external processes.
 * Prevents command injection attacks.
 */
fun validateSafeString(input: String, fieldName: String, maxLength: Int = 256): Result<String> {
    if (input.length > maxLength) {
        return Result.failure(SecurityException("$fieldName exceeds maximum length of $maxLength"))
    }

    // Allow alphanumeric, common punctuation, but block shell metacharacters
    val dangerousChars = listOf(';', '&', '|', '$', '`', '\\', '"', '\'', '<', '>', '\n', '\r', '\u0000')
    for (char in dangerousChars) {
        if (input.contains(char)) {
            return Result.failure(SecurityException("$fieldName contains invalid character"))
        }
    }

    return Result.success(input)
}

/**
 * Validates user ID format for bank connections.
 */
fun validateUserId(userId: String): Result<String> {
    if (userId.isBlank()) {
        return Result.failure(SecurityException("User ID cannot be empty"))
    }
    return validateSafeString(userId, "User ID", 128)
}

/**
 * Validates password - allows more characters but still blocks dangerous ones.
 */
fun validatePassword(password: String): Result<String> {
    if (password.length < 8) {
        return Result.failure(SecurityException("Password must be at least 8 characters"))
    }
    if (password.length > 256) {
        return Result.failure(SecurityException("Password exceeds maximum length"))
    }
    // Block null bytes and newlines which could break keychain commands
    if (password.contains('\u0000') || password.contains('\n') || password.contains('\r')) {
        return Result.failure(SecurityException("Password contains invalid characters"))
    }
    return Result.success(password)
}

/**
 * Validates bank name.
 */
fun validateBankName(bankName: String): Result<String> {
    if (bankName.isBlank()) {
        return Result.failure(SecurityException("Bank name cannot be empty"))
    }
    return validateSafeString(bankName, "Bank name", 128)
}

class SecurityException(message: String) : Exception(message)
