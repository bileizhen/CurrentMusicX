package io.github.currencortex.music.feature.player

import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.*
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.core.media.FluidBackgroundMotion
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported

@Composable internal fun rememberPlayerFluidSpeed(meter: DockAudioLevel, playing: Boolean, active: Boolean,
    song: Song?, playbackRate: Float = 1f): State<Float> {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val motionEnabled = (rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    val enabled = active && motionEnabled && lifecycle.isAtLeast(Lifecycle.State.RESUMED) &&
        Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated && isRuntimeShaderSupported()
    val speed = remember { mutableFloatStateOf(.35f) }
    val rate by rememberUpdatedState(playbackRate)
    val isPlaying by rememberUpdatedState(playing)
    DisposableEffect(meter, enabled, playing) {
        val subscription = if (enabled && playing) meter.acquire() else null
        onDispose { subscription?.close() }
    }
    LaunchedEffect(meter, enabled, song?.id, song?.musicSource, song?.privateIdentity, song?.localUri) {
        speed.floatValue = .35f
        if (!enabled) return@LaunchedEffect
        val motion = FluidBackgroundMotion()
        var started = System.nanoTime()
        var wasPlaying = isPlaying
        var previous = 0L
        while (isActive) {
            withInfiniteAnimationFrameNanos { frame ->
                val seconds = if (previous == 0L) 0f else (frame - previous) / 1e9f
                previous = frame
                val now = System.nanoTime()
                if (wasPlaying != isPlaying) { started = now; wasPlaying = isPlaying }
                val sample = meter.sample.takeIf {
                    isPlaying && it.timestampNanos >= started && now - it.timestampNanos in 0L..500_000_000L
                }
                // This state is read by the shader's frame loop, never by composition.
                speed.floatValue = motion.update(sample, seconds, rate, isPlaying)
            }
        }
    }
    return speed
}
