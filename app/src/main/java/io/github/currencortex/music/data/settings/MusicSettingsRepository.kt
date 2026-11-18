package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import io.github.currencortex.music.core.config.ServerDefaults
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.media.PlaybackNetworkKind
import io.github.currencortex.music.core.network.ServerUrl
import io.github.currencortex.music.core.network.CurrentMusicServer
import io.github.currencortex.music.data.song.MusicSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class MusicSettings(val server: String = ServerDefaults.URL,
                         val mobileQuality: AudioQuality = AudioQuality.AUTO,
                         val wirelessQuality: AudioQuality = AudioQuality.AUTO,
                         val downloadQuality: AudioQuality = AudioQuality.AUTO,
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
                         val crossfadeSeconds: Int = 5,
                         val lyricsSource: LyricsSource = LyricsSource.AUTO,
                         val lyricsFont: LyricsFont = LyricsFont.SYSTEM, val lyricsFontFile: String = "",
                         val serverMode: CurrentMusicServer = CurrentMusicServer.AUTO,
                         val customServer: String = "",
                         val listeningSource: ListeningSource = ListeningSource.LOCAL,
                         val musicPlatform: MusicPlatform = MusicPlatform.NETEASE,
                         val qqMobileQuality: AudioQuality = AudioQuality.AUTO,
                         val qqWirelessQuality: AudioQuality = AudioQuality.AUTO,
                         val qqDownloadQuality: AudioQuality = AudioQuality.AUTO) {
    fun qualityFor(source: MusicSource?, network: PlaybackNetworkKind = PlaybackNetworkKind.MOBILE): AudioQuality =
        if (source == MusicSource.QQ_MUSIC) {
            if (network == PlaybackNetworkKind.WIRELESS) qqWirelessQuality else qqMobileQuality
        } else if (network == PlaybackNetworkKind.WIRELESS) wirelessQuality else mobileQuality

    fun downloadQualityFor(source: MusicSource?): AudioQuality =
        if (source == MusicSource.QQ_MUSIC) qqDownloadQuality else downloadQuality
}

enum class MusicPlatform(val label: String) {
    NETEASE("网易云音乐"), QQ_MUSIC("QQ 音乐");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: NETEASE }
}

enum class ListeningSource(val label: String) {
    LOCAL("CurrentMusicX"), NETEASE("网易云");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: LOCAL }
}

enum class LyricsFont(val label: String) {
    SYSTEM("系统字体"), WENKAI("霞鹜文楷"), IMPORTED("导入字体");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: SYSTEM }
}

enum class LyricsSource(val label: String) {
    AUTO("自动择优 · 逐字优先"), CURRENT_MUSIC("CurrentMusic 歌词"), NETEASE("网易云歌词"), LOCAL("本地内嵌歌词");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO }
}

enum class MusicTransitionMode { OFF, SMART, CROSSFADE;
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: OFF }
}

enum class KaraokeScope(val label: String) {
    CURRENT("仅当前行"), ALL("拓展全部行"), ALWAYS("总是");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: ALL }
}

enum class LyricsStyle(val label: String) {
    CLASSIC("经典"), APPLE_MUSIC("Apple Music");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: CLASSIC }
}

