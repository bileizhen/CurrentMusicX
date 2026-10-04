package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.core.config.ServerDefaults
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.ServerUrl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MusicSettings(val server: String = ServerDefaults.URL, val quality: AudioQuality = AudioQuality.AUTO,
                         val warnHighSpec: Boolean = true, val restoreQueue: Boolean = true,
                         val nickname: String = "", val accountId: Long = 0,
                         val preloadAudio: Boolean = true, val preloadMetered: Boolean = false,
                         val lyricsFontSize: Float = LyricsTypography.DEFAULT_SIZE)

object LyricsTypography {
    const val DEFAULT_SIZE = 30f
    const val MIN_SIZE = 22f
    const val MAX_SIZE = 40f
    fun normalize(value: Float) = if (value.isFinite()) value.coerceIn(MIN_SIZE, MAX_SIZE) else DEFAULT_SIZE
}
class MusicSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val server = stringPreferencesKey("music.server")
    private val quality = stringPreferencesKey("music.quality")
    private val warning = booleanPreferencesKey("music.warn")
    private val restore = booleanPreferencesKey("music.restore")
    private val nickname = stringPreferencesKey("account.nickname")
    private val account = longPreferencesKey("account.id")
    private val preload = booleanPreferencesKey("music.preload")
    private val metered = booleanPreferencesKey("music.preloadMetered")
    private val lyricsSize = floatPreferencesKey("lyrics.fontSize")
    private fun decode(p: Preferences) = MusicSettings(p[server] ?: ServerDefaults.URL, AudioQuality.from(p[quality].orEmpty()),
        p[warning] ?: true, p[restore] ?: true, p[nickname].orEmpty(), p[account] ?: 0,
        p[preload] ?: true, p[metered] ?: false, LyricsTypography.normalize(p[lyricsSize] ?: LyricsTypography.DEFAULT_SIZE))
    val state = store.data.map(::decode)
        .stateIn(scope, SharingStarted.Eagerly, MusicSettings())
    suspend fun snapshot(): MusicSettings {
        val p = store.data.first()
        return decode(p)
    }
    suspend fun setServer(value: String) { val normalized = ServerUrl.normalize(value); store.edit { it[server] = normalized } }
    suspend fun setQuality(value: AudioQuality) { store.edit { it[quality] = value.value } }
    suspend fun setWarning(value: Boolean) { store.edit { it[warning] = value } }
    suspend fun setRestore(value: Boolean) { store.edit { it[restore] = value } }
    suspend fun setPreload(value: Boolean) { store.edit { it[preload] = value } }
    suspend fun setPreloadMetered(value: Boolean) { store.edit { it[metered] = value } }
    suspend fun setLyricsFontSize(value: Float) { store.edit { it[lyricsSize] = LyricsTypography.normalize(value) } }
    suspend fun setAccount(id: Long, name: String) { store.edit { it[account] = id; it[nickname] = name } }
}
