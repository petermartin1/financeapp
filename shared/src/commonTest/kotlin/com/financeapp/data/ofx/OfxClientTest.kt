package com.financeapp.data.ofx

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfxClientTest {

    private val creds = BankCredentials(odataConnectionId = 1, userId = "user", password = "pass")

    @Test
    fun `transaction request XML-escapes account and FI fields`() {
        val config = BankConfig(
            name = "Test",
            ofxUrl = "https://example.com/ofx",
            fiOrg = "O&rg",
            fiId = "<fid>",
            routingNumber = "11&22"
        )

        val xml = OfxClient().buildTransactionRequest(
            config = config,
            credentials = creds,
            accountId = "123<inject>",
            accountType = OfxAccountType.CHECKING,
            startDate = LocalDate(2024, 1, 1),
            endDate = LocalDate(2024, 1, 31)
        )

        // The injected tag must not appear raw; only its escaped form.
        assertFalse(xml.contains("<inject>"), "accountId not escaped: $xml")
        assertTrue(xml.contains("123&lt;inject&gt;"), "accountId escaped form missing")
        assertTrue(xml.contains("11&amp;22"), "routingNumber not escaped")
        assertTrue(xml.contains("O&amp;rg"), "fiOrg not escaped")
        assertTrue(xml.contains("&lt;fid&gt;"), "fiId not escaped")
    }

    @Test
    fun `investment request XML-escapes the broker id`() {
        val config = BankConfig(
            name = "Test",
            ofxUrl = "https://example.com/ofx",
            fiOrg = "Org",
            fiId = "fid",
            brokerId = "brk&<id>"
        )

        val xml = OfxClient().buildTransactionRequest(
            config = config,
            credentials = creds,
            accountId = "acct",
            accountType = OfxAccountType.INVESTMENT,
            startDate = LocalDate(2024, 1, 1),
            endDate = LocalDate(2024, 1, 31)
        )

        assertFalse(xml.contains("brk&<id>"), "brokerId not escaped: $xml")
        assertTrue(xml.contains("brk&amp;&lt;id&gt;"), "brokerId escaped form missing")
    }
}
