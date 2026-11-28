package io.github.currencortex.music.core.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Media3 AudioProcessor performing non-destructive volume normalization (ReplayGain / EBU R128).
 * Sits ahead of EQ/effects so it measures raw decoded loudness and provides clean headroom.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class LoudnessNormalizationProcessor(
    private val settings: () -> VolumeNormalizationSettings,
    private val trackSpec: () -> TrackLoudnessSpec,
    private val onMeasured: (trackKey: String, lufs: Float, peak: Float, gatedBlocks: Int) -> Unit = { _, _, _, _ -> },
    private val report: (VolumeNormalizationRuntime) -> Unit = {},
) : BaseAudioProcessor() {
    private var dsp: LoudnessNormalizationDsp? = null
    private var frame = DoubleArray(0)
    private var activeTrackKey = ""
    private var activePeriod: Any? = null
    private var framesProcessed = 0L
    private var lastReportFrames = 0L
    private var lastSavedBlocks = 0
    private var reportedActive = false
    private var bypassReset = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat = inputAudioFormat

    override fun onFlush(streamMetadata: StreamMetadata) {
        emitMeasurementIfReady(force = true)
        val spec = trackSpec()
        val s = settings()
        val format = inputAudioFormat
        val valid = format.channelCount > 0 && format.sampleRate > 0 && format.encoding in SUPPORTED_ENCODINGS
        val newTrack = spec.trackKey != activeTrackKey ||
            (spec.trackKey.isEmpty() && streamMetadata.periodUid != activePeriod)

        if (!valid) {
            dsp = null
            frame = DoubleArray(0)
            activeTrackKey = spec.trackKey
            activePeriod = streamMetadata.periodUid
            framesProcessed = 0
            lastReportFrames = 0
            lastSavedBlocks = 0
            reportedActive = false
            bypassReset = false
            report(VolumeNormalizationRuntime(message = if (s.enabled) "当前音频格式不支持，已旁路" else "未开启"))
            return
        }

        val currentDsp = dsp?.takeIf { it.sampleRate == format.sampleRate && it.channels == format.channelCount }
            ?: LoudnessNormalizationDsp(format.sampleRate, format.channelCount).also {
                dsp = it
                frame = DoubleArray(format.channelCount)
            }

        activeTrackKey = spec.trackKey
        activePeriod = streamMetadata.periodUid
        framesProcessed = 0
        lastReportFrames = 0
        bypassReset = false

        if (newTrack) {
            lastSavedBlocks = 0
            currentDsp.onNewTrack(s, spec)
        } else {
            currentDsp.onSeek(s, spec)
        }
        publishRuntime(s, currentDsp)
    }

    override fun onQueueEndOfStream() {
        emitMeasurementIfReady(force = true)
    }

    override fun onReset() {
        emitMeasurementIfReady(force = true)
        dsp = null
        frame = DoubleArray(0)
        activeTrackKey = ""
        activePeriod = null
        framesProcessed = 0
        lastReportFrames = 0
        lastSavedBlocks = 0
        reportedActive = false
        bypassReset = false
        report(VolumeNormalizationRuntime())
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val s = settings()
        val spec = trackSpec()
        val engine = dsp
        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        if (engine == null || bytesPerFrame <= 0 || inputBuffer.remaining() % bytesPerFrame != 0) {
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
            return
        }

        if (spec.trackKey.isNotEmpty() && spec.trackKey != activeTrackKey) {
            emitMeasurementIfReady(force = true)
            activeTrackKey = spec.trackKey
            lastSavedBlocks = 0
            engine.onNewTrack(s, spec)
        } else {
            engine.updateState(s, spec, snapInitialGain = false)
        }

        if (!s.enabled && engine.settledBypass) {
            if (!bypassReset) {
                engine.meter.resetAll()
                lastSavedBlocks = 0
                bypassReset = true
            }
            if (reportedActive) {
                reportedActive = false
                report(VolumeNormalizationRuntime(message = "未开启"))
            }
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
            return
        }
        bypassReset = false

        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        val input = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val encoding = inputAudioFormat.encoding
        val channels = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate

        while (input.hasRemaining()) {
            when (encoding) {
                C.ENCODING_PCM_16BIT -> {
                    for (ch in 0 until channels) frame[ch] = input.short / 32768.0
                }
                C.ENCODING_PCM_FLOAT -> {
                    for (ch in 0 until channels) frame[ch] = input.float.toDouble()
                }
                C.ENCODING_PCM_24BIT -> {
                    for (ch in 0 until channels) {
                        val b0 = input.get().toInt() and 0xFF
                        val b1 = input.get().toInt() and 0xFF
                        val b2 = input.get().toInt()
                        val raw = (b2 shl 16) or (b1 shl 8) or b0
                        frame[ch] = raw / 8388608.0
                    }
                }
                C.ENCODING_PCM_32BIT -> {
                    for (ch in 0 until channels) frame[ch] = input.int / 2147483648.0
                }
            }

            engine.processFrame(frame, s)

            when (encoding) {
                C.ENCODING_PCM_16BIT -> {
                    for (ch in 0 until channels) {
                        val clamped = frame[ch].coerceIn(-1.0, 1.0)
                        output.putShort((clamped * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
                    }
                }
                C.ENCODING_PCM_FLOAT -> {
                    for (ch in 0 until channels) output.putFloat(frame[ch].coerceIn(-1.0, 1.0).toFloat())
                }
                C.ENCODING_PCM_24BIT -> {
                    for (ch in 0 until channels) {
                        val scaled = (frame[ch].coerceIn(-1.0, 1.0) * 8388608.0).roundToInt().coerceIn(-8388608, 8388607)
                        output.put((scaled and 0xFF).toByte())
                        output.put(((scaled ushr 8) and 0xFF).toByte())
                        output.put(((scaled ushr 16) and 0xFF).toByte())
                    }
                }
                C.ENCODING_PCM_32BIT -> {
                    for (ch in 0 until channels) {
                        val scaled = (frame[ch].coerceIn(-1.0, 1.0) * 2147483648.0).roundToLong()
                            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                        output.putInt(scaled)
                    }
                }
            }
            framesProcessed++
        }
        output.flip()

        if (framesProcessed - lastReportFrames >= rate / 2) {
            lastReportFrames = framesProcessed
            publishRuntime(s, engine)
            emitMeasurementIfReady(force = false)
        }
    }

    private fun publishRuntime(s: VolumeNormalizationSettings, engine: LoudnessNormalizationDsp) {
        reportedActive = s.enabled
        val measured = (engine.meter.integratedLufs ?: engine.meter.activeAverageLufs)?.toFloat()
        val gain = engine.currentGainDb.toFloat()
        val msg = when {
            !s.enabled -> "未开启"
            engine.activeSourceLabel.isNotBlank() -> {
                val gainStr = String.format(java.util.Locale.ROOT, "%+.1f dB", gain)
                val lufsStr = measured?.let { String.format(java.util.Locale.ROOT, "%.1f LUFS", it) }
                if (lufsStr != null) "$lufsStr · $gainStr（${engine.activeSourceLabel}）"
                else "$gainStr（${engine.activeSourceLabel}）"
            }
            else -> "正在分析音频响度…"
        }
        report(
            VolumeNormalizationRuntime(
                active = s.enabled,
                trackKey = activeTrackKey,
                gainDb = gain,
                measuredLufs = measured,
                sourceLabel = engine.activeSourceLabel,
                message = msg,
            )
        )
    }

    private fun emitMeasurementIfReady(force: Boolean) {
        val engine = dsp ?: return
        val key = activeTrackKey
        if (key.isBlank()) return
        val lufs = (if (force) engine.meter.flushIntegration() else engine.meter.integratedLufs) ?: return
        val blocks = engine.meter.gatedIntegratedBlocks
        if (blocks < MIN_PERSIST_GATED_BLOCKS) return
        if (!force && blocks - lastSavedBlocks < PERSIST_STEP_BLOCKS) return
        if (force && blocks <= lastSavedBlocks) return
        val peak = engine.meter.samplePeak.toFloat()
        if (!lufs.isFinite() || !peak.isFinite() || lufs < -65.0 || lufs > 6.0) return
        lastSavedBlocks = blocks
        onMeasured(key, lufs.toFloat(), peak, blocks)
    }

    companion object {
        private val SUPPORTED_ENCODINGS = setOf(
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
        )
        private const val MIN_PERSIST_GATED_BLOCKS = 50 // ~5 seconds of active audio
        private const val PERSIST_STEP_BLOCKS = 150 // Re-save every ~15 seconds of new audio
    }
}
