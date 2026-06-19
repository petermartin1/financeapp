package com.financeapp.security.vault

import kotlinx.serialization.Serializable
import java.util.Base64

@Serializable
data class SerializableBox(val nonceB64: String, val ctB64: String) {
    fun toGcmBox(): GcmBox = GcmBox(
        Base64.getDecoder().decode(nonceB64),
        Base64.getDecoder().decode(ctB64)
    )

    companion object {
        fun of(box: GcmBox): SerializableBox = SerializableBox(
            Base64.getEncoder().encodeToString(box.nonce),
            Base64.getEncoder().encodeToString(box.ciphertext)
        )
    }
}

@Serializable
data class WrappedDek(
    val password: SerializableBox,
    val recovery: SerializableBox?
)

@Serializable
data class VaultFile(
    val version: Int,
    val kdf: Argon2Params,
    val kdfSaltB64: String,
    val cipher: String = "AES-256-GCM",
    val wrappedDek: WrappedDek
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
