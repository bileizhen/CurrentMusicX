// Copyright 2013 The Flutter Authors. SPDX-License-Identifier: BSD-3-Clause
// Native translation of material_ui 1.5.0 ink_sparkle.dart. License:
// assets/legal/Flutter-BSD-3-Clause.txt. Shader: ink_sparkle.agsl.
package io.github.currencortex.music.ui.component.nativeui

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.*

internal class InkSparklePainter(private val source: String) {
    private var platform: PlatformInkSparkle? = null
    fun draw(scope: DrawScope, origin: Offset, color: Color, seed: Float, progress: Float, native: Boolean) = with(scope) {
        if (progress <= 0f || progress >= 1f || size.width <= 0f || size.height <= 0f) return@with
        val radiusScale = FastOutSlowIn.transform((progress / .75f).coerceAtMost(1f))
        val centerProgress = (radiusScale * 2).coerceAtMost(1f)
        val start = if (origin.isSpecified) origin else center
        val rippleCenter = start + (center - start) * centerProgress
        val radius = hypot(size.width, size.height) / 2 * 2.3f
        val alpha = when {
            progress < .13f -> progress / .13f
            progress < .4f -> 1f
            else -> (1 - progress) / .6f
        }
        // TweenSequence's 13/27/50 weights are normalized to their total, 90.
        val sparkleAlpha = when {
            progress < 13f / 90 -> progress / (13f / 90)
            progress < 40f / 90 -> 1f
            else -> (1 - progress) / (50f / 90)
        }
        if (native && Build.VERSION.SDK_INT >= 33) {
            val painter = platform ?: PlatformInkSparkle(source).also { platform = it }
            painter.draw(scope, rippleCenter, radius, color, seed, radiusScale, alpha, sparkleAlpha)
        } else {
            // The same wave on Android 8–12/software canvases. Those renderers
            // cannot execute RuntimeShader's white sparkle fragment pass.
            val extent = radius * radiusScale * 1.5f
            if (extent > 0) {
                val stops = (0..12).map { i ->
                    val t = (i / 12f * 1.5f - .5f).coerceIn(0f, 1f)
                    color.copy(alpha = color.alpha * alpha * (1 - t * t * (3 - 2 * t)))
                }
                drawCircle(Brush.radialGradient(stops, rippleCenter, extent), extent, rippleCenter)
            }
        }
    }
    private companion object { val FastOutSlowIn = CubicBezierEasing(.4f, 0f, .2f, 1f) }
}

@RequiresApi(33)
private class PlatformInkSparkle(source: String) {
    private val shader = RuntimeShader(source)
    private val paint = Paint().apply { shader = this@PlatformInkSparkle.shader }
    fun draw(scope: DrawScope, center: Offset, radius: Float, color: Color, seed: Float,
        radiusScale: Float, alpha: Float, sparkleAlpha: Float) = with(scope) {
        val width = size.width / density; val height = size.height / density
        val phase = seed + radiusScale
        val right = PI.toFloat() * .0078125f
        shader.setFloatUniform("u_density", density)
        shader.setFloatUniform("u_color", color.red, color.green, color.blue, color.alpha)
        shader.setFloatUniform("u_composite_1", alpha, sparkleAlpha, 1f, radiusScale)
        shader.setFloatUniform("u_center", center.x / density, center.y / density)
        shader.setFloatUniform("u_max_radius", radius / density)
        shader.setFloatUniform("u_resolution_scale", 1 / width, 1 / height)
        shader.setFloatUniform("u_noise_scale", 2.1f / width, 2.1f / height)
        shader.setFloatUniform("u_noise_phase", phase / 1000f)
        shader.setFloatUniform("u_circle1", .75f + phase * .01f * cos(.825f), .75f + phase * .01f * sin(.825f))
        shader.setFloatUniform("u_circle2", .3f - phase * .0066f * cos(.675f), .3f - phase * .0066f * sin(.675f))
        shader.setFloatUniform("u_circle3", 1.5f - phase * .0066f * cos(.525f), 1.5f - phase * .0066f * sin(.525f))
        val rotations = listOf(phase * right + 1.7f * PI.toFloat(), -phase * right + 2 * PI.toFloat(), phase * right + 2.75f * PI.toFloat())
        rotations.forEachIndexed { i, rotation -> shader.setFloatUniform("u_rotation${i + 1}", cos(rotation), sin(rotation)) }
        drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}