data class LyricsDisplayOptions(
    val centered: Boolean = false, val fontWeight: Int = 500,
    val blur: Boolean = true, val stagger: Boolean = true,
    val karaokeScope: KaraokeScope = KaraokeScope.ALL, val hideControls: Boolean = false,
    val translation: Boolean = true, val romanization: Boolean = false, val wordAnimation: Boolean = true,
    val perspective: Boolean = false,
    val glow: Boolean = true, val wordLift: Boolean = true,
    val smoke: Boolean = false,
    val particles: Boolean = false,
    val style: LyricsStyle = LyricsStyle.CLASSIC,
    val appleMusic: AppleMusicLyricsOptions = AppleMusicLyricsOptions(),
) {
    /** Classic stages own their layout; their saved options must not constrain Apple Music. */
    fun normalized(): LyricsDisplayOptions {
        val value = copy(appleMusic = appleMusic.normalized())
        return when {
            style == LyricsStyle.APPLE_MUSIC -> value
            particles -> value.copy(centered = true, perspective = false, smoke = false)
            smoke -> value.copy(centered = true, perspective = false)
            else -> value
        }
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
    private val platform = stringPreferencesKey("music.platform")
    private val server = stringPreferencesKey("music.server")
    private val serverMode = stringPreferencesKey("music.serverMode")
    private val customServer = stringPreferencesKey("music.customServer")
    private val quality = stringPreferencesKey("music.quality")
    private val mobileQuality = stringPreferencesKey("music.quality.mobile")
    private val wirelessQuality = stringPreferencesKey("music.quality.wireless")
    private val downloadQuality = stringPreferencesKey("music.quality.download")
    private val qqQuality = stringPreferencesKey("music.qqQuality")
    private val qqMobileQuality = stringPreferencesKey("music.qqQuality.mobile")
    private val qqWirelessQuality = stringPreferencesKey("music.qqQuality.wireless")
    private val qqDownloadQuality = stringPreferencesKey("music.qqQuality.download")
    private val restore = booleanPreferencesKey("music.restore")
    private val nickname = stringPreferencesKey("account.nickname")
    private val account = longPreferencesKey("account.id")
    private val preload = booleanPreferencesKey("music.preload")
    private val metered = booleanPreferencesKey("music.preloadMetered")
    private val lyricsSize = floatPreferencesKey("lyrics.fontSize")
    private val lyricsFont = stringPreferencesKey("lyrics.font")
    private val lyricsFontFile = stringPreferencesKey("lyrics.fontFile")
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
    private val lyricsStyle = stringPreferencesKey("lyrics.style")
    private val appleLineSpacing = floatPreferencesKey("lyrics.appleMusic.lineSpacing")
    private val appleLineHeight = floatPreferencesKey("lyrics.appleMusic.lineHeight")
    private val appleAnimationSpeed = floatPreferencesKey("lyrics.appleMusic.animationSpeed")
    private val appleGlowIntensity = floatPreferencesKey("lyrics.appleMusic.glowIntensity")
    private val appleBlurStrength = floatPreferencesKey("lyrics.appleMusic.blurStrength")
    private val neteaseMain = booleanPreferencesKey("library.neteaseMain")
    private val listeningSource = stringPreferencesKey("listening.source")
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
    private fun decodeAppleMusic(p: Preferences) = AppleMusicLyricsOptions(
        lineSpacing = p[appleLineSpacing] ?: AppleMusicLyricsOptions.DEFAULT_LINE_SPACING,
        lineHeight = p[appleLineHeight] ?: AppleMusicLyricsOptions.DEFAULT_LINE_HEIGHT,
        animationSpeed = p[appleAnimationSpeed] ?: AppleMusicLyricsOptions.DEFAULT_ANIMATION_SPEED,
        glowIntensity = p[appleGlowIntensity] ?: AppleMusicLyricsOptions.DEFAULT_GLOW_INTENSITY,
        blurStrength = p[appleBlurStrength] ?: AppleMusicLyricsOptions.DEFAULT_BLUR_STRENGTH,
    )
    private fun decode(p: Preferences): MusicSettings {
        val legacyQuality = AudioQuality.from(p[quality].orEmpty())
        val legacyQqQuality = p[qqQuality]?.let(AudioQuality::from) ?: when (legacyQuality) {
            AudioQuality.STANDARD -> AudioQuality.STANDARD
            AudioQuality.EXHIGH, AudioQuality.HIGHER -> AudioQuality.EXHIGH
            AudioQuality.LOSSLESS, AudioQuality.HIRES -> AudioQuality.LOSSLESS
            else -> AudioQuality.AUTO
        }
        return MusicSettings(p[server] ?: ServerDefaults.URL,
            p[mobileQuality]?.let(AudioQuality::from) ?: legacyQuality,
            p[wirelessQuality]?.let(AudioQuality::from) ?: legacyQuality,
            p[downloadQuality]?.let(AudioQuality::from) ?: legacyQuality,
        p[restore] ?: true, p[nickname].orEmpty(), p[account] ?: 0,
        p[preload] ?: true, p[metered] ?: false, LyricsTypography.normalize(p[lyricsSize] ?: LyricsTypography.DEFAULT_SIZE),
        LyricsWeight.from(p[lyricsWeight]), p[lyricsOffset] ?: 0,
        LyricsDisplayOptions(p[centered] ?: false, (p[fontWeight] ?: 500).coerceIn(400, 900),
            p[blur] ?: true, p[stagger] ?: true, KaraokeScope.from(p[karaokeScope]), p[hideControls] ?: false,
            p[translation] ?: true, p[romanization] ?: false, p[wordAnimation] ?: true,
            p[perspective] ?: false, p[glow] ?: true, p[wordLift] ?: true, p[smoke] ?: false, p[particles] ?: false,
            LyricsStyle.from(p[lyricsStyle]), decodeAppleMusic(p)).normalized(), p[neteaseMain] ?: true,
        p[concurrentPlayback] ?: false, MusicTransitionMode.from(p[transition]), (p[crossfadeSeconds] ?: 5).coerceIn(1, 12), decodeLyricsSource(p),
        LyricsFont.from(p[lyricsFont]), p[lyricsFontFile].orEmpty(),
        CurrentMusicServer.from(p[serverMode], p[server]),
            p[customServer] ?: p[server]?.takeIf { CurrentMusicServer.infer(it) == CurrentMusicServer.CUSTOM }.orEmpty(),
            ListeningSource.from(p[listeningSource]),
            MusicPlatform.from(p[platform]),
            p[qqMobileQuality]?.let(AudioQuality::from) ?: legacyQqQuality,
            p[qqWirelessQuality]?.let(AudioQuality::from) ?: legacyQqQuality,
            p[qqDownloadQuality]?.let(AudioQuality::from) ?: legacyQuality)
    }
    // Read the persisted value directly, without the StateFlow's initial defaults.
    val concurrentPlaybackEnabled = store.data.map { it[concurrentPlayback] ?: false }.distinctUntilChanged()
    val state = store.data.map(::decode)
        .stateIn(scope, SharingStarted.Eagerly, MusicSettings())
    val serverSelection = store.data.map {
        CurrentMusicServer.from(it[serverMode], it[server]) to (it[server] ?: ServerDefaults.URL)
    }.distinctUntilChanged()
    suspend fun snapshot(): MusicSettings {
        val p = store.data.first()
        return decode(p)
    }
    suspend fun setServer(value: String) {
        val normalized = ServerUrl.normalize(value)
        store.edit {
            // Restoring the same saved account must not turn AUTO/CDN back into MAIN.
            if (it[server] != normalized) it[serverMode] = CurrentMusicServer.infer(normalized).name
            it[server] = normalized
            if (CurrentMusicServer.infer(normalized) == CurrentMusicServer.CUSTOM) it[customServer] = normalized
        }
    }
    suspend fun setServerSelection(mode: CurrentMusicServer, value: String) {
        val normalized = ServerUrl.normalize(value)
        require(mode == CurrentMusicServer.CUSTOM || CurrentMusicServer.builtIn(normalized))
        store.edit {
            it[server] = normalized
            it[serverMode] = mode.name
            if (mode == CurrentMusicServer.CUSTOM) it[customServer] = normalized
        }
    }
    suspend fun setMobileQuality(value: AudioQuality, qq: Boolean = false) {
        store.edit { if (qq) it[qqMobileQuality] = value.value else it[mobileQuality] = value.value }
    }
    suspend fun setWirelessQuality(value: AudioQuality, qq: Boolean = false) {
        store.edit { if (qq) it[qqWirelessQuality] = value.value else it[wirelessQuality] = value.value }
    }
    suspend fun setDownloadQuality(value: AudioQuality, qq: Boolean = false) {
        store.edit { if (qq) it[qqDownloadQuality] = value.value else it[downloadQuality] = value.value }
    }
    suspend fun setRestore(value: Boolean) { store.edit { it[restore] = value } }
    suspend fun setConcurrentPlayback(value: Boolean) { store.edit { it[concurrentPlayback] = value } }
    suspend fun setTransitionMode(value: MusicTransitionMode) { store.edit { it[transition] = value.name } }
    suspend fun setCrossfadeSeconds(value: Int) { store.edit { it[crossfadeSeconds] = value.coerceIn(1, 12) } }
    suspend fun setPreload(value: Boolean) { store.edit { it[preload] = value } }
    suspend fun setPreloadMetered(value: Boolean) { store.edit { it[metered] = value } }
    suspend fun setLyricsFontSize(value: Float) { store.edit { it[lyricsSize] = LyricsTypography.normalize(value) } }
    suspend fun setLyricsFont(value: LyricsFont) { store.edit { it[lyricsFont] = value.name } }
    suspend fun setLyricsFontFile(value: String) { store.edit { it[lyricsFontFile] = value; it[lyricsFont] = LyricsFont.IMPORTED.name } }
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
            p[lyricsStyle] = value.style.name
            p[appleLineSpacing] = value.appleMusic.lineSpacing
            p[appleLineHeight] = value.appleMusic.lineHeight
            p[appleAnimationSpeed] = value.appleMusic.animationSpeed
            p[appleGlowIntensity] = value.appleMusic.glowIntensity
            p[appleBlurStrength] = value.appleMusic.blurStrength
        }
    }
    suspend fun setAccount(id: Long, name: String) { store.edit { it[account] = id; it[nickname] = name } }
    suspend fun setNeteaseMainLibrary(value: Boolean) { store.edit { it[neteaseMain] = value } }
    suspend fun setMusicPlatform(value: MusicPlatform) { store.edit { it[platform] = value.name } }
    suspend fun setListeningSource(value: ListeningSource) { store.edit { it[listeningSource] = value.name } }
}

private const val LYRICS_SOURCE_AUTOMATIC_VERSION = 1
