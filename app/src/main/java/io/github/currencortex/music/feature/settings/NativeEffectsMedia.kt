package io.github.currencortex.music.feature.settings

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.request.ImageRequest
import io.github.currencortex.music.R
import kotlinx.serialization.json.*

/** Decorative video must yield the entire pointer stream to the Compose pager. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EffectsCardPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : PlayerView(context, attrs) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
}

/** Local vendor artwork remains usable offline; no HTML or JavaScript is involved. */
internal object NativeEffectsAssets {
    private var urls: Map<String, String>? = null
    @Synchronized fun image(context: Context, remote: String): String {
        if (urls == null) urls = context.assets.open("netease-effects-ui/resource-manifest.json").bufferedReader().use { input ->
            Json.parseToJsonElement(input.readText()).jsonArray.associate { entry ->
                val row = entry.jsonObject
                row.getValue("source").jsonPrimitive.content.substringBefore('?').replace("http:", "https:") to
                    "file:///android_asset/netease-effects-ui/${row.getValue("path").jsonPrimitive.content}"
            }
        }
        return urls!![remote.substringBefore('?').replace("http:", "https:")] ?: remote
    }
    fun local(name: String) = "file:///android_asset/netease-effects-ui/assets/$name"
}

@Composable internal fun EffectsArtwork(source: String, modifier: Modifier = Modifier, description: String? = null,
    scale: ContentScale = ContentScale.Crop, animated: Boolean = false) {
    val context = LocalContext.current
    val request = remember(context, source, animated) {
        ImageRequest.Builder(context).data(if (source.startsWith("file:")) source else NativeEffectsAssets.image(context, source))
            .apply { if (animated && Build.VERSION.SDK_INT >= 28) decoderFactory(AnimatedImageDecoder.Factory()) }.build()
    }
    AsyncImage(request, description, modifier, contentScale = scale)
}

/** A silent native texture decoder, scoped to the visible card and resumed Activity. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun EffectsVideo(video: String, poster: String, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val videoUri = if (video.contains("://")) NativeEffectsAssets.image(context, video) else NativeEffectsAssets.local(video)
    val posterUri = if (poster.contains("://")) NativeEffectsAssets.image(context, poster) else NativeEffectsAssets.local(poster)
    var rendered by remember(video) { mutableStateOf(false) }
    Box(modifier) {
        if (active && state.isAtLeast(Lifecycle.State.RESUMED)) {
            val player = remember(video) {
                ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false)
                    trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                    repeatMode = Player.REPEAT_MODE_ONE
                    addListener(object : Player.Listener { override fun onRenderedFirstFrame() { rendered = true } })
                    setMediaItem(MediaItem.fromUri(videoUri))
                    prepare(); playWhenReady = true
                }
            }
            DisposableEffect(player) { onDispose { player.release(); rendered = false } }
            AndroidView(modifier = Modifier.matchParentSize(), factory = {
                (LayoutInflater.from(it).inflate(R.layout.effects_card_video, null, false) as PlayerView).apply {
                    this.player = player
                }
            }, update = { it.player = player }, onRelease = { it.player = null })
        }
        if (!active || !rendered || !state.isAtLeast(Lifecycle.State.RESUMED))
            EffectsArtwork(posterUri, Modifier.matchParentSize())
    }
}
