package com.financeapp.security.vault

import kotlin.test.*

class Argon2Test {

    private val cheap = Argon2Params(memoryKiB = 1024, iterations = 1, parallelism = 1)

    @Test
    fun `derives a 32-byte key`() {
        val salt = ByteArray(16) { 1 }
        val key = Argon2.deriveKey("correct horse".toCharArray(), salt, cheap)
        assertEquals(32, key.size)
    }

    @Test
    fun `same password and salt give the same key`() {
        val salt = ByteArray(16) { 7 }
        val a = Argon2.deriveKey("pw".toCharArray(), salt, cheap)
        val b = Argon2.deriveKey("pw".toCharArray(), salt, cheap)
        assertContentEquals(a, b)
    }

    @Test
    fun `different salt gives a different key`() {
        val a = Argon2.deriveKey("pw".toCharArray(), ByteArray(16) { 1 }, cheap)
        val b = Argon2.deriveKey("pw".toCharArray(), ByteArray(16) { 2 }, cheap)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `different password gives a different key`() {
        val salt = ByteArray(16) { 3 }
        val a = Argon2.deriveKey("pw1".toCharArray(), salt, cheap)
        val b = Argon2.deriveKey("pw2".toCharArray(), salt, cheap)
        assertFalse(a.contentEquals(b))
    }
}
