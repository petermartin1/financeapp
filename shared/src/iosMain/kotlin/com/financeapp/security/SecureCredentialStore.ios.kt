package com.financeapp.security

import platform.Foundation.*
import platform.Security.*
import kotlinx.cinterop.*

/**
 * iOS Keychain implementation for secure credential storage.
 */
actual class SecureCredentialStore actual constructor() {
    private val serviceName = "com.financeapp.credentials"

    actual fun store(key: String, value: String): Boolean {
        // Delete existing item first
        delete(key)

        val valueData = value.encodeToByteArray().toNSData()

        val query = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to key,
            kSecValueData to valueData
        )

        val status = SecItemAdd(query.toCFDictionary(), null)
        return status == errSecSuccess
    }

    actual fun retrieve(key: String): String? {
        val query = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to key,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne
        )

        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.toCFDictionary(), result.ptr)

            if (status == errSecSuccess) {
                val data = result.value as? NSData
                return data?.toByteArray()?.decodeToString()
            }
        }
        return null
    }

    actual fun delete(key: String): Boolean {
        val query = mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to key
        )

        val status = SecItemDelete(query.toCFDictionary())
        return status == errSecSuccess || status == errSecItemNotFound
    }

    private fun ByteArray.toNSData(): NSData {
        return this.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), this.size.toULong())
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        return ByteArray(this.length.toInt()).apply {
            usePinned { pinned ->
                memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
            }
        }
    }

    private fun Map<Any?, Any?>.toCFDictionary(): CFDictionaryRef? {
        return (this as NSDictionary) as CFDictionaryRef?
    }
}
