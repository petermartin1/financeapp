package com.financeapp.security

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Monitor certificate pins and track expiration/rotation.
 *
 * Certificate pins should be rotated periodically and backup pins maintained.
 * This monitor helps track pin age and alerts when rotation is needed.
 *
 * Best practices:
 * - Always maintain at least 2 backup pins
 * - Rotate pins every 6-12 months
 * - Monitor pin age and alert before expiration
 */
object CertificatePinMonitor {
    private val pinRegistry = mutableMapOf<String, PinMetadata>()

    /**
     * Register a certificate pin for monitoring.
     *
     * @param hostname The hostname this pin is for
     * @param pin The certificate pin (SHA-256)
     * @param addedDate When this pin was added
     * @param expirationWarningDays Days before expiration to warn (default 90)
     */
    fun registerPin(
        hostname: String,
        pin: String,
        addedDate: Instant = Clock.System.now(),
        expirationWarningDays: Int = 90
    ) {
        val key = "$hostname:$pin"
        pinRegistry[key] = PinMetadata(
            hostname = hostname,
            pin = pin,
            addedDate = addedDate,
            expirationWarningDays = expirationWarningDays
        )
    }

    /**
     * Check if any pins need attention (rotation, backup, etc.).
     *
     * @return List of warnings about pins
     */
    fun checkPinHealth(): List<PinWarning> {
        val warnings = mutableListOf<PinWarning>()
        val now = Clock.System.now()

        // Group pins by hostname
        val pinsByHost = pinRegistry.values.groupBy { it.hostname }

        for ((hostname, pins) in pinsByHost) {
            // Check if we have backup pins (should have at least 2)
            if (pins.size < 2) {
                warnings.add(PinWarning(
                    hostname = hostname,
                    severity = WarningSeverity.HIGH,
                    message = "Only ${pins.size} pin(s) configured for $hostname. Add backup pins."
                ))
            }

            // Check pin age
            for (pinMeta in pins) {
                val age = now - pinMeta.addedDate
                val warningThreshold = pinMeta.expirationWarningDays.days

                // Warn if pin is older than 1 year
                if (age > 365.days) {
                    warnings.add(PinWarning(
                        hostname = hostname,
                        severity = WarningSeverity.CRITICAL,
                        message = "Pin for $hostname is ${age.inWholeDays} days old. Rotate immediately."
                    ))
                } else if (age > (365.days - warningThreshold)) {
                    warnings.add(PinWarning(
                        hostname = hostname,
                        severity = WarningSeverity.MEDIUM,
                        message = "Pin for $hostname is ${age.inWholeDays} days old. Consider rotating soon."
                    ))
                }
            }
        }

        return warnings
    }

    /**
     * Get all registered pins for a hostname.
     */
    fun getPinsForHost(hostname: String): List<PinMetadata> {
        return pinRegistry.values.filter { it.hostname == hostname }
    }

    /**
     * Remove a pin from monitoring (when rotated out).
     */
    fun removePin(hostname: String, pin: String) {
        val key = "$hostname:$pin"
        pinRegistry.remove(key)
    }

    /**
     * Clear all pins (for testing).
     */
    fun clear() {
        pinRegistry.clear()
    }
}

/**
 * Metadata about a certificate pin.
 */
data class PinMetadata(
    val hostname: String,
    val pin: String,
    val addedDate: Instant,
    val expirationWarningDays: Int
)

/**
 * Warning about certificate pin health.
 */
data class PinWarning(
    val hostname: String,
    val severity: WarningSeverity,
    val message: String
)

/**
 * Severity levels for pin warnings.
 */
enum class WarningSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
