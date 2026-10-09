package io.github.currencortex.music.core.visualizer

import io.github.currencortex.music.data.visualizer.VisualizerQuality

object VisualizerShaderPolicy {
    fun enabled(api: Int, hardware: Boolean, quality: VisualizerQuality, failed: Boolean) =
        api >= 33 && hardware && quality.ordinal < VisualizerQuality.BALANCED.ordinal && !failed
}
