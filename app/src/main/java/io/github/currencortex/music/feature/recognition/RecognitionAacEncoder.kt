package io.github.currencortex.music.feature.recognition

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Encodes the ten-second lyric query in memory. No recording or temporary AAC file is written. */
class RecognitionAacEncoder {
    suspend fun encode(samples: ShortArray): ByteArray = withContext(Dispatchers.Default) {
        require(samples.size == 80_000)
        // Capture windows are 8 kHz. AAC-LC at 16 kHz is supported by Android's audio encoder.
        val pcm = ByteArray(samples.size * 4)
        samples.forEachIndexed { index, sample ->
            val offset = index * 4
            val between = ((sample.toInt() + samples.getOrElse(index + 1) { sample }.toInt()) / 2).toShort()
            pcm[offset] = sample.toByte(); pcm[offset + 1] = (sample.toInt() shr 8).toByte()
            pcm[offset + 2] = between.toByte(); pcm[offset + 3] = (between.toInt() shr 8).toByte()
        }
        val output = WipingBuffer()
        var codec: MediaCodec? = null
        try {
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec = encoder
            encoder.configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 16_000, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 32_000)
            }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val info = MediaCodec.BufferInfo()
            var position = 0
            var inputEnded = false
            var outputEnded = false
            val deadline = SystemClock.elapsedRealtime() + 8_000
            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                check(SystemClock.elapsedRealtime() < deadline) { "歌词识别音频编码超时" }
                if (!inputEnded) {
                    val index = encoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = checkNotNull(encoder.getInputBuffer(index))
                        buffer.clear()
                        val count = minOf(buffer.remaining() / 2 * 2, pcm.size - position)
                        buffer.put(pcm, position, count)
                        val timestamp = position.toLong() * 1_000_000 / 32_000
                        position += count
                        inputEnded = position == pcm.size
                        encoder.queueInputBuffer(index, 0, count, timestamp,
                            if (inputEnded) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    }
                }
                val index = encoder.dequeueOutputBuffer(info, 10_000)
                if (index >= 0) {
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val buffer = checkNotNull(encoder.getOutputBuffer(index))
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            val packet = ByteArray(info.size)
                            try {
                                buffer.get(packet)
                                output.write(adtsHeader(packet.size)); output.write(packet)
                                check(output.size() <= 128_000) { "歌词识别音频过大" }
                            } finally { packet.fill(0) }
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally { encoder.releaseOutputBuffer(index, false) }
                }
            }
            check(output.size() > 7) { "无法生成歌词识别音频" }
            output.toByteArray()
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }
            pcm.fill(0); output.wipe()
        }
    }

    private class WipingBuffer : ByteArrayOutputStream() {
        fun wipe() { buf.fill(0); reset() }
    }

    private fun adtsHeader(payloadSize: Int): ByteArray {
        val length = payloadSize + 7
        // MPEG-4 AAC-LC, frequency index 8 (16 kHz), one channel, no CRC.
        return byteArrayOf(0xff.toByte(), 0xf1.toByte(), 0x60, (0x40 or (length shr 11)).toByte(),
            (length shr 3).toByte(), (((length and 7) shl 5) or 0x1f).toByte(), 0xfc.toByte())
    }
}
