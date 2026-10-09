package io.github.currencortex.music.core.visualizer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class CaptureStatus(val label: String) {
    DISABLED("尚未开启"), HIDDEN("可视化页面未显示"), PERMISSION_REQUIRED("需要音频采集权限"),
    UNSUPPORTED("DLNA / MV 模式不采集"), PAUSED("等待音乐播放"), WAITING_SESSION("等待音频会话"),
    STARTING("正在连接音频会话"), CAPTURING("正在采集实际播放音频"), FAILED("采集不可用，音乐继续播放"),
}

data class CaptureRequest(val enabled: Boolean = false, val visible: Boolean = false,
    val permission: Boolean = false, val supported: Boolean = true, val playing: Boolean = false,
    val sessionId: Int = 0, val timelineRevision: Long = 0, val trackId: Long? = null) {
    val status: CaptureStatus get() = when {
        !enabled -> CaptureStatus.DISABLED
        !visible -> CaptureStatus.HIDDEN
        !permission -> CaptureStatus.PERMISSION_REQUIRED
        !supported -> CaptureStatus.UNSUPPORTED
        !playing -> CaptureStatus.PAUSED
        sessionId <= 0 -> CaptureStatus.WAITING_SESSION
        else -> CaptureStatus.STARTING
    }
}

/** Serial session replacement, bounded source buffering, analysis on Default, no UI dependency. */
class AudioAnalysisEngine(scope: CoroutineScope, private val source: AudioCaptureSource,
    dispatcher: CoroutineDispatcher = Dispatchers.Default) : AutoCloseable {
    private val requests = MutableStateFlow(CaptureRequest())
    private val mutableFrames = MutableStateFlow(AudioAnalysisFrame())
    private val mutableStatus = MutableStateFlow(CaptureStatus.DISABLED)
    val frames = mutableFrames.asStateFlow()
    val status = mutableStatus.asStateFlow()
    private var generation = 0L
    private val job = scope.launch(dispatcher) {
        requests.collectLatest { request ->
            var epoch = ++generation
            mutableFrames.value = AudioAnalysisFrame()
            mutableStatus.value = request.status
            if (request.status != CaptureStatus.STARTING) return@collectLatest
            val analyzer = AudioSignalAnalyzer()
            try {
                source.frames(request.sessionId).collect { packet ->
                    val previous = mutableFrames.value
                    if (previous.timestampNanos > 0 && (previous.sampleRateHz != packet.sampleRateHz || previous.captureSize != packet.fft.size))
                        epoch = ++generation
                    mutableFrames.value = analyzer.analyze(packet).copy(generation = epoch)
                    mutableStatus.value = CaptureStatus.CAPTURING
                }
                mutableStatus.value = CaptureStatus.FAILED
                mutableFrames.value = AudioAnalysisFrame()
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                mutableStatus.value = CaptureStatus.FAILED
                mutableFrames.value = AudioAnalysisFrame()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutableStatus.value = CaptureStatus.FAILED
                mutableFrames.value = AudioAnalysisFrame()
            }
        }
    }
    fun request(value: CaptureRequest) { requests.value = value }
    override fun close() { job.cancel() }
}
