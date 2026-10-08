package io.github.currencortex.music.feature.visualizer

import androidx.compose.runtime.*
import io.github.currencortex.music.core.visualizer.*
import io.github.currencortex.music.data.visualizer.*

@Stable class VisualizerRenderState {
    internal val interpolator = VisualizerFrameInterpolator()
    val frame get() = interpolator.output
    val monitor = VisualizerPerformanceMonitor()
    val statistics = mutableStateOf(VisualizerPerformanceSnapshot())
    val audioReadout = mutableStateOf(AudioAnalysisFrame())
    internal var revision by mutableIntStateOf(0)
    var preferredFps by mutableFloatStateOf(0f)
        internal set
    @Volatile var running = false
        internal set
    internal var quality = VisualizerQuality.ULTRA
    internal fun clear() {
        running = false; preferredFps = 0f; interpolator.reset(); revision++
        statistics.value = statistics.value.copy(preferenceFps = 0, effectiveTargetFps = 0,
            renderTickRate = 0f, canvasDrawRate = 0f, measuredFrameRate = null, running = false,
            audioCaptureRate = 0f, audioFrameAgeMs = null, renderJankPercentage = 0f, estimatedDroppedFrames = 0)
        audioReadout.value = AudioAnalysisFrame()
    }
}
