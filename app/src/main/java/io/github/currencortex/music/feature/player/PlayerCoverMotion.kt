package io.github.currencortex.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.MusicCover
import kotlin.math.roundToInt

private const val COVER_ARC_DURATION = 520

// Refreshing the image or metadata of the same song must not launch another arc.
internal fun Song.coverMotionKey() = listOf(musicSource, id, privateIdentity, emby?.id,
    navidrome?.id, localUri)

internal data class PlayerCoverMotion(val song: Song?, val active: Boolean)
internal val LocalPlayerCoverMotion = staticCompositionLocalOf<PlayerCoverMotion?> { null }

@Composable internal fun rememberPlayerCoverMotion(song: Song?, enabled: Boolean): PlayerCoverMotion {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return PlayerCoverMotion(song, enabled && lifecycle.isAtLeast(Lifecycle.State.RESUMED))
}

/** Constant horizontal travel with a parabolic rise and fall; both endpoints are at rest height. */
internal fun playerCoverArcOffset(progress: Float, outgoing: Boolean, travel: Int, lift: Int): IntOffset {
    val p = progress.coerceIn(0f, 1f)
    return IntOffset((travel * if (outgoing) -p else 1f - p).roundToInt(),
        (-4f * lift * p * (1f - p)).roundToInt())
}

internal fun playerCoverArcFrames(outgoing: Boolean, travel: Int, lift: Int) = keyframes<IntOffset> {
    durationMillis = COVER_ARC_DURATION
    // Short linear segments follow the parabola without imposing a second easing curve.
    for (step in 0..32) {
        playerCoverArcOffset(step / 32f, outgoing, travel, lift) at COVER_ARC_DURATION * step / 32
    }
}

/** The shared positioning stays fixed; each cover carries its own shadow, crop and record rotation. */
@Composable internal fun PlayerSongCover(url: String, modifier: Modifier, artworkModifier: Modifier,
    motion: PlayerCoverMotion?, pixels: Int = 800, cornerRadius: Dp = 0.dp) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val travel = with(density) { minOf(64.dp, maxWidth * .22f).roundToPx() }
        val lift = with(density) { minOf(36.dp, maxHeight * .12f).roundToPx() }
        if (motion?.active == true && motion.song != null) {
            // AnimatedContent retains outgoing faces and retargets the slide, fade and turn together.
            // Leaving this branch immediately cancels motion when the player hides or changes page.
            AnimatedContent(motion.song, Modifier.fillMaxSize(), contentKey = { it.coverMotionKey() },
                label = "player cover arc", transitionSpec = {
                    (slideIn(playerCoverArcFrames(false, travel, lift)) { IntOffset(travel, 0) } +
                        fadeIn(tween(320, delayMillis = 100))) togetherWith
                        (slideOut(playerCoverArcFrames(true, travel, lift)) { IntOffset(-travel, 0) } +
                            fadeOut(tween(320, delayMillis = 100))) using SizeTransform(clip = false)
                }) { song ->
                val turn = transition.animateFloat(
                    transitionSpec = { tween(COVER_ARC_DURATION, easing = LinearEasing) },
                    label = "player cover turn") { phase ->
                    when (phase) {
                        EnterExitState.PreEnter -> -90f
                        EnterExitState.Visible -> 0f
                        EnterExitState.PostExit -> 90f
                    }
                }
                MusicCover(song.cover, Modifier.fillMaxSize().graphicsLayer {
                    // Turn about the screen's vertical axis, outside the existing record rotation.
                    // Keep interrupted turns front-facing even if a spring briefly overshoots.
                    rotationY = turn.value.coerceIn(-90f, 90f)
                    cameraDistance = 12f * size.maxDimension
                }.then(artworkModifier), pixels, cornerRadius)
            }
        } else MusicCover(url, Modifier.fillMaxSize().then(artworkModifier), pixels, cornerRadius)
    }
}
