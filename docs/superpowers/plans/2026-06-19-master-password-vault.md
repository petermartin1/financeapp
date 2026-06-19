# Master-Password Vault Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Seal the H2 database under a key derived from the user's master password (Argon2id envelope encryption), so the database is cryptographically unrecoverable without that password or a recovery key — replacing the OS-keystore-backed random DB key.

**Architecture:** A random Data Encryption Key (DEK) string is the H2 file secret (unchanged H2 format). The DEK is wrapped with AES-256-GCM under a key derived from the master password via Argon2id, plus an optional second wrap under a high-entropy recovery key. A new `KeyVault` holds the unlocked DEK in memory; the database is opened only after unlock. Existing installs migrate by adopting the current keystore key as the DEK (no DB re-encryption).

**Tech Stack:** Kotlin (desktop JVM), Bouncy Castle (Argon2id), JDK `javax.crypto` (AES-GCM, `SecureRandom`), kotlinx.serialization (vault JSON), Exposed/H2, Koin, Compose.

**Spec:** `docs/superpowers/specs/2026-06-19-master-password-vault-design.md`

**Phasing:** Phase 1 (Tasks 1–7) builds and fully unit-tests the crypto + `KeyVault` engine with zero behavior change. Phase 2 (Tasks 8–11) rewires bootstrap and migrates existing installs. Phase 3 (Tasks 12–16) adds the UI and hardenings. Each phase ends green and committable.

**Key invariant — the DEK is a String:** the DEK is the exact secret string the existing `DatabaseDriverFactory(encryptionKey: String)` consumes (it internally stretches it with PBKDF2 + `finance.config` salt into the H2 password). New installs use `Base64(32 random bytes)`; migrated installs reuse the legacy keystore key string verbatim. The wrapped payload is `dek.toByteArray(UTF_8)`. **Do not change `DatabaseDriverFactory`'s internal stretching** — that is what keeps existing databases readable.

---

## Phase 1 — Crypto & vault engine

### Task 1: Add the Bouncy Castle dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts:25-32` (commonMain dependencies block)

- [ ] **Step 1: Add the version and library to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:

```toml
bouncycastle = "1.78.1"
```

Under `[libraries]` add (near the other security-relevant libs, e.g. after the Koin block):

```toml
# Crypto
bouncycastle = { module = "org.bouncycastle:bcprov-jdk18on", version.ref = "bouncycastle" }
```

- [ ] **Step 2: Add the dependency to shared commonMain**

In `shared/build.gradle.kts`, inside `val commonMain by getting { dependencies { ... } }`, after the Koin entries add:

```kotlin
                // Argon2id key derivation
                implementation(libs.bouncycastle)
```

- [ ] **Step 3: Verify it resolves**

Run: `./gradlew :shared:dependencies --configuration desktopCompileClasspath 2>&1 | grep -i bouncycastle`
Expected: a line showing `org.bouncycastle:bcprov-jdk18on:1.78.1`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "build: add Bouncy Castle for Argon2id key derivation"
```

---

### Task 2: AES-256-GCM box (encrypt/decrypt with authentication)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/security/vault/GcmBox.kt`
- Create: `shared/src/commonMain/kotlin/com/financeapp/security/vault/AesGcm.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/security/vault/AesGcmTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.AesGcmTest"`
Expected: FAIL — `AesGcm` / `GcmBox` unresolved.

- [ ] **Step 3: Write minimal implementation**

`GcmBox.kt`:

```kotlin
package com.financeapp.security.vault

/** An AES-GCM ciphertext together with the random nonce it was produced with. */
data class GcmBox(
    val nonce: ByteArray,
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GcmBox) return false
        return nonce.contentEquals(other.nonce) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int = 31 * nonce.contentHashCode() + ciphertext.contentHashCode()
}
```

`AesGcm.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.AesGcmTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/security/vault/GcmBox.kt \
        shared/src/commonMain/kotlin/com/financeapp/security/vault/AesGcm.kt \
        shared/src/commonTest/kotlin/com/financeapp/security/vault/AesGcmTest.kt
git commit -m "feat: AES-256-GCM authenticated encryption helper for the vault"
```

---

### Task 3: Argon2id key derivation

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/security/vault/Argon2Params.kt`
- Create: `shared/src/commonMain/kotlin/com/financeapp/security/vault/Argon2.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/security/vault/Argon2Test.kt`

- [ ] **Step 1: Write the failing test**

Note: use deliberately cheap params in tests (`memoryKiB = 1024, iterations = 1`) so the suite stays fast; production defaults are exercised by Task 7.

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.Argon2Test"`
Expected: FAIL — `Argon2` / `Argon2Params` unresolved.

- [ ] **Step 3: Write minimal implementation**

`Argon2Params.kt`:

```kotlin
package com.financeapp.security.vault

import kotlinx.serialization.Serializable

@Serializable
data class Argon2Params(
    val memoryKiB: Int = 65536,   // 64 MiB
    val iterations: Int = 3,
    val parallelism: Int = 4
) {
    companion object {
        val DEFAULT = Argon2Params()
    }
}
```

`Argon2.kt`:

```kotlin
package com.financeapp.security.vault

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** Argon2id password-based key derivation (Bouncy Castle). */
object Argon2 {
    private const val KEY_BYTES = 32

    fun deriveKey(password: CharArray, salt: ByteArray, params: Argon2Params): ByteArray {
        val passwordBytes = charsToUtf8(password)
        try {
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(params.iterations)
                .withMemoryAsKB(params.memoryKiB)
                .withParallelism(params.parallelism)
                .withSalt(salt)
                .build()
            val generator = Argon2BytesGenerator().apply { init(parameters) }
            val out = ByteArray(KEY_BYTES)
            generator.generateBytes(passwordBytes, out)
            return out
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun charsToUtf8(chars: CharArray): ByteArray =
        String(chars).encodeToByteArray()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.Argon2Test"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/security/vault/Argon2Params.kt \
        shared/src/commonMain/kotlin/com/financeapp/security/vault/Argon2.kt \
        shared/src/commonTest/kotlin/com/financeapp/security/vault/Argon2Test.kt
git commit -m "feat: Argon2id key derivation for the vault"
```

---

### Task 4: Recovery-key generation & Crockford Base32 codec

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/security/vault/RecoveryKey.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/security/vault/RecoveryKeyTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.financeapp.security.vault

import kotlin.test.*

class RecoveryKeyTest {

