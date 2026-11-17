package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.song.ImportedServerAudioSource
import io.github.currencortex.music.data.song.SERVER_AUDIO_SOURCE_IDENTITY_PREFIX
import io.github.currencortex.music.data.song.ServerAudioSource
import io.github.currencortex.music.data.song.ServerAudioSourceOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

enum class AudioProvider(val label: String) { NETEASE("平台直连"), LEIZ("LeiZ API"), LX("落雪音源"), SERVER("服务器音源") }
data class AudioProviderState(val provider: AudioProvider = AudioProvider.NETEASE,
    val keyConfigured: Boolean = false, val identity: String = "netease", val lxSourceId: String = "", val lxSmartSwitch: Boolean = false,
    val serverSource: ServerAudioSource? = null)

/** Immutable request credentials. Never include credentials in generated toString/equals/logs. */
class AudioSourceAccess(val provider: AudioProvider, internal val key: String = "", val lxSourceId: String = "",
    val lxSmartSwitch: Boolean = false, internal val serverSource: ServerAudioSource? = null,
    internal val serverSourceOwner: ServerAudioSourceOwner? = null) {
    val identity = when (provider) {
        AudioProvider.NETEASE -> "netease"
        AudioProvider.LEIZ -> "leiz:" + MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        AudioProvider.LX -> if (lxSmartSwitch) "lx:$lxSourceId:smart" else "lx:$lxSourceId"
        AudioProvider.SERVER -> SERVER_AUDIO_SOURCE_IDENTITY_PREFIX + requireNotNull(serverSource).id
    }
    override fun toString() = "AudioSourceAccess($provider, key=[redacted])"
}

