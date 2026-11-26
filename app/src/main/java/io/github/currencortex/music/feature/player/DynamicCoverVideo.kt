package io.github.currencortex.music.feature.player

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import io.github.currencortex.music.R
import okhttp3.OkHttpClient

/** Decorative album motion texture must never intercept pager or sheet gestures. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DynamicCoverPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : PlayerView(context, attrs) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
}

/**
 * Silent, looping video cover overlay rendered only when [active] and [videoUrl] has decoded its
 * first frame, leaving the static cover underneath visible as fallback at all other times.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun DynamicCoverVideo(
    videoUrl: String?,
    active: Boolean,
    httpClient: OkHttpClient,
    modifier: Modifier = Modifier,
    visible: Boolean = active,
    instantHide: Boolean = false,
    onPlaybackError: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var failedUrl by remember(videoUrl) { mutableStateOf<String?>(null) }
    val effectiveUrl = videoUrl?.takeIf { it != failedUrl }
    val playable = active && effectiveUrl != null && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    var rendered by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(effectiveUrl) {
        if (effectiveUrl == null) {
            rendered = false
            currentUrl = null
            player?.stop()
            player?.clearMediaItems()
        } else if (currentUrl != effectiveUrl) {
            rendered = false
        }
    }

    LaunchedEffect(playable, effectiveUrl) {
        val url = if (playable) effectiveUrl else null
        if (url != null) {
            val instance = player ?: run {
                val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
                    .setUserAgent(VIDEO_USER_AGENT)
                ExoPlayer.Builder(context)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                    .build()
                    .apply {
                        volume = 0f
                        setAudioAttributes(AudioAttributes.DEFAULT, false)
                        trackSelectionParameters = trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                            .build()
                        repeatMode = Player.REPEAT_MODE_ONE
                        videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                        addListener(object : Player.Listener {
                            override fun onRenderedFirstFrame() {
                                rendered = true
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                val failing = currentUrl ?: return
                                failedUrl = failing
                                rendered = false
                                onPlaybackError(failing)
                            }
                        })
                    }
                    .also { player = it }
            }
            if (currentUrl != url) {
                currentUrl = url
                rendered = false
                instance.setMediaItem(MediaItem.fromUri(url))
                instance.prepare()
            }
            instance.playWhenReady = true
        } else {
            player?.pause()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    val shown = playable && visible && rendered
    val animatedAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = if (shown) 280 else 160),
        label = "dynamicCoverVideoAlpha",
    )
    val visibleAlpha = if (instantHide && !shown) 0f else animatedAlpha

    if (effectiveUrl == null && visibleAlpha <= 0.001f) return

    Box(
        modifier
            .semantics { hideFromAccessibility() }
            .testTag("player_dynamic_cover_video")
            .graphicsLayer { alpha = visibleAlpha },
    ) {
        if (player != null && (playable || visibleAlpha > 0.001f)) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.dynamic_cover_video, null, false) as PlayerView).apply {
                        this.player = player
                    }
                },
                update = { view ->
                    if (view.player !== player) view.player = player
                },
                onRelease = { view -> view.player = null },
            )
        }
    }
}

private const val VIDEO_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
