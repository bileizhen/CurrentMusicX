package io.github.currencortex.music.data.visualizer

import kotlin.math.roundToInt

enum class VisualizerFrameRate(val targetFps: Int?, val label: String) {
    AUTO(null, "Auto"), FPS_30(30, "30"), FPS_60(60, "60"), FPS_90(90, "90"), FPS_120(120, "120");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO }
}

enum class VisualizerQuality(val fpsCap: Int, val spectrumStride: Int, val waveformStride: Int) {
    ULTRA(120, 1, 1), HIGH(120, 2, 2), BALANCED(60, 2, 2), ECO(30, 4, 4)
}

data class VisualizerRenderSettings(val frameRate: VisualizerFrameRate = VisualizerFrameRate.AUTO,
    val automaticOptimization: Boolean = true, val rawSpectrum: Boolean = false)

data class VisualizerDisplayState(val refreshRate: Float = 60f, val supportedRates: List<Float> = listOf(60f),
    val powerSave: Boolean = false, val thermalLimited: Boolean = false)

data class VisualizerFrameRateDecision(val preferenceFps: Int, val effectiveFps: Int, val limitation: String?)

object VisualizerFrameRatePolicy {
    fun decide(settings: VisualizerRenderSettings, display: VisualizerDisplayState,
        quality: VisualizerQuality): VisualizerFrameRateDecision {
        val refresh = display.refreshRate.takeIf { it.isFinite() && it >= 20 } ?: 60f
        val maximum = display.supportedRates.filter { it.isFinite() && it >= 20 }.maxOrNull() ?: refresh
        val requested = settings.frameRate.targetFps ?: minOf(120, maximum.roundToInt())
        val environmentCap = when { display.powerSave -> 30; display.thermalLimited -> 60; else -> 120 }
        val preference = minOf(requested, maximum.roundToInt(), quality.fpsCap, environmentCap).coerceAtLeast(20)
        // Choose an integer VSync divisor. 90 on a current 120 Hz panel draws at 60 until the
        // system actually switches to 90 Hz; requesting 90 never creates synthetic callbacks.
        val divisor = kotlin.math.ceil((refresh / preference) - .025f).toInt().coerceAtLeast(1)
        val effective = (refresh / divisor).roundToInt().coerceIn(1, preference)
        val limitation = when {
            display.powerSave -> "省电模式限制"
            display.thermalLimited -> "温控限制"
            requested > maximum + 1 -> "设备显示模式限制"
            effective < preference - 2 -> "当前显示刷新率限制 / 使用 VSync 整数分频"
            quality != VisualizerQuality.ULTRA -> "自动性能档位 ${quality.name}"
            else -> null
        }
        return VisualizerFrameRateDecision(preference, effective, limitation)
    }
}
