package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import io.github.currencortex.music.ui.component.miuix.effect.BgEffectBackground
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.sin

private const val BRAND = "Current Music"
private val brandStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
private val flightEasing = CubicBezierEasing(.25f, .1f, .25f, 1f)

@Stable class LaunchBrandState {
    var active by mutableStateOf(false)
    var anchor by mutableStateOf<Rect?>(null)
    val reveal = Animatable(0f)
    val idle = Animatable(0f)
    val flight = Animatable(0f)
}
val LocalLaunchBrand = staticCompositionLocalOf<LaunchBrandState?> { null }

/** The destination and animated wordmark share one font and measurement, including user scaling. */
@Composable fun HomeBrandTitle() {
    val launch = LocalLaunchBrand.current
    BasicText(BRAND, style = brandStyle.copy(color = MiuixTheme.colorScheme.onSurface),
        modifier = Modifier.testTag("home_brand_title").onGloballyPositioned { launch?.anchor = it.boundsInRoot() }
            .graphicsLayer { alpha = if (launch?.active == true) 0f else 1f })
}

private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
private fun mix(a: Float, b: Float, value: Float) = a + (b - a) * value
private fun curve(start: Offset, control: Offset, end: Offset, progress: Float): Offset {
    val q = 1f - progress
    return start * (q * q) + control * (2f * q * progress) + end * (progress * progress)
}

/** Only canvas transforms change per frame; the already-loading home keeps its full viewport. */
@Composable fun LaunchBrandOverlay(state: LaunchBrandState, ready: Boolean, canLandOnHome: Boolean,
    windowReady: Boolean = true, enableBlur: Boolean = true, onFinished: () -> Unit) {
    val latestReady by rememberUpdatedState(ready)
    val latestCanLand by rememberUpdatedState(canLandOnHome)
    val latestWindowReady by rememberUpdatedState(windowReady)
    val finish by rememberUpdatedState(onFinished)
    var destination by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var reducedMotion by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        // Android's system splash can still cover the first Compose frames. Begin after its exit.
        // Some non-launcher starts have no system splash, so this gate is bounded too.
        withTimeoutOrNull(600) { snapshotFlow { latestWindowReady }.first { it } }
        val reduced = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f
        reducedMotion = reduced
        if (reduced) state.reveal.snapTo(1f) else state.reveal.animateTo(1f, tween(980))
        // Wait for the actual first home load, including settled errors. Background prefetch is separate.
        coroutineScope {
            val pulse = if (reduced) null else launch {
                while (isActive) state.idle.animateTo(state.idle.value + 1f, tween(1600, easing = LinearEasing))
            }
            try { snapshotFlow { latestReady }.first { it } }
            finally { pulse?.cancelAndJoin() }
        }
        destination = state.anchor?.takeIf { latestCanLand }
        if (!reduced) state.flight.animateTo(1f, tween(740, easing = flightEasing))
        finish()
    }
    val colors = MiuixTheme.colorScheme
    // Match AboutContent's renderer, appearance and capability fallback exactly.
    val effectBackground = enableBlur && android.os.Build.VERSION.SDK_INT >= 35 &&
        LocalView.current.isHardwareAccelerated && isRuntimeShaderSupported()
    val measurer = rememberTextMeasurer()
    val layout = measurer.measure(BRAND, brandStyle)
    Box(Modifier.fillMaxSize().testTag("launch_animation")
        .clearAndSetSemantics { contentDescription = "Current Music 正在打开" }
        .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } }) {
        BgEffectBackground(dynamicBackground = effectBackground && !reducedMotion,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - smooth(state.flight.value) }
                .background(colors.surface),
            isFullSize = true, effectBackground = effectBackground) { }
        Canvas(Modifier.fillMaxSize()) {
            val intro = state.reveal.value
            val idle = state.idle.value
            val flight = state.flight.value
            val target = destination?.takeIf { latestCanLand }
            val fade = smooth(flight)
            val center = Offset(size.width / 2f, size.height * .47f)
            val centralScale = minOf(1.72f, (size.width - 40.dp.toPx()) / layout.size.width).coerceAtLeast(.2f)
            val ambientAlpha = smooth(intro * 2f) * (1f - smooth(flight * 2.5f))
            // A compact audio waveform settles into silence as the wordmark takes flight.
            val waveY = center.y + layout.size.height * centralScale * .65f + 30.dp.toPx()
            repeat(17) { i ->
                val rhythm = (.35f + .65f * sin((intro * 4f + idle * 2f) * PI.toFloat() - i * .55f).let { it * it })
                val envelope = 1f - kotlin.math.abs(i - 8) / 10f
                val barHeight = (5f + 19f * rhythm * envelope) * density
                val x = center.x + (i - 8) * 7.dp.toPx()
                drawLine(colors.primary.copy(alpha = .65f * ambientAlpha), Offset(x, waveY - barHeight / 2), Offset(x, waveY + barHeight / 2),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            val destinationCenter = target?.center?.minus(origin) ?: center
            val control = Offset(center.x + minOf(size.width * .1f, 54.dp.toPx()), mix(center.y, destinationCenter.y, .58f))
            val position = curve(center, control, destinationCenter, flight)
            val targetScale = target?.width?.div(layout.size.width) ?: centralScale
            val scale = mix(centralScale, targetScale, flight)
            val textAlpha = if (target == null) 1f - fade else 1f
            val textColor = lerp(Color.White, colors.onSurface, fade)
            val letterLift = 12.dp.toPx()
            withTransform({ translate(position.x, position.y); scale(scale, scale, Offset.Zero); translate(-layout.size.width / 2f, -layout.size.height / 2f) }) {
                if (intro >= .72f) drawText(layout, color = textColor, alpha = textAlpha)
                else BRAND.indices.forEach { index ->
                    val amount = smooth((intro * 1.55f - index * .025f) / .35f)
                    val bounds = layout.getBoundingBox(index)
                    withTransform({ translate(0f, (1f - amount) * letterLift) }) {
                        clipRect(bounds.left, 0f, bounds.right, layout.size.height.toFloat()) {
                            drawText(layout, color = textColor, alpha = amount * textAlpha)
                        }
                    }
                }
                val sweepPhase = if (idle > 0f) idle % 1f else ((intro - .3f) / .7f).coerceIn(0f, 1f)
                val sweep = sweepPhase * (layout.size.width * 1.5f) - layout.size.width * .25f
                val glow = smooth((intro - .32f) * 4f) * (1f - smooth(flight * 3f)) * textAlpha
                if (glow > 0f) drawText(layout, brush = Brush.linearGradient(listOf(colors.primary.copy(alpha = 0f),
                    colors.primary.copy(alpha = .8f * glow), colors.primary.copy(alpha = 0f)),
                    start = Offset(sweep - 32.dp.toPx(), 0f), end = Offset(sweep + 32.dp.toPx(), layout.size.height.toFloat())))
            }
        }
    }
}
