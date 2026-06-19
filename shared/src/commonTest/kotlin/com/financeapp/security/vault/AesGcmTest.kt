package com.financeapp.security.vault

import javax.crypto.AEADBadTagException
import java.security.SecureRandom
import kotlin.test.*

class AesGcmTest {

    private fun key(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Test
    fun `encrypt then decrypt round-trips the plaintext`() {
        val k = key()
        val plaintext = "the-data-encryption-key".encodeToByteArray()

        val box = AesGcm.encrypt(k, plaintext)
        val out = AesGcm.decrypt(k, box)

        assertContentEquals(plaintext, out)
    }

    @Test
    fun `each encryption uses a fresh random nonce`() {
        val k = key()
        val a = AesGcm.encrypt(k, "x".encodeToByteArray())
        val b = AesGcm.encrypt(k, "x".encodeToByteArray())
        assertFalse(a.nonce.contentEquals(b.nonce), "nonces must differ")
        assertFalse(a.ciphertext.contentEquals(b.ciphertext), "ciphertexts must differ")
    }

    @Test
    fun `decrypt with the wrong key fails the auth tag`() {
        val box = AesGcm.encrypt(key(), "secret".encodeToByteArray())
        assertFailsWith<AEADBadTagException> {
            AesGcm.decrypt(key(), box)
        }
    }

    @Test
    fun `tampering with the ciphertext fails the auth tag`() {
        val k = key()
        val box = AesGcm.encrypt(k, "secret".encodeToByteArray())
        val tampered = box.copy(ciphertext = box.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() })
        assertFailsWith<AEADBadTagException> {
            AesGcm.decrypt(k, tampered)
        }
    }
}
