package io.github.currencortex.music.feature.recognition

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/** Audio only: no virtual display, screen images, microphone source or capture-policy override. */
@RequiresApi(Build.VERSION_CODES.Q)
class PlaybackAudioRecorder(private val projection: MediaProjection) : RecognitionRecorder {
    @SuppressLint("MissingPermission") // The visible activity obtains RECORD_AUDIO and projection consent first.
    override suspend fun record(stop: AtomicBoolean, progress: (Int, Float) -> Unit): ShortArray = withContext(Dispatchers.IO) {
        val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
        var audio: AudioRecord? = null
        var rate = 0
        for (candidate in listOf(48000, 16000, 8000)) {
            val minimum = AudioRecord.getMinBufferSize(candidate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) continue
            val next = runCatching { AudioRecord.Builder().setAudioPlaybackCaptureConfig(capture)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(candidate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(maxOf(minimum * 2, candidate / 2)).build() }.getOrNull() ?: continue
            if (next.state == AudioRecord.STATE_INITIALIZED) { audio = next; rate = candidate; break }
            next.release()
        }
        val recorder = audio ?: error("无法采集系统音频，请重新授权后重试")
        val output = ShortArray(80_000)
        val buffer = ShortArray(rate / 10)
        var count = 0
        var accumulated = 0; var accumulatedCount = 0
        val factor = rate / 8000
        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "系统音频采集未开始，请重新授权" }
            while (count < output.size && !stop.get()) {
                currentCoroutineContext().ensureActive()
                val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                check(read > 0) { "系统音频采集中断，请重新授权" }
                var peak = 0
                for (index in 0 until read) {
                    peak = maxOf(peak, abs(buffer[index].toInt()))
                    accumulated += buffer[index]; accumulatedCount++
                    if (accumulatedCount == factor) {
                        if (count < output.size) output[count++] = (accumulated / factor).toShort()
                        accumulated = 0; accumulatedCount = 0
                    }
                }
                progress(count / 8000, (peak / 12000f).coerceIn(0f, 1f))
            }
            check((0 until count).any { output[it] != 0.toShort() }) { "未采集到系统声音，请播放音乐；部分应用不允许采集，可切换麦克风" }
            output.copyOf(count)
        } finally {
            runCatching { recorder.stop() }; recorder.release()
            buffer.fill(0); output.fill(0)
        }
    }
}
