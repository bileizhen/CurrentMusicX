package io.github.currencortex.music.feature.update

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope

import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.currencortex.music.ui.component.ArtworkGlowPalette
import kotlin.math.sin

/** Native interpretation of Neon Reveal's public Sunset controls:
 * https://pro.reactbits.dev/docs/components/neon-reveal
 * One half-resolution GPU surface. Typography and controls remain at display resolution. */
@Composable
internal fun NeonRevealBackground(palette: ArtworkGlowPalette, modifier: Modifier = Modifier,
    active: Boolean = true, preferShader: Boolean = true) {
    val warm = animateColorAsState(Color(palette.primary), tween(1100), label = "neon tube colour")
    val cool = animateColorAsState(Color(palette.secondary), tween(1100), label = "neon light colour")
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val motion = (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    val elapsed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, resumed, motion) {
        if (!active || !resumed || !motion) return@LaunchedEffect
        var previous = 0L
        var pending = 0f
        while (true) withInfiniteAnimationFrameNanos { now ->
            if (previous != 0L) pending += ((now - previous) / 1e9f).coerceAtMost(.1f)
            previous = now
            if (pending >= 1f / 30f) { elapsed.floatValue += pending; pending = 0f }
        }
    }
    val density = LocalDensity.current
    val inset = WindowInsets.safeDrawing
    // Keep the emitter beyond the physical screen; only its downward spill is visible.
    val top = -with(density) { 24.dp.toPx() } / 2f
    val bottom = (inset.getBottom(density) + with(density) { 10.dp.toPx() }) / 2f
    val thickness = with(density) { 3.dp.toPx() } / 2f
    val hardware = LocalView.current.isHardwareAccelerated
    val renderer = remember(hardware, preferShader) {
        if (preferShader && hardware && Build.VERSION.SDK_INT >= 33) runCatching { NeonRevealRenderer() }.getOrNull() else null
    }
    Layout(modifier = modifier.clipToBounds().testTag("update_neon_background"), content = {
        Canvas(Modifier.graphicsLayer {
            scaleX = 2f; scaleY = 2f; transformOrigin = TransformOrigin(0f, 0f)
            compositingStrategy = CompositingStrategy.Offscreen
        }) {
            val time = elapsed.floatValue
            val phase = ((time - .3f) / 2.6f).coerceIn(0f, 1f)
            val intro = if (!motion || time == 0f && !active) 1f else 1f - (1f - phase) * (1f - phase) * (1f - phase)
            if (renderer != null && Build.VERSION.SDK_INT >= 33) {
                renderer.draw(this, warm.value, cool.value, time, intro, top, bottom, thickness)
            } else drawCanvasNeon(warm.value, cool.value, time, intro, top, bottom, thickness)
        }
    }) { measurables, constraints ->
        val width = constraints.maxWidth.coerceAtLeast(2)
        val height = constraints.maxHeight.coerceAtLeast(2)
        val child = measurables.single().measure(Constraints.fixed((width + 1) / 2, (height + 1) / 2))
        layout(width, height) { child.place(0, 0) }
    }
}

@RequiresApi(33)
internal class NeonRevealRenderer {
    private val shader = RuntimeShader(NEON_REVEAL_SHADER)
    private val paint = Paint().apply { shader = this@NeonRevealRenderer.shader }
    fun draw(scope: DrawScope, tube: Color, light: Color, time: Float, progress: Float,
        top: Float, bottom: Float, thickness: Float) {
        shader.setFloatUniform("resolution", scope.size.width, scope.size.height)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("progress", progress)
        shader.setFloatUniform("insets", top, bottom)
        shader.setFloatUniform("thickness", thickness)
        shader.setColorUniform("tubeColor", tube.toArgb())
        shader.setColorUniform("lightColor", light.toArgb())
        scope.drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, scope.size.width, scope.size.height, paint) }
    }
}

/** Canvas fallback retains the centre-out reveal and mirrored tubes on older Android. */
private fun DrawScope.drawCanvasNeon(tube: Color, light: Color, time: Float, progress: Float,
    top: Float, bottom: Float, thickness: Float) {
    drawRect(Color(0xFF0A0A0A))
    if (progress <= 0f) return
    val half = size.width * .45f * progress
    val left = size.width / 2 - half
    val right = size.width / 2 + half
    val hum = .98f + .02f * sin(time * 1.4f)
    drawRect(Brush.verticalGradient(listOf(tube.copy(alpha = .65f * progress),
        light.copy(alpha = .7f * progress), light.copy(alpha = .32f * progress),
        light.copy(alpha = .7f * progress), tube.copy(alpha = .65f * progress)),
        startY = top, endY = size.height - bottom), Offset(left, top),
        androidx.compose.ui.geometry.Size(half * 2, (size.height - bottom - top).coerceAtLeast(1f)))
    for (y in listOf(top, size.height - bottom)) {
        for (i in 12 downTo 1) drawLine(tube.copy(alpha = .018f * progress * hum),
            Offset(left, y), Offset(right, y), thickness * i * 2, StrokeCap.Round)
        drawLine(tube.copy(alpha = progress), Offset(left, y), Offset(right, y), thickness * 1.6f, StrokeCap.Round)
        drawLine(lerp(tube, Color.White, .8f).copy(alpha = progress), Offset(left, y), Offset(right, y),
            thickness * .65f, StrokeCap.Round)
    }
}

// Independently authored AGSL from the public appearance and controls, not third-party source.
internal const val NEON_REVEAL_SHADER = """
uniform float2 resolution;
uniform float2 insets;
uniform float thickness;
uniform float time;
uniform float progress;
layout(color) uniform half4 tubeColor;
layout(color) uniform half4 lightColor;

float3 neon(float2 pos, float tubeY, float direction) {
    float halfLength = resolution.x * .45 * progress;
    float x = abs(pos.x - resolution.x * .5);
    float dy = (pos.y - tubeY) * direction;
    float height = max(1., resolution.y - insets.x - insets.y);
    float distanceToTube = length(float2(max(0., x - halfLength), dy));
    float core = 1. - smoothstep(thickness * .3, thickness * .65, distanceToTube);
    float glow = exp(-distanceToTube / (thickness * 3.)) * .7
        + exp(-distanceToTube / (thickness * 12.)) * .18;
    float y = max(0., dy / height);
    float feather = resolution.x * (.025 + .12 * y);
    float edge = 1. - smoothstep(max(0., halfLength - feather), halfLength + feather, x);
    float3 tube = float3(tubeColor.rgb);
    float3 light = float3(lightColor.rgb);
    float3 wash = mix(tube, light, smoothstep(0., .22, y));
    float strength = exp(-y * 2.3) * (.84 + .16 * cos(x / resolution.x * 3.));
    float hum = .98 + .02 * sin(time * 1.4);
    float lit = smoothstep(0., .10, progress);
    return lit * hum * (wash * strength * edge * smoothstep(-thickness, thickness, dy)
        + tube * glow + mix(tube, float3(1.), .85) * core);
}
half4 main(float2 pos) {
    float3 bottom = neon(pos, resolution.y - insets.y, -1.);
    float3 top = neon(pos, insets.x, 1.);
    // Screen blend keeps the overlap luminous without clipping the two colour layers to white.
    float3 color = float3(.0392) + 1. - (1. - min(bottom, float3(1.))) * (1. - min(top, float3(1.)));
    return half4(half3(clamp(color, 0., 1.)), 1.);
}
"""

