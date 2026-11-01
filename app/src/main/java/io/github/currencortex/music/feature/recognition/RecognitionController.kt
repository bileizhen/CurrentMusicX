package io.github.currencortex.music.feature.recognition

import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class RecognitionPhase { IDLE, RECORDING, RECOGNIZING, MATCHED, NO_MATCH, ERROR }
enum class RecognitionAudioSource { SYSTEM, MICROPHONE }
enum class RecognitionStep { CAPTURE, ANALYZE, MATCH }
data class RecognitionState(val phase: RecognitionPhase = RecognitionPhase.IDLE, val seconds: Int = 0,
    val level: Float = 0f, val songs: List<Song> = emptyList(), val message: String? = null,
    val source: RecognitionAudioSource = RecognitionAudioSource.MICROPHONE,
    val step: RecognitionStep = RecognitionStep.CAPTURE, val round: Int = 0) {
    val busy get() = phase == RecognitionPhase.RECORDING || phase == RecognitionPhase.RECOGNIZING
}

fun interface RecognitionRecorder {
    suspend fun record(stop: AtomicBoolean, progress: (Int, Float) -> Unit): ShortArray

    /** Each window transfers ownership of an independent cumulative 8 kHz PCM copy. */
    suspend fun recordStreaming(stop: AtomicBoolean, progress: (Int, Float) -> Unit,
        onWindow: (ShortArray) -> Unit): ShortArray = record(stop, progress)
}

