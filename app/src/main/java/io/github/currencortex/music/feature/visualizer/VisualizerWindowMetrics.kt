package io.github.currencortex.music.feature.visualizer

import android.os.*
import android.view.*
import io.github.currencortex.music.core.visualizer.VisualizerPerformanceMonitor

/** Window-wide measurements; physical presentation must be verified separately with SurfaceFlinger. */
internal class VisualizerWindowMetrics(private val window: Window, monitor: VisualizerPerformanceMonitor,
    private val refreshRate: () -> Float) : AutoCloseable {
    private val thread = HandlerThread("VisualizerWindowMetrics").apply { start() }
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        val deadline = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE)
            else (1e9 / refreshRate().coerceAtLeast(1f)).toLong()
        monitor.window(metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
            metrics.getMetric(FrameMetrics.TOTAL_DURATION), deadline, dropped, if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else -1)
    }
    init {
        try { window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        catch (error: RuntimeException) { thread.quitSafely(); throw error }
    }
    override fun close() { window.removeOnFrameMetricsAvailableListener(listener); thread.quitSafely() }
}
