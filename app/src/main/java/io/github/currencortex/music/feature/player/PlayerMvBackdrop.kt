package io.github.currencortex.music.feature.player

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.currencortex.music.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

internal data class PlayerMvSource(
    val urls: List<String>,
    val referer: String,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String = "Mozilla/5.0 (Linux; Android 13)",
    val mimeType: String? = null,
) {
    constructor(url: String, referer: String) : this(listOf(url), referer)
}

/** A muted looping MV layer. Audio remains owned by MusicService and the subtitle clock stays unchanged. */
@OptIn(UnstableApi::class)
@Composable
internal fun PlayerMvBackdrop(source: PlayerMvSource, playing: Boolean, active: Boolean,
    positionMs: Long, timelineRevision: Long, poster: String, onError: (String) -> Unit,
    modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val currentError by rememberUpdatedState(onError)
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
        }
    }
    val mediaSourceFactory = remember(source.referer, source.headers, source.userAgent) {
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(buildMap {
                put("Referer", source.referer)
                put("User-Agent", source.userAgent)
                putAll(source.headers)
            })
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
        DefaultMediaSourceFactory(http)
    }
    val player = remember(source.referer) {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
            }
    }
    val latest by rememberUpdatedState(MvClock(active, positionMs))
    var attempt by remember(source.urls) { mutableIntStateOf(0) }
    val latestAttempt by rememberUpdatedState(attempt)
    val currentUrl = source.urls.getOrNull(attempt) ?: source.urls.firstOrNull().orEmpty()

    DisposableEffect(player, source.urls) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val next = latestAttempt + 1
                android.util.Log.w("MvBackdrop", "candidate ${latestAttempt + 1}/${source.urls.size} failed: ${error.errorCodeName}", error)
                if (next < source.urls.size) attempt = next
                else currentError("MV 背景播放失败，已尝试全部线路")
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, currentUrl) {
        if (currentUrl.isBlank()) return@LaunchedEffect
        val item = MediaItem.Builder().setUri(currentUrl).apply {
            source.mimeType?.let(::setMimeType)
        }.build()
        player.setMediaItem(item)
        player.prepare()
        player.seekTo(positionMs.coerceAtLeast(0))
    }
    LaunchedEffect(player, playing, active, lifecycleState) {
        player.playWhenReady = active && playing && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    }
    LaunchedEffect(player, timelineRevision) {
        val target = positionMs.coerceAtLeast(0)
        if (abs(player.currentPosition - target) > 600) player.seekTo(target)
    }
    LaunchedEffect(player, currentUrl) {
        while (isActive) {
            val clock = latest
            if (clock.active && !player.isLoading) {
                val drift = abs(player.currentPosition - clock.positionMs)
                if (drift > 1_800) player.seekTo(clock.positionMs.coerceAtLeast(0))
            }
            delay(1_000)
        }
    }

    Box(modifier.background(Color.Black).testTag("player_mv_backdrop")) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF12211F), Color.Black)),
            ),
        )
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_mv_backdrop, null) as PlayerView).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Color.Black.copy(alpha = .28f),
            Color.Black.copy(alpha = .14f),
            Color.Black.copy(alpha = .62f),
            Color.Black.copy(alpha = .82f),
        ))))
    }
}

private data class MvClock(val active: Boolean, val positionMs: Long)
