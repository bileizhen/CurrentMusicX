package io.github.currencortex.music.core.visualizer

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun interface VisualizerFrameClock { suspend fun nextFrameNanos(): Long }

/** Paces drawing on the existing VSync clock; no timer and no fixed-step animation. */
class VisualizerFramePacer {
    private var previousTick = 0L
    private var previousRender = 0L
    private var target = 0
    var deltaSeconds = 0f
        private set
    fun select(timeNanos: Long, fps: Int): Boolean {
        if (timeNanos <= 0 || timeNanos <= previousTick) return false
        previousTick = timeNanos
        val frameTarget = fps.coerceIn(1, 120)
        if (target != frameTarget) { target = frameTarget; previousRender = 0 }
        val period = 1_000_000_000L / frameTarget
        if (previousRender != 0L && timeNanos - previousRender < period - 500_000L) return false
        val gap = if (previousRender == 0L) 0L else timeNanos - previousRender
        // Resume/long stalls reset integration, rather than advancing an animation by seconds.
        deltaSeconds = if (gap > 250_000_000L) 0f else minOf(gap / 1e9f, .05f)
        previousRender = timeNanos
        return true
    }
    fun reset() { previousTick = 0; previousRender = 0; target = 0; deltaSeconds = 0f }
}

class VisualizerRenderLoop(private val clock: VisualizerFrameClock) {
    suspend fun run(targetFps: () -> Int, tick: (Long) -> Unit = {}, draw: (Long, Float) -> Boolean) {
        val pacer = VisualizerFramePacer()
        while (true) {
            currentCoroutineContext().ensureActive()
            val time = clock.nextFrameNanos()
            currentCoroutineContext().ensureActive()
            tick(time)
            if (pacer.select(time, targetFps()) && !draw(time, pacer.deltaSeconds)) return
        }
    }
}
