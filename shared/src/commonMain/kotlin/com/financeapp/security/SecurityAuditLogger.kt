package com.financeapp.security

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Security audit logging framework for tracking sensitive operations.
 *
 * IMPORTANT: This logger MUST NEVER log passwords, credentials, or other sensitive data.
 * All events are sanitized to remove sensitive information before logging.
 *
 * Events are logged to console and kept in memory (up to 1000 events).
 * Note: Events are NOT persisted to disk and will be lost on app restart.
 */
object SecurityAuditLogger {
    private val events = mutableListOf<SecurityEvent>()
    private const val MAX_EVENTS_IN_MEMORY = 1000

    /**
     * Log a security event.
     *
     * @param event The security event to log
     */
    fun log(event: SecurityEvent) {
        // Add timestamp if not set
        val timestampedEvent = if (event.timestamp == null) {
            event.copy(timestamp = Clock.System.now())
        } else {
            event
        }

        // Store in memory (limited size)
        synchronized(events) {
            events.add(timestampedEvent)
            if (events.size > MAX_EVENTS_IN_MEMORY) {
                events.removeAt(0)
            }
        }

        // Log to console in dev mode
        logToConsole(timestampedEvent)

        // TODO: In production, also write to persistent storage
        // persistToFile(timestampedEvent)
    }

    /**
     * Log authentication success.
     */
    fun logAuthSuccess(userId: String, bankName: String) {
        log(SecurityEvent(
            type = EventType.AUTH_SUCCESS,
            category = EventCategory.AUTHENTICATION,
            severity = Severity.INFO,
            message = "Authentication succeeded",
            userId = sanitizeUserId(userId),
            bankName = bankName
        ))
    }

    /**
     * Log authentication failure.
     */
    fun logAuthFailure(userId: String, bankName: String, reason: String) {
        log(SecurityEvent(
            type = EventType.AUTH_FAILURE,
            category = EventCategory.AUTHENTICATION,
            severity = Severity.WARNING,
            message = "Authentication failed: $reason",
            userId = sanitizeUserId(userId),
            bankName = bankName
        ))
    }

    /**
     * Log rate limit exceeded.
     */
    fun logRateLimitExceeded(userId: String, bankName: String) {
        log(SecurityEvent(
            type = EventType.RATE_LIMIT_EXCEEDED,
            category = EventCategory.SECURITY,
            severity = Severity.WARNING,
            message = "Rate limit exceeded",
            userId = sanitizeUserId(userId),
            bankName = bankName
        ))
    }

    /**
     * Log connection attempt.
     */
    fun logConnectionAttempt(userId: String, bankName: String) {
        log(SecurityEvent(
            type = EventType.CONNECTION_ATTEMPT,
            category = EventCategory.CONNECTION,
            severity = Severity.INFO,
            message = "Connection attempt",
            userId = sanitizeUserId(userId),
            bankName = bankName
        ))
    }

    /**
     * Log connection success.
     */
    fun logConnectionSuccess(userId: String, bankName: String) {
        log(SecurityEvent(
            type = EventType.CONNECTION_SUCCESS,
            category = EventCategory.CONNECTION,
            severity = Severity.INFO,
            message = "Connection succeeded",
            userId = sanitizeUserId(userId),
            bankName = bankName
        ))
    }

    /**
     * Log connection failure.
     */
    fun logConnectionFailure(userId: String, bankName: String, error: String) {
        log(SecurityEvent(
            type = EventType.CONNECTION_FAILURE,
            category = EventCategory.CONNECTION,
            severity = Severity.WARNING,
            message = "Connection failed: ${sanitizeErrorMessage(error)}",
            userId = sanitizeUserId(userId),
            bankName = bankName
        ))
    }

    /**
     * Log credential storage event.
     */
    fun logCredentialStored(key: String) {
        log(SecurityEvent(
            type = EventType.CREDENTIAL_STORED,
            category = EventCategory.CREDENTIAL_MANAGEMENT,
            severity = Severity.INFO,
            message = "Credential stored: ${sanitizeKey(key)}"
        ))
    }

