package com.financeapp.data.ofx

import com.financeapp.security.CertificatePinMonitor

object BankConfigs {

    val ALLIANT_CREDIT_UNION = BankConfig(
        name = "Alliant Credit Union",
        ofxUrl = "https://www.alliantcreditunion.org/OFX/ofx.dll",
        fiOrg = "Alliant Credit Union",
        fiId = "11075",
        routingNumber = "271081528"
    )

    val FIDELITY_INVESTMENTS = BankConfig(
        name = "Fidelity Investments",
        ofxUrl = "https://ofx.fidelity.com/ftgw/OFX/clients/download",
        fiOrg = "fidelity.com",
        fiId = "7776",
        brokerId = "fidelity.com"
    )

    val ALL_BANKS = listOf(
        ALLIANT_CREDIT_UNION,
        FIDELITY_INVESTMENTS
    )

    init {
        // Validate certificate pins at startup
        ALL_BANKS.forEach { config ->
            val hostname = config.ofxUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
                .substringBefore(":")

            config.certificatePins.forEach { pin ->
                require(isValidCertificatePin(pin)) {
                    "Invalid certificate pin format for ${config.name}: '$pin'. " +
                    "Pins must be in format 'sha256/BASE64=='"
                }

                // Register pin for monitoring
                CertificatePinMonitor.registerPin(hostname, pin)
            }
        }
    }

    fun getByName(name: String): BankConfig? {
        return ALL_BANKS.find { it.name == name }
    }

    /**
     * Validates that a certificate pin is in the correct SHA-256 format.
     * Expected format: sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
     * - Prefix: "sha256/"
     * - 43 base64 characters (256 bits / 6 bits per char ≈ 43 chars)
     * - Padding: "="
     */
    fun isValidCertificatePin(pin: String): Boolean {
        // Check format: sha256/ followed by 43 base64 chars and one = padding
        val regex = Regex("^sha256/[A-Za-z0-9+/]{43}=$")
        return regex.matches(pin)
    }
}