/** Continuous capture, fingerprint matching and the optional lyric query share one attempt. */
class RecognitionController(private val scope: CoroutineScope, private val recorder: RecognitionRecorder,
    private val fingerprint: suspend (ShortArray) -> String,
    private val recognize: suspend (String, Double) -> List<Song>,
    private val source: RecognitionAudioSource = RecognitionAudioSource.MICROPHONE,
    private val recognizeInSession: suspend (String, Double, String, Int) -> List<Song> = { fp, seconds, _, _ -> recognize(fp, seconds) },
    private val recognizeLyrics: (suspend (ShortArray, String) -> List<Song>)? = null) {
    private val mutable = MutableStateFlow(RecognitionState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var stop = AtomicBoolean(false)
    private val epoch = AtomicLong(0)

    fun start() {
        if (state.value.busy) return
        work?.cancel()
        val attempt = epoch.incrementAndGet()
        val session = UUID.randomUUID().toString()
        stop = AtomicBoolean(false)
        val requestStop = stop
        mutable.value = RecognitionState(RecognitionPhase.RECORDING, source = source)
        fun publish(transform: (RecognitionState) -> RecognitionState) {
            mutable.update { if (epoch.get() == attempt) transform(it) else it }
        }
        work = scope.launch {
            try {
                withTimeout(42_000) { supervisorScope {
                    // Slow HTTP must never block recording or accumulate obsolete audio copies.
                    val windows = Channel<ShortArray>(Channel.CONFLATED, onUndeliveredElement = { it.fill(0) })
                    val lyricWindows = Channel<ShortArray>(1, onUndeliveredElement = { it.fill(0) })
                    val results = Channel<BranchResult>(2)
                    var latestCaptureSize = -1
                    var lastAfpOffered = -1
                    var lyricsOffered = false
                    fun offer(samples: ShortArray, final: Boolean = false) {
                        if (samples.size < latestCaptureSize) { samples.fill(0); return }
                        latestCaptureSize = samples.size
                        if (recognizeLyrics != null && !lyricsOffered && samples.size in 80_000..120_000) {
                            lyricsOffered = true
                            val lyricPcm = if (samples.size == 80_000) samples else samples.copyOf(80_000)
                            if (lyricWindows.trySend(lyricPcm).isFailure) lyricPcm.fill(0)
                            if (samples.size == 80_000) return
                        }
                        // The ten-second milestone belongs to the auxiliary lyric query, not AFP.
                        // Preserve a manually finished ten-second AFP clip when no lyric matcher
                        // exists, while never adding an AFP request for the streaming milestone.
                        if (samples.size == 80_000 && (recognizeLyrics != null || !final)) { samples.fill(0); return }
                        if (samples.size <= lastAfpOffered) { samples.fill(0); return }
                        lastAfpOffered = samples.size
                        if (windows.trySend(samples).isFailure) samples.fill(0)
                    }
                    fun closeWindows(cause: Throwable? = null) {
                        windows.close(cause)
                        lyricWindows.close(cause)
                    }
                    fun validate(samples: ShortArray) {
                        check(samples.size >= 16_000) { "片段太短，请至少听满 2 秒后再识别" }
                        check(samples.size <= 120_000) { "识别片段过长，请重新录音" }
                        check(samples.any { it != 0.toShort() }) {
                            if (source == RecognitionAudioSource.SYSTEM) "未采集到系统声音，请播放音乐或切换录制授权方式"
                            else "未采集到麦克风声音，请检查麦克风隐私开关、录音占用或靠近音源"
                        }
                    }
                    val capture = launch {
                        try {
                            val final = withTimeout(18_000) {
                                recorder.recordStreaming(requestStop, { seconds, level ->
                                    publish { it.copy(seconds = seconds, level = level) }
                                }, { offer(it) })
                            }
                            offer(final, final = true)
                            publish { it.copy(phase = RecognitionPhase.RECOGNIZING, level = 0f) }
                            closeWindows()
                        } catch (timeout: TimeoutCancellationException) {
                            closeWindows(IllegalStateException("采集声音超时，请切换采集方式", timeout))
                        } catch (failure: Throwable) {
                            closeWindows(failure)
                        }
                    }
                    val fingerprintWorker = launch {
                        var result = BranchResult()
                        var round = 0
                        try {
                            for (samples in windows) {
                                var analyzing = true
                                try {
                                    validate(samples)
                                    publish { it.copy(step = RecognitionStep.ANALYZE) }
                                    val encoded = withTimeout(10_000) { fingerprint(samples) }
                                    analyzing = false
                                    round++
                                    publish { it.copy(step = RecognitionStep.MATCH, round = round) }
                                    val songs = withTimeout(15_000) {
                                        recognizeInSession(encoded, samples.size / 8000.0, session, round)
                                    }
                                    ensureActive()
                                    result = BranchResult(songs = songs)
                                    if (songs.isNotEmpty()) break
                                } catch (timeout: TimeoutCancellationException) {
                                    currentCoroutineContext().ensureActive()
                                    result = BranchResult(failure = IllegalStateException(if (analyzing)
                                        "声音处理超时，请重试" else "网易云匹配超时，请检查网络后重试", timeout))
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { result = BranchResult(failure = failure) }
                                finally { samples.fill(0) }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { result = BranchResult(failure = failure) }
                        results.send(result)
                    }
                    val lyricWorker = recognizeLyrics?.let { matchLyrics -> launch {
                        var result = BranchResult()
                        try {
                            val samples = lyricWindows.receive()
                            try {
                                validate(samples)
                                publish { it.copy(step = RecognitionStep.MATCH) }
                                val songs = withTimeout(20_000) { matchLyrics(samples, session) }
                                ensureActive()
                                result = BranchResult(songs = songs)
                            } catch (timeout: TimeoutCancellationException) {
                                currentCoroutineContext().ensureActive()
                                result = BranchResult(failure = IllegalStateException("歌词辅助匹配超时，请检查网络后重试", timeout))
                            } finally { samples.fill(0) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (closed: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                            // Capture can end before ten seconds; there is no auxiliary query then.
                        } catch (failure: Exception) { result = BranchResult(failure = failure) }
                        results.send(result)
                    } }
                    val jobs = listOfNotNull(capture, fingerprintWorker, lyricWorker)
                    suspend fun stopWorkers() {
                        requestStop.set(true)
                        windows.cancel()
                        lyricWindows.cancel()
                        jobs.forEach { it.cancel() }
                        withContext(NonCancellable) { jobs.joinAll() }
                    }
                    try {
                        val completed = mutableListOf<BranchResult>()
                        repeat(if (lyricWorker == null) 1 else 2) {
                            val result = results.receive()
                            if (result.songs.isNotEmpty()) {
                                stopWorkers()
                                publish { it.copy(phase = RecognitionPhase.MATCHED, songs = result.songs, message = null, level = 0f) }
                                return@supervisorScope
                            }
                            completed += result
                        }
                        completed.firstNotNullOfOrNull { it.failure }?.let { throw it }
                        publish { it.copy(phase = RecognitionPhase.NO_MATCH, level = 0f,
                            message = if (source == RecognitionAudioSource.SYSTEM) "暂时没有识别到，请播放歌曲原声，或切换采集方式"
                            else "暂时没有识别到，靠近音源再试一次") }
                    } finally {
                        stopWorkers()
                        results.cancel()
                    }
                } }
            } catch (timeout: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                publish { it.copy(phase = RecognitionPhase.ERROR, level = 0f, message = "识曲超时，请检查网络或切换采集方式后重试") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                publish { it.copy(phase = RecognitionPhase.ERROR, level = 0f,
                    message = if (failure is SecurityException) "录音权限已关闭，请重新授权"
                    else failure.message?.take(180) ?: "识曲失败，请重试") }
            }
        }
    }

    /** Flush the current recording into the same recognition session. */
    fun finishRecording() { stop.set(true) }
    fun cancel() {
        epoch.incrementAndGet(); stop.set(true); work?.cancel(); work = null
        if (state.value.busy) mutable.value = RecognitionState()
    }
    fun reset() { cancel(); mutable.value = RecognitionState() }

    private data class BranchResult(val songs: List<Song> = emptyList(), val failure: Exception? = null)
}
