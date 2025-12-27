package com.financeapp.data.ofx

import com.financeapp.security.RateLimiter
import com.financeapp.security.RateLimitException
import com.financeapp.security.SecurityAuditLogger
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class OfxClient {
    private val rateLimiter = RateLimiter()

    private fun getHttpClientForConfig(config: BankConfig): HttpClient {
        // HSTS enforcement - reject non-HTTPS URLs
        require(config.ofxUrl.startsWith("https://")) {
            "OFX URL must use HTTPS for security. Got: ${config.ofxUrl}"
        }

        if (config.certificatePins.isEmpty()) {
            // Non-pinned client with timeouts
            return HttpClient {
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000  // 60 seconds
                    connectTimeoutMillis = 10_000  // 10 seconds
                    socketTimeoutMillis = 30_000   // 30 seconds
                }
            }
        }

        // Extract hostname from URL for pinning
        val hostname = config.ofxUrl
            .removePrefix("https://")
            .substringBefore("/")
            .substringBefore(":")

        return createSecureHttpClient(mapOf(hostname to config.certificatePins))
    }

    suspend fun fetchTransactions(
        config: BankConfig,
        credentials: BankCredentials,
        accountId: String,
        accountType: OfxAccountType,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<String> {
        val rateLimitKey = "ofx_${config.fiId}_${credentials.userId}"

        SecurityAuditLogger.logConnectionAttempt(credentials.userId, config.name)

        // Check rate limit and wait if needed
        try {
            rateLimiter.checkAndWait(rateLimitKey)
        } catch (e: RateLimitException) {
            SecurityAuditLogger.logRateLimitExceeded(credentials.userId, config.name)
            return Result.failure(e)
        }

        val httpClient = getHttpClientForConfig(config)
        return try {
            val request = buildTransactionRequest(
                config, credentials, accountId, accountType, startDate, endDate
            )

            val response = httpClient.post(config.ofxUrl) {
                contentType(ContentType("application", "x-ofx"))
                setBody(request)
            }

            val body = response.bodyAsText()

            // Check for authentication failures in OFX response
            if (isAuthenticationError(body)) {
                rateLimiter.recordFailure(rateLimitKey)
                SecurityAuditLogger.logAuthFailure(credentials.userId, config.name, "Invalid credentials")
                Result.failure(Exception("Authentication failed"))
            } else {
                rateLimiter.recordSuccess(rateLimitKey)
                SecurityAuditLogger.logConnectionSuccess(credentials.userId, config.name)
                Result.success(body)
            }
        } catch (e: Exception) {
            rateLimiter.recordFailure(rateLimitKey)
            SecurityAuditLogger.logConnectionFailure(credentials.userId, config.name, e.message ?: "Unknown error")
            Result.failure(e)
        } finally {
            httpClient.close()
        }
    }

    suspend fun fetchAccounts(
        config: BankConfig,
        credentials: BankCredentials
    ): Result<String> {
        val rateLimitKey = "ofx_${config.fiId}_${credentials.userId}"

        SecurityAuditLogger.logConnectionAttempt(credentials.userId, config.name)

        // Check rate limit and wait if needed
        try {
            rateLimiter.checkAndWait(rateLimitKey)
        } catch (e: RateLimitException) {
            SecurityAuditLogger.logRateLimitExceeded(credentials.userId, config.name)
            return Result.failure(e)
        }

        val httpClient = getHttpClientForConfig(config)
        return try {
            val request = buildAccountListRequest(config, credentials)

            val response = httpClient.post(config.ofxUrl) {
                contentType(ContentType("application", "x-ofx"))
                setBody(request)
            }

            val body = response.bodyAsText()

            // Check for authentication failures in OFX response
            if (isAuthenticationError(body)) {
                rateLimiter.recordFailure(rateLimitKey)
                SecurityAuditLogger.logAuthFailure(credentials.userId, config.name, "Invalid credentials")
                Result.failure(Exception("Authentication failed"))
            } else {
                rateLimiter.recordSuccess(rateLimitKey)
                SecurityAuditLogger.logConnectionSuccess(credentials.userId, config.name)
                Result.success(body)
            }
        } catch (e: Exception) {
            rateLimiter.recordFailure(rateLimitKey)
            SecurityAuditLogger.logConnectionFailure(credentials.userId, config.name, e.message ?: "Unknown error")
            Result.failure(e)
        } finally {
            httpClient.close()
        }
    }

    private fun buildTransactionRequest(
        config: BankConfig,
        credentials: BankCredentials,
        accountId: String,
        accountType: OfxAccountType,
        startDate: LocalDate,
        endDate: LocalDate
    ): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val timestamp = formatOfxDateTime(now)
        val transactionId = generateTransactionId()

        return buildString {
            append(ofxHeader())
            append("<OFX>")
            append(signonRequest(config, credentials, timestamp))

            when (accountType) {
                OfxAccountType.CHECKING, OfxAccountType.SAVINGS -> {
                    append("<BANKMSGSRQV1>")
                    append("<STMTTRNRQ>")
                    append("<TRNUID>$transactionId</TRNUID>")
                    append("<STMTRQ>")
                    append("<BANKACCTFROM>")
                    append("<BANKID>${config.routingNumber}</BANKID>")
                    append("<ACCTID>$accountId</ACCTID>")
                    append("<ACCTTYPE>${accountType.ofxValue}</ACCTTYPE>")
                    append("</BANKACCTFROM>")
                    append("<INCTRAN>")
                    append("<DTSTART>${formatOfxDate(startDate)}</DTSTART>")
                    append("<DTEND>${formatOfxDate(endDate)}</DTEND>")
                    append("<INCLUDE>Y</INCLUDE>")
                    append("</INCTRAN>")
                    append("</STMTRQ>")
                    append("</STMTTRNRQ>")
                    append("</BANKMSGSRQV1>")
                }
                OfxAccountType.CREDIT_CARD -> {
                    append("<CREDITCARDMSGSRQV1>")
                    append("<CCSTMTTRNRQ>")
                    append("<TRNUID>$transactionId</TRNUID>")
                    append("<CCSTMTRQ>")
                    append("<CCACCTFROM>")
                    append("<ACCTID>$accountId</ACCTID>")
                    append("</CCACCTFROM>")
                    append("<INCTRAN>")
                    append("<DTSTART>${formatOfxDate(startDate)}</DTSTART>")
                    append("<DTEND>${formatOfxDate(endDate)}</DTEND>")
                    append("<INCLUDE>Y</INCLUDE>")
                    append("</INCTRAN>")
                    append("</CCSTMTRQ>")
                    append("</CCSTMTTRNRQ>")
                    append("</CREDITCARDMSGSRQV1>")
                }
                OfxAccountType.INVESTMENT -> {
                    append("<INVSTMTMSGSRQV1>")
                    append("<INVSTMTTRNRQ>")
                    append("<TRNUID>$transactionId</TRNUID>")
                    append("<INVSTMTRQ>")
                    append("<INVACCTFROM>")
                    append("<BROKERID>${config.brokerId ?: ""}</BROKERID>")
                    append("<ACCTID>$accountId</ACCTID>")
                    append("</INVACCTFROM>")
                    append("<INCTRAN>")
                    append("<DTSTART>${formatOfxDate(startDate)}</DTSTART>")
                    append("<DTEND>${formatOfxDate(endDate)}</DTEND>")
                    append("<INCLUDE>Y</INCLUDE>")
                    append("</INCTRAN>")
                    append("<INCOO>Y</INCOO>")
                    append("<INCPOS>")
                    append("<INCLUDE>Y</INCLUDE>")
                    append("</INCPOS>")
                    append("<INCBAL>Y</INCBAL>")
                    append("</INVSTMTRQ>")
                    append("</INVSTMTTRNRQ>")
                    append("</INVSTMTMSGSRQV1>")
                }
            }

            append("</OFX>")
        }
    }

    private fun buildAccountListRequest(
        config: BankConfig,
        credentials: BankCredentials
    ): String {
        val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        val timestamp = formatOfxDateTime(now)
        val transactionId = generateTransactionId()

        return buildString {
            append(ofxHeader())
            append("<OFX>")
            append(signonRequest(config, credentials, timestamp))
            append("<SIGNUPMSGSRQV1>")
            append("<ACCTINFOTRNRQ>")
            append("<TRNUID>$transactionId</TRNUID>")
            append("<ACCTINFORQ>")
            append("<DTACCTUP>19700101</DTACCTUP>")
            append("</ACCTINFORQ>")
            append("</ACCTINFOTRNRQ>")
            append("</SIGNUPMSGSRQV1>")
            append("</OFX>")
        }
    }

    private fun ofxHeader(): String {
        return """OFXHEADER:100
DATA:OFXSGML
VERSION:102
SECURITY:NONE
ENCODING:USASCII
CHARSET:1252
COMPRESSION:NONE
OLDFILEUID:NONE
NEWFILEUID:NONE

"""
    }

    private fun signonRequest(
        config: BankConfig,
        credentials: BankCredentials,
        timestamp: String
    ): String {
        return buildString {
            append("<SIGNONMSGSRQV1>")
            append("<SONRQ>")
            append("<DTCLIENT>$timestamp</DTCLIENT>")
            append("<USERID>${credentials.userId}</USERID>")
            append("<USERPASS>${credentials.password}</USERPASS>")
            append("<LANGUAGE>ENG</LANGUAGE>")
            append("<FI>")
            append("<ORG>${config.fiOrg}</ORG>")
            append("<FID>${config.fiId}</FID>")
            append("</FI>")
            append("<APPID>QWIN</APPID>")
            append("<APPVER>2700</APPVER>")
            append("</SONRQ>")
            append("</SIGNONMSGSRQV1>")
        }
    }

    private fun formatOfxDate(date: LocalDate): String {
        return "${date.year}${date.monthNumber.toString().padStart(2, '0')}${date.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun formatOfxDateTime(dt: kotlinx.datetime.LocalDateTime): String {
        return "${dt.year}${dt.monthNumber.toString().padStart(2, '0')}${dt.dayOfMonth.toString().padStart(2, '0')}${dt.hour.toString().padStart(2, '0')}${dt.minute.toString().padStart(2, '0')}${dt.second.toString().padStart(2, '0')}"
    }

    private fun generateTransactionId(): String {
        // Use timestamp + random component for better uniqueness and security
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val random = kotlin.random.Random.nextInt(100000, 999999)
        return "${timestamp}_$random"
    }

    /**
     * Check if OFX response contains an authentication error.
     * Common auth error codes: 2000, 15500, 15000, 2002
     */
    private fun isAuthenticationError(ofxResponse: String): Boolean {
        if (!ofxResponse.contains("<CODE>")) return false

        // Extract error code
        val codeMatch = Regex("<CODE>(\\d+)</CODE>").find(ofxResponse)
        val code = codeMatch?.groupValues?.get(1) ?: return false

        // Authentication-related error codes
        return code in setOf(
            "2000",  // General authentication error
            "2002",  // Account locked
            "15500", // Signon invalid
            "15000"  // Must change password
        )
    }
}

data class BankConfig(
    val name: String,
    val ofxUrl: String,
    val fiOrg: String,
    val fiId: String,
    val routingNumber: String? = null,
    val brokerId: String? = null,
    val certificatePins: List<String> = emptyList() // SHA-256 certificate pins for pinning
)

data class BankCredentials(
    val odataConnectionId: Long,
    val userId: String,
    val password: String
)

enum class OfxAccountType(val ofxValue: String) {
    CHECKING("CHECKING"),
    SAVINGS("SAVINGS"),
    CREDIT_CARD("CREDITCARD"),
    INVESTMENT("INVESTMENT")
}
