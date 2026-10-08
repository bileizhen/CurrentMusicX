package io.github.currencortex.music.core.visualizer

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Uses only the service's session, never the global output mix (session zero). */
class VisualizerCaptureSource : AudioCaptureSource {
    override fun frames(audioSessionId: Int): Flow<AudioCapturePacket> = flow {
        require(audioSessionId > 0) { "A local audio session is required" }
        val thread = HandlerThread("MusicVisualizerCapture").apply { start() }
        val dispatcher = Handler(thread.looper).asCoroutineDispatcher()
        val packets = Channel<AudioCapturePacket>(Channel.CONFLATED)
        var effect: Visualizer? = null
        try {
            withContext(dispatcher) {
                val capture = Visualizer(audioSessionId).also { effect = it }
                check(capture.setEnabled(false) == Visualizer.SUCCESS)
                val range = Visualizer.getCaptureSizeRange()
                check(capture.setCaptureSize(1024.coerceIn(range[0], range[1])) == Visualizer.SUCCESS)
                check(capture.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED) == Visualizer.SUCCESS)
                var waveform = ByteArray(0)
                var waveformTime = 0L
                var waveformRate = 0
                check(capture.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, data: ByteArray, rate: Int) {
                        waveform = data.copyOf()
                        waveformTime = System.nanoTime()
                        waveformRate = rate
                    }
                    override fun onFftDataCapture(v: Visualizer, data: ByteArray, rate: Int) {
                        // CLOCK_MONOTONIC / uptime, matching Android's Choreographer frame clock.
                        // elapsedRealtimeNanos includes deep sleep and must not be compared to VSync.
                        val now = System.nanoTime()
                        if (rate > 0 && rate == waveformRate && waveform.size == data.size &&
                            now - waveformTime <= 100_000_000L) {
                            packets.trySend(AudioCapturePacket(now, rate / 1000, data.copyOf(), waveform))
                        }
                    }
                }, minOf(30_000, Visualizer.getMaxCaptureRate()), true, true) == Visualizer.SUCCESS)
                check(capture.setEnabled(true) == Visualizer.SUCCESS)
            }
            while (true) emit(withTimeout(2_000) { packets.receive() })
        } finally {
            // Joining the old collector waits for native release before a new session is attached.
            withContext(NonCancellable + dispatcher) {
                effect?.let { capture ->
                    try { capture.setEnabled(false) } catch (_: RuntimeException) { /* dead server */ }
                    try { capture.release() } catch (_: RuntimeException) { /* already dead */ }
                }
                packets.close()
            }
            thread.quitSafely()
        }
    }
}
