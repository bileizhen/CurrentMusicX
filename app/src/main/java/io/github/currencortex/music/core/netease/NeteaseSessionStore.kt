package io.github.currencortex.music.core.netease

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.binding.BoundProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.util.UUID

data class NeteaseAccountState(val profile: BoundProfile? = null, val loggedIn: Boolean = false,
    val stale: Boolean = false, val revision: Long = 0)

@Serializable internal class NeteaseCredential(val cookies: Map<String, String> = emptyMap(),
    val profile: BoundProfile? = null, val deviceId: String = UUID.randomUUID().toString(), val stale: Boolean = false) {
    override fun toString() = "NeteaseCredential([redacted])"
}
internal class NeteaseSession(val credential: NeteaseCredential, val revision: Long) {
    override fun toString() = "NeteaseSession(revision=$revision, credentials=[redacted])"
}

/** Independent, device-local NetEase login. The vault is Android Keystore-backed in production. */
class NeteaseSessionStore(private val vault: TokenStore, scope: CoroutineScope) {
    private val mutex = Mutex()
    @Volatile private var current = NeteaseCredential()
    val state = MutableStateFlow(NeteaseAccountState())
    val ready = CompletableDeferred<Unit>()
    init { scope.launch(Dispatchers.IO) {
        mutex.withLock {
            current = runCatching { vault.read()?.let { ApiJson.decodeFromString(NeteaseCredential.serializer(), it) } }
                .getOrNull() ?: NeteaseCredential()
            // Persist the device identity even before first login, keeping QR checks consistent.
            vault.write(ApiJson.encodeToString(NeteaseCredential.serializer(), current))
            publish(0)
            ready.complete(Unit)
        }
    }.invokeOnCompletion { error -> if (error != null) ready.completeExceptionally(error) } }
    private fun publish(revision: Long) {
        state.value = NeteaseAccountState(current.profile, !current.cookies["MUSIC_U"].isNullOrBlank(), current.stale, revision)
    }
    internal suspend fun snapshot(): NeteaseSession { ready.await(); return mutex.withLock { NeteaseSession(current, state.value.revision) } }
    internal fun check(expected: NeteaseSession) {
        if (state.value.revision != expected.revision) throw ApiException(ErrorKind.NeteaseSessionChanged)
    }
    internal suspend fun login(cookies: Map<String, String>, profile: BoundProfile, expected: NeteaseSession) {
        require(!cookies["MUSIC_U"].isNullOrBlank() && profile.uid > 0)
        ready.await()
        withContext(Dispatchers.IO) { mutex.withLock {
            check(expected)
            val record = NeteaseCredential(cookies, profile, current.deviceId)
            vault.write(ApiJson.encodeToString(NeteaseCredential.serializer(), record))
            current = record; publish(state.value.revision + 1)
        } }
    }
    internal suspend fun updateCookies(cookies: Map<String, String>, expected: NeteaseSession) {
        if (cookies.isEmpty()) return
        withContext(Dispatchers.IO) { mutex.withLock {
            check(expected)
            val merged = current.cookies + cookies.filterValues { it.isNotBlank() }
            if (merged == current.cookies) return@withLock
            val record = NeteaseCredential(merged, current.profile, current.deviceId, current.stale)
            vault.write(ApiJson.encodeToString(NeteaseCredential.serializer(), record))
            val authChanged = current.cookies["MUSIC_U"] != merged["MUSIC_U"]
            current = record; publish(state.value.revision + if (authChanged) 1 else 0)
        } }
    }
    internal suspend fun markStale(expected: NeteaseSession) = withContext(Dispatchers.IO) { mutex.withLock {
        check(expected)
        if (current.stale || !state.value.loggedIn) return@withLock
        val record = NeteaseCredential(current.cookies, current.profile, current.deviceId, stale = true)
        vault.write(ApiJson.encodeToString(NeteaseCredential.serializer(), record))
        current = record; publish(state.value.revision + 1)
    } }
    suspend fun clear() { ready.await(); withContext(Dispatchers.IO) { mutex.withLock {
        val record = NeteaseCredential(deviceId = current.deviceId)
        vault.write(ApiJson.encodeToString(NeteaseCredential.serializer(), record))
        current = record; publish(state.value.revision + 1)
    } } }
}
