package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.core.config.ServerDefaults
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.ServerUrl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MusicSettings(val server: String = ServerDefaults.URL, val quality: AudioQuality = AudioQuality.AUTO,
                         val restoreQueue: Boolean = true,
                         val nickname: String = "", val accountId: Long = 0,
                         val preloadAudio: Boolean = true, val preloadMetered: Boolean = false,
                         val lyricsFontSize: Float = LyricsTypography.DEFAULT_SIZE,
                         val lyricsWeight: LyricsWeight = LyricsWeight.CURRENT,
                         val lyricsOffsetMs: Long = 0,
                         val lyricsDisplay: LyricsDisplayOptions = LyricsDisplayOptions(),
                         val neteaseMainLibrary: Boolean = true,
                         val allowConcurrentPlayback: Boolean = false,
                         val transitionMode: MusicTransitionMode = MusicTransitionMode.OFF,
                         val crossfadeSeconds: Int = 5, val likeMode: LikeMode = LikeMode.AUTO,
                         val lyricsSource: LyricsSource = LyricsSource.AUTO)

enum class LyricsSource(val label: String) {
    AUTO("自动择优 · 内容与时间轴校验"), CURRENT_MUSIC("CurrentMusic 歌词"), NETEASE("网易云歌词"), LOCAL("本地内嵌歌词");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO }
}

enum class LikeMode(val label: String) {
    AUTO("自动"), NETEASE("网易云 · 我喜欢"), CURRENT_MUSIC("CurrentMusic · 我喜欢");
    fun useNetease(bound: Boolean) = bound && this != CURRENT_MUSIC
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO }
}

enum class MusicTransitionMode { OFF, SMART, CROSSFADE;
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: OFF }
}

enum class KaraokeScope(val label: String) {
    CURRENT("仅当前行"), ALL("拓展全部行"), ALWAYS("总是");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: ALL }
}

