package io.github.currencortex.music.core.media

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.SongPreviewClip
import io.github.currencortex.music.data.settings.RecordShelfPreviewMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal data class RecordPreviewSource(val uri: String, val cacheKey: String, val clip: SongPreviewClip, val fullLength: Boolean = false)
internal interface RecordPreviewAudio {
    var gain: Float
    val positionMs: Long
    val playing: Boolean
    val ended: Boolean
    val failed: Boolean get() = false
    val durationMs: Long get() = 0L
    val buffering: Boolean get() = false
    suspend fun prepare(source: RecordPreviewSource)
    fun play()
    fun stop()
    fun release()
}
internal data class RecordPreviewState(val song: Song? = null, val label: String = "", val audible: Boolean = false)

/** One cancellable audition deck. A new selection drains the old gain before replacing its source. */
internal class RecordPreviewController(
    private val scope: CoroutineScope,
    private val audio: RecordPreviewAudio,
    private val resolve: suspend (Song) -> RecordPreviewSource,
    private val beforePlay: suspend () -> Unit = {},
    private val stillAllowed: () -> Boolean = { true },
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutable = MutableStateFlow(RecordPreviewState())
    val state = mutable.asStateFlow()
    private val next = MutableSharedFlow<Song>(extraBufferCapacity = 1)
    val nextRequests = next.asSharedFlow()
    private var mode = RecordShelfPreviewMode.ONCE
    private var fullLength = false
    private var finishedNaturally = false
    private var work: Job? = null
    private var selected: Song? = null
    private var closed = false
    init { audio.gain = 0f }

    fun setFullLength(value: Boolean) {
        if (closed || fullLength == value) return
        fullLength = value
        selected?.takeIf { stillAllowed() }?.let(::start)
    }

    fun setMode(value: RecordShelfPreviewMode) {
        if (closed || mode == value) return
        mode = value
        val song = selected ?: return
        if (!finishedNaturally || !stillAllowed()) return
        when (value) {
            RecordShelfPreviewMode.LOOP -> start(song)
            RecordShelfPreviewMode.NEXT, RecordShelfPreviewMode.RANDOM -> { finishedNaturally = false; next.tryEmit(song) }
            RecordShelfPreviewMode.ONCE -> Unit
        }
    }

    fun select(song: Song?) {
        if (closed || selected == song) return
        start(song)
    }

    private fun start(song: Song?) {
        finishedNaturally = false
        selected = song
        work?.cancel()
        work = scope.launch {
            try {
                coroutineScope {
                    val loading = song?.let { async { delay(90); withTimeout(12_000) { resolve(it) } } }
                    fade(0f, 220); audio.stop()
                    if (song == null) { mutable.value = RecordPreviewState(); return@coroutineScope }
                    mutable.value = RecordPreviewState(song, "正在准备试听")
                    var source = requireNotNull(loading).await()
                    while (isActive) {
                        if (!stillAllowed()) { audio.stop(); mutable.value = RecordPreviewState(); return@coroutineScope }
                        val audition = if (fullLength) source.copy(clip = SongPreviewClip(0,
                            song.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE, false), fullLength = true) else source
                        withTimeout(12_000) { audio.prepare(audition) }
                        beforePlay()
                        if (!stillAllowed()) { audio.stop(); mutable.value = RecordPreviewState(); return@coroutineScope }
                        audio.play()
                        withTimeout(8_000) { while (!audio.playing) { ensureActive(); delay(16) } }
                        mutable.value = RecordPreviewState(song, when {
                            audition.fullLength -> "整首试听"
                            audition.clip.chorus -> "高潮试听"
                            else -> "片段试听"
                        }, true)
                        val duration = if (audition.fullLength) audio.durationMs.takeIf { it > 0 } ?: audition.clip.durationMs else audition.clip.durationMs
                        val fadeOut = minOf(400, duration / 4).coerceAtLeast(1)
                        fade(1f, minOf(500, duration / 4).coerceAtLeast(1))
                        val fadeAt = duration - fadeOut - 50
                        while (stillAllowed() && !audio.failed && !audio.ended && (audio.playing || audio.buffering) && audio.positionMs < fadeAt) delay(30)
                        val reachedEnd = !audio.failed && (audio.ended || (audio.playing && audio.positionMs >= fadeAt))
                        fade(0f, fadeOut)
                        finishedNaturally = reachedEnd && stillAllowed() && !audio.failed && (audio.playing || audio.ended)
                        audio.stop()
                        mutable.value = RecordPreviewState(song, "试听结束")
                        if (!finishedNaturally) break
                        when (mode) {
                            RecordShelfPreviewMode.ONCE -> break
                            RecordShelfPreviewMode.NEXT, RecordShelfPreviewMode.RANDOM -> { finishedNaturally = false; next.emit(song); break }
                            RecordShelfPreviewMode.LOOP -> {
                                finishedNaturally = false
                                source = withTimeout(12_000) { resolve(song) }
                            }
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                fade(0f, 180); audio.stop()
                mutable.value = RecordPreviewState(song, "试听加载超时")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                fade(0f, 180); audio.stop()
                mutable.value = RecordPreviewState(song, "暂时无法试听")
            }
        }
    }

    fun finish(release: Boolean = false, after: () -> Unit = {}) {
        if (closed) return
        finishedNaturally = false
        if (release) closed = true
        work?.cancel()
        work = scope.launch {
            fade(0f, 220); audio.stop(); mutable.value = RecordPreviewState()
            if (release) audio.release()
            after()
        }
    }

    private suspend fun fade(target: Float, duration: Long) {
        val from = audio.gain
        if (from == target) return
        val start = nowMs()
        while (true) {
            currentCoroutineContext().ensureActive()
            val t = ((nowMs() - start).toFloat() / duration).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            audio.gain = from + (target - from) * eased
            if (t == 1f) return
            delay(16)
        }
    }
}
