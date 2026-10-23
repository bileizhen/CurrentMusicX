package io.github.currencortex.music.core.netease

import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.security.ProviderException
import java.security.UnrecoverableKeyException

class NeteaseSessionStoreTest {
    private class Vault : TokenStore {
        var value: String? = null
        var readError: Exception? = null
        var writeError: Exception? = null
        var writes = 0
        override fun read(): String? { readError?.let { throw it }; return value }
        override fun write(token: String) {
            writes++
            writeError?.let { throw it }
            value = token
        }
        override fun clear() { value = null }
    }

    private fun checkStore(block: suspend (CoroutineScope, Vault) -> Unit) = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        try { withTimeout(5000) { block(scope, Vault()) } }
        finally { scope.cancel() }
    }

    @Test fun unreadableKeyDoesNotCrashStartupOrOverwriteSavedSession() = checkStore { scope, vault ->
        vault.value = "existing-encrypted-session"
        vault.readError = UnrecoverableKeyException("Keystore unavailable")
        val sessions = NeteaseSessionStore(vault, scope)
        sessions.ready.await()
        assertFalse(sessions.state.value.loggedIn)
        assertNotNull(sessions.snapshot().credential.deviceId)
        assertEquals(0, vault.writes)
        assertEquals("existing-encrypted-session", vault.value)
        assertTrue(scope.isActive)
    }

    @Test fun failedInitialKeyGenerationStillCompletesStartup() = checkStore { scope, vault ->
        vault.writeError = ProviderException("Keystore key generation failed")
        val sessions = NeteaseSessionStore(vault, scope)
        sessions.ready.await()
        assertFalse(sessions.state.value.loggedIn)
        assertEquals(1, vault.writes)
        assertNull(vault.value)
        assertTrue(scope.isActive)
    }

    @Test fun restoredLoginSurvivesFailedStartupWrite() = checkStore { scope, vault ->
        vault.value = ApiJson.encodeToString(NeteaseCredential.serializer(), NeteaseCredential(
            mapOf("MUSIC_U" to "fixture-cookie"), BoundProfile(7, "Existing"), "stable-device"))
        val saved = vault.value
        vault.writeError = ProviderException("Keystore unavailable")
        val sessions = NeteaseSessionStore(vault, scope)
        sessions.ready.await()
        assertTrue(sessions.state.value.loggedIn)
        assertEquals(7L, sessions.state.value.profile?.uid)
        assertEquals("stable-device", sessions.snapshot().credential.deviceId)
        assertEquals(saved, vault.value)
    }

    @Test fun failedLoginWriteIsReportedAndCanRetryAfterKeystoreRecovers() = checkStore { scope, vault ->
        vault.writeError = ProviderException("Keystore unavailable")
        val sessions = NeteaseSessionStore(vault, scope)
        sessions.ready.await()
        val expected = sessions.snapshot()
        try {
            sessions.login(mapOf("MUSIC_U" to "fixture-cookie"), BoundProfile(7, "Existing"), expected)
            fail("Login must not succeed without encrypted persistence")
        } catch (_: ProviderException) { }
        assertFalse(sessions.state.value.loggedIn)
        assertEquals(0L, sessions.state.value.revision)
        vault.writeError = null
        sessions.login(mapOf("MUSIC_U" to "fixture-cookie"), BoundProfile(7, "Existing"), expected)
        val restarted = NeteaseSessionStore(vault, scope)
        restarted.ready.await()
        assertTrue(restarted.state.value.loggedIn)
        assertEquals(expected.credential.deviceId, restarted.snapshot().credential.deviceId)
    }

    @Test fun firstLaunchPersistsIdentityForTheNextLaunch() = checkStore { scope, vault ->
        val first = NeteaseSessionStore(vault, scope)
        first.ready.await()
        val restarted = NeteaseSessionStore(vault, scope)
        restarted.ready.await()
        assertEquals(first.snapshot().credential.deviceId, restarted.snapshot().credential.deviceId)
    }
}
