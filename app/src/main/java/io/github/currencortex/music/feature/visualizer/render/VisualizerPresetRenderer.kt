package io.github.currencortex.music.feature.visualizer.render

import android.os.Build
import androidx.compose.ui.unit.dp
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
    private val rayCounts = intArrayOf(96,72,48,32)
    private val rayX = Array(4) { q -> FloatArray(rayCounts[q]) { cos(it * 2f * PI.toFloat() / rayCounts[q] - PI.toFloat() / 2) } }
    private val rayY = Array(4) { q -> FloatArray(rayCounts[q]) { sin(it * 2f * PI.toFloat() / rayCounts[q] - PI.toFloat() / 2) } }
    private var cyan = Color(0xFF54DCEB)
    private var violet = Color(0xFF9D78ED)
    internal var verticalNeon = true // Selected after native A/B review; diagnostic alternative uses the same FFT.
    private val defaultPrimary = when (preset) { VisualizerPreset.NEON_PULSE -> Color(0xFF7B61FF); VisualizerPreset.ORBIT_SPECTRUM -> Color(0xFF46DED3); VisualizerPreset.BASS_IMPACT -> Color(0xFFFFB548); VisualizerPreset.DARK_GLITCH -> Color(0xFFEF4D62) }
    private var primary = defaultPrimary
    private var artworkPalette: VisualizerArtworkPalette? = null
    internal val artworkColors get() = artworkPalette
    fun updateArtworkPalette(palette: VisualizerArtworkPalette?) {
        if (palette == artworkPalette) return
        artworkPalette = palette
        val sampled = palette?.let { Color(it.primary) }
        primary = when {
            sampled == null -> defaultPrimary
            preset == VisualizerPreset.BASS_IMPACT || preset == VisualizerPreset.DARK_GLITCH -> lerp(sampled, defaultPrimary, .35f)
            else -> sampled
        }
        coverColor = sampled ?: Color(0xFF183B50)
        violet = sampled ?: Color(0xFF9D78ED)
        cyan = palette?.let { Color(it.accent) } ?: Color(0xFF54DCEB)
        // Size/color caches also depend on the preset tint when an image is replaced.
        cachedColor = Color.Unspecified
    }
    private var gradient: Brush = Brush.verticalGradient(listOf(Color(0xFF050B14), Color(0xFF09212D)))
    fun step(frame: VisualizerInterpolatedFrame, time: Long, dt: Float, level: VisualizerQuality) {
        if (!closed) {
            if (!active) effects.activate(frame)
            active = true; quality = level; effects.step(frame, config, level, time, dt, systemReduceMotion)
        }
    }
    fun reset() { active = false; effects.reset(); if (Build.VERSION.SDK_INT >= 33) gpu?.close(); gpu = null }
    fun DrawScope.atmosphere() {
        if (cachedSize != size || cachedColor != coverColor) {
            cachedSize = size; cachedColor = coverColor
            val tint = lerp(primary, coverColor, if (preset.circular) .4f else .18f)
            gradient = Brush.radialGradient(listOf(lerp(Color(0xFF080B14), tint, if (preset == VisualizerPreset.BASS_IMPACT) .45f else .25f), Color(0xFF050B14)), center, size.maxDimension * .7f)
        }
        drawRect(gradient)
        if (config.enabled) {
            drawCircle(primary, size.minDimension * (.31f + effects.bass * .035f), center,
                alpha = effects.bass * .035f * config.glowIntensity, style = broad)
            // Deep shadow and separate near glow anchor the artwork in the atmosphere.
            drawCircle(Color(0xFF030610), size.minDimension * .28f, center, alpha = .25f)
        }
    }
    fun DrawScope.render(frame: VisualizerInterpolatedFrame) {
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
                gpu?.draw(this, effects, config, frame.pulse, config.reduceMotion || systemReduceMotion, primary, cyan)
            } catch (_: RuntimeException) { gpu?.close(); gpu = null; shaderFailed = true }
        }
    }
    private fun DrawScope.orbit(frame: VisualizerInterpolatedFrame, r: Float) {
        val breathing = r * (1.08f + effects.bass * .018f)
        drawCircle(cyan, breathing, alpha = .08f * config.glowIntensity, style = glowStroke)
        drawCircle(cyan, breathing, alpha = .55f, style = thin)
        drawCircle(primary, r * 1.19f, alpha = .24f, style = thin)
        val count = when (quality) { VisualizerQuality.ULTRA -> 96; VisualizerQuality.HIGH -> 72; VisualizerQuality.BALANCED -> 48; VisualizerQuality.ECO -> 32 }
        for (i in 0 until count) {
            val direction = Offset(rayX[quality.ordinal][i], rayY[quality.ordinal][i])
            val frequency = abs(i.toFloat() / count * 2f - 1f)
            val level = VisualizerEffectState.spectrum(frame.spectrum, frequency, config.spectrumSensitivity * config.globalIntensity)
            val color = lerp(primary, cyan, 1f - frequency)
            val start = center + direction * (r * 1.27f)
            val end = center + direction * (r * (1.29f + level * .63f))
            if (quality.ordinal < 2) drawLine(color, start, end, size.minDimension * .012f,
                cap = StrokeCap.Round, alpha = level * .075f * config.glowIntensity)
            drawLine(color, start, end, size.minDimension * .0042f, cap = StrokeCap.Round, alpha = .2f + level * .7f)
        }
    }
    private fun DrawScope.neon(frame: VisualizerInterpolatedFrame, r: Float) {
        val pulse = frame.pulse * config.globalIntensity.coerceAtMost(1f) * if (config.reduceMotion || systemReduceMotion) .2f else 1f
        rotate(effects.phase * 3f, center) {
            for (layer in 0..1) {
                val radius = r * (if (layer == 0) 1.62f else 1.87f) * (1f + pulse * .018f)
                path.reset(); path.moveTo(center.x, center.y - radius)
                path.lineTo(center.x + radius, center.y); path.lineTo(center.x, center.y + radius)
                path.lineTo(center.x - radius, center.y); path.close()
                drawPath(path, violet, alpha = (.08f + pulse * .07f) * config.glowIntensity, style = broad)
                drawPath(path, if (layer == 0) violet else lerp(primary, cyan, .6f), alpha = if (layer == 0) .42f else .18f, style = thin)
            }
        }
        val count = when (quality) { VisualizerQuality.ULTRA -> 28; VisualizerQuality.HIGH -> 24; VisualizerQuality.BALANCED -> 20; VisualizerQuality.ECO -> 14 }
        val dy = r * 2.35f / count
        for (i in 0 until count) {
            val frequency = i.toFloat() / (count - 1)
            val energy = VisualizerEffectState.spectrum(frame.spectrum, frequency, config.spectrumSensitivity * config.globalIntensity)
            val y = center.y + r * 1.175f - (i + .5f) * dy
            val length = size.minDimension * (.004f + .165f * energy * (1f - frequency * .2f))
            val color = lerp(violet, cyan, frequency)
            for (side in -1..1 step 2) {
                val start = if (verticalNeon) Offset(center.x + side * (r * 1.14f + i * size.minDimension * .15f / count), center.y + r)
                    else Offset(center.x + side * r * 1.14f, y)
                val end = if (verticalNeon) start - Offset(0f, energy * r * 2f) else start + Offset(side * length, 0f)
                val width = if (verticalNeon) size.minDimension * .003f * (1.25f - frequency * .3f) else dy * (.34f + (1f - frequency) * .12f)
                if (quality.ordinal < 2) drawLine(color, start, end, width * 2.8f, cap = StrokeCap.Round, alpha = energy * .09f * config.glowIntensity)
                drawLine(color, start, end, width, cap = StrokeCap.Round, alpha = .16f + energy * .78f)
                drawCircle(lerp(color, Color.White, .28f), width * .4f, end, alpha = energy * .65f)
            }
        }
        if (effects.treble > .04f && quality != VisualizerQuality.ECO) {
            for (side in -1..1 step 2) {
                val x = center.x + side * r * 1.67f
                path.reset(); path.moveTo(x, center.y - r)
                for (i in 1..16) {
                    val y = center.y - r + i * r / 8f
                    val branch = sin(i * 2.7f + effects.phase * 14f) * r * .09f * effects.treble
                    path.lineTo(x + branch, y)
                    if (i % 4 == 0) { path.lineTo(x + branch + side * r * .09f, y - r * .06f); path.lineTo(x + branch, y) }
                }
                drawPath(path, cyan, alpha = (effects.treble * .3f + pulse * .18f).coerceAtMost(.6f), style = thin)
            }
        }
    }
    private fun DrawScope.impact(frame: VisualizerInterpolatedFrame, r: Float) {
        val reduced = config.reduceMotion || systemReduceMotion
        val count = minOf((size.minDimension / 9f).toInt().coerceAtLeast(12), when (quality) { VisualizerQuality.ULTRA -> 56; VisualizerQuality.HIGH -> 36; VisualizerQuality.BALANCED -> 24; VisualizerQuality.ECO -> 12 })
        val pulse = frame.pulse * if (reduced) .15f else 1f
        for (i in 0 until count) {
            val angle = i * 2f * PI.toFloat() / count + sin(i * 7f) * .08f
            val direction = Offset(cos(angle), sin(angle))
            val start = center + direction * (r * (1.47f + (i % 7) * .085f + pulse * .18f))
            val end = start + direction * size.minDimension * (.012f + pulse * (.11f + (i % 5) * .02f) * config.motionIntensity)
            drawLine(primary, start, end, if (i % 4 == 0) 2f else 1f,
                alpha = (.02f + pulse * if (i % 3 == 0) .5f else .24f) * config.globalIntensity.coerceAtMost(1f))
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
        if (preset.circular) {
            drawCircle(cyan, r * 1.025f, alpha = .1f * config.glowIntensity, style = glowStroke)
            drawCircle(cyan, r * 1.012f, alpha = .5f, style = thin)
            return
        }
        val topLeft = center - Offset(r,r)
        drawRoundRect(primary, topLeft, Size(r * 2f,r * 2f), CornerRadius(8.dp.toPx() * effects.coverScale),
            alpha = (.08f + effects.bass * .15f) * config.glowIntensity, style = glowStroke)
        drawRoundRect(if (preset == VisualizerPreset.NEON_PULSE) lerp(violet, Color.White, .55f) else primary,
            topLeft, Size(r * 2f,r * 2f), CornerRadius(8.dp.toPx() * effects.coverScale), alpha = .75f, style = border)
    }
    override fun close() { reset(); closed = true; path.reset() }
}
