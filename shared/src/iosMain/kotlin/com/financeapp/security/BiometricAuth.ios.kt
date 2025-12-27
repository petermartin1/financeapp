package com.financeapp.security

import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.LocalAuthentication.LABiometryTypeFaceID
import platform.LocalAuthentication.LABiometryTypeTouchID
import platform.Foundation.NSError
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.ObjCObjectVar

actual class BiometricAuth {
    private val context = LAContext()

    @OptIn(ExperimentalForeignApi::class)
    actual fun isAvailable(): Boolean {
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            context.canEvaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                error.ptr
            )
        }
    }

    actual fun getBiometricType(): BiometricType {
        return when (context.biometryType) {
            LABiometryTypeFaceID -> BiometricType.FACE
            LABiometryTypeTouchID -> BiometricType.FINGERPRINT
            else -> BiometricType.NONE
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun authenticate(reason: String, onResult: (BiometricResult) -> Unit) {
        if (!isAvailable()) {
            onResult(BiometricResult.NOT_AVAILABLE)
            return
        }

        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = reason
        ) { success, error ->
            when {
                success -> onResult(BiometricResult.SUCCESS)
                error != null -> {
                    // Check error code for specific cases
                    val errorCode = error.code
                    when (errorCode) {
                        -2L -> onResult(BiometricResult.CANCELLED) // User cancelled
                        -7L -> onResult(BiometricResult.NOT_ENROLLED) // No biometrics enrolled
                        else -> onResult(BiometricResult.FAILED)
                    }
                }
                else -> onResult(BiometricResult.FAILED)
            }
        }
    }
}
