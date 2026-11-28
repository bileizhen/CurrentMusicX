package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.currencortex.music.core.media.DockAudioLevel
import io.github.currencortex.music.core.media.smoothDockEnergy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * One current-line glow envelope. The playback thread publishes numbers; the UI never owns PCM.
 * The subscription is held only while the screen is resumed, so backgrounding the app stops both
 * the sampler and the PCM metering it keeps alive.
 */
@Composable internal fun rememberLyricsRhythmGlow(
    meter: DockAudioLevel?,
    active: Boolean,
    playing: Boolean,
): State<Float>? {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    if (!active || !resumed) return null
    val glow = remember { mutableFloatStateOf(0f) }
    DisposableEffect(meter) {
        val subscription = meter?.acquire()
        onDispose { subscription?.close(); glow.floatValue = 0f }
    }
    LaunchedEffect(meter, playing) {
        var value = glow.floatValue
        while (isActive && meter != null) {
            val sample = meter.sample
            val target = if (playing)
                (sample.pulse * .62f + sample.bass * .28f + sample.energy * .10f).coerceIn(0f, 1f)
            else 0f
            value = smoothDockEnergy(value, target, .048f)
            glow.floatValue = value
            delay(48)
        }
    }
    return glow
}
