package io.github.currencortex.music.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.MusicCover

internal const val COVER_ARC_DURATION = 520

// Refreshing the image or metadata of the same song must not launch another arc.
internal fun Song.coverMotionKey() = listOf(musicSource, id, privateIdentity, emby?.id,
    navidrome?.id, localUri)

internal data class PlayerCoverMotion(val song: Song?, val active: Boolean)
internal val LocalPlayerCoverMotion = staticCompositionLocalOf<PlayerCoverMotion?> { null }

@Composable internal fun rememberPlayerCoverMotion(song: Song?, enabled: Boolean): PlayerCoverMotion {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return PlayerCoverMotion(song, enabled && lifecycle.isAtLeast(Lifecycle.State.RESUMED))
}

internal data class PlayerCoverTossFrame(val lift: Float, val rotation: Float, val alpha: Float)

/** Incoming runs 0..1 and outgoing 1..2. The faces exchange only when edge-on at the apex. */
internal fun playerCoverTossFrame(phase: Float): PlayerCoverTossFrame {
    val value = phase.coerceIn(0f, 2f)
    val outgoing = value > 1f
    val p = if (outgoing) value - 1f else value
    return PlayerCoverTossFrame(
        lift = 4f * p * (1f - p),
        rotation = if (outgoing) (180f * p).coerceAtMost(90f) else (180f * p - 180f).coerceAtLeast(-90f),
        alpha = if (value in .5f..1.5f) 1f else 0f)
}

/** The shared positioning stays fixed; each cover carries its own shadow, crop and record rotation. */
@Composable internal fun PlayerSongCover(url: String, modifier: Modifier, artworkModifier: Modifier,
    motion: PlayerCoverMotion?, pixels: Int = 800, cornerRadius: Dp = 0.dp) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val lift = with(density) { minOf(48.dp, maxHeight * .16f).toPx() }
        if (motion?.active == true && motion.song != null) {
            // One animated phase drives height, rotation and face visibility together.
            // Leaving this branch immediately cancels motion when the player hides or changes page.
            AnimatedContent(motion.song, Modifier.fillMaxSize(), contentKey = { it.coverMotionKey() },
                label = "player cover toss", transitionSpec = {
                    (EnterTransition.None togetherWith ExitTransition.None) using SizeTransform(clip = false)
                }) { song ->
                val phase = transition.animateFloat(
                    transitionSpec = { tween(COVER_ARC_DURATION, easing = LinearEasing) },
                    label = "player cover toss phase") { state ->
                    when (state) {
                        EnterExitState.PreEnter -> 0f
                        EnterExitState.Visible -> 1f
                        EnterExitState.PostExit -> 2f
                    }
                }
                MusicCover(song.cover, Modifier.fillMaxSize().graphicsLayer {
                    val frame = playerCoverTossFrame(phase.value)
                    translationY = -lift * frame.lift
                    rotationY = frame.rotation
                    alpha = frame.alpha
                    cameraDistance = 12f * size.maxDimension
                }.then(artworkModifier), pixels, cornerRadius)
            }
        } else MusicCover(url, Modifier.fillMaxSize().then(artworkModifier), pixels, cornerRadius)
    }
}
