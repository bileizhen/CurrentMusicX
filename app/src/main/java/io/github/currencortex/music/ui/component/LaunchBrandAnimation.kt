package io.github.currencortex.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import io.github.currencortex.music.ui.component.miuix.effect.BgEffectBackground
import io.github.currencortex.music.ui.component.miuix.effect.ColorBlendToken
import io.github.currencortex.music.ui.theme.LocalDarkTheme
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import kotlin.coroutines.coroutineContext

private const val BRAND = "Current Music"
private val brandStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
private val flightEasing = CubicBezierEasing(.25f, .1f, .25f, 1f)

@Stable class LaunchBrandState {
    var active by mutableStateOf(false)
    var anchor by mutableStateOf<Rect?>(null)
    var avatar by mutableStateOf<AvatarFlightOrigin?>(null)
    var avatarPending by mutableStateOf(false)
    var landedAvatarUrl by mutableStateOf<String?>(null)
    val reveal = Animatable(0f)
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
    var departing by remember { mutableStateOf(false) }
    var departureAvatar by remember { mutableStateOf<AvatarFlightOrigin?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var reducedMotion by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        // Android's system splash can still cover the first Compose frames. Begin after its exit.
        // Some non-launcher starts have no system splash, so this gate is bounded too.
        withTimeoutOrNull(600) { snapshotFlow { latestWindowReady }.first { it } }
        val reduced = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f
        reducedMotion = reduced
        if (reduced) state.reveal.snapTo(1f) else state.reveal.animateTo(1f, tween(520))
        // Wait for the actual first home load, including settled errors. Background prefetch is separate.
        snapshotFlow { latestReady }.first { it }
        // Give an in-flight photo request a brief chance to finish, without blocking startup
        // indefinitely on a missing image. Home preloads the very same Coil request.
        if (latestCanLand) withTimeoutOrNull(800) { snapshotFlow { state.avatarPending }.first { !it } }
        destination = state.anchor?.takeIf { latestCanLand }
        departureAvatar = state.avatar
        departing = true
        if (!reduced) state.flight.animateTo(1f, tween(1100, delayMillis = 250, easing = flightEasing))
        state.landedAvatarUrl = departureAvatar?.url?.takeIf { latestCanLand }
        finish()
    }
    val avatar = if (departing) departureAvatar else state.avatar
    val avatarReveal = remember { Animatable(0f) }
    LaunchedEffect(avatar?.url) {
        if (avatar == null) avatarReveal.snapTo(0f)
        else avatarReveal.animateTo(1f, tween(280))
    }
    val colors = MiuixTheme.colorScheme
    val blurEnabled = enableBlur && android.os.Build.VERSION.SDK_INT >= 33 &&
        LocalView.current.isHardwareAccelerated && isRuntimeShaderSupported()
    val effectBackground = blurEnabled && android.os.Build.VERSION.SDK_INT >= 35
    val backdrop = rememberLayerBackdrop()
    val isDark = LocalDarkTheme.current
    val inkColors = remember(isDark) { BlurColors(blendColors = ColorBlendToken.brandInk(isDark)) }
    val measurer = rememberTextMeasurer()
    val layout = measurer.measure(BRAND, brandStyle)
    val displayDensity = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("launch_animation")
        .clearAndSetSemantics { contentDescription = "Current Music 正在打开" }
        .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } } }) {
        BgEffectBackground(dynamicBackground = effectBackground && !reducedMotion,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - smooth(state.flight.value) }
                .background(colors.surface),
            bgModifier = Modifier.layerBackdrop(backdrop),
            isFullSize = true, effectBackground = effectBackground, animationSpeed = 3f) { }
        val viewportWidth = with(displayDensity) { maxWidth.toPx() }
        val viewportHeight = with(displayDensity) { maxHeight.toPx() }
        val center = Offset(viewportWidth / 2f, viewportHeight * .53f)
        val centralScale = minOf(1.72f, (viewportWidth - with(displayDensity) { 40.dp.toPx() }) / layout.size.width).coerceAtLeast(.2f)
        avatar?.let { photo ->
            val baseSize = with(displayDensity) { 64.dp.toPx() }
            val diameter = minOf(with(displayDensity) { 88.dp.toPx() }, viewportHeight * .2f)
            val start = Offset(center.x, center.y - layout.size.height * centralScale / 2f -
                with(displayDensity) { 28.dp.toPx() } - diameter / 2f)
            AvatarFlightPicture(photo.url, Modifier.align(Alignment.Center).size(64.dp)
                .testTag("launch_avatar").graphicsLayer {
                    val target = photo.bounds.takeIf { departing && latestCanLand }
                    val end = target?.center?.minus(origin) ?: start
                    val control = Offset(mix(start.x, end.x, .5f),
                        minOf(start.y, end.y) - minOf(viewportHeight * .12f, 80.dp.toPx()))
                    val position = curve(start, control, end, state.flight.value)
                    translationX = position.x - viewportWidth / 2f
                    translationY = position.y - viewportHeight / 2f + (1f - avatarReveal.value) * 8.dp.toPx()
                    val scale = mix(diameter, target?.width ?: diameter, state.flight.value) / baseSize
                    scaleX = scale; scaleY = scale
                    alpha = avatarReveal.value * smooth(state.reveal.value * 2f) *
                        (if (departing && target == null) 1f - smooth(state.flight.value) else 1f)
                }, photo.decorationUrl, photo.decorationScale)
        }
        // Sample the same background and blend tokens as About's wordmark. Keep the blur
        // surface at text size; the parabolic flight only transforms these small layers.
        val wordmark = Modifier.align(Alignment.Center)
            .requiredSize(with(displayDensity) { layout.size.width.toDp() }, with(displayDensity) { layout.size.height.toDp() })
            .graphicsLayer {
                val target = destination?.takeIf { latestCanLand }
                val end = target?.center?.minus(origin) ?: center
                val control = Offset(center.x + minOf(viewportWidth * .1f, 54.dp.toPx()), mix(center.y, end.y, .58f))
                val position = curve(center, control, end, state.flight.value)
                translationX = position.x - viewportWidth / 2f
                translationY = position.y - viewportHeight / 2f
                val scale = mix(centralScale, target?.width?.div(layout.size.width) ?: centralScale, state.flight.value)
                scaleX = scale; scaleY = scale
                alpha = if (target == null) 1f - smooth(state.flight.value) else 1f
            }
        if (blurEnabled) Canvas(wordmark.testTag("launch_wordmark")
            .graphicsLayer { alpha = 1f - smooth(state.flight.value) }
            .textureBlur(backdrop = backdrop, shape = RoundedCornerShape(0.dp), blurRadius = 150f,
                colors = inkColors, contentBlendMode = BlendMode.DstIn, enabled = true)) {
            drawWordmark(layout, state.reveal.value, Color.White)
        }
        Canvas(wordmark.then(if (blurEnabled) Modifier else Modifier.testTag("launch_wordmark"))
            .graphicsLayer { alpha = if (blurEnabled) smooth(state.flight.value) else 1f }) {
            drawWordmark(layout, state.reveal.value, colors.onSurface)
        }
    }
}

private fun DrawScope.drawWordmark(layout: TextLayoutResult, intro: Float, color: Color) {
    val letterLift = 12.dp.toPx()
    if (intro >= .72f) drawText(layout, color = color)
    else BRAND.indices.forEach { index ->
        val amount = smooth((intro * 1.55f - index * .025f) / .35f)
        val bounds = layout.getBoundingBox(index)
        withTransform({ translate(0f, (1f - amount) * letterLift) }) {
            clipRect(bounds.left, 0f, bounds.right, layout.size.height.toFloat()) {
                drawText(layout, color = color, alpha = amount)
            }
        }
    }
}
