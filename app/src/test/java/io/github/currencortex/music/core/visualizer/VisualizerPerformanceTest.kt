package io.github.currencortex.music.core.visualizer

import io.github.currencortex.music.data.visualizer.*
import org.junit.Assert.*
import org.junit.Test

class VisualizerPerformanceTest {
    private fun policy(fps: VisualizerFrameRate, refresh: Float, modes: List<Float> = listOf(refresh)) =
        VisualizerFrameRatePolicy.decide(VisualizerRenderSettings(fps), VisualizerDisplayState(refresh, modes), VisualizerQuality.ULTRA)
    @Test fun device60NeverSimulates120() {
        val decision = policy(VisualizerFrameRate.FPS_120, 60f)
        assertEquals(60, decision.preferenceFps); assertEquals(60, decision.effectiveFps); assertNotNull(decision.limitation)
    }
    @Test fun request120CanPromoteWhileDrawingAtCurrent60() {
        val decision = policy(VisualizerFrameRate.FPS_120, 60f, listOf(60f, 90f, 120f))
        assertEquals(120, decision.preferenceFps); assertEquals(60, decision.effectiveFps)
    }
    @Test fun mode90And120UseCompatibleCadence() {
        assertEquals(90, policy(VisualizerFrameRate.FPS_90, 90f).effectiveFps)
        assertEquals(60, policy(VisualizerFrameRate.FPS_90, 120f).effectiveFps)
        assertEquals(120, policy(VisualizerFrameRate.FPS_120, 120f).effectiveFps)
        assertEquals(30, policy(VisualizerFrameRate.FPS_30, 120f).effectiveFps)
    }
    @Test fun autoUsesSupportedModesAndQualityOnlyChangesEffectivePreference() {
        val settings = VisualizerRenderSettings(VisualizerFrameRate.AUTO)
        val display = VisualizerDisplayState(120f, listOf(60f, 90f, 120f))
        assertEquals(120, VisualizerFrameRatePolicy.decide(settings, display, VisualizerQuality.ULTRA).preferenceFps)
        assertEquals(60, VisualizerFrameRatePolicy.decide(settings, display, VisualizerQuality.BALANCED).preferenceFps)
        assertEquals(VisualizerFrameRate.AUTO, settings.frameRate)
    }
    @Test fun powerAndThermalCapsAreIndependentOfSavedPreference() {
        val settings = VisualizerRenderSettings(VisualizerFrameRate.FPS_120)
        assertEquals(30, VisualizerFrameRatePolicy.decide(settings, VisualizerDisplayState(120f, listOf(120f), powerSave = true), VisualizerQuality.ULTRA).preferenceFps)
        assertEquals(60, VisualizerFrameRatePolicy.decide(settings, VisualizerDisplayState(120f, listOf(120f), thermalLimited = true), VisualizerQuality.ULTRA).preferenceFps)
        assertEquals(120, settings.frameRate.targetFps)
    }
    @Test fun invalidDisplayAndUnknownPreferenceFallBack() {
        assertEquals(60, policy(VisualizerFrameRate.AUTO, Float.NaN, emptyList()).effectiveFps)
        assertEquals(VisualizerFrameRate.AUTO, VisualizerFrameRate.from("future"))
    }
    @Test fun fractionalPanelRateDoesNotAccidentallyAddAnExtraVsyncDivisor() {
        assertEquals(60, policy(VisualizerFrameRate.FPS_60, 59.94f).effectiveFps)
        assertEquals(30, policy(VisualizerFrameRate.FPS_30, 59.94f).effectiveFps)
        assertEquals(120, policy(VisualizerFrameRate.AUTO, 119.88f).effectiveFps)
    }
    private fun stats(poor: Boolean) = VisualizerPerformanceSnapshot(effectiveTargetFps = 120,
        canvasDrawRate = if (poor) 65f else 120f, frameIntervalP95Ms = if (poor) 24f else 8.5f,
        jankPercentage = if (poor) 20f else 1f, running = true)
    @Test fun sustainedPressureReducesLoadBeforeFpsAndHasCooldown() {
        val controller = VisualizerQualityController()
        for (i in 1..4) controller.observe(i * 1_000_000_000L, stats(true), true)
        assertEquals(VisualizerQuality.HIGH, controller.quality)
        for (i in 5..8) controller.observe(i * 1_000_000_000L, stats(true), true)
        assertEquals(VisualizerQuality.HIGH, controller.quality)
        controller.observe(9_000_000_000L, stats(true), true)
        assertEquals(VisualizerQuality.BALANCED, controller.quality)
    }
    @Test fun recoveryRequiresLongHealthyWindow() {
        val controller = VisualizerQualityController()
        for (i in 1..4) controller.observe(i * 1_000_000_000L, stats(true), true)
        for (i in 5..19) controller.observe(i * 1_000_000_000L, stats(false), true)
        assertEquals(VisualizerQuality.HIGH, controller.quality)
        controller.observe(20_000_000_000L, stats(false), true)
        assertEquals(VisualizerQuality.ULTRA, controller.quality)
    }
    @Test fun intermittentPressureDoesNotOscillateAndManualDisablesOptimization() {
        val controller = VisualizerQualityController()
        for (i in 1..20) controller.observe(i * 1_000_000_000L, stats(i % 2 == 0), true)
        assertEquals(VisualizerQuality.ULTRA, controller.quality)
        for (i in 21..24) controller.observe(i * 1_000_000_000L, stats(true), true)
        assertEquals(VisualizerQuality.HIGH, controller.quality)
        controller.observe(25_000_000_000L, stats(true), false)
        assertEquals(VisualizerQuality.ULTRA, controller.quality)
    }
    @Test fun stoppedFramesDoNotCausePerformanceDowngrade() {
        val controller = VisualizerQualityController()
        for (i in 1..30) controller.observe(i * 1_000_000_000L, stats(true).copy(running = false), true)
        assertEquals(VisualizerQuality.ULTRA, controller.quality)
    }
    @Test fun monitorDistinguishesTicksDrawsWindowAndPresentation() {
        val monitor = VisualizerPerformanceMonitor()
        for (i in 0 until 120) {
            val now = 1_000_000_000L + i * 8_333_333L
            monitor.tick(now)
            if (i % 2 == 0) { monitor.draw(now, 100_000); monitor.window(now, 1_000_000, 16_666_666) }
            if (i % 6 == 0) monitor.audio(now)
        }
        val snapshot = monitor.snapshot(2_000_000_000L, VisualizerRenderSettings(), VisualizerDisplayState(120f),
            VisualizerFrameRateDecision(120, 60, null), VisualizerQuality.ULTRA, true)
        assertEquals(120f, snapshot.renderTickRate, .1f); assertEquals(60f, snapshot.canvasDrawRate, .1f)
        assertEquals(60f, snapshot.measuredFrameRate!!, .1f); assertNull(snapshot.presentedFrameRate)
        assertEquals(20f, snapshot.audioCaptureRate, .1f)
    }
    @Test fun percentilesAndDroppedReportsAreWindowMeasurements() {
        val monitor = VisualizerPerformanceMonitor()
        for (i in 0..20) {
            val now = 1_000_000_000L + i * 20_000_000L
            monitor.draw(now, 100_000); monitor.window(now, if (i % 2 == 0) 30_000_000 else 1_000_000, 16_666_666, 1)
        }
        val snapshot = monitor.snapshot(1_500_000_000L, VisualizerRenderSettings(), VisualizerDisplayState(),
            VisualizerFrameRateDecision(60, 60, null), VisualizerQuality.ULTRA, true)
        assertEquals(20f, snapshot.frameIntervalP95Ms, .01f); assertEquals(20f, snapshot.frameIntervalP99Ms, .01f)
        assertTrue(snapshot.jankPercentage > 45); assertEquals(21L, snapshot.droppedWindowReports)
        assertEquals(0f, snapshot.renderJankPercentage, .01f)
        assertEquals(0f, monitor.snapshot(1_500_000_000L, VisualizerRenderSettings(), VisualizerDisplayState(),
            VisualizerFrameRateDecision(60, 60, null), VisualizerQuality.ULTRA, false).canvasDrawRate, 0f)
    }
    @Test fun lowTargetDoesNotMistakeWindowDeadlineForRenderPressure() {
        val controller = VisualizerQualityController()
        val metrics = VisualizerPerformanceSnapshot(effectiveTargetFps = 30, canvasDrawRate = 30f,
            frameIntervalP95Ms = 34f, jankPercentage = 80f, renderJankPercentage = 0f, running = true)
        for (i in 1..30) controller.observe(i * 1_000_000_000L, metrics, true)
        assertEquals(VisualizerQuality.ULTRA, controller.quality)
    }
    @Test fun lateCanvasIntervalsCountMissedTargetSlotsSeparatelyFromWindowReports() {
        val monitor = VisualizerPerformanceMonitor()
        monitor.draw(1_000_000_000L, 100_000); monitor.draw(1_050_000_000L, 100_000)
        val snapshot = monitor.snapshot(1_100_000_000L, VisualizerRenderSettings(), VisualizerDisplayState(60f),
            VisualizerFrameRateDecision(60, 60, null), VisualizerQuality.ULTRA, true)
        assertEquals(100f, snapshot.renderJankPercentage, .01f)
        assertEquals(2, snapshot.estimatedDroppedFrames); assertEquals(0L, snapshot.droppedWindowReports)
    }
}