class AudioSourceSettings(private val store: DataStore<Preferences>, private val vault: TokenStore, scope: CoroutineScope,
    private val serverSourceOwner: () -> ServerAudioSourceOwner? = { null }) {
    private val preference = stringPreferencesKey("music.audioProvider")
    private val resumePreference = stringPreferencesKey("music.audioProviderResume")
    private val lxPreference = stringPreferencesKey("music.lxSourceId")
    private val smartPreference = booleanPreferencesKey("music.lxSmartSwitch")
    private val serverSourcesPreference = stringPreferencesKey("music.serverAudioSources")
    private val selectedServerOwnerPreference = stringPreferencesKey("music.selectedServerAudioSourceOwner")
    private var savedSmartSwitch = false
    private val lock = Mutex()
    @Volatile private var current = AudioSourceAccess(AudioProvider.NETEASE)
    private var savedKey = ""
    private var savedLxSource = ""
    private var serverSources = emptyMap<ServerAudioSourceOwner, ServerAudioSource>()
    private var selectedServerOwner: ServerAudioSourceOwner? = null
    val state = MutableStateFlow(AudioProviderState())
    val ready = CompletableDeferred<Unit>()
    init { scope.launch(Dispatchers.IO) {
        lock.withLock {
            val preferences = store.data.first()
            savedKey = runCatching { vault.read().orEmpty() }.getOrDefault("")
            savedLxSource = preferences[lxPreference].orEmpty().takeIf { it.matches(Regex("[a-f0-9]{64}")) }.orEmpty()
            savedSmartSwitch = preferences[smartPreference] ?: false
            serverSources = preferences[serverSourcesPreference]?.let { raw ->
                runCatching { ApiJson.decodeFromString<List<ImportedServerAudioSource>>(raw) }.getOrDefault(emptyList())
                    .filter { it.source.supported() }.associate { it.owner to it.source }
            }.orEmpty()
            selectedServerOwner = preferences[selectedServerOwnerPreference]?.let {
                runCatching { ApiJson.decodeFromString<ServerAudioSourceOwner>(it) }.getOrNull()
            }
            val savedProvider = preferences[preference]
            val provider = decodeAudioProvider(savedProvider)?.takeIf {
                it != AudioProvider.SERVER || selectedServerOwner?.let(serverSources::get) != null
            } ?: AudioProvider.NETEASE
            val providerChanged = savedProvider != null && savedProvider != provider.name
            val resumeStale = preferences[resumePreference] != null
            if (providerChanged || resumeStale) {
                try {
                    store.edit { settings ->
                        if (providerChanged) settings[preference] = provider.name
                        if (resumeStale) settings.remove(resumePreference)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
            }
            publish(provider)
            ready.complete(Unit)
        }
    } }
    fun access(): AudioSourceAccess {
        val saved = current
        return if (serverSourceDetached(saved)) AudioSourceAccess(AudioProvider.NETEASE) else saved
    }

    internal fun hasDetachedServerSource(): Boolean = serverSourceDetached(current)

    private fun serverSourceDetached(source: AudioSourceAccess): Boolean =
        source.provider == AudioProvider.SERVER && source.serverSourceOwner != serverSourceOwner()

    private fun publish(provider: AudioProvider) {
        current = AudioSourceAccess(provider, savedKey, savedLxSource, savedSmartSwitch,
            selectedServerOwner?.let(serverSources::get), selectedServerOwner)
        val effective = access()
        state.value = AudioProviderState(effective.provider, savedKey.isNotBlank(), effective.identity, savedLxSource,
            savedSmartSwitch, serverSourceOwner()?.let(serverSources::get))
    }
    suspend fun select(provider: AudioProvider) {
        ready.await()
        lock.withLock {
            val owner = if (provider == AudioProvider.SERVER) serverSourceOwner() else null
            if (provider == AudioProvider.SERVER && owner?.let(serverSources::get) == null)
                throw ApiException(ErrorKind.AudioSourceChanged, userMessage = "请先从当前服务器导入音源")
            store.edit { preferences ->
                if (owner != null && owner != serverSourceOwner()) throw ApiException(ErrorKind.Unauthorized)
                preferences.selectProvider(provider, owner)
            }
            selectedServerOwner = owner
            publish(provider)
        }
    }

    internal suspend fun importServerSource(owner: ServerAudioSourceOwner, source: ServerAudioSource, isCurrentSession: () -> Boolean) {
        require(source.supported())
        ready.await()
        lock.withLock {
            val updated = serverSources + (owner to source)
            store.edit { preferences ->
                if (owner != serverSourceOwner() || !isCurrentSession()) throw ApiException(ErrorKind.Unauthorized)
                preferences[serverSourcesPreference] = encodeServerSources(updated)
                preferences.selectProvider(AudioProvider.SERVER, owner)
            }
            serverSources = updated
            selectedServerOwner = owner
            publish(AudioProvider.SERVER)
        }
    }

    internal suspend fun removeServerSource() {
        ready.await()
        lock.withLock {
            val owner = serverSourceOwner() ?: return
            val updated = serverSources - owner
            val selected = current.provider == AudioProvider.SERVER && selectedServerOwner == owner
            store.edit { preferences ->
                if (owner != serverSourceOwner()) throw ApiException(ErrorKind.Unauthorized)
                preferences[serverSourcesPreference] = encodeServerSources(updated)
                if (selected) preferences.selectProvider(AudioProvider.NETEASE, null)
            }
            serverSources = updated
            if (selected) selectedServerOwner = null
            publish(if (selected) AudioProvider.NETEASE else current.provider)
        }
    }

    internal suspend fun refreshServerSourceOwner(): Boolean {
        ready.await()
        return lock.withLock {
            val detach = hasDetachedServerSource()
            if (detach) {
                store.edit { it.selectProvider(AudioProvider.NETEASE, null) }
                selectedServerOwner = null
            }
            publish(if (detach) AudioProvider.NETEASE else current.provider)
            detach
        }
    }

    private fun MutablePreferences.selectProvider(provider: AudioProvider, owner: ServerAudioSourceOwner?) {
        this[preference] = provider.name
        if (owner == null) remove(selectedServerOwnerPreference)
        else this[selectedServerOwnerPreference] = ApiJson.encodeToString(ServerAudioSourceOwner.serializer(), owner)
    }

    private fun encodeServerSources(sources: Map<ServerAudioSourceOwner, ServerAudioSource>): String =
        ApiJson.encodeToString(sources.map { (owner, source) -> ImportedServerAudioSource(owner, source) })
    suspend fun selectLxSource(id: String) {
        require(id.isEmpty() || id.matches(Regex("[a-f0-9]{64}"))) { "请选择有效的落雪音源" }
        ready.await()
        lock.withLock {
            store.edit { it[lxPreference] = id }
            savedLxSource = id
            publish(current.provider)
        }
    }
    suspend fun setLxSmartSwitch(enabled: Boolean) {
        ready.await()
        lock.withLock {
            store.edit { it[smartPreference] = enabled }
            savedSmartSwitch = enabled
            publish(current.provider)
        }
    }
    suspend fun saveKey(value: String) {
        val key = value.trim()
        require(key.isNotBlank() && key.length <= 512 && key.all { it.isLetterOrDigit() || it in "_-" }) { "请输入有效的 API Key" }
        ready.await()
        withContext(Dispatchers.IO) { lock.withLock { vault.write(key); savedKey = key; publish(current.provider) } }
    }
    suspend fun clearKey() {
        ready.await()
        withContext(Dispatchers.IO) { lock.withLock { vault.clear(); savedKey = ""; publish(current.provider) } }
    }
}

internal fun decodeAudioProvider(value: String?): AudioProvider? = when (value) {
    "CURRENT_MUSIC", "QQ_MUSIC" -> AudioProvider.NETEASE
    else -> value?.let { runCatching { AudioProvider.valueOf(it) }.getOrNull() }
}
