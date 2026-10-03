package io.github.currencortex.music.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class AccountVaultTest {
    private class Store : TokenStore {
        var value: String? = null
        override fun read() = value
        override fun write(token: String) { value = token }
        override fun clear() { value = null }
    }
    // Android DataStore uses POSIX replacement; Windows JVM File.renameTo cannot replace
    // an existing target. Device tests cover actual disk storage and encryption.
    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }
    @Test fun serverAndIdScopeCredentialsAndMetadataNeverContainsTokens() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val stores = mutableMapOf<String, Store>(); val data = MemoryPreferences()
            val vault = EncryptedAccountVault(data, scope) { stores.getOrPut(it) { Store() } }
            vault.remember("https://a.example/cm/", UserDto(7, "one", "One"), "secret-token-A")
            val first = vault.current()!!
            vault.remember("https://b.example/cm/", UserDto(7, "one", "One"), "secret-token-B")
            assertNotEquals(first.key, vault.current()!!.key)
            assertEquals("secret-token-A", vault.credential(first.key)!!.second)
            assertFalse(data.data.first().asMap().values.joinToString().contains("secret-token"))
            assertEquals(2, withTimeout(3000) { vault.accounts.first { it.size == 2 } }.size)
            vault.remove(first.key); assertNull(vault.credential(first.key)); assertEquals("secret-token-B", vault.credential(vault.current()!!.key)!!.second)
        } finally { scope.cancel() }
    }
    @Test fun legacySessionMigratesAndOfflineRestoreRetainsItsCachedAccount() = runBlocking {
        MockWebServer().use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val stores = mutableMapOf<String, Store>()
                val vault = EncryptedAccountVault(MemoryPreferences(), scope) { stores.getOrPut(it) { Store() } }
                val legacy = Store().apply { value = "legacy-token" }; val accounts = AccountRepository(legacy, scope, vault)
                val base = server.url("/cm/").toString()
                val auth = AuthRepository(ApiClient({ accounts.server }, { accounts.token }, accounts::expired), accounts, "device") { _, _ -> }
                server.enqueue(MockResponse().setBody("""{"id":7,"username":"one","nickname":"One"}"""))
                assertTrue(auth.restore(base) is AppResult.Success)
                assertEquals("legacy-token", vault.credential(vault.current()!!.key)!!.second)
                server.enqueue(MockResponse().setResponseCode(503))
                assertTrue(auth.restore(base) is AppResult.Failure)
                assertEquals(7L, accounts.state.value.account!!.id); assertTrue(accounts.state.value.offline)
                assertEquals("legacy-token", legacy.value)
            } finally { scope.cancel() }
        }
    }
    @Test fun switchingChecksStoredCredentialAndExpiredSessionLeavesOtherAccountIntact() = runBlocking {
        MockWebServer().use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val stores = mutableMapOf<String, Store>()
                val vault = EncryptedAccountVault(MemoryPreferences(), scope) { stores.getOrPut(it) { Store() } }
                val active = Store(); val accounts = AccountRepository(active, scope, vault)
                val base = server.url("/cm/").toString(); accounts.server = base
                accounts.save("credential-one", UserDto(7, "one", "One")); val first = vault.current()!!
                accounts.save("credential-two", UserDto(8, "two", "Two")); val second = vault.current()!!
                val auth = AuthRepository(ApiClient({ accounts.server }, { accounts.token }, accounts::expired), accounts, "device") { _, _ -> }
                server.enqueue(MockResponse().setBody("""{"id":7,"username":"one","nickname":"One"}"""))
                assertTrue(auth.switchAccount(first.key) is AppResult.Success)
                assertEquals("Bearer credential-one", server.takeRequest().getHeader("Authorization"))
                accounts.expired(RequestSession(base, "credential-one"))
                withTimeout(3000) { while (vault.credential(first.key) != null) delay(10) }
                assertEquals("credential-two", vault.credential(second.key)!!.second)
                assertNull(active.value)
            } finally { scope.cancel() }
        }
    }
    @Test fun staleProfileUpdateCannotOverwriteAnAccountAfterActivation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val stores = mutableMapOf<String, Store>()
            val vault = EncryptedAccountVault(MemoryPreferences(), scope) { stores.getOrPut(it) { Store() } }
            val accounts = AccountRepository(Store(), scope, vault); accounts.server = "https://example.com/cm/"
            accounts.save("one", UserDto(7)); val first = vault.current()!!
            accounts.save("two", UserDto(8)); val expected = RequestSession(accounts.server, "two")
            accounts.activate(first.key)
            assertTrue(appResult { accounts.save("two", UserDto(8, nickname = "Stale"), expected) } is AppResult.Failure)
            assertEquals(7L, accounts.state.value.account!!.id); assertEquals("one", accounts.token)
        } finally { scope.cancel() }
    }
}
