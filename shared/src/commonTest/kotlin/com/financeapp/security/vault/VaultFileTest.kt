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
