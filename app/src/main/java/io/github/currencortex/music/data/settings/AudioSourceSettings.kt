package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.core.security.TokenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

enum class AudioProvider(val label: String) { CURRENT_MUSIC("CurrentMusic"), LEIZ("LeiZ API") }
data class AudioProviderState(val provider: AudioProvider = AudioProvider.CURRENT_MUSIC,
    val keyConfigured: Boolean = false, val identity: String = "currentmusic")

/** Immutable request credentials. Never include credentials in generated toString/equals/logs. */
class AudioSourceAccess(val provider: AudioProvider, internal val key: String = "") {
    val identity = if (provider == AudioProvider.CURRENT_MUSIC) "currentmusic" else "leiz:" +
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    override fun toString() = "AudioSourceAccess($provider, key=[redacted])"
}

class AudioSourceSettings(private val store: DataStore<Preferences>, private val vault: TokenStore, scope: CoroutineScope) {
    private val preference = stringPreferencesKey("music.audioProvider")
    private val lock = Mutex()
    @Volatile private var current = AudioSourceAccess(AudioProvider.CURRENT_MUSIC)
    private var savedKey = ""
    val state = MutableStateFlow(AudioProviderState())
    val ready = CompletableDeferred<Unit>()
    init { scope.launch(Dispatchers.IO) {
        lock.withLock {
            savedKey = runCatching { vault.read().orEmpty() }.getOrDefault("")
            val provider = runCatching { AudioProvider.valueOf(store.data.first()[preference].orEmpty()) }
                .getOrDefault(AudioProvider.CURRENT_MUSIC)
            publish(provider)
            ready.complete(Unit)
        }
    } }
    fun access() = current
    private fun publish(provider: AudioProvider) {
        current = AudioSourceAccess(provider, savedKey)
        state.value = AudioProviderState(provider, savedKey.isNotBlank(), current.identity)
    }
    suspend fun select(provider: AudioProvider) {
        ready.await()
        lock.withLock { store.edit { it[preference] = provider.name }; publish(provider) }
    }
    suspend fun saveKey(value: String) {
        val key = value.trim()
        require(key.isNotBlank() && key.length <= 512 && key.all { it.isLetterOrDigit() || it in "_-" })
        ready.await()
        withContext(Dispatchers.IO) { lock.withLock { vault.write(key); savedKey = key; publish(current.provider) } }
    }
    suspend fun clearKey() {
        ready.await()
        withContext(Dispatchers.IO) { lock.withLock { vault.clear(); savedKey = ""; publish(current.provider) } }
    }
}
