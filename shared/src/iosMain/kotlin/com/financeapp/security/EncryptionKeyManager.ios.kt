package com.financeapp.security

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

actual class EncryptionKeyManager {
    private val serviceName = "com.financeapp.dbkey"
    private val accountName = "encryption_key"

    actual fun getOrCreateKey(): String {
        return getKeyFromKeychain() ?: run {
            val newKey = generateKey()
            saveKeyToKeychain(newKey)
            newKey
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun getKeyFromKeychain(): String? {
        val query = CFDictionaryCreateMutable(null, 4, null, null).apply {
            CFDictionarySetValue(this, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(this, kSecAttrService, CFBridgingRetain(serviceName))
            CFDictionarySetValue(this, kSecAttrAccount, CFBridgingRetain(accountName))
            CFDictionarySetValue(this, kSecReturnData, kCFBooleanTrue)
        }

        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)

            return if (status == errSecSuccess) {
                val data = CFBridgingRelease(result.value) as? NSData
                data?.let {
                    NSString.create(it, NSUTF8StringEncoding)?.toString()
                }
            } else {
                null
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun saveKeyToKeychain(key: String) {
        val keyData = (key as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return

        val query = CFDictionaryCreateMutable(null, 4, null, null).apply {
            CFDictionarySetValue(this, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(this, kSecAttrService, CFBridgingRetain(serviceName))
            CFDictionarySetValue(this, kSecAttrAccount, CFBridgingRetain(accountName))
            CFDictionarySetValue(this, kSecValueData, CFBridgingRetain(keyData))
        }

        SecItemAdd(query, null)
    }

    private fun generateKey(): String {
        val bytes = NSMutableData.dataWithLength(32u) ?: return ""
        val result = SecRandomCopyBytes(kSecRandomDefault, 32u, bytes.mutableBytes)
        return if (result == errSecSuccess) {
            bytes.base64EncodedStringWithOptions(0u)
        } else {
            // Fallback to UUID-based key
            NSUUID().UUIDString + NSUUID().UUIDString
        }
    }
}
