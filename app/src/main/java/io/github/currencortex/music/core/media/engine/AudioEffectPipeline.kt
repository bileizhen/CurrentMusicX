package io.github.currencortex.music.core.media.engine

import io.github.currencortex.music.data.settings.AudioOutputMode

data class AudioEffectPlan(val bypass: Boolean, val requested: List<String>, val reason: String?)
object AudioEffectPipeline {
    fun plan(mode: AudioOutputMode, effects: List<String>): AudioEffectPlan = if (mode.bypassProcessing)
        AudioEffectPlan(true, effects, "${mode.label} 绕过 DSP、重采样、变速和淡入淡出")
    else AudioEffectPlan(false, effects, null)
}
