// SPDX-License-Identifier: GPL-3.0-only
package io.github.currencortex.music.ui.component.nativeui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The scrim is drawn behind the toolbar, so dithering never touches text or controls. */
@Composable internal fun Modifier.headerScrim(background: Color, status: Dp, toolbar: Dp,
    opacity: () -> Float): Modifier = if (Build.VERSION.SDK_INT >= 33 && background.luminance() < .5f) {
    ditheredHeaderScrim(background, status, toolbar, opacity)
} else drawWithCache {
    val start = (size.height - 16.dp.toPx()) * status.toPx() / (status + toolbar).toPx() / size.height
    val easing = CubicBezierEasing(.42f, 0f, .58f, 1f)
    val stops = buildList {
        add(0f to background.copy(alpha = 1f))
        for (i in 0..8) {
            val t = i / 8f
            add(start + (1 - start) * t to background.copy(alpha = 1 - easing.transform(t)))
        }
    }
    val brush = Brush.verticalGradient(*stops.toTypedArray())
    onDrawBehind { drawRect(brush, alpha = opacity().coerceIn(0f, 1f)) }
}

@RequiresApi(33)
@Composable private fun Modifier.ditheredHeaderScrim(background: Color, status: Dp, toolbar: Dp,
    opacity: () -> Float): Modifier {
    val shader = remember { RuntimeShader(HeaderScrimShader) }
    val paint = remember(shader) {
        Paint().also {
            it.shader = shader
            // Use the pixel noise below instead of stacking Skia's ordered dither on it.
            it.nativePaint.isDither = false
        }
    }
    return drawWithCache {
        // Keep FloatingHeader's status inset and 16dp visual overhang unchanged.
        val start = (size.height - 16.dp.toPx()) * status.toPx() / (status + toolbar).toPx()
        shader.setFloatUniform("fade", start, (size.height - start).coerceAtLeast(1f))
        shader.setColorUniform("tint", background.copy(alpha = 1f).toArgb())
        onDrawBehind {
            shader.setFloatUniform("opacity", opacity().coerceIn(0f, 1f))
            drawIntoCanvas { it.drawRect(0f, 0f, size.width, size.height, paint) }
        }
    }
}

private const val HeaderScrimShader = """
layout(color) uniform half4 tint;
uniform float2 fade;
uniform float opacity;

half4 main(float2 coord) {
    float t = clamp((coord.y - fade.x) / fade.y, 0.0, 1.0);
    // Invert x of cubic-bezier(.42, 0, .58, 1), then evaluate its continuous y.
    float u = t;
    for (int i = 0; i < 4; ++i) {
        float x = u * (1.26 + u * (-0.78 + 0.52 * u));
        float dx = 1.26 + u * (-1.56 + 1.56 * u);
        u = clamp(u - (x - t) / dx, 0.0, 1.0);
    }
    float alpha = (1.0 - u * u * (3.0 - 2.0 * u)) * opacity;
    // A stationary, sub-LSB RGB dither breaks dark 8-bit bands without shimmer.
    // Alpha-only noise would be attenuated by the low contrast of a dark scrim.
    float noise = fract(52.9829189 * fract(dot(floor(coord), float2(0.06711056, 0.00583715)))) - 0.5;
    float weight = smoothstep(0.0, 0.02, t) * smoothstep(0.0, 0.02, 1.0 - t);
    float dither = noise / 255.0 * weight * min(alpha * 255.0, 1.0);
    // AGSL requires premultiplied output, including at the transparent tail.
    float3 rgb = clamp(float3(tint.rgb) * alpha + dither, float3(0.0), float3(alpha));
    return half4(rgb, alpha);
}
"""
