package com.financeapp.security

// Desktop biometric authentication
// Note: True biometric support on desktop requires platform-specific native libraries
// This implementation provides a placeholder that falls back to PIN
actual class BiometricAuth {
    actual fun isAvailable(): Boolean {
        // Desktop biometric support would require:
        // - Windows Hello API on Windows
        // - Touch ID API on macOS
        // - Fingerprint readers on Linux
        // For now, return false to fall back to PIN
        return false
    }

    actual fun getBiometricType(): BiometricType {
        return BiometricType.NONE
    }

    actual fun authenticate(reason: String, onResult: (BiometricResult) -> Unit) {
        // No biometric available on desktop in this implementation
        onResult(BiometricResult.NOT_AVAILABLE)
    }
}
