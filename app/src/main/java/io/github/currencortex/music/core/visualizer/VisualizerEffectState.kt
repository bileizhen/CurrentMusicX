package io.github.currencortex.music.core.visualizer

import io.github.currencortex.music.data.visualizer.VisualizerQuality
import kotlin.math.*

/** Primitive, bounded pools. Owned by the active renderer; no Android/GPU or capture dependency. */
class VisualizerEffectState {
    val waveAge = FloatArray(4) { -1f }
    val waveStrength = FloatArray(4)
    val particleAngle = FloatArray(96) { it * 2.3999632f }
    val particleRadius = FloatArray(96) { .2f + (it * 37 % 97) / 97f * .75f }
    var particleCount = 0; private set
    var coverScale = 1f; private set
    var shakeX = 0f; private set
    var shakeY = 0f; private set
    var phase = 0f; private set
    var bass = 0f; private set
    var treble = 0f; private set
    var glitch = 0f; private set
    var transition = 0f; private set
    var events = 0L; private set
    private var generation = Long.MIN_VALUE
    private var kick = 0L
    private var transient = 0L
    private var lastGlitch = 0L
    private var glitchAge = 1f
    private var elapsed = 0f
    private var ringCursor = 0
    private var coverOffset = 0f
    private var coverVelocity = 0f
    fun activate(frame: VisualizerInterpolatedFrame) {
        reset(); generation = frame.generation; kick = frame.kickCount; transient = frame.transientCount
    }
    fun step(frame: VisualizerInterpolatedFrame, config: VisualizerEffectConfig, quality: VisualizerQuality,
        now: Long, delta: Float, systemReduceMotion: Boolean = false) {
        if (generation != frame.generation) { reset(); generation = frame.generation }
        val dt = delta.takeIf { it.isFinite() }?.coerceIn(0f, .05f) ?: 0f
        val c = config // Repository / renderer normalize only when settings change.
        val reduced = c.reduceMotion || systemReduceMotion
        elapsed += dt; transition = (elapsed / .24f).coerceIn(0f, 1f)
        bass = energy(frame.bass, c.bassSensitivity * c.globalIntensity)
        treble = energy(frame.treble, c.globalIntensity)
        val pulse = safe(frame.pulse) * c.globalIntensity.coerceAtMost(1f)
        val impact = if (c.presetId == VisualizerPreset.BASS_IMPACT) .12f else .08f
        val motion = c.motionIntensity * if (reduced) .3f else 1f
        val target = bass * .028f * motion
        // Stable small integration steps use elapsed seconds, never a fixed number of frames.
        var remaining = dt
        while (remaining > 0f) {
            val h = minOf(remaining, 1f / 240f)
            coverVelocity += ((target - coverOffset) * 400f - coverVelocity * 28f) * h
            coverOffset += coverVelocity * h
            remaining -= h
        }
        coverScale = 1f + coverOffset.coerceIn(0f, impact)
        // Phase advances only with genuine energy. Silent frames do not invent beats or motion.
        phase = (phase + dt * (.08f + treble * .4f) * energy(frame.rms, c.globalIntensity) * c.motionIntensity * if (reduced) .2f else 1f) % (2f * PI.toFloat())
        for (i in waveAge.indices) if (waveAge[i] >= 0f) { waveAge[i] += dt; if (waveAge[i] > .35f) waveAge[i] = -1f }
        if (frame.kickCount < kick) kick = 0
        if (frame.kickCount > kick) {
            kick = frame.kickCount; events++
            coverVelocity = minOf(2f, coverVelocity + pulse * motion * if (c.presetId == VisualizerPreset.BASS_IMPACT) 1.8f else 1.35f)
            waveAge[ringCursor] = 0f; waveStrength[ringCursor] = pulse * if (reduced) .25f else 1f
            ringCursor = (ringCursor + 1) % waveAge.size
        }
        glitchAge += dt
        if (frame.transientCount < transient) transient = 0
        if (frame.transientCount > transient) {
            transient = frame.transientCount
            val cooldown = if (reduced) 1_500_000_000L else 450_000_000L
            if (lastGlitch == 0L || now - lastGlitch >= cooldown) { lastGlitch = now; glitchAge = 0f }
        }
        glitch = if (glitchAge < .075f) (1f - glitchAge / .075f) * c.glitchIntensity * c.globalIntensity.coerceAtMost(1f) * if (reduced) .15f else 1f else 0f
        shakeX = if (!reduced && c.presetId == VisualizerPreset.BASS_IMPACT) sin(elapsed * 91f) * pulse * c.motionIntensity * 2f else 0f
        shakeY = if (!reduced && c.presetId == VisualizerPreset.BASS_IMPACT) cos(elapsed * 73f) * pulse * c.motionIntensity * 1.5f else 0f
        val maximum = when (quality) { VisualizerQuality.ULTRA -> 96; VisualizerQuality.HIGH -> 48; VisualizerQuality.BALANCED -> 24; VisualizerQuality.ECO -> 8 }
        particleCount = (maximum * c.particleDensity * if (reduced) .35f else 1f).toInt()
        for (i in 0 until particleCount) {
            particleAngle[i] += dt * (bass * .06f + treble * .16f) * c.motionIntensity * if (reduced) .2f else 1f
            particleRadius[i] += dt * pulse * .18f * c.motionIntensity * if (reduced) .1f else 1f
            if (particleRadius[i] > 1f) particleRadius[i] = .2f
        }
    }
    fun reset() {
        waveAge.fill(-1f); waveStrength.fill(0f)
        for (i in particleAngle.indices) { particleAngle[i] = i * 2.3999632f; particleRadius[i] = .2f + (i * 37 % 97) / 97f * .75f }
        coverScale = 1f; shakeX = 0f; shakeY = 0f; phase = 0f; bass = 0f; treble = 0f; glitch = 0f
        coverOffset = 0f; coverVelocity = 0f; transition = 0f; elapsed = 0f; kick = 0; transient = 0; lastGlitch = 0; glitchAge = 1f; ringCursor = 0; events = 0; particleCount = 0
    }
    companion object {
        fun safe(value: Float) = value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        // Visualizer AS_PLAYED is volume dependent. This bounded display gain leaves analysis untouched.
        fun energy(value: Float, sensitivity: Float) = (sqrt(safe(value)) * 3f * sensitivity).coerceIn(0f, 1f)
        fun spectrum(values: FloatArray, position: Float, sensitivity: Float): Float {
            if (values.isEmpty()) return 0f
            val p = (position.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f) * (values.size - 1)
            val i = p.toInt(); val mix = p - i
            val magnitude = safe(values[i]) * (1f - mix) + safe(values[minOf(i + 1, values.lastIndex)]) * mix
            // Soft shoulder retains differences in loud broadband music instead of clipping bars.
            val gain = sensitivity.takeIf { it.isFinite() }?.coerceIn(0f, 6f) ?: 0f
            return 1f - exp(-magnitude.pow(.72f) * 4f * gain)
        }
    }
}
