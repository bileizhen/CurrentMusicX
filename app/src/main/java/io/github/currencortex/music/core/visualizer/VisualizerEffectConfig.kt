package io.github.currencortex.music.core.visualizer

enum class VisualizerPreset(val displayName: String, val circular: Boolean) {
    NEON_PULSE("Neon Pulse · 霓虹脉冲", false),
    ORBIT_SPECTRUM("Orbit Spectrum · 环绕频谱", true),
    BASS_IMPACT("Bass Impact · 低音冲击", false),
    DARK_GLITCH("Dark Glitch · 暗黑故障", false);
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: ORBIT_SPECTRUM }
}

/** Enabled and adaptiveQuality are projections of the existing M0/M1 keys, never new switches. */
data class VisualizerEffectConfig(
    val enabled: Boolean = false, val presetId: VisualizerPreset = VisualizerPreset.ORBIT_SPECTRUM,
    val globalIntensity: Float = 1f, val bassSensitivity: Float = 1f, val spectrumSensitivity: Float = 1f,
    val glowIntensity: Float = 1f, val particleDensity: Float = .65f, val motionIntensity: Float = .6f,
    val distortionIntensity: Float = .5f, val glitchIntensity: Float = .5f,
    val reduceMotion: Boolean = false, val adaptiveQuality: Boolean = true,
) {
    fun normalized() = copy(globalIntensity = range(globalIntensity, 1f, 2f),
        bassSensitivity = range(bassSensitivity, 1f, 3f), spectrumSensitivity = range(spectrumSensitivity, 1f, 3f),
        glowIntensity = range(glowIntensity, 1f, 2f), particleDensity = range(particleDensity, .65f, 1f),
        motionIntensity = range(motionIntensity, .6f, 1f), distortionIntensity = range(distortionIntensity, .5f, 1f),
        glitchIntensity = range(glitchIntensity, .5f, 1f))
    private fun range(value: Float, fallback: Float, maximum: Float) =
        if (value.isFinite()) value.coerceIn(0f, maximum) else fallback
}