    /**
     * Log credential retrieval.
     */
    fun logCredentialRetrieved(key: String) {
        log(SecurityEvent(
            type = EventType.CREDENTIAL_RETRIEVED,
            category = EventCategory.CREDENTIAL_MANAGEMENT,
            severity = Severity.INFO,
            message = "Credential retrieved: ${sanitizeKey(key)}"
        ))
    }

    /**
     * Log credential deletion.
     */
    fun logCredentialDeleted(key: String) {
        log(SecurityEvent(
            type = EventType.CREDENTIAL_DELETED,
            category = EventCategory.CREDENTIAL_MANAGEMENT,
            severity = Severity.INFO,
            message = "Credential deleted: ${sanitizeKey(key)}"
        ))
    }

    /**
     * Log certificate validation failure.
     */
    fun logCertificateValidationFailure(hostname: String, reason: String) {
        log(SecurityEvent(
            type = EventType.CERT_VALIDATION_FAILURE,
            category = EventCategory.SECURITY,
            severity = Severity.ERROR,
            message = "Certificate validation failed for $hostname: $reason"
        ))
    }

    /**
     * Get recent events (for debugging or UI display).
     *
     * @param count Number of recent events to return
     * @return List of recent security events
     */
    fun getRecentEvents(count: Int = 100): List<SecurityEvent> {
        return synchronized(events) {
            events.takeLast(count)
        }
    }

    /**
     * Get events by category.
     */
    fun getEventsByCategory(category: EventCategory): List<SecurityEvent> {
        return synchronized(events) {
            events.filter { it.category == category }
        }
    }

    /**
     * Get events by severity.
     */
    fun getEventsBySeverity(severity: Severity): List<SecurityEvent> {
        return synchronized(events) {
            events.filter { it.severity == severity }
        }
    }

    /**
     * Clear all events (use with caution - for testing/dev only).
     */
    fun clear() {
        synchronized(events) {
            events.clear()
        }
    }

    // === PRIVATE HELPERS ===

    private fun logToConsole(event: SecurityEvent) {
        val level = when (event.severity) {
            Severity.INFO -> "INFO"
            Severity.WARNING -> "WARN"
            Severity.ERROR -> "ERROR"
            Severity.CRITICAL -> "CRITICAL"
        }
        println("[$level] [${event.category}] ${event.message}")
    }

    /**
     * Sanitize user ID to prevent logging full credentials.
     * Shows first 3 chars and masks the rest.
     */
    private fun sanitizeUserId(userId: String): String {
        if (userId.length <= 3) return "***"
        return "${userId.take(3)}***"
    }

    /**
     * Sanitize key to prevent logging sensitive information.
     */
    private fun sanitizeKey(key: String): String {
        // Remove any credential values, just show the type
        return key.replace(Regex("_\\d+$"), "_XXX")
    }

    /**
     * Sanitize error messages to remove sensitive data.
     */
    private fun sanitizeErrorMessage(error: String): String {
        // Remove any passwords or credentials from error messages
        var sanitized = error
        // Remove anything that looks like a password parameter
        sanitized = sanitized.replace(Regex("password=\\S+"), "password=***")
        sanitized = sanitized.replace(Regex("token=\\S+"), "token=***")
        sanitized = sanitized.replace(Regex("key=\\S+"), "key=***")
        return sanitized
    }
}

/**
 * Security event data class.
 */
data class SecurityEvent(
    val type: EventType,
    val category: EventCategory,
    val severity: Severity,
    val message: String,
    val userId: String? = null,
    val bankName: String? = null,
    val timestamp: Instant? = null
)

/**
 * Event types for different security operations.
 */
enum class EventType {
    AUTH_SUCCESS,
    AUTH_FAILURE,
    CONNECTION_ATTEMPT,
    CONNECTION_SUCCESS,
    CONNECTION_FAILURE,
    CREDENTIAL_STORED,
    CREDENTIAL_RETRIEVED,
    CREDENTIAL_DELETED,
    CERT_VALIDATION_FAILURE,
    RATE_LIMIT_EXCEEDED
}

/**
 * Event categories for organizing security events.
 */
enum class EventCategory {
    AUTHENTICATION,
    CONNECTION,
    CREDENTIAL_MANAGEMENT,
    SECURITY
}

/**
 * Severity levels for security events.
 */
enum class Severity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}
