package com.financeapp.security.vault

import kotlin.test.*

class PasswordStrengthTest {

    @Test
    fun `rejects short passwords`() {
        assertFalse(PasswordStrength.evaluate("Ab1!xyz".toCharArray()).acceptable) // 7 chars
    }

    @Test
    fun `accepts a 12+ character mixed password`() {
        assertTrue(PasswordStrength.evaluate("Tr0ub4dour!xQ".toCharArray()).acceptable)
    }

    @Test
    fun `accepts a four-word passphrase even if each word is simple`() {
        assertTrue(PasswordStrength.evaluate("correct horse battery staple".toCharArray()).acceptable)
    }

    @Test
    fun `rejects a long but trivially repetitive password`() {
        assertFalse(PasswordStrength.evaluate("aaaaaaaaaaaaaa".toCharArray()).acceptable)
    }

    @Test
    fun `rejects a common password`() {
        assertFalse(PasswordStrength.evaluate("password1234".toCharArray()).acceptable)
    }
}
