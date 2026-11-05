package io.github.currencortex.music.core.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import io.github.currencortex.music.data.settings.AdaptiveAudioSettings
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AdaptiveAudioRuntime(val processing: Boolean = false, val message: String = "播放后生效",
    val gains: List<Float> = List(10) { 0f })

/** Producer is the PCM thread; the service consumes only its audible deck's media timestamps. */
internal class MusicHapticTimeline {
    data class Frame(val positionUs: Long, val period: Any?, val bass: Float, val full: Float)
    private val frames = ArrayDeque<Frame>()
    @Synchronized fun offer(frame: Frame) {
        if (frames.size >= 256) frames.removeFirst()
        frames.addLast(frame)
    }
    @Synchronized fun clear() = frames.clear()
    @Synchronized fun poll(positionUs: Long, period: Any?): Frame? {
        var result: Frame? = null
        while (frames.isNotEmpty()) {
            val first = frames.first()
            if (first.period != period) break // The decoder may already be preparing the next period.
            if (first.positionUs > positionUs + 20_000) break
            result = frames.removeFirst()
        }
        return result?.takeIf { positionUs - it.positionUs <= 100_000 }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class AdaptiveAudioProcessor(
    private val settings: () -> AdaptiveAudioSettings,
    private val haptics: MusicHapticTimeline = MusicHapticTimeline(),
    private val report: (AdaptiveAudioRuntime) -> Unit = {},
) : BaseAudioProcessor() {
    private var dsp: AdaptiveAudioDsp? = null
    private var frame = DoubleArray(0)
    private var positionUs = 0L
    private var period: Any? = null
    private var frames = 0L
    private var samples = 0
    private var full = 0.0
    private var bass = 0.0
    private var lastReport = 0L
    private var reported = false
    private var bypassReset = false
    override fun onConfigure(inputAudioFormat: AudioFormat) = inputAudioFormat
    override fun onFlush(streamMetadata: StreamMetadata) {
        positionUs = streamMetadata.positionOffsetUs; period = streamMetadata.periodUid
        frames = 0; samples = 0; full = 0.0; bass = 0.0; reported = false; lastReport = 0; bypassReset = false
        haptics.clear()
        val format = inputAudioFormat
        val configured = format.channelCount > 0 && format.sampleRate > 0
        // The DSP keeps independent history for every input channel. Do not reject
        // surround PCM or high sample rates before the output sink can route them.
        dsp = if (configured && format.encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT))
            AdaptiveAudioDsp(format.sampleRate, format.channelCount) else null
        frame = DoubleArray(if (dsp != null) format.channelCount else 0)
        val message = when {
            !configured -> "等待音频格式"
            dsp == null -> "当前 PCM 编码不支持，已旁路"
            else -> "等待音频处理"
        }
        CurrentMusicAudioDiagnostics.record("adaptive.configure rate=${format.sampleRate} channels=${format.channelCount} " +
            "encoding=${format.encoding} supported=${dsp != null}")
        report(AdaptiveAudioRuntime(message = message))
    }
    override fun onReset() { dsp = null; haptics.clear(); report(AdaptiveAudioRuntime()) }
    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val s = settings()
        val engine = dsp
        val bytes = inputAudioFormat.bytesPerFrame
        if (engine == null || bytes <= 0 || inputBuffer.remaining() % bytes != 0 || !s.enabled && engine.settledBypass) {
            if (engine != null && !s.enabled && engine.settledBypass && !bypassReset) {
                // No PCM reaches the DSP during exact byte bypass. Discard old filter tails
                // and analysis history once, so enabling on a later passage starts cleanly.
                dsp = AdaptiveAudioDsp(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
                samples = 0; full = 0.0; bass = 0.0; bypassReset = true
            }
            if (bytes > 0) frames += inputBuffer.remaining() / bytes
            haptics.clear()
            if (reported) { reported = false; report(AdaptiveAudioRuntime()) }
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
            return
        }
        bypassReset = false
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val input = inputBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val floating = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val channels = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate
        while (input.hasRemaining()) {
            for (channel in 0 until channels) frame[channel] = if (floating) input.float.toDouble() else input.short / 32768.0
            engine.process(frame, s)
            for (channel in 0 until channels) {
                val sample = frame[channel].coerceIn(-1.0, 1.0)
                if (floating) output.putFloat(sample.toFloat())
                else output.putShort((sample * 32768).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            frames++; samples++; full += engine.fullPower; bass += engine.bassPower
            if (samples >= rate / 25) {
                if (s.enabled && s.haptics) haptics.offer(MusicHapticTimeline.Frame(
                    positionUs + (frames - samples) * 1_000_000 / rate, period,
                    sqrt(bass / samples).toFloat(), sqrt(full / samples).toFloat()))
                samples = 0; full = 0.0; bass = 0.0
            }
        }
        output.flip()
        if (!s.haptics || !s.enabled) haptics.clear()
        if (!reported || frames - lastReport >= rate / 5) {
            lastReport = frames; reported = s.enabled
            report(AdaptiveAudioRuntime(s.enabled, if (s.enabled) "本地音频处理中" else "已关闭", engine.gains))
        }
    }
}
