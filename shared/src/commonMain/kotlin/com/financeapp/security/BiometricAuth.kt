package com.financeapp.security

enum class BiometricType {
    NONE,
    FINGERPRINT,
    FACE,
    IRIS
}

enum class BiometricResult {
    SUCCESS,
    FAILED,
    CANCELLED,
    NOT_AVAILABLE,
    NOT_ENROLLED
}

expect class BiometricAuth {
    fun isAvailable(): Boolean
    fun getBiometricType(): BiometricType
    fun authenticate(reason: String, onResult: (BiometricResult) -> Unit)
}