data class LyricsDisplayOptions(
    val centered: Boolean = false, val fontWeight: Int = 500,
    val blur: Boolean = true, val stagger: Boolean = true,
    val karaokeScope: KaraokeScope = KaraokeScope.ALL, val hideControls: Boolean = false,
    val translation: Boolean = true, val romanization: Boolean = false, val wordAnimation: Boolean = true,
    val perspective: Boolean = true,
    val glow: Boolean = true, val wordLift: Boolean = true,
    val smoke: Boolean = false,
    val particles: Boolean = false,
) {
    /** Smoke owns a flat, centered stage; also repairs incompatible older preferences. */
    fun normalized() = when {
        particles -> copy(centered = true, perspective = false, smoke = false)
        smoke -> copy(centered = true, perspective = false)
        else -> this
    }
}

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
    private val restore = booleanPreferencesKey("music.restore")
    private val nickname = stringPreferencesKey("account.nickname")
    private val account = longPreferencesKey("account.id")
    private val preload = booleanPreferencesKey("music.preload")
    private val metered = booleanPreferencesKey("music.preloadMetered")
    private val lyricsSize = floatPreferencesKey("lyrics.fontSize")
    private val lyricsWeight = stringPreferencesKey("lyrics.weight")
    private val lyricsOffset = longPreferencesKey("lyrics.offsetMs")
    private val lyricsSource = stringPreferencesKey("lyrics.source")
    private val lyricsSourceMigration = intPreferencesKey("lyrics.source.automaticMigrationVersion")
    private val centered = booleanPreferencesKey("lyrics.centered")
    private val fontWeight = intPreferencesKey("lyrics.fontWeight")
    private val blur = booleanPreferencesKey("lyrics.blur")
    private val stagger = booleanPreferencesKey("lyrics.stagger")
    private val karaokeScope = stringPreferencesKey("lyrics.karaokeScope")
    private val hideControls = booleanPreferencesKey("lyrics.hideControls")
    private val translation = booleanPreferencesKey("lyrics.translation")
    private val romanization = booleanPreferencesKey("lyrics.romanization")
    private val wordAnimation = booleanPreferencesKey("lyrics.wordAnimation")
    private val perspective = booleanPreferencesKey("lyrics.perspective")
    private val glow = booleanPreferencesKey("lyrics.glow")
    private val wordLift = booleanPreferencesKey("lyrics.wordLift")
    private val smoke = booleanPreferencesKey("lyrics.smoke")
    private val particles = booleanPreferencesKey("lyrics.particles")
    private val neteaseMain = booleanPreferencesKey("library.neteaseMain")
    private val likeMode = stringPreferencesKey("music.likeMode")
    private val concurrentPlayback = booleanPreferencesKey("music.allowConcurrentPlayback")
    private val transition = stringPreferencesKey("music.transitionMode")
    private val crossfadeSeconds = intPreferencesKey("music.crossfadeSeconds")
    init {
        scope.launch {
            try {
                store.edit { p ->
                    if ((p[lyricsSourceMigration] ?: 0) < LYRICS_SOURCE_AUTOMATIC_VERSION) {
                        p[lyricsSource] = LyricsSource.AUTO.name
                        p[lyricsSourceMigration] = LYRICS_SOURCE_AUTOMATIC_VERSION
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: java.io.IOException) {
                // The read side already uses AUTO until migration commits. A temporarily
                // unavailable disk must not crash playback; a new repository retries it.
            }
        }
    }
    private fun decodeLyricsSource(p: Preferences) =
        if ((p[lyricsSourceMigration] ?: 0) < LYRICS_SOURCE_AUTOMATIC_VERSION) LyricsSource.AUTO
        else LyricsSource.from(p[lyricsSource])
    private fun decode(p: Preferences) = MusicSettings(p[server] ?: ServerDefaults.URL, AudioQuality.from(p[quality].orEmpty()),
        p[restore] ?: true, p[nickname].orEmpty(), p[account] ?: 0,
        p[preload] ?: true, p[metered] ?: false, LyricsTypography.normalize(p[lyricsSize] ?: LyricsTypography.DEFAULT_SIZE),
        LyricsWeight.from(p[lyricsWeight]), p[lyricsOffset] ?: 0,
        LyricsDisplayOptions(p[centered] ?: false, (p[fontWeight] ?: 500).coerceIn(400, 900),
            p[blur] ?: true, p[stagger] ?: true, KaraokeScope.from(p[karaokeScope]), p[hideControls] ?: false,
            p[translation] ?: true, p[romanization] ?: false, p[wordAnimation] ?: true,
            p[perspective] ?: true, p[glow] ?: true, p[wordLift] ?: true, p[smoke] ?: false, p[particles] ?: false).normalized(), p[neteaseMain] ?: true,
        p[concurrentPlayback] ?: false, MusicTransitionMode.from(p[transition]), (p[crossfadeSeconds] ?: 5).coerceIn(1, 12), LikeMode.from(p[likeMode]), decodeLyricsSource(p))
    // Read the persisted value directly, without the StateFlow's initial defaults.
    val concurrentPlaybackEnabled = store.data.map { it[concurrentPlayback] ?: false }.distinctUntilChanged()
    val state = store.data.map(::decode)
        .stateIn(scope, SharingStarted.Eagerly, MusicSettings())
    suspend fun snapshot(): MusicSettings {
        val p = store.data.first()
        return decode(p)
    }
    suspend fun setServer(value: String) { val normalized = ServerUrl.normalize(value); store.edit { it[server] = normalized } }
    suspend fun setQuality(value: AudioQuality) { store.edit { it[quality] = value.value } }
    suspend fun setRestore(value: Boolean) { store.edit { it[restore] = value } }
    suspend fun setConcurrentPlayback(value: Boolean) { store.edit { it[concurrentPlayback] = value } }
    suspend fun setTransitionMode(value: MusicTransitionMode) { store.edit { it[transition] = value.name } }
    suspend fun setCrossfadeSeconds(value: Int) { store.edit { it[crossfadeSeconds] = value.coerceIn(1, 12) } }
    suspend fun setPreload(value: Boolean) { store.edit { it[preload] = value } }
    suspend fun setPreloadMetered(value: Boolean) { store.edit { it[metered] = value } }
    suspend fun setLyricsFontSize(value: Float) { store.edit { it[lyricsSize] = LyricsTypography.normalize(value) } }
    suspend fun setLyricsWeight(value: LyricsWeight) { store.edit { it[lyricsWeight] = value.name } }
    suspend fun setLyricsOffset(value: Long) { store.edit { it[lyricsOffset] = value.coerceIn(-60000, 60000) } }
    suspend fun setLyricsSource(value: LyricsSource) {
        store.edit {
            it[lyricsSource] = value.name
            // A user choice made before startup migration runs must already be authoritative.
            it[lyricsSourceMigration] = maxOf(it[lyricsSourceMigration] ?: 0, LYRICS_SOURCE_AUTOMATIC_VERSION)
        }
    }
    suspend fun editLyricsDisplay(transform: (LyricsDisplayOptions) -> LyricsDisplayOptions) {
        store.edit { p ->
            val value = transform(decode(p).lyricsDisplay).normalized()
            p[centered] = value.centered; p[fontWeight] = value.fontWeight.coerceIn(400, 900)
            p[blur] = value.blur; p[stagger] = value.stagger; p[karaokeScope] = value.karaokeScope.name
            p[hideControls] = value.hideControls; p[translation] = value.translation
            p[romanization] = value.romanization; p[wordAnimation] = value.wordAnimation
            p[perspective] = value.perspective
            p[glow] = value.glow; p[wordLift] = value.wordLift
            p[smoke] = value.smoke
            p[particles] = value.particles
        }
    }
    suspend fun setAccount(id: Long, name: String) { store.edit { it[account] = id; it[nickname] = name } }
    suspend fun setNeteaseMainLibrary(value: Boolean) { store.edit { it[neteaseMain] = value } }
    suspend fun setLikeMode(value: LikeMode) { store.edit { it[likeMode] = value.name } }
}

private const val LYRICS_SOURCE_AUTOMATIC_VERSION = 1
