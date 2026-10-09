package io.github.currencortex.music.feature.visualizer.render

import android.os.Build
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import io.github.currencortex.music.core.visualizer.*
import io.github.currencortex.music.data.visualizer.VisualizerQuality
import kotlin.math.*

/** One active preset, no player/session/clock access. Geometry and pools are reused. */
class VisualizerPresetRenderer(val preset: VisualizerPreset) : AutoCloseable {
    val effects = VisualizerEffectState()
    var config = VisualizerEffectConfig(presetId = preset)
        set(value) { field = value.normalized() }
    var quality = VisualizerQuality.ULTRA
    var systemReduceMotion = false
    var coverLoaded = false
    var coverColor = Color(0xFF183B50)
    var closed = false; private set
    var active = false; private set
    var hardwareAccelerated = true
    var forceCanvas = false // Explicit diagnostic fallback, never a second renderer.
    private var shaderFailed = false
    private var gpu: ShaderEffectRenderer? = null
    val shaderStatus: String get() = when { shaderFailed -> "Canvas / Shader failure"; gpu != null -> "AGSL"; else -> "Canvas" }
    fun coverEffect(): androidx.compose.ui.graphics.RenderEffect? =
        if (Build.VERSION.SDK_INT >= 33 && gpu != null) gpu?.cover(effects.glitch) else null
    private val path = Path()
    private val thin = Stroke(1.5f)
    private val glowStroke = Stroke(8f)
    private var cachedSize = Size.Zero
    private var cachedColor = Color.Unspecified
    private val broad = Stroke(18f)
    private val border = Stroke(2.5f)
    private val primary = when (preset) { VisualizerPreset.NEON_PULSE -> Color(0xFF7B61FF); VisualizerPreset.ORBIT_SPECTRUM -> Color(0xFF46DED3); VisualizerPreset.BASS_IMPACT -> Color(0xFFFFB548); VisualizerPreset.DARK_GLITCH -> Color(0xFFEF4D62) }
    private var gradient: Brush = Brush.verticalGradient(listOf(Color(0xFF050B14), Color(0xFF09212D)))
    fun step(frame: VisualizerInterpolatedFrame, time: Long, dt: Float, level: VisualizerQuality) {
        if (!closed) {
            if (!active) effects.activate(frame)
            active = true; quality = level; effects.step(frame, config, level, time, dt, systemReduceMotion)
        }
    }
    fun reset() { active = false; effects.reset(); if (Build.VERSION.SDK_INT >= 33) gpu?.close(); gpu = null }
    fun DrawScope.render(frame: VisualizerInterpolatedFrame) {
        if (cachedSize != size || cachedColor != coverColor) {
            cachedSize = size; cachedColor = coverColor
            val tint = if (preset.circular) coverColor else primary.copy(alpha = 1f)
            gradient = Brush.radialGradient(listOf(lerp(Color(0xFF080B14), tint, if (preset == VisualizerPreset.BASS_IMPACT) .45f else .25f), Color(0xFF050B14)), center, size.maxDimension * .7f)
        }
        drawRect(gradient)
        if (!config.enabled) return
        val r = size.minDimension * .235f
        when (preset) {
            VisualizerPreset.ORBIT_SPECTRUM -> orbit(frame, r)
            VisualizerPreset.NEON_PULSE -> neon(frame, r)
            VisualizerPreset.BASS_IMPACT -> impact(frame, r)
            VisualizerPreset.DARK_GLITCH -> dark(frame, r)
        }
        val e = effects
        for (i in 0 until e.particleCount) {
            val angle = e.particleAngle[i]
            val p = center + Offset(cos(angle), sin(angle)) * (size.minDimension * .48f * e.particleRadius[i])
            if ((p - center).getDistance() > r * 1.17f)
                drawCircle(primary, 1f + (i % 3) * .4f, p, alpha = (.12f + e.treble * .5f) * config.globalIntensity.coerceAtMost(1f))
        }
        for (i in e.waveAge.indices) if (e.waveAge[i] >= 0) {
            val age = e.waveAge[i] / .35f
            drawCircle(primary, r * (1.25f + age * .75f), alpha = (1f - age) * e.waveStrength[i] * .45f,
                style = thin)
        }
    }
    fun DrawScope.shaderBackground(frame: VisualizerInterpolatedFrame) {
        val allowGpu = active && !closed && !forceCanvas && VisualizerShaderPolicy.enabled(Build.VERSION.SDK_INT, hardwareAccelerated, quality, shaderFailed) && !preset.circular
        if (Build.VERSION.SDK_INT >= 33) {
            if (!allowGpu) { gpu?.close(); gpu = null }
            else try {
                if (gpu == null) gpu = ShaderEffectRenderer(preset)
                gpu?.draw(this, effects, config, frame.pulse, config.reduceMotion || systemReduceMotion)
            } catch (_: RuntimeException) { gpu?.close(); gpu = null; shaderFailed = true }
        }
    }
    private fun DrawScope.orbit(frame: VisualizerInterpolatedFrame, r: Float) {
        val c = Color(0xFF46DED3)
        val breathing = r * (1.08f + effects.bass * .025f)
        drawCircle(c, breathing, alpha = (.06f + effects.bass * .12f) * config.glowIntensity.coerceAtMost(2f), style = glowStroke)
        drawCircle(c, breathing, alpha = .8f, style = thin)
        drawCircle(Color(0xFF84E5FF), r * 1.22f, alpha = .3f, style = thin)
        val count = when (quality) { VisualizerQuality.ULTRA -> 96; VisualizerQuality.HIGH -> 72; VisualizerQuality.BALANCED -> 48; VisualizerQuality.ECO -> 32 }
        for (i in 0 until count) {
            val angle = i * 2f * PI.toFloat() / count - PI.toFloat() / 2
            val direction = Offset(cos(angle), sin(angle))
            val mirrored = abs(i.toFloat() / count * 2f - 1f)
            val level = VisualizerEffectState.spectrum(frame.spectrum, mirrored, config.spectrumSensitivity * config.globalIntensity)
            val start = center + direction * (r * 1.28f)
            val end = center + direction * (r * (1.3f + level * .65f))
            drawLine(c, start, end, size.minDimension * .005f, cap = StrokeCap.Round, alpha = .3f + level * .7f)
            if (quality.ordinal < 2 && level > .1f) drawCircle(c, 4f, end, alpha = level * .15f * config.glowIntensity.coerceAtMost(2f))
        }
    }
    private fun DrawScope.neon(frame: VisualizerInterpolatedFrame, r: Float) {
        rotate(effects.phase * 5f, center) {
            path.reset(); path.moveTo(center.x, center.y - r * 1.55f)
            path.lineTo(center.x + r * 1.55f, center.y); path.lineTo(center.x, center.y + r * 1.55f)
            path.lineTo(center.x - r * 1.55f, center.y); path.close()
            drawPath(path, primary, alpha = .07f * config.glowIntensity, style = broad)
            drawPath(path, Color(0xFF387BFF), alpha = .6f, style = thin)
        }
        val count = when (quality) { VisualizerQuality.ULTRA -> 48; VisualizerQuality.HIGH -> 32; VisualizerQuality.BALANCED -> 24; VisualizerQuality.ECO -> 16 }
        val dy = r * 2f / count
        for (i in 0 until count) {
            val energy = VisualizerEffectState.spectrum(frame.spectrum, i.toFloat() / (count - 1), config.spectrumSensitivity * config.globalIntensity)
            val y = center.y - r + (i + .5f) * dy
            val length = size.minDimension * (.007f + .16f * energy)
            val color = if (i > count * .65f) Color(0xFF16D9FF) else primary
            val gap = r * 1.14f
            for (sign in -1..1 step 2) {
                val start = Offset(center.x + sign * gap, y); val end = Offset(start.x + sign * length, y)
                if (quality.ordinal < 2) drawLine(color, start, end, dy * 1.25f, alpha = energy * .15f * config.glowIntensity)
                drawLine(color, start, end, dy * .5f, cap = StrokeCap.Round, alpha = .3f + energy * .7f)
            }
        }
        if (effects.treble > .08f && quality != VisualizerQuality.ECO) {
            for (side in -1..1 step 2) {
                path.reset()
                val x = center.x + side * r * 1.6f
                path.moveTo(x, center.y - r * 1.1f)
                for (i in 1..12) path.lineTo(x + sin(i * 3.1f + effects.phase * 20f) * r * .13f * effects.treble,
                    center.y - r * 1.1f + i * r * 2.2f / 12)
                drawPath(path, Color(0xFF16D9FF), alpha = effects.treble * .55f, style = thin)
            }
        }
    }
    private fun DrawScope.impact(frame: VisualizerInterpolatedFrame, r: Float) {
        val reduced = config.reduceMotion || systemReduceMotion
        val count = when (quality) { VisualizerQuality.ULTRA -> 56; VisualizerQuality.HIGH -> 36; VisualizerQuality.BALANCED -> 24; VisualizerQuality.ECO -> 12 }
        val pulse = frame.pulse * if (reduced) .15f else 1f
        for (i in 0 until count) {
            val angle = i * 2f * PI.toFloat() / count + sin(i * 7f) * .08f
            val direction = Offset(cos(angle), sin(angle))
            val start = center + direction * (r * (1.65f + (i % 7) * .07f))
            val end = start + direction * size.minDimension * (.015f + pulse * .2f * config.motionIntensity)
            drawLine(primary, start, end, if (i % 4 == 0) 2f else 1f,
                alpha = (.05f + pulse * .45f) * config.globalIntensity.coerceAtMost(1f))
        }
        drawCircle(primary, r * (1.65f + effects.bass * .15f), alpha = effects.bass * .1f * config.glowIntensity, style = broad)
        // Spectrum remains present even when heavy motion/shaders are disabled.
        val countBars = 32 / quality.spectrumStride
        for (i in 0 until countBars) {
            val energy = VisualizerEffectState.spectrum(frame.spectrum, i.toFloat() / (countBars - 1), config.spectrumSensitivity * config.globalIntensity)
            val x = center.x - r + i * r * 2f / countBars
            drawLine(primary, Offset(x, center.y + r * 1.65f), Offset(x, center.y + r * 1.65f - energy * r * .45f), 3f, cap = StrokeCap.Round)
        }
    }
    private fun DrawScope.dark(frame: VisualizerInterpolatedFrame, r: Float) {
        drawCircle(primary, r * 1.5f, alpha = effects.bass * .08f * config.glowIntensity, style = broad)
        val step = if (quality.ordinal < 2) 10 else 20
        var y = 0f
        while (y < size.height) { drawLine(Color.White, Offset(0f,y), Offset(size.width,y), 1f, alpha = .025f); y += step }
        for (i in 0 until 16 / quality.spectrumStride) {
            val x = (i * 37 % 101) / 101f * size.width
            val y0 = (i * 53 % 103) / 103f * size.height
            drawLine(primary, Offset(x,y0), Offset(x + size.width * .04f,y0), 1f, alpha = .05f + effects.treble * .1f)
        }
        if (effects.glitch > 0f) {
            for (i in 0 until 3) {
                val y0 = center.y + r * (-.8f + i * .65f)
                drawRect(primary, Offset(center.x - r * 1.4f, y0), Size(r * 2.8f, 2f + effects.glitch * 8f), alpha = effects.glitch * .25f)
                drawLine(Color(0xFF30FFFF), Offset(center.x - r * 1.4f + 5f, y0 - 2f), Offset(center.x + r * 1.4f, y0 - 2f), 1f, alpha = effects.glitch)
            }
        }
        for (i in 0 until 24 / quality.spectrumStride) {
            val value = VisualizerEffectState.spectrum(frame.spectrum, i / 23f * quality.spectrumStride, config.spectrumSensitivity * config.globalIntensity)
            val x = size.width * .12f + i * size.width * .76f / (24 / quality.spectrumStride)
            drawLine(primary, Offset(x, size.height * .84f), Offset(x,size.height * .84f - value * r * .3f), 2f)
        }
    }
    fun DrawScope.foreground() {
        if (!config.enabled) return
        val r = size.minDimension * .235f * effects.coverScale
        if (preset.circular) return
        val topLeft = center - Offset(r,r)
        drawRoundRect(primary, topLeft, Size(r * 2f,r * 2f), CornerRadius(8f),
            alpha = (.08f + effects.bass * .15f) * config.glowIntensity, style = glowStroke)
        drawRoundRect(if (preset == VisualizerPreset.NEON_PULSE) Color.White else primary,
            topLeft, Size(r * 2f,r * 2f), CornerRadius(8f), alpha = .75f, style = border)
    }
    override fun close() { reset(); closed = true; path.reset() }
}
