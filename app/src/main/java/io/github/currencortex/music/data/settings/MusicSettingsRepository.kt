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
                         val lyricsFontSize: Float = LyricsTypography.DEFAULT_SIZE,
                         val lyricsWeight: LyricsWeight = LyricsWeight.CURRENT,
                         val lyricsOffsetMs: Long = 0,
                         val lyricsDisplay: LyricsDisplayOptions = LyricsDisplayOptions(),
                         val neteaseMainLibrary: Boolean = true)

enum class KaraokeScope(val label: String) {
    CURRENT("仅当前行"), ALL("拓展全部行"), ALWAYS("总是");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: ALL }
}

data class LyricsDisplayOptions(
    val centered: Boolean = false, val fontWeight: Int = 500,
    val blur: Boolean = true, val stagger: Boolean = true,
    val karaokeScope: KaraokeScope = KaraokeScope.ALL, val hideControls: Boolean = false,
    val translation: Boolean = true, val romanization: Boolean = false, val wordAnimation: Boolean = true,
)

enum class LyricsWeight(val label: String) {
    NORMAL("常规"), ALL("全部粗体"), CURRENT("仅当前行粗体");
    fun isBold(current: Boolean) = this == ALL || (this == CURRENT && current)
    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: CURRENT
    }
}

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
    private val lyricsWeight = stringPreferencesKey("lyrics.weight")
    private val lyricsOffset = longPreferencesKey("lyrics.offsetMs")
    private val centered = booleanPreferencesKey("lyrics.centered")
    private val fontWeight = intPreferencesKey("lyrics.fontWeight")
    private val blur = booleanPreferencesKey("lyrics.blur")
    private val stagger = booleanPreferencesKey("lyrics.stagger")
    private val karaokeScope = stringPreferencesKey("lyrics.karaokeScope")
    private val hideControls = booleanPreferencesKey("lyrics.hideControls")
    private val translation = booleanPreferencesKey("lyrics.translation")
    private val romanization = booleanPreferencesKey("lyrics.romanization")
    private val wordAnimation = booleanPreferencesKey("lyrics.wordAnimation")
    private val neteaseMain = booleanPreferencesKey("library.neteaseMain")
    private fun decode(p: Preferences) = MusicSettings(p[server] ?: ServerDefaults.URL, AudioQuality.from(p[quality].orEmpty()),
        p[warning] ?: true, p[restore] ?: true, p[nickname].orEmpty(), p[account] ?: 0,
        p[preload] ?: true, p[metered] ?: false, LyricsTypography.normalize(p[lyricsSize] ?: LyricsTypography.DEFAULT_SIZE),
        LyricsWeight.from(p[lyricsWeight]), p[lyricsOffset] ?: 0,
        LyricsDisplayOptions(p[centered] ?: false, (p[fontWeight] ?: 500).coerceIn(400, 900),
            p[blur] ?: true, p[stagger] ?: true, KaraokeScope.from(p[karaokeScope]), p[hideControls] ?: false,
            p[translation] ?: true, p[romanization] ?: false, p[wordAnimation] ?: true), p[neteaseMain] ?: true)
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
    suspend fun setLyricsWeight(value: LyricsWeight) { store.edit { it[lyricsWeight] = value.name } }
    suspend fun setLyricsOffset(value: Long) { store.edit { it[lyricsOffset] = value.coerceIn(-60000, 60000) } }
    suspend fun editLyricsDisplay(transform: (LyricsDisplayOptions) -> LyricsDisplayOptions) {
        store.edit { p ->
            val value = transform(decode(p).lyricsDisplay)
            p[centered] = value.centered; p[fontWeight] = value.fontWeight.coerceIn(400, 900)
            p[blur] = value.blur; p[stagger] = value.stagger; p[karaokeScope] = value.karaokeScope.name
            p[hideControls] = value.hideControls; p[translation] = value.translation
            p[romanization] = value.romanization; p[wordAnimation] = value.wordAnimation
        }
    }
    suspend fun setAccount(id: Long, name: String) { store.edit { it[account] = id; it[nickname] = name } }
    suspend fun setNeteaseMainLibrary(value: Boolean) { store.edit { it[neteaseMain] = value } }
}
