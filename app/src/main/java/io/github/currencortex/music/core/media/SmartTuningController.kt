package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.effects.NeteaseSongAnalysis
import io.github.currencortex.music.data.settings.EqualizerPreset
import io.github.currencortex.music.data.settings.EqualizerSettings
import io.github.currencortex.music.data.settings.SoundEffectsPreferences
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SmartTuningState(
    val songId: Long? = null,
    val analysis: NeteaseSongAnalysis? = null,
    val loading: Boolean = false,
    val message: String = "智能调音已关闭，使用手动均衡器",
    val gains: List<Int>? = null,
    val active: Boolean = false,
)

/** NetEase provides analysis; these conservative EQ adjustments are computed locally. */
internal fun localSmartCurve(analysis: NeteaseSongAnalysis): List<Int>? {
    fun Float?.unit() = this?.takeIf { it.isFinite() && it in 0f..1f }
    val bass = analysis.bassEnergy.unit()
    val vocal = analysis.vocalEnergy.unit()
    val bpm = analysis.bpm?.takeIf { it.isFinite() && it in 30f..300f }
    if (bass == null && vocal == null && bpm == null) return null
    // Small corrections around a neutral mix; emotion labels are descriptive only.
    val low = bass?.let { ((.45f - it) * 300f).coerceIn(-150f, 150f) } ?: 0f
    val voice = vocal?.let { ((.5f - it) * 240f).coerceIn(-120f, 120f) } ?: 0f
    val presence = bpm?.let { ((it - 110f) / 100f * 60f).coerceIn(-40f, 70f) } ?: 0f
    return listOf(low, low * .5f, voice * .5f, voice + presence, presence * .5f).map {
        ((it / 100f).roundToInt() * 100).coerceIn(-300, 300)
    }
}

class SmartTuningController(
    manual: StateFlow<EqualizerSettings>,
    preferences: StateFlow<SoundEffectsPreferences>,
    songs: Flow<Song?>,
    private val analyze: suspend (Long) -> NeteaseSongAnalysis?,
    private val scope: CoroutineScope,
) : java.io.Closeable {
    private data class Target(val id: Long, val source: MusicSource, val video: Boolean)
    private val mutableState = MutableStateFlow(SmartTuningState())
    private val manualPreview = MutableStateFlow<EqualizerSettings?>(null)
    val state: StateFlow<SmartTuningState> = mutableState.asStateFlow()
    val effective: StateFlow<EqualizerSettings> = combine(manual, preferences, state, manualPreview) { saved, config, runtime, preview ->
        if (preview != null) preview
        else if (config.smartEnabled && runtime.active && runtime.gains != null)
            EqualizerSettings(true, EqualizerPreset.CUSTOM, runtime.gains).normalized()
        else saved
    }.stateIn(scope, SharingStarted.Eagerly, manual.value)
    private var generation = 0L
    private var request: Job? = null
    private val manualObserver = scope.launch {
        manual.collect { saved ->
            // A committed slider value no longer needs a transient override.
            if (manualPreview.value == saved) manualPreview.value = null
        }
    }
    private val observer = scope.launch {
        combine(songs.map { it?.let { song -> Target(song.id, song.musicSource, song.video) } }.distinctUntilChanged(),
            preferences.map { it.smartEnabled }.distinctUntilChanged()) { target, enabled -> target to enabled }
            .distinctUntilChanged().collect { (target, enabled) ->
                val token = ++generation
                request?.cancel()
                if (enabled) manualPreview.value = null
                mutableState.value = when {
                    !enabled -> SmartTuningState(songId = target?.id)
                    target == null -> SmartTuningState(message = "开始播放网易云歌曲后自动分析")
                    target.source != MusicSource.NETEASE || target.video || target.id <= 0 ->
                        SmartTuningState(songId = target.id, message = "此音源暂不支持网易云分析，使用手动均衡器")
                    else -> SmartTuningState(songId = target.id, loading = true, message = "正在获取网易云歌曲分析")
                }
                if (!enabled || target == null || target.source != MusicSource.NETEASE || target.video || target.id <= 0) return@collect
                // Do not wait for a cancelled transport: a late old request cannot delay the next track.
                request = scope.launch requestJob@ {
                    val analysis = try { analyze(target.id) } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { null }
                    if (token != generation) return@requestJob
                    val valid = analysis?.takeIf { it.songId == target.id }
                    val gains = valid?.let(::localSmartCurve)
                    mutableState.value = SmartTuningState(target.id, valid, message =
                        if (gains != null) "网易云歌曲分析 · 本机智能调音"
                        else "未取得可用分析，使用手动均衡器", gains = gains, active = gains != null)
                }
            }
    }
    /** Synchronous override closes the gap before DataStore publishes smartEnabled=false. */
    fun previewManual(settings: EqualizerSettings) {
        ++generation
        request?.cancel()
        mutableState.value = SmartTuningState(songId = state.value.songId)
        manualPreview.value = settings.normalized()
    }
    fun clearManualPreview() { manualPreview.value = null }
    override fun close() { ++generation; request?.cancel(); observer.cancel(); manualObserver.cancel() }
}
