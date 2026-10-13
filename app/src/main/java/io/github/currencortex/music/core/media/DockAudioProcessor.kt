package io.github.currencortex.music.core.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/** Pass-through PCM meter. The sink still receives every original byte, in the original format. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class DockAudioProcessor(private val meter: DockAudioLevel, private val probe: TrackEnergyProbe? = null) : BaseAudioProcessor() {
    private var count = 0
    private var square = 0.0
    private var bassSquare = 0.0
    private var low = 0.0
    private var coefficient = 0.0
    private var window = 1

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) inputAudioFormat else AudioFormat.NOT_SET

    override fun onFlush(streamMetadata: StreamMetadata) {
        count = 0; square = 0.0; bassSquare = 0.0; low = 0.0
        coefficient = 1.0 - exp(-2.0 * PI * 180.0 / inputAudioFormat.sampleRate.coerceAtLeast(1))
        window = (inputAudioFormat.sampleRate / 25).coerceAtLeast(1)
        meter.reset()
        probe?.reset()
    }

    override fun onReset() { meter.reset(); probe?.reset() }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // The sink also pumps EMPTY_BUFFER while draining downstream processors.
        // BaseAudioProcessor starts with that same singleton as its backing buffer.
        if (!inputBuffer.hasRemaining()) return
        if (meter.enabled || probe?.enabled == true) {
            val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
            val sampleBytes = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val frameBytes = channels * sampleBytes
            var offset = inputBuffer.position()
            while (offset + frameBytes <= inputBuffer.limit()) {
                var mono = 0.0
                repeat(channels) { channel ->
                    val at = offset + channel * sampleBytes
                    val value = if (sampleBytes == 2) ((inputBuffer.get(at).toInt() and 255) or (inputBuffer.get(at + 1).toInt() shl 8)).toShort() / 32768.0
                        else Float.fromBits((inputBuffer.get(at).toInt() and 255) or ((inputBuffer.get(at + 1).toInt() and 255) shl 8) or
                            ((inputBuffer.get(at + 2).toInt() and 255) shl 16) or (inputBuffer.get(at + 3).toInt() shl 24)).toDouble().let { if (it.isFinite()) it else 0.0 }
                    square += value * value / channels
                    mono += value / channels
                }
                low += coefficient * (mono - low)
                bassSquare += low * low
                count++
                if (count >= window) {
                    // RMS compression keeps quiet recordings visible without making peaks flash.
                    meter.publish(sqrt(sqrt(square / count)).toFloat(), sqrt(sqrt(bassSquare / count)).toFloat())
                    probe?.publish(sqrt(square / count).toFloat(), sqrt(bassSquare / count).toFloat())
                    count = 0; square = 0.0; bassSquare = 0.0
                }
                offset += frameBytes
            }
        } else { count = 0; square = 0.0; bassSquare = 0.0; low = 0.0 }
        val output = replaceOutputBuffer(inputBuffer.remaining())
        output.put(inputBuffer).flip()
    }
}
