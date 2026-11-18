package io.github.currencortex.music.core.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
internal class NeteaseUsbChannelProcessor(private val enabled: () -> Boolean) : BaseAudioProcessor() {
    private var converting = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        converting = enabled() && inputAudioFormat.channelCount > 2 &&
            inputAudioFormat.encoding in SUPPORTED_ENCODINGS
        if (!converting) return inputAudioFormat
        CurrentMusicAudioDiagnostics.record("usbHost.channelAdaptation=${inputAudioFormat.channelCount}ch_to_2ch downmix=average")
        return AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
    }

    override fun onFlush(streamMetadata: StreamMetadata) = Unit

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!converting || !inputBuffer.hasRemaining()) {
            if (inputBuffer.hasRemaining()) replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
            return
        }
        val format = inputAudioFormat
        val sampleBytes = format.bytesPerFrame / format.channelCount
        val frameBytes = format.bytesPerFrame
        val frames = inputBuffer.remaining() / frameBytes
        val output = replaceOutputBuffer(frames * sampleBytes * 2).order(ByteOrder.LITTLE_ENDIAN)
        val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) {
            var left = 0.0
            var right = 0.0
            repeat(format.channelCount) { channel ->
                val value = readSample(input, format.encoding)
                if (channel % 2 == 0) left += value else right += value
            }
            writeSample(output, format.encoding, left / ((format.channelCount + 1) / 2))
            writeSample(output, format.encoding, right / (format.channelCount / 2))
        }
        inputBuffer.position(inputBuffer.position() + frames * frameBytes)
        output.flip()
    }

    private fun readSample(input: ByteBuffer, encoding: Int): Double = when (encoding) {
        C.ENCODING_PCM_16BIT -> input.short / 32768.0
        C.ENCODING_PCM_24BIT -> {
            val value = (input.get().toInt() and 255) or
                ((input.get().toInt() and 255) shl 8) or
                (input.get().toInt() shl 16)
            (if (value and 0x800000 != 0) value or -0x1000000 else value) / 8388608.0
        }
        C.ENCODING_PCM_32BIT -> input.int / 2147483648.0
        C.ENCODING_PCM_FLOAT -> input.float.toDouble()
        else -> error("Unsupported PCM encoding: $encoding")
    }

    private fun writeSample(output: ByteBuffer, encoding: Int, value: Double) {
        val normalized = value.coerceIn(-1.0, 1.0)
        when (encoding) {
            C.ENCODING_PCM_16BIT -> output.putShort((normalized * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
            C.ENCODING_PCM_24BIT -> {
                val sample = (normalized * 8388608.0).roundToInt().coerceIn(-8388608, 8388607)
                output.put((sample and 255).toByte())
                output.put(((sample shr 8) and 255).toByte())
                output.put((sample shr 16).toByte())
            }
            C.ENCODING_PCM_32BIT -> output.putInt((normalized * 2147483648.0).toLong().coerceIn(-2147483648L, 2147483647L).toInt())
            C.ENCODING_PCM_FLOAT -> output.putFloat(normalized.toFloat())
        }
    }

    companion object {
        private val SUPPORTED_ENCODINGS = setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)
    }
}
