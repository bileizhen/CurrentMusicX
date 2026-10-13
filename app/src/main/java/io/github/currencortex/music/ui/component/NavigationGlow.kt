package io.github.currencortex.music.ui.component

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.core.media.smoothDockEnergy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.sin

private val glowPalettes = LruCache<String, ArtworkGlowPalette>(16)

@Composable internal fun NavigationGlow(cover: String, playback: StateFlow<PlayerState>, meter: DockAudioLevel,
    modifier: Modifier = Modifier, active: Boolean = true) {
    val playing by remember(playback) { playback.map { it.playing }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = false)
    val palette by rememberArtworkGlowPalette(cover)
    DockGlowLight(palette, playing, meter, modifier, active)
}

/** Shared by the dock and lyrics; Coil and this bounded palette cache reuse cover requests. */
@Composable internal fun rememberArtworkGlowPalette(cover: String): State<ArtworkGlowPalette> {
    val context = LocalContext.current.applicationContext
    return produceState(glowPalettes.get(cover) ?: ArtworkGlowPalette.Neutral, cover) {
        if (cover.isBlank()) { value = ArtworkGlowPalette.Neutral; return@produceState }
        glowPalettes.get(cover)?.let { value = it; return@produceState }
        val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context)
            .data(coverRequestUrl(cover, 160)).size(64).allowHardware(false).build())
        value = if (result is SuccessResult) withContext(Dispatchers.Default) {
            val bitmap = result.image.toBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            ArtworkGlowPalette.fromPixels(pixels).also { glowPalettes.put(cover, it) }
        } else ArtworkGlowPalette.Neutral
    }
}

/** Broad, overlapping pools rise from below the glass edge, like the expanded atomic island. */
@Composable internal fun DockGlowLight(palette: ArtworkGlowPalette, playing: Boolean, meter: DockAudioLevel,
    modifier: Modifier = Modifier, active: Boolean = true) {
    val owner = LocalLifecycleOwner.current
    val motionEnabled = (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner, meter, active, motionEnabled) {
        fun update() {
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            meter.enabled = active && resumed && motionEnabled
            if (!meter.enabled) meter.reset()
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer); update()
        onDispose { owner.lifecycle.removeObserver(observer); meter.enabled = false; meter.reset() }
    }
    val primary = animateColorAsState(Color(palette.primary), tween(900), label = "dock primary colour")
    val secondary = animateColorAsState(Color(palette.secondary), tween(900), label = "dock secondary colour")
    val energy = remember { mutableFloatStateOf(0f) }
    val bass = remember { mutableFloatStateOf(0f) }
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active, resumed, playing, motionEnabled) {
        if (!active || !resumed || !motionEnabled) { energy.floatValue = 0f; bass.floatValue = 0f; return@LaunchedEffect }
        var previous = 0L
        while (playing || energy.floatValue > .001f || bass.floatValue > .001f) {
            withInfiniteAnimationFrameNanos { now ->
                val seconds = if (previous == 0L) 0f else ((now - previous) / 1e9f).coerceAtMost(.1f)
                previous = now
                val sample = meter.sample
                val fresh = playing && sample.timestampNanos != 0L && System.nanoTime() - sample.timestampNanos < 500_000_000L
                energy.floatValue = smoothDockEnergy(energy.floatValue, if (fresh) sample.energy else 0f, seconds)
                bass.floatValue = smoothDockEnergy(bass.floatValue, if (fresh) sample.bass else 0f, seconds)
                if (playing) phase.floatValue += seconds * (.28f + energy.floatValue * .25f)
            }
        }
        energy.floatValue = 0f
        bass.floatValue = 0f
    }
    Canvas(modifier.clipToBounds().testTag("navigation_glow")) {
        if (!active || !resumed) return@Canvas
        val amplitude = energy.floatValue
        val beat = bass.floatValue
        val audio = (amplitude * .72f + beat * .28f).coerceIn(0f, 1f)
        val travel = sin(phase.floatValue) * audio
        val swell = sin(phase.floatValue * 1.37f) * audio
        val restingRise = minOf(46.dp.toPx(), size.height * .55f)
        val rise = restingRise + (size.height * 1.12f - restingRise) * audio
        val strength = .68f + audio * .22f

        // Elliptical colour pools have their brightest point below the visible edge.
        // Several soft stops avoid a visible oval outline or a bright white bottom rim.
        fun pool(colour: Color, x: Float, width: Float, height: Float, opacity: Float) {
            val centre = Offset(size.width * x, size.height + height * .12f)
            scale(size.width * width, height, pivot = centre) {
                drawCircle(Brush.radialGradient(
                    0f to colour.copy(alpha = opacity),
                    .22f to colour.copy(alpha = opacity * .90f),
                    .46f to colour.copy(alpha = opacity * .56f),
                    .68f to colour.copy(alpha = opacity * .22f),
                    .86f to colour.copy(alpha = opacity * .045f),
                    1f to colour.copy(alpha = 0f),
                    center = centre, radius = 1f), radius = 1f, center = centre)
            }
        }
        // A low continuous base joins the pools; the darker valley above it remains free
        // to drift, instead of lifting the entire colour field as a single flat band.
        pool(androidx.compose.ui.graphics.lerp(primary.value, secondary.value, .5f),
            .5f, .86f, restingRise * .66f, strength * .26f)
        pool(primary.value, .18f + travel * .07f, .55f,
            rise * (1f + swell * .16f), strength * .70f)
        pool(secondary.value, .82f + travel * .06f, .55f,
            rise * (1f - swell * .16f), strength * .70f)
        pool(androidx.compose.ui.graphics.lerp(primary.value, secondary.value, .5f),
            .5f - travel * .12f, .40f, rise * (.48f + beat * .18f), strength * (.18f + beat * .10f))
    }
}
