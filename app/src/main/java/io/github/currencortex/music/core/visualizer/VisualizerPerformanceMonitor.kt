package io.github.currencortex.music.core.visualizer

import io.github.currencortex.music.data.visualizer.*
import kotlin.math.ceil

data class VisualizerPerformanceSnapshot(
    val requestedFps: Int? = null, val preferenceFps: Int = 0, val effectiveTargetFps: Int = 0,
    val displayRefreshRate: Float = 0f, val renderTickRate: Float = 0f, val canvasDrawRate: Float = 0f,
    /** Window frame reports / intended VSync intervals, NOT proof of physical presentation. */
    val measuredFrameRate: Float? = null, val presentedFrameRate: Float? = null,
    val frameIntervalAverageMs: Float = 0f, val frameIntervalP95Ms: Float = 0f, val frameIntervalP99Ms: Float = 0f,
    val jankPercentage: Float = 0f, val audioCaptureRate: Float = 0f, val audioFrameAgeMs: Float? = null,
    val windowTotalAverageMs: Float = 0f, val canvasCpuAverageMs: Float = 0f,
    val qualityLevel: VisualizerQuality = VisualizerQuality.ULTRA, val limitation: String? = null,
    val clockTicks: Long = 0, val canvasDraws: Long = 0, val compositions: Long = 0,
    val windowFrames: Long = 0, val droppedWindowReports: Long = 0, val running: Boolean = false,
    /** Late Canvas intervals and estimated missed target slots in the last five seconds. */
    val renderJankPercentage: Float = 0f, val estimatedDroppedFrames: Int = 0,
)

/** Bounded rings. Frame callbacks only record primitives; sorting happens at 2 Hz, never in drawing. */
class VisualizerPerformanceMonitor {
    private class Samples {
        val time = LongArray(1024); val value = FloatArray(1024)
        var cursor = 0; var size = 0
        fun add(now: Long, sample: Float) {
            time[cursor] = now; value[cursor] = sample
            cursor = (cursor + 1) % time.size; size = minOf(size + 1, time.size)
        }
        fun recent(now: Long): FloatArray {
            val values = FloatArray(size)
            var count = 0
            for (i in time.indices) if (time[i] > 0 && now - time[i] in 0..5_000_000_000L) values[count++] = value[i]
            return values.copyOf(count).apply { sort() }
        }
        fun clear() { time.fill(0); value.fill(0f); cursor = 0; size = 0 }
    }
    private val ticks = Samples(); private val draws = Samples(); private val audio = Samples()
    private val windowIntervals = Samples(); private val windowTotal = Samples(); private val jank = Samples()
    private val drawCpu = Samples()
    private var lastTick = 0L; private var lastDraw = 0L; private var lastAudio = 0L; private var lastWindow = 0L
    private var tickCount = 0L; private var drawCount = 0L; private var compositionCount = 0L
    private var windowCount = 0L; private var droppedReports = 0L
    @Synchronized fun tick(time: Long) {
        if (time <= lastTick || time <= 0) return
        if (lastTick > 0) ticks.add(time, (time - lastTick) / 1e6f)
        lastTick = time; tickCount++
    }
    @Synchronized fun draw(time: Long, cpuNanos: Long) {
        if (time <= lastDraw || time <= 0) return
        if (lastDraw > 0) draws.add(time, (time - lastDraw) / 1e6f)
        drawCpu.add(time, cpuNanos.coerceAtLeast(0) / 1e6f)
        lastDraw = time; drawCount++
    }
    @Synchronized fun audio(time: Long) {
        if (time <= lastAudio || time <= 0) return
        if (lastAudio > 0) audio.add(time, (time - lastAudio) / 1e6f)
        lastAudio = time
    }
    @Synchronized fun composition() { compositionCount++ }
    @Synchronized fun window(time: Long, totalNanos: Long, deadlineNanos: Long, reportsDropped: Int = 0) {
        if (time <= lastWindow || time <= 0 || totalNanos < 0) return
        if (lastWindow > 0) windowIntervals.add(time, (time - lastWindow) / 1e6f)
        windowTotal.add(time, totalNanos / 1e6f)
        jank.add(time, if (deadlineNanos > 0 && totalNanos > deadlineNanos) 100f else 0f)
        lastWindow = time; windowCount++; droppedReports += reportsDropped.coerceAtLeast(0)
    }
    @Synchronized fun resetTiming() {
        ticks.clear(); draws.clear(); audio.clear(); windowIntervals.clear(); windowTotal.clear(); jank.clear(); drawCpu.clear()
        lastTick = 0; lastDraw = 0; lastAudio = 0; lastWindow = 0
    }
    @Synchronized fun snapshot(now: Long, settings: VisualizerRenderSettings, display: VisualizerDisplayState,
        decision: VisualizerFrameRateDecision, quality: VisualizerQuality, running: Boolean): VisualizerPerformanceSnapshot {
        val tick = ticks.recent(now); val draw = draws.recent(now); val window = windowIntervals.recent(now)
        fun mean(values: FloatArray) = if (values.isEmpty()) 0f else values.average().toFloat()
        fun rate(values: FloatArray): Float = mean(values).let { if (it > 0) 1000f / it else 0f }
        fun percentile(values: FloatArray, p: Float) = if (values.isEmpty()) 0f else values[(ceil(values.size * p).toInt() - 1).coerceIn(values.indices)]
        val budget = 1000f / decision.effectiveFps.coerceAtLeast(1)
        val late = draw.count { it > budget * 1.5f }
        val missed = draw.sumOf { (kotlin.math.round(it / budget).toInt() - 1).coerceAtLeast(0) }
        return VisualizerPerformanceSnapshot(settings.frameRate.targetFps, if (running) decision.preferenceFps else 0,
            if (running) decision.effectiveFps else 0, display.refreshRate,
            if (running) rate(tick) else 0f, if (running) rate(draw) else 0f,
            window.takeIf { running && it.size >= 3 }?.let(::rate), null,
            mean(draw), percentile(draw, .95f), percentile(draw, .99f), mean(jank.recent(now)),
            if (running) rate(audio.recent(now)) else 0f,
            lastAudio.takeIf { it > 0 && running }?.let { (now - it).coerceAtLeast(0) / 1e6f },
            mean(windowTotal.recent(now)), mean(drawCpu.recent(now)), quality, decision.limitation,
            tickCount, drawCount, compositionCount, windowCount, droppedReports, running,
            if (running && draw.isNotEmpty()) late * 100f / draw.size else 0f, if (running) missed else 0)
    }
}