    @Test
    fun `generate produces 32 bytes and a grouped display string`() {
        val rk = RecoveryKey.generate()
        assertEquals(32, rk.bytes.size)
        // Crockford Base32 of 32 bytes = 52 symbols, shown in groups of 4 separated by '-'.
        assertEquals(52 + (52 / 4 - 1), rk.display.length)
        assertTrue(rk.display.all { it == '-' || it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" })
    }

    @Test
    fun `display round-trips back to the same bytes`() {
        val rk = RecoveryKey.generate()
        val decoded = RecoveryKey.decode(rk.display)
        assertNotNull(decoded)
        assertContentEquals(rk.bytes, decoded)
    }

    @Test
    fun `decode is tolerant of spaces, lowercase, and Crockford confusables`() {
        val rk = RecoveryKey.generate()
        val messy = rk.display.lowercase().replace("-", " ")
        assertContentEquals(rk.bytes, RecoveryKey.decode(messy))
        // Crockford: I/L map to 1, O maps to 0.
        val confusable = rk.display.replace("1", "I").replace("0", "O")
        assertContentEquals(rk.bytes, RecoveryKey.decode(confusable))
    }

    @Test
    fun `decode returns null for the wrong length`() {
        assertNull(RecoveryKey.decode("ABCD-EFGH"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.RecoveryKeyTest"`
Expected: FAIL — `RecoveryKey` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.financeapp.security.vault

import java.security.SecureRandom

/** A 256-bit recovery secret and its human-readable Crockford-Base32 form. */
class RecoveryKey private constructor(val bytes: ByteArray, val display: String) {

    companion object {
        private const val KEY_BYTES = 32
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford, no I L O U
        private const val GROUP = 4

        fun generate(): RecoveryKey {
            val bytes = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
            return RecoveryKey(bytes, group(encode(bytes)))
        }

        /** Decode user-entered text (tolerant of case, spaces, dashes, and I/L/O confusables). */
        fun decode(input: String): ByteArray? {
            val cleaned = input.uppercase()
                .replace('I', '1').replace('L', '1').replace('O', '0')
                .filter { it in ALPHABET }
            if (cleaned.length != encodedLength(KEY_BYTES)) return null
            val out = ByteArray(KEY_BYTES)
            var buffer = 0L
            var bits = 0
            var idx = 0
            for (c in cleaned) {
                buffer = (buffer shl 5) or ALPHABET.indexOf(c).toLong()
                bits += 5
                if (bits >= 8) {
                    bits -= 8
                    out[idx++] = ((buffer shr bits) and 0xFF).toByte()
                }
            }
            return if (idx == KEY_BYTES) out else null
        }

        private fun encodedLength(byteCount: Int): Int = (byteCount * 8 + 4) / 5

        private fun encode(bytes: ByteArray): String {
            val sb = StringBuilder()
            var buffer = 0L
            var bits = 0
            for (b in bytes) {
                buffer = (buffer shl 8) or (b.toLong() and 0xFF)
                bits += 8
                while (bits >= 5) {
                    bits -= 5
                    sb.append(ALPHABET[((buffer shr bits) and 0x1F).toInt()])
                }
            }
            if (bits > 0) sb.append(ALPHABET[((buffer shl (5 - bits)) and 0x1F).toInt()])
            return sb.toString()
        }

        private fun group(s: String): String =
            s.chunked(GROUP).joinToString("-")
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.RecoveryKeyTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/security/vault/RecoveryKey.kt \
        shared/src/commonTest/kotlin/com/financeapp/security/vault/RecoveryKeyTest.kt
git commit -m "feat: recovery key generation with Crockford Base32 codec"
```

---

### Task 5: Vault file model & store interface

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/security/vault/VaultFile.kt`
- Create: `shared/src/commonMain/kotlin/com/financeapp/security/vault/VaultStore.kt`
- Create: `shared/src/commonTest/kotlin/com/financeapp/security/vault/InMemoryVaultStore.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/security/vault/VaultFileTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.financeapp.security.vault

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class VaultFileTest {

    @Test
    fun `serializes and deserializes round-trip`() {
        val vault = VaultFile(
            version = 1,
            kdf = Argon2Params.DEFAULT,
            kdfSaltB64 = "c2FsdA==",
            wrappedDek = WrappedDek(
                password = SerializableBox("bm9uY2U=", "Y3Q="),
                recovery = SerializableBox("bm9uY2Uy", "Y3Qy")
            )
        )
        val json = Json.encodeToString(VaultFile.serializer(), vault)
        val back = Json.decodeFromString(VaultFile.serializer(), json)
        assertEquals(vault, back)
    }

    @Test
    fun `store round-trips a vault and reports existence`() = runTest {
        val store = InMemoryVaultStore()
        assertNull(store.read())
        val vault = VaultFile(
            version = 1,
            kdf = Argon2Params.DEFAULT,
            kdfSaltB64 = "c2FsdA==",
            wrappedDek = WrappedDek(SerializableBox("bm9uY2U=", "Y3Q="), recovery = null)
        )
        store.write(vault)
        assertEquals(vault, store.read())
        store.deleteVault()
        assertNull(store.read())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.VaultFileTest"`
Expected: FAIL — `VaultFile` / `WrappedDek` / `SerializableBox` / `VaultStore` / `InMemoryVaultStore` unresolved.

- [ ] **Step 3: Write minimal implementation**

`VaultFile.kt`:

```kotlin
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
```

`VaultStore.kt`:

```kotlin
package com.financeapp.security.vault

/** Persists the (entirely encrypted) vault file. */
interface VaultStore {
    suspend fun read(): VaultFile?
    suspend fun write(vault: VaultFile)
    suspend fun deleteVault()
}
```

`InMemoryVaultStore.kt` (test helper):

```kotlin
package com.financeapp.security.vault

class InMemoryVaultStore(private var current: VaultFile? = null) : VaultStore {
    override suspend fun read(): VaultFile? = current
    override suspend fun write(vault: VaultFile) { current = vault }
    override suspend fun deleteVault() { current = null }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.VaultFileTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/security/vault/VaultFile.kt \
        shared/src/commonMain/kotlin/com/financeapp/security/vault/VaultStore.kt \
        shared/src/commonTest/kotlin/com/financeapp/security/vault/InMemoryVaultStore.kt \
        shared/src/commonTest/kotlin/com/financeapp/security/vault/VaultFileTest.kt
git commit -m "feat: vault file model and store interface"
```

---

### Task 6: Password strength policy

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/security/vault/PasswordStrength.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/security/vault/PasswordStrengthTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.PasswordStrengthTest"`
Expected: FAIL — `PasswordStrength` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.financeapp.security.vault

/**
 * Lightweight password policy (not full zxcvbn): a password is acceptable if it is either
 * a passphrase of >= 4 words (each >= 3 chars) or >= 12 characters with some variety, and
 * is neither trivially repetitive nor a well-known common password.
 */
object PasswordStrength {

    data class Result(val acceptable: Boolean, val reason: String?)

    private val COMMON = setOf(
        "password", "password1234", "123456789012", "qwertyuiop12", "letmein12345"
    )

    fun evaluate(password: CharArray): Result {
        val pw = String(password)
        val lower = pw.lowercase()

        if (COMMON.contains(lower)) return Result(false, "This is a commonly used password.")
        if (isRepetitive(pw)) return Result(false, "Too repetitive.")

        val words = pw.trim().split(Regex("\\s+")).filter { it.length >= 3 }
        if (words.size >= 4) return Result(true, null)

        if (pw.length < 12) return Result(false, "Use at least 12 characters or a 4-word passphrase.")

        val variety = listOf(
            pw.any { it.isLowerCase() },
            pw.any { it.isUpperCase() },
            pw.any { it.isDigit() },
            pw.any { !it.isLetterOrDigit() }
        ).count { it }
        if (variety < 2) return Result(false, "Mix letters with numbers or symbols.")

        return Result(true, null)
    }

    private fun isRepetitive(pw: String): Boolean {
        if (pw.isEmpty()) return true
        return pw.toSet().size <= 2
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.PasswordStrengthTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/security/vault/PasswordStrength.kt \
        shared/src/commonTest/kotlin/com/financeapp/security/vault/PasswordStrengthTest.kt
git commit -m "feat: master-password strength policy"
```

---

### Task 7: KeyVault — setUp / unlock / recovery / changePassword

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/security/vault/KeyVault.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/security/vault/KeyVaultTest.kt`

This is the heart of Phase 1. The DEK is a String (see the plan's key invariant). `setUp`/migration generate or accept the DEK string; it is wrapped as UTF-8 bytes.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.financeapp.security.vault

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class KeyVaultTest {

    // Cheap KDF so the suite stays fast.
    private val cheap = Argon2Params(memoryKiB = 1024, iterations = 1, parallelism = 1)
    private fun vault(store: VaultStore = InMemoryVaultStore()) = KeyVault(store, cheap)

    @Test
    fun `status is NoVault before setup, Unlocked after`() = runTest {
        val kv = vault()
        assertEquals(KeyVault.Status.NoVault, kv.status())
        kv.setUp("correct horse battery staple".toCharArray())
        assertEquals(KeyVault.Status.Unlocked, kv.status())
        assertNotNull(kv.currentDek())
    }

    @Test
    fun `unlock with the correct password returns the same DEK`() = runTest {
        val store = InMemoryVaultStore()
        val dek = vault(store).setUp("correct horse battery staple".toCharArray()).dek

        val kv2 = vault(store)
        assertEquals(KeyVault.Status.Locked, kv2.status())
        assertEquals(dek, kv2.unlock("correct horse battery staple".toCharArray()))
        assertEquals(KeyVault.Status.Unlocked, kv2.status())
    }

    @Test
    fun `unlock with a wrong password returns null and stays locked`() = runTest {
        val store = InMemoryVaultStore()
        vault(store).setUp("correct horse battery staple".toCharArray())

        val kv2 = vault(store)
        assertNull(kv2.unlock("wrong password here".toCharArray()))
        assertEquals(KeyVault.Status.Locked, kv2.status())
        assertNull(kv2.currentDek())
    }

    @Test
    fun `recovery key unlocks and lets the password be reset`() = runTest {
        val store = InMemoryVaultStore()
        val setup = vault(store).setUp("correct horse battery staple".toCharArray())
        val recovery = assertNotNull(setup.recoveryKey)

        val kv2 = vault(store)
        assertEquals(setup.dek, kv2.unlockWithRecovery(recovery.display))

        assertTrue(kv2.resetPasswordWithRecovery(recovery.display, "a brand new passphrase here".toCharArray()))

        val kv3 = vault(store)
        assertNull(kv3.unlock("correct horse battery staple".toCharArray()))
        assertEquals(setup.dek, kv3.unlock("a brand new passphrase here".toCharArray()))
    }

    @Test
    fun `changePassword keeps the same DEK and rejects the old password`() = runTest {
        val store = InMemoryVaultStore()
        val dek = vault(store).setUp("correct horse battery staple".toCharArray()).dek

        val kv2 = vault(store)
        kv2.unlock("correct horse battery staple".toCharArray())
        assertTrue(kv2.changePassword("correct horse battery staple".toCharArray(), "another good passphrase x".toCharArray()))

        val kv3 = vault(store)
        assertNull(kv3.unlock("correct horse battery staple".toCharArray()))
        assertEquals(dek, kv3.unlock("another good passphrase x".toCharArray()))
    }

    @Test
    fun `adopting an existing key uses it verbatim as the DEK`() = runTest {
        val store = InMemoryVaultStore()
        val legacyKey = "kZ3legacyBase64KeyString=="

        val kv = vault(store)
        val result = kv.adoptExistingKeyAsDek(legacyKey, "correct horse battery staple".toCharArray())
        assertEquals(legacyKey, result.dek)

        val kv2 = vault(store)
        assertEquals(legacyKey, kv2.unlock("correct horse battery staple".toCharArray()))
    }

    @Test
    fun `a corrupted vault version fails closed on status`() = runTest {
        val store = InMemoryVaultStore()
        vault(store).setUp("correct horse battery staple".toCharArray())
        val bumped = store.read()!!.copy(version = 999)
        store.write(bumped)

        assertFailsWith<VaultException> { vault(store).status() }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.KeyVaultTest"`
Expected: FAIL — `KeyVault` / `VaultException` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.financeapp.security.vault

import javax.crypto.AEADBadTagException
import java.security.SecureRandom
import java.util.Base64

class VaultException(message: String) : Exception(message)

/**
 * Holds the unlocked Data Encryption Key (DEK) in memory and seals it on disk under a
 * password-derived Argon2id key (and an optional recovery key). The DEK is the exact secret
 * string consumed by DatabaseDriverFactory.
 */
class KeyVault(
    private val store: VaultStore,
    private val kdfParams: Argon2Params = Argon2Params.DEFAULT
) {
    enum class Status { NoVault, Locked, Unlocked }

    data class SetupResult(val dek: String, val recoveryKey: RecoveryKey?)

    private var unlockedDek: String? = null

    suspend fun status(): Status {
        if (unlockedDek != null) return Status.Unlocked
        val vault = store.read() ?: return Status.NoVault
        if (vault.version != VaultFile.CURRENT_VERSION) {
            throw VaultException("Unsupported vault version ${vault.version}")
        }
        return Status.Locked
    }

    fun currentDek(): String? = unlockedDek

    suspend fun setUp(password: CharArray, generateRecovery: Boolean = true): SetupResult {
        val dek = Base64.getEncoder().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        return writeVault(dek, password, generateRecovery)
    }

    suspend fun adoptExistingKeyAsDek(
        existingKey: String,
        password: CharArray,
        generateRecovery: Boolean = true
    ): SetupResult = writeVault(existingKey, password, generateRecovery)

    suspend fun unlock(password: CharArray): String? {
        val vault = store.read() ?: return null
        val kek = deriveKek(password, vault)
        return try {
            val dek = AesGcm.decrypt(kek, vault.wrappedDek.password.toGcmBox()).decodeToString()
            unlockedDek = dek
            dek
        } catch (e: AEADBadTagException) {
            null
        } finally {
            kek.fill(0)
        }
    }

    suspend fun unlockWithRecovery(code: String): String? {
        val vault = store.read() ?: return null
        val recoveryBox = vault.wrappedDek.recovery ?: return null
        val key = RecoveryKey.decode(code) ?: return null
        return try {
            val dek = AesGcm.decrypt(key, recoveryBox.toGcmBox()).decodeToString()
            unlockedDek = dek
            dek
        } catch (e: AEADBadTagException) {
            null
        } finally {
            key.fill(0)
        }
    }

    suspend fun changePassword(old: CharArray, new: CharArray): Boolean {
        val dek = unlock(old) ?: return false
        writeVault(dek, new, regenerateRecovery = false, existingVault = store.read())
        return true
    }

    suspend fun resetPasswordWithRecovery(code: String, new: CharArray): Boolean {
        val dek = unlockWithRecovery(code) ?: return false
        writeVault(dek, new, regenerateRecovery = false, existingVault = store.read())
        return true
    }

    fun lock() {
        unlockedDek = null
    }

    // --- internals ---

    private suspend fun writeVault(
        dek: String,
        password: CharArray,
        generateRecovery: Boolean = true,
        regenerateRecovery: Boolean = true,
        existingVault: VaultFile? = null
    ): SetupResult {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val kek = Argon2.deriveKey(password, salt, kdfParams)
        val dekBytes = dek.encodeToByteArray()

        val passwordBox = SerializableBox.of(AesGcm.encrypt(kek, dekBytes))

        // Recovery: regenerate when asked, otherwise preserve any existing recovery wrap.
        var recoveryKey: RecoveryKey? = null
        val recoveryBox: SerializableBox? = when {
            regenerateRecovery && generateRecovery -> {
                val rk = RecoveryKey.generate()
                recoveryKey = rk
                SerializableBox.of(AesGcm.encrypt(rk.bytes, dekBytes))
            }
            !regenerateRecovery -> existingVault?.wrappedDek?.recovery
            else -> null
        }

        kek.fill(0)
        dekBytes.fill(0)

        store.write(
            VaultFile(
                version = VaultFile.CURRENT_VERSION,
                kdf = kdfParams,
                kdfSaltB64 = Base64.getEncoder().encodeToString(salt),
                wrappedDek = WrappedDek(passwordBox, recoveryBox)
            )
        )
        unlockedDek = dek
        return SetupResult(dek, recoveryKey)
    }

    private fun deriveKek(password: CharArray, vault: VaultFile): ByteArray =
        Argon2.deriveKey(password, Base64.getDecoder().decode(vault.kdfSaltB64), vault.kdf)
}
```

Note for the implementer: when `changePassword`/`resetPasswordWithRecovery` call `writeVault` with `regenerateRecovery = false`, the recovery wrap is preserved verbatim from the existing vault, so the original recovery key keeps working after a password change. This matches the `resetPasswordWithRecovery` test (old password fails, DEK unchanged).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.KeyVaultTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Run the full suite (Phase 1 regression gate)**

Run: `./gradlew :shared:desktopTest`
Expected: BUILD SUCCESSFUL — no existing test regressed.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/security/vault/KeyVault.kt \
        shared/src/commonTest/kotlin/com/financeapp/security/vault/KeyVaultTest.kt
git commit -m "feat: KeyVault envelope encryption engine (setup/unlock/recovery/change-password)"
```

---

## Phase 2 — Wiring, migration & bootstrap reordering

### Task 8: Desktop vault store (real file at ~/.financeapp/vault.json)

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/financeapp/security/vault/DesktopVaultStore.kt`
- Test: `shared/src/desktopTest/kotlin/com/financeapp/security/vault/DesktopVaultStoreTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.financeapp.security.vault

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DesktopVaultStoreTest {

    private lateinit var dir: File

    @BeforeTest fun setup() { dir = Files.createTempDirectory("vault-test").toFile() }
    @AfterTest fun teardown() { dir.deleteRecursively() }

    private fun store() = DesktopVaultStore(File(dir, "vault.json"))

    @Test
    fun `read returns null when no vault file exists`() = runTest {
        assertNull(store().read())
    }

    @Test
    fun `write then read round-trips the vault`() = runTest {
        val s = store()
        val vault = VaultFile(
            version = 1,
            kdf = Argon2Params.DEFAULT,
            kdfSaltB64 = "c2FsdA==",
            wrappedDek = WrappedDek(SerializableBox("bm9uY2U=", "Y3Q="), recovery = null)
        )
        s.write(vault)
        assertEquals(vault, s.read())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.DesktopVaultStoreTest"`
Expected: FAIL — `DesktopVaultStore` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.financeapp.security.vault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class DesktopVaultStore(
    private val file: File = File(System.getProperty("user.home"), ".financeapp/vault.json")
) : VaultStore {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override suspend fun read(): VaultFile? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        json.decodeFromString(VaultFile.serializer(), file.readText())
    }

    override suspend fun write(vault: VaultFile) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(VaultFile.serializer(), vault))
        restrict(file)
    }

    override suspend fun deleteVault() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }

    private fun restrict(f: File) {
        try {
            f.setReadable(false, false); f.setReadable(true, true)
            f.setWritable(false, false); f.setWritable(true, true)
            f.setExecutable(false, false)
        } catch (_: Exception) { /* non-POSIX */ }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.DesktopVaultStoreTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/financeapp/security/vault/DesktopVaultStore.kt \
        shared/src/desktopTest/kotlin/com/financeapp/security/vault/DesktopVaultStoreTest.kt
git commit -m "feat: desktop vault store at ~/.financeapp/vault.json"
```

---

### Task 9: Migration helper — adopt the legacy keystore key as the DEK

**Files:**
- Create: `shared/src/desktopMain/kotlin/com/financeapp/security/vault/LegacyKeyMigration.kt`
- Test: `shared/src/desktopTest/kotlin/com/financeapp/security/vault/LegacyKeyMigrationTest.kt`

Goal: on first run with no vault but an existing `EncryptionKeyManager` keystore key, the user provides a master password and the legacy key is adopted verbatim as the DEK (so the existing DB still opens). This task provides the pure decision/execution helper; Task 11 wires it to the UI.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.financeapp.security.vault

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LegacyKeyMigrationTest {

    private val cheap = Argon2Params(memoryKiB = 1024, iterations = 1, parallelism = 1)

    @Test
    fun `needsMigration is true when a legacy key exists but no vault`() = runTest {
        val store = InMemoryVaultStore()
        val migration = LegacyKeyMigration(KeyVault(store, cheap), legacyKeyProvider = { "legacyKey==" }, onMigrated = {})
        assertTrue(migration.needsMigration())
    }

    @Test
    fun `needsMigration is false when a vault already exists`() = runTest {
        val store = InMemoryVaultStore()
        KeyVault(store, cheap).setUp("correct horse battery staple".toCharArray())
        val migration = LegacyKeyMigration(KeyVault(store, cheap), legacyKeyProvider = { "legacyKey==" }, onMigrated = {})
        assertFalse(migration.needsMigration())
    }

    @Test
    fun `migrate adopts the legacy key as the DEK and clears it afterwards`() = runTest {
        val store = InMemoryVaultStore()
        var cleared = false
        val migration = LegacyKeyMigration(
            KeyVault(store, cheap),
            legacyKeyProvider = { "legacyKey==" },
            onMigrated = { cleared = true }
        )

        val result = migration.migrate("correct horse battery staple".toCharArray())
        assertEquals("legacyKey==", result.dek)
        assertTrue(cleared, "onMigrated must run so the keystore key can be deleted")

        // The same legacy key now unlocks via the new vault.
        assertEquals("legacyKey==", KeyVault(store, cheap).unlock("correct horse battery staple".toCharArray()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.LegacyKeyMigrationTest"`
Expected: FAIL — `LegacyKeyMigration` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.financeapp.security.vault

/**
 * Migrates an install whose DB key lives in the OS keystore to a password-sealed vault by
 * adopting the existing key verbatim as the DEK (so the on-disk DB still opens). After a
 * successful migration, onMigrated() runs so the caller can delete the keystore entry.
 */
class LegacyKeyMigration(
    private val keyVault: KeyVault,
    private val legacyKeyProvider: () -> String?,
    private val onMigrated: () -> Unit
) {
    suspend fun needsMigration(): Boolean =
        keyVault.status() == KeyVault.Status.NoVault && legacyKeyProvider() != null

    suspend fun migrate(password: CharArray, generateRecovery: Boolean = true): KeyVault.SetupResult {
        val legacyKey = legacyKeyProvider()
            ?: throw VaultException("No legacy key to migrate")
        val result = keyVault.adoptExistingKeyAsDek(legacyKey, password, generateRecovery)
        onMigrated()
        return result
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.security.vault.LegacyKeyMigrationTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/financeapp/security/vault/LegacyKeyMigration.kt \
        shared/src/desktopTest/kotlin/com/financeapp/security/vault/LegacyKeyMigrationTest.kt
git commit -m "feat: legacy keystore-key to vault migration helper"
```

Implementer note: `EncryptionKeyManager` currently *creates* a key if none exists. Add a read-only accessor in Task 10 so `legacyKeyProvider` does not mint a brand-new key during migration detection.

---

### Task 10: DI wiring — open the DB from the vault DEK, not the keystore

**Files:**
- Modify: `shared/src/desktopMain/kotlin/com/financeapp/security/EncryptionKeyManager.desktop.kt` (add a read-only `peekExistingKey()` and a `deleteKey()`)
- Modify: `shared/src/desktopMain/kotlin/com/financeapp/di/Modules.desktop.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/di/Modules.kt:72-74` (the `Database` single)

- [ ] **Step 1: Add read-only key access + delete to EncryptionKeyManager**

In `EncryptionKeyManager.desktop.kt`, add these methods to the class (they reuse the existing private lookups, and must NOT create a key):

```kotlin
    /** Returns the existing keystore key without creating one, or null if absent. */
    fun peekExistingKey(): String? = when {
        isMacOS() -> getFromKeychain()
        isWindows() -> getFromDpapi()
        LinuxSecretService.isAvailable() -> LinuxSecretService.lookup(LINUX_KEY_ATTRIBUTE)
        else -> null
    }

    /** Removes the keystore key after a successful migration to the password vault. */
    fun deleteKey() {
        try {
            when {
                isMacOS() -> ProcessRunner.run(listOf("security", "delete-generic-password", "-s", serviceName, "-a", accountName))
                isWindows() -> if (keyFile.exists()) secureDeleteFile(keyFile)
                LinuxSecretService.isAvailable() -> LinuxSecretService.delete(LINUX_KEY_ATTRIBUTE)
            }
        } catch (_: Exception) { /* best effort */ }
    }
```

If `LinuxSecretService` lacks a `delete`, add a matching `secret-tool clear` wrapper there.

- [ ] **Step 2: Rewire the desktop module**

Replace the body of `platformModule()` in `Modules.desktop.kt` with:

```kotlin
actual fun platformModule(): Module = module {
    single { EncryptionKeyManager() }
    single<VaultStore> { DesktopVaultStore() }
    single { KeyVault(get<VaultStore>()) }
    single {
        LegacyKeyMigration(
            keyVault = get(),
            legacyKeyProvider = { get<EncryptionKeyManager>().peekExistingKey() },
            onMigrated = { get<EncryptionKeyManager>().deleteKey() }
        )
    }
    // The DB driver is built from the unlocked DEK; resolving it before unlock is a bug.
    single { DatabaseDriverFactory(get<KeyVault>().currentDek()
        ?: error("Database accessed before the vault was unlocked")) }
    single<PreferencesStore> { DesktopPreferencesStore() }
    single { BiometricAuth() }
}
```

Add imports for `VaultStore`, `DesktopVaultStore`, `KeyVault`, `LegacyKeyMigration`.

- [ ] **Step 3: Confirm the Database single stays lazy**

`Modules.kt:72-74` already reads:

```kotlin
    single<Database> {
        get<DatabaseDriverFactory>().createDriver()
    }
```

Leave it unchanged. Because Koin `single` is lazy, `Database` now opens on first `get()` — which Task 11 guarantees happens only after unlock.

- [ ] **Step 4: Compile**

Run: `./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL. (No test here; behavior is exercised in Task 11.)

- [ ] **Step 5: Commit**

```bash
git add shared/src/desktopMain/kotlin/com/financeapp/security/EncryptionKeyManager.desktop.kt \
        shared/src/desktopMain/kotlin/com/financeapp/di/Modules.desktop.kt
git commit -m "wire: build the database driver from the vault DEK instead of the keystore"
```

---

### Task 11: Bootstrap reordering — VaultViewModel gates the DB

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/ui/VaultViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/AppViewModel.kt` (remove DB-backed startup from `init`; expose a `startPostUnlock()` called once unlocked)
- Modify: `shared/src/commonMain/kotlin/com/financeapp/App.kt:60-115` (drive the gate from vault status)

The problem: `AppViewModel` is injected at the top of `App()` and its `init{}` starts `databaseSeeder`, `priceRefreshService`, `snapshotScheduler` — all of which resolve `Database` before unlock. Fix: a lightweight `VaultViewModel` (depends only on `KeyVault` + `LegacyKeyMigration`) drives the gate, and the DB-backed startup moves into an explicit `startPostUnlock()` that runs only after the DEK exists.

- [ ] **Step 1: Create VaultViewModel**

```kotlin
package com.financeapp.ui

import com.financeapp.security.vault.KeyVault
import com.financeapp.security.vault.LegacyKeyMigration
import com.financeapp.security.vault.PasswordStrength
import com.financeapp.security.vault.RecoveryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VaultGate { Loading, Setup, Migrate, Locked, Unlocked }

class VaultViewModel(
    private val keyVault: KeyVault,
    private val migration: LegacyKeyMigration
) {
    private val scope = supervisedViewModelScope()

    private val _gate = MutableStateFlow(VaultGate.Loading)
    val gate: StateFlow<VaultGate> = _gate.asStateFlow()

    private val _recoveryToShow = MutableStateFlow<RecoveryKey?>(null)
    val recoveryToShow: StateFlow<RecoveryKey?> = _recoveryToShow.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { refresh() }

    private fun refresh() {
        scope.launch {
            _gate.value = when {
                keyVault.status() == KeyVault.Status.Unlocked -> VaultGate.Unlocked
                migration.needsMigration() -> VaultGate.Migrate
                keyVault.status() == KeyVault.Status.NoVault -> VaultGate.Setup
                else -> VaultGate.Locked
            }
        }
    }

    fun checkStrength(password: CharArray): PasswordStrength.Result = PasswordStrength.evaluate(password)

    fun setUp(password: CharArray) {
        scope.launch {
            val result = keyVault.setUp(password)
            _recoveryToShow.value = result.recoveryKey
            _gate.value = VaultGate.Unlocked
        }
    }

    fun migrate(password: CharArray) {
        scope.launch {
            val result = migration.migrate(password)
            _recoveryToShow.value = result.recoveryKey
            _gate.value = VaultGate.Unlocked
        }
    }

    fun unlock(password: CharArray) {
        scope.launch {
            if (keyVault.unlock(password) != null) {
                _error.value = null
                _gate.value = VaultGate.Unlocked
            } else {
                _error.value = "Incorrect password."
            }
        }
    }

    fun unlockWithRecovery(code: String, newPassword: CharArray) {
        scope.launch {
            if (keyVault.resetPasswordWithRecovery(code, newPassword)) {
                _gate.value = VaultGate.Unlocked
            } else {
                _error.value = "That recovery key was not recognized."
            }
        }
    }

    fun dismissRecovery() { _recoveryToShow.value = null }

    fun lock() {
        keyVault.lock()
        _gate.value = VaultGate.Locked
    }
}
```

- [ ] **Step 2: Move DB-backed startup out of AppViewModel.init**

In `AppViewModel.kt`, delete the three startup calls from `init{}` (`seedDatabaseIfNeeded()`, `startPriceRefreshService()`, `startSnapshotScheduler()`), keeping only the non-DB ones. Add a single public method:

```kotlin
    private var started = false

    /** Runs DB-backed startup exactly once, after the vault is unlocked. */
    fun startPostUnlock() {
        if (started) return
        started = true
        seedDatabaseIfNeeded()
        loadThemeMode()
        startPriceRefreshService()
        startSnapshotScheduler()
    }
```

Update `init{}` to call only `checkLockSetup()` (theme now loads in `startPostUnlock`, since it is DB-backed via `PreferencesRepository`). The setup/lock screens render in the default theme, which is acceptable.

- [ ] **Step 3: Register VaultViewModel and drive the gate in App.kt**

In `Modules.kt` `sharedModule`, add near the other view models:

```kotlin
    single { VaultViewModel(get(), get()) }
```

In `App.kt`, replace the `when { ... }` lock branches so the vault gate runs first. The DB-backed `AppViewModel` must only be injected once `Unlocked`:

```kotlin
@Composable
fun App() {
    val vaultViewModel: VaultViewModel = koinInject()
    val gate by vaultViewModel.gate.collectAsState()
    val recoveryToShow by vaultViewModel.recoveryToShow.collectAsState()
    val vaultError by vaultViewModel.error.collectAsState()

    FinanceAppTheme(themeMode = ThemeMode.SYSTEM) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when (gate) {
                    VaultGate.Loading -> { /* brief spinner */ }
                    VaultGate.Setup -> VaultSetupScreen(
                        checkStrength = vaultViewModel::checkStrength,
                        onCreate = vaultViewModel::setUp
                    )
                    VaultGate.Migrate -> VaultMigrateScreen(
                        checkStrength = vaultViewModel::checkStrength,
                        onMigrate = vaultViewModel::migrate
                    )
                    VaultGate.Locked -> VaultUnlockScreen(
                        error = vaultError,
                        onUnlock = vaultViewModel::unlock,
                        onRecover = vaultViewModel::unlockWithRecovery
                    )
                    VaultGate.Unlocked -> UnlockedApp()
                }
                recoveryToShow?.let { RecoveryKeyDialog(it, onDismiss = vaultViewModel::dismissRecovery) }
            }
        }
    }
}

@Composable
private fun UnlockedApp() {
    val appViewModel: AppViewModel = koinInject()   // first DB touch happens here, post-unlock
    LaunchedEffect(Unit) { appViewModel.startPostUnlock() }
    val themeMode by appViewModel.themeMode.collectAsState()
    FinanceAppTheme(themeMode = themeMode) { MainContent() }
}
```

(The screens `VaultSetupScreen`, `VaultMigrateScreen`, `VaultUnlockScreen`, `RecoveryKeyDialog` are built in Phase 3 — Task 12/13. Until then, stub them as simple placeholders so the project compiles, then flesh them out.)

- [ ] **Step 4: Manual smoke test**

Run: `./gradlew :desktopApp:run`
Expected: with a pre-existing install, the app shows the migrate screen; entering the password opens the existing data. With `~/.financeapp` moved aside, it shows setup; creating a password opens an empty DB. Verify no DB is opened before unlock (add a temporary log line in `DatabaseDriverFactory.createDriver()` and confirm it prints only after unlock; remove it after).

- [ ] **Step 5: Run the full suite**

Run: `./gradlew :shared:desktopTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: gate database open behind vault unlock; defer DB startup to post-unlock"
```

---

## Phase 3 — UI & hardenings

### Task 12: Vault setup & migrate screens (password + strength meter)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/ui/lock/VaultSetupScreen.kt` (contains `VaultSetupScreen` and `VaultMigrateScreen` — they share a password+strength composable)

- [ ] **Step 1: Build the shared password form**

Implement a private `MasterPasswordForm(title, subtitle, checkStrength, confirmLabel, onSubmit)` composable: two password fields (password + confirm), a live strength line driven by `checkStrength(password.toCharArray()).reason`, a submit button enabled only when the strength result is `acceptable` and the two fields match. Include a one-line note: "Tip: also enable full-disk encryption (FileVault/BitLocker) for defense in depth."

- [ ] **Step 2: Build the two screens on top of it**

```kotlin
@Composable
fun VaultSetupScreen(checkStrength: (CharArray) -> PasswordStrength.Result, onCreate: (CharArray) -> Unit) =
    MasterPasswordForm(
        title = "Create your master password",
        subtitle = "This password encrypts your financial data. It can't be recovered if you forget it (a recovery key is generated next).",
        checkStrength = checkStrength,
        confirmLabel = "Create",
        onSubmit = onCreate
    )

@Composable
fun VaultMigrateScreen(checkStrength: (CharArray) -> PasswordStrength.Result, onMigrate: (CharArray) -> Unit) =
    MasterPasswordForm(
        title = "Secure your data",
        subtitle = "We're upgrading your encryption. Set a master password — from now on it's required each time you open the app.",
        checkStrength = checkStrength,
        confirmLabel = "Secure",
        onSubmit = onMigrate
    )
```

- [ ] **Step 3: Compile & smoke test**

Run: `./gradlew :desktopApp:run`
Expected: setup/migrate screens render; weak passwords block the button with the policy reason.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/ui/lock/VaultSetupScreen.kt
git commit -m "feat: vault setup and migrate screens with password strength meter"
```

---

### Task 13: Recovery-key dialog & unlock screen (with forgot-password recovery)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/financeapp/ui/lock/RecoveryKeyDialog.kt`
- Create: `shared/src/commonMain/kotlin/com/financeapp/ui/lock/VaultUnlockScreen.kt`

- [ ] **Step 1: RecoveryKeyDialog**

A modal showing `recoveryKey.display` in a selectable monospace block, a Copy button, a bold "This is the only time you'll see this. Store it somewhere safe — anyone with it can open your data." warning, and a checkbox "I've saved my recovery key" that gates the Done button (calls `onDismiss`).

- [ ] **Step 2: VaultUnlockScreen**

Reuse the existing `PinUnlockScreen` layout/lockout countdown as a reference. A password field → `onUnlock(password.toCharArray())`, the `error` text when non-null, and a "Forgot password?" text button that expands a recovery sub-form (recovery-key field + new-password field with the same strength gate) → `onRecover(code, newPassword.toCharArray())`.

- [ ] **Step 3: Compile & smoke test**

Run: `./gradlew :desktopApp:run`
Expected: after setup, the recovery dialog appears; on next launch the unlock screen accepts the password, a wrong password shows the error, and "Forgot password?" + the recovery key resets the password and unlocks.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/financeapp/ui/lock/RecoveryKeyDialog.kt \
        shared/src/commonMain/kotlin/com/financeapp/ui/lock/VaultUnlockScreen.kt
git commit -m "feat: recovery-key dialog and vault unlock screen with recovery"
```

---

### Task 14: Lockout integration on the unlock path

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/VaultViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/financeapp/ui/VaultViewModelLockoutTest.kt`

Reuse the existing `AppLockRepository` lockout counter (5 tries → exponential backoff, persisted) to throttle online password guessing against the vault. Inject `AppLockRepository` into `VaultViewModel`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.financeapp.ui

import com.financeapp.data.repository.AppLockRepositoryImpl
import com.financeapp.data.repository.PreferencesStore
import com.financeapp.security.vault.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class VaultViewModelLockoutTest {

    private class MapPrefs : PreferencesStore {
        val m = mutableMapOf<String, String>()
        override suspend fun getString(key: String) = m[key]
        override suspend fun putString(key: String, value: String) { m[key] = value }
        override suspend fun remove(key: String) { m.remove(key) }
    }

    @Test
    fun `repeated wrong passwords trip the persisted lockout`() = runTest {
        val cheap = Argon2Params(1024, 1, 1)
        val store = InMemoryVaultStore()
        KeyVault(store, cheap).setUp("correct horse battery staple".toCharArray())

        val prefs = MapPrefs()
        val lock = AppLockRepositoryImpl(prefs) { 0L }
        val vm = VaultViewModel(KeyVault(store, cheap), noMigration(store, cheap), lock)

        repeat(5) { vm.unlockBlockingForTest("wrong wrong wrong".toCharArray()) }

        assertNotNull(lock.getLockoutState().lockedUntilEpochMs, "should be locked out after 5 failures")
    }

    private fun noMigration(store: InMemoryVaultStore, p: Argon2Params) =
        LegacyKeyMigration(KeyVault(store, p), legacyKeyProvider = { null }, onMigrated = {})
}
```

(Implementer: add a small `suspend fun unlockBlockingForTest` that awaits the same logic as `unlock`, or refactor `unlock` to return the result from a suspend function the UI wrapper launches — keep the coroutine seam testable.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.VaultViewModelLockoutTest"`
Expected: FAIL — constructor arity / method missing.

- [ ] **Step 3: Implement**

Add `appLock: AppLockRepository` to `VaultViewModel`. In the unlock flow: if `appLock.getLockoutState().isLockedOut(now)` refuse; on wrong password call `appLock.recordFailedAttempt()` and surface the countdown; on success call `appLock.resetFailedAttempts()`. Update the DI registration `single { VaultViewModel(get(), get(), get()) }`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.VaultViewModelLockoutTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: throttle vault unlock with the persisted lockout/backoff"
```

---

### Task 15: Auto-lock on idle + key zeroing

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/VaultViewModel.kt` (idle timer → `lock()`)
- Modify: `shared/src/commonMain/kotlin/com/financeapp/App.kt` (reset idle timer on user interaction; call `appViewModel`/services teardown on lock)
- Test: `shared/src/commonTest/kotlin/com/financeapp/ui/AutoLockTest.kt`

- [ ] **Step 1: Write the failing test (pure idle policy)**

Extract the idle decision into a pure helper so it is testable without UI/time:

```kotlin
package com.financeapp.ui

import kotlin.test.*

class AutoLockTest {
    @Test fun `locks after the timeout elapses`() {
        assertTrue(AutoLockPolicy.shouldLock(lastActivityMs = 0, nowMs = 10 * 60_000, timeoutMs = 10 * 60_000))
    }
    @Test fun `does not lock before the timeout`() {
        assertFalse(AutoLockPolicy.shouldLock(lastActivityMs = 0, nowMs = 5 * 60_000, timeoutMs = 10 * 60_000))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.AutoLockTest"`
Expected: FAIL — `AutoLockPolicy` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.financeapp.ui

object AutoLockPolicy {
    fun shouldLock(lastActivityMs: Long, nowMs: Long, timeoutMs: Long): Boolean =
        nowMs - lastActivityMs >= timeoutMs
}
```

Then in `VaultViewModel`, run a coroutine ticking every ~30s that calls `lock()` when `AutoLockPolicy.shouldLock(lastActivity, now, timeoutMs)` (default `timeoutMs = 10*60_000`). Expose `fun noteActivity()` that updates `lastActivity`. In `App.kt`, wrap `UnlockedApp` in a `Modifier.pointerInput`/`onKeyEvent` that calls `vaultViewModel.noteActivity()`. On `lock()`, also reset `AppViewModel.started` path so services restart cleanly on next unlock (call an `AppViewModel.onLocked()` that cancels the price-refresh/snapshot schedulers; add it mirroring the existing desktop `Main.kt` shutdown of those services).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:desktopTest --tests "com.financeapp.ui.AutoLockTest"`
Expected: PASS.

- [ ] **Step 5: Manual smoke test**

Run: `./gradlew :desktopApp:run`; leave idle past the timeout (temporarily set it to ~15s) and confirm the app returns to the unlock screen and re-unlocks cleanly. Restore the default timeout.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: auto-lock on idle with DEK zeroing"
```

---

### Task 16: Retire the legacy app-lock PIN path & update docs

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/financeapp/App.kt` (remove the old `PinSetupScreen`/`PinUnlockScreen` branches and the `AppViewModel` lock state now that `VaultViewModel` owns the gate)
- Modify: `shared/src/commonMain/kotlin/com/financeapp/ui/AppViewModel.kt` (drop the now-unused lock fields/methods, or keep only what `MainContent`/settings still use)
- Modify: `SECURITY.md`, `CLAUDE.md` (document the vault)

- [ ] **Step 1: Remove the superseded PIN gate**

Delete the `!lockState.isSetUp` / `lockState.isLocked` branches and the corresponding `AppViewModel` lock plumbing that `VaultViewModel` now replaces. Keep `AppLockRepository` (its lockout counter is reused by Task 14). If `PinSetupScreen`/`PinUnlockScreen` are now unreferenced, delete them; if a "change master password" entry point is wanted, add it in Settings calling `keyVault.changePassword`.

- [ ] **Step 2: Update SECURITY.md and CLAUDE.md**

Add an "Encryption & master password" section documenting: Argon2id envelope encryption, password-every-launch, the recovery key, auto-lock, and the explicit non-goals (no defense against same-user malware on an unlocked session; recommend OS full-disk encryption). In `CLAUDE.md`, update the Encryption section to point at `KeyVault` instead of the keystore-key model.

- [ ] **Step 3: Full regression + manual run**

Run: `./gradlew :shared:desktopTest && ./gradlew :desktopApp:run`
Expected: suite green; fresh-setup, migrate, unlock, wrong-password lockout, recovery, change-password, and auto-lock all work.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: retire the legacy PIN gate in favor of the master-password vault; docs"
```

---

## Self-review notes (for the implementer)

- **DEK-as-String invariant** is load-bearing: it is why migration needs no DB re-encryption. Do not change `DatabaseDriverFactory`'s internal PBKDF2/`finance.config` stretching.
- **KDF cost in tests:** always use the cheap `Argon2Params(1024, 1, 1)` in unit tests; production defaults (64 MiB/t=3/p=4) are only used by the real DI singletons.
- **No DB before unlock:** the entire correctness of Phase 2 rests on nothing resolving `Database` until `VaultGate.Unlocked`. Task 11 Step 4 verifies this explicitly.
- **Spec coverage check:** crypto core (Tasks 2–7), vault format (Task 5), strict unlock + bootstrap (Tasks 10–11), migration (Tasks 9–11), recovery (Tasks 7,13), password policy/R22 (Task 6,12), auto-lock (Task 15), lockout (Task 14), docs/non-goals (Task 16). All spec sections map to a task.
