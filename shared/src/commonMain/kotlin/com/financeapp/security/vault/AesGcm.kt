package com.financeapp.security.vault

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM authenticated encryption. Wrong key or tampering throws AEADBadTagException on decrypt. */
object AesGcm {
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private val rng = SecureRandom()

    fun encrypt(key: ByteArray, plaintext: ByteArray): GcmBox {
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
        val nonce = ByteArray(NONCE_BYTES).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return GcmBox(nonce, cipher.doFinal(plaintext))
    }

    fun decrypt(key: ByteArray, box: GcmBox): ByteArray {
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, box.nonce))
        return cipher.doFinal(box.ciphertext)
    }
}
