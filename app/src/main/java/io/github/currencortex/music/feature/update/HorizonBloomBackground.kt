package io.github.currencortex.music.feature.update

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** Native interpretation of Horizon Bloom's public Eclipse controls:
 * https://pro.reactbits.dev/docs/components/horizon-bloom
 * One half-resolution GPU surface. Typography and controls remain at display resolution. */
@Composable
internal fun HorizonBloomBackground(palette: ArtworkGlowPalette, modifier: Modifier = Modifier,
    active: Boolean = true, pulse: Int = 0, touchX: Float = .5f, preferShader: Boolean = true) {
    val warm = animateColorAsState(Color(palette.primary), tween(1100), label = "horizon warm colour")
    val cool = animateColorAsState(Color(palette.secondary), tween(1100), label = "horizon cool colour")
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val motion = (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    val elapsed = remember { mutableFloatStateOf(0f) }
    var bloomStarted by remember { mutableFloatStateOf(-100f) }
    LaunchedEffect(pulse) { if (pulse > 0) bloomStarted = elapsed.floatValue }
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
    val hardware = LocalView.current.isHardwareAccelerated
    val renderer = remember(hardware, preferShader) {
        if (preferShader && hardware && Build.VERSION.SDK_INT >= 33) runCatching { HorizonBloomRenderer() }.getOrNull() else null
    }
    Layout(modifier = modifier.clipToBounds().testTag("update_horizon_background"), content = {
        Canvas(Modifier.graphicsLayer {
            scaleX = 2f; scaleY = 2f; transformOrigin = TransformOrigin(0f, 0f)
            compositingStrategy = CompositingStrategy.Offscreen
        }) {
            val time = elapsed.floatValue
            val intro = if (!motion || time == 0f && !active) 1f else (time / 1.8f).coerceIn(0f, 1f)
            if (renderer != null && Build.VERSION.SDK_INT >= 33) {
                renderer.draw(this, warm.value, cool.value, time, intro, time - bloomStarted, touchX)
            } else drawCanvasHorizon(warm.value, cool.value, time, intro)
        }
    }) { measurables, constraints ->
        val width = constraints.maxWidth.coerceAtLeast(2)
        val height = constraints.maxHeight.coerceAtLeast(2)
        val child = measurables.single().measure(Constraints.fixed((width + 1) / 2, (height + 1) / 2))
        layout(width, height) { child.place(0, 0) }
    }
}

@RequiresApi(33)
internal class HorizonBloomRenderer {
    private val shader = RuntimeShader(HORIZON_BLOOM_SHADER)
    private val paint = Paint().apply { shader = this@HorizonBloomRenderer.shader }
    fun draw(scope: DrawScope, warm: Color, cool: Color, time: Float, intro: Float, age: Float, touch: Float) {
        shader.setFloatUniform("resolution", scope.size.width, scope.size.height)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("intro", intro)
        shader.setFloatUniform("auroraAge", age)
        shader.setFloatUniform("touchX", touch)
        shader.setColorUniform("warmColor", warm.toArgb())
        shader.setColorUniform("coolColor", cool.toArgb())
        scope.drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, scope.size.width, scope.size.height, paint) }
    }
}

/** The same curved silhouette and diffused rim on API 26–32 / unsupported renderers. */
private fun DrawScope.drawCanvasHorizon(warm: Color, cool: Color, time: Float, intro: Float) {
    drawRect(Color(0xFF0A0A0A))
    val radius = size.width * .70f
    val center = Offset(size.width * .5f, size.height * .62f + radius)
    val sunlight = Offset(size.width * (.5f + .02f * sin(time * .12f)), size.height * .62f)
    drawCircle(Brush.radialGradient(listOf(cool.copy(alpha = .40f * intro), Color.Transparent), sunlight, size.width * .9f),
        size.width * .9f, sunlight)
    for (index in 10 downTo 1) {
        drawCircle(cool.copy(alpha = .023f * intro), radius, center,
            style = Stroke(size.width * index * .012f))
    }
    drawCircle(Color(0xFF070709), radius, center)
    drawCircle(warm.copy(alpha = .85f * intro), radius, center, style = Stroke(size.width * .003f))
    drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = .65f * intro), Color.Transparent), sunlight, size.width * .24f),
        size.width * .24f, sunlight)
}

// Independently authored AGSL; no React/WebView runtime or third-party shader source.
internal const val HORIZON_BLOOM_SHADER = """
uniform float2 resolution;
uniform float time;
uniform float intro;
uniform float auroraAge;
uniform float touchX;
layout(color) uniform half4 warmColor;
layout(color) uniform half4 coolColor;

float hash(float2 p) {
    float3 q = fract(float3(p.xyx) * .1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

half4 main(float2 coord) {
    float w = resolution.x;
    float2 uv = coord / resolution;
    float2 p = float2((coord.x - w * .5) / w, (coord.y - resolution.y * .62) / w);
    float radius = .70;
    float d = length(p - float2(0., radius)) - radius;
    float outside = smoothstep(-.0015, .0015, d);
    float rise = smoothstep(0., 1., intro);
    float sunX = .022 * sin(time * .12);
    float sunY = radius - sqrt(max(.001, radius * radius - sunX * sunX));
    float2 sun = float2(sunX, sunY);
    float sunlight = .45 + .55 * exp(-abs(p.x - sunX) * 1.1);
    float depth = max(d, 0.);
    float haze = exp(-depth * 9.) * .29;
    float airglow = exp(-pow((depth - .065) * 24., 2.)) * .055;
    float rim = exp(-abs(d) * 430.) * sunlight;
    float near = exp(-depth * 65.) * .38;
    float cloud = .80 + .12 * sin(p.x * 42. + time * .19) * sin(p.x * 19. - time * .13);
    float flare = exp(-length(p - sun) * 27.) * .72;
    float streak = exp(-abs(p.y - sunY) * 380.) * exp(-abs(p.x - sunX) * 5.) * .20;
    float3 warm = float3(warmColor.rgb);
    float3 cool = float3(coolColor.rgb);
    float3 color = float3(.012, .012, .016);
    color += outside * rise * (cool * (haze + airglow) + warm * near * cloud) * sunlight;
    color += rise * warm * (rim * .82 + flare + streak);
    color *= mix(.40, 1., outside);

    float2 cell = floor(coord / 9.);
    float random = hash(cell);
    float2 star = (cell + float2(hash(cell + 3.), hash(cell + 11.))) * 9.;
    float sparkle = exp(-dot(coord - star, coord - star) * 2.8);
    float twinkle = .6 + .4 * sin(time * .6 + random * 80.);
    color += outside * step(.994, random) * sparkle * twinkle * .32;

    float age = max(auroraAge, 0.);
    float pulse = step(0., auroraAge) * (1. - exp(-age * 4.)) * exp(-age * .62);
    float ray = .45 + .30 * sin(p.x * 75. + time * .7) + .25 * sin(p.x * 119. - time * .4);
    float curtain = exp(-pow((uv.x - touchX) * 5.5, 2.)) * exp(-depth * 5.) * smoothstep(0., .05, depth);
    color += cool * outside * curtain * ray * pulse * .42;
    float grain = (hash(floor(coord)) - .5) * .007 * rise;
    color += grain * min(1., haze * 4.);
    return half4(half3(clamp(color, 0., 1.)), 1.);
}
"""
