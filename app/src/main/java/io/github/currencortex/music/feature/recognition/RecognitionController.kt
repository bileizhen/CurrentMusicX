package io.github.currencortex.music.feature.recognition

import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class RecognitionPhase { IDLE, RECORDING, RECOGNIZING, MATCHED, NO_MATCH, ERROR }
data class RecognitionState(val phase: RecognitionPhase = RecognitionPhase.IDLE, val seconds: Int = 0,
    val level: Float = 0f, val songs: List<Song> = emptyList(), val message: String? = null) {
    val busy get() = phase == RecognitionPhase.RECORDING || phase == RecognitionPhase.RECOGNIZING
}

fun interface RecognitionRecorder {
    suspend fun record(stop: AtomicBoolean, progress: (Int, Float) -> Unit): ShortArray
}

/** A new attempt owns its recording and result. Cancellation can never publish a stale match. */
class RecognitionController(private val scope: CoroutineScope, private val recorder: RecognitionRecorder,
    private val fingerprint: suspend (ShortArray) -> String,
    private val recognize: suspend (String, Double) -> List<Song>) {
    private val mutable = MutableStateFlow(RecognitionState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var stop = AtomicBoolean(false)
    private val epoch = AtomicLong(0)

    fun start() {
        if (state.value.busy) return
        work?.cancel()
        val attempt = epoch.incrementAndGet()
        stop = AtomicBoolean(false)
        val requestStop = stop
        mutable.value = RecognitionState(RecognitionPhase.RECORDING)
        work = scope.launch {
            var samples: ShortArray? = null
            try {
                samples = recorder.record(requestStop) { seconds, level ->
                    if (epoch.get() == attempt) mutable.value = mutable.value.copy(seconds = seconds, level = level)
                }
                ensureActive()
                if (samples.size < 3 * 8000) throw IllegalStateException("片段太短，请至少听满 3 秒后再识别")
                if (samples.size > 10 * 8000) throw IllegalStateException("识别片段过长，请重新录音")
                if (epoch.get() == attempt) mutable.value = mutable.value.copy(phase = RecognitionPhase.RECOGNIZING, level = 0f)
                val encoded = withTimeout(10_000) { fingerprint(samples) }
                val songs = recognize(encoded, samples.size / 8000.0)
                ensureActive()
                if (epoch.get() == attempt) mutable.value = mutable.value.copy(
                    phase = if (songs.isEmpty()) RecognitionPhase.NO_MATCH else RecognitionPhase.MATCHED, songs = songs,
                    message = if (songs.isEmpty()) "暂时没有识别到，靠近音源再试一次" else null)
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                if (epoch.get() == attempt) mutable.value = mutable.value.copy(phase = RecognitionPhase.ERROR, message = "识曲超时，请重试", level = 0f)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (epoch.get() == attempt) mutable.value = mutable.value.copy(phase = RecognitionPhase.ERROR, level = 0f,
                    message = if (e is SecurityException) "麦克风权限已关闭，请重新授权" else e.message?.take(180) ?: "识曲失败，请重试")
            } finally { samples?.fill(0) }
        }
    }

    /** Finish the current clip rather than discarding it. */
    fun finishRecording() { stop.set(true) }
    fun cancel() {
        epoch.incrementAndGet(); stop.set(true); work?.cancel(); work = null
        if (state.value.busy) mutable.value = RecognitionState()
    }
    fun reset() { cancel(); mutable.value = RecognitionState() }
}
