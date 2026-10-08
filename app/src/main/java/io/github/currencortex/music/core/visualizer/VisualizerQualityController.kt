package io.github.currencortex.music.core.visualizer

import io.github.currencortex.music.data.visualizer.VisualizerQuality

/** Changes at most once per observation window; never overwrites the user's saved preference. */
class VisualizerQualityController {
    var quality = VisualizerQuality.ULTRA
        private set
    private var lastObservation = 0L
    private var lastChange = 0L
    private var poorSince = 0L
    private var healthySince = 0L
    fun observe(now: Long, metrics: VisualizerPerformanceSnapshot, automatic: Boolean): VisualizerQuality {
        if (!automatic) { reset(); return quality }
        if (!metrics.running || metrics.effectiveTargetFps <= 0 || metrics.canvasDrawRate <= 0) {
            poorSince = 0; healthySince = 0; return quality
        }
        if (lastObservation > 0 && now - lastObservation < 1_000_000_000L) return quality
        lastObservation = now
        val budget = 1000f / metrics.effectiveTargetFps
        val poor = metrics.renderJankPercentage > 12 || metrics.frameIntervalP95Ms > budget * 1.65f ||
            metrics.canvasDrawRate < metrics.effectiveTargetFps * .8f
        val healthy = metrics.renderJankPercentage < 3 && metrics.frameIntervalP95Ms < budget * 1.3f &&
            metrics.canvasDrawRate >= metrics.effectiveTargetFps * .94f
        if (poor) {
            healthySince = 0
            if (poorSince == 0L) poorSince = now
            if (now - poorSince >= 3_000_000_000L && (lastChange == 0L || now - lastChange >= 5_000_000_000L)) {
                quality = VisualizerQuality.entries[(quality.ordinal + 1).coerceAtMost(VisualizerQuality.entries.lastIndex)]
                lastChange = now; poorSince = 0
            }
        } else if (healthy) {
            poorSince = 0
            if (healthySince == 0L) healthySince = now
            if (now - healthySince >= 15_000_000_000L && now - lastChange >= 15_000_000_000L) {
                quality = VisualizerQuality.entries[(quality.ordinal - 1).coerceAtLeast(0)]
                lastChange = now; healthySince = 0
            }
        } else { poorSince = 0; healthySince = 0 }
        return quality
    }
    fun reset() { quality = VisualizerQuality.ULTRA; lastObservation = 0; lastChange = 0; poorSince = 0; healthySince = 0 }
}
