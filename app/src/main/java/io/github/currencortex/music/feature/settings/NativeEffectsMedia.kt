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
import io.github.currencortex.music.data.effects.NeteaseEffectsAssetRepository
import io.github.currencortex.music.ui.component.LocalPageContainerSettled
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Decorative video must yield the entire pointer stream to the Compose pager. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EffectsCardPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : PlayerView(context, attrs) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
}

/** Optional vendor artwork is resolved from the verified downloaded package. */
internal object NativeEffectsAssets {
    private const val LOCAL_ROOT = "file:///netease-effects/"
    private var urls: Map<String, String>? = null

    fun managed(source: String) = source.startsWith(LOCAL_ROOT)
    fun local(name: String) = "${LOCAL_ROOT}assets/$name"

    @Synchronized fun image(context: Context, remote: String): String? {
        if (managed(remote)) return resolveLocal(context, remote)
        val manifest = File(NeteaseEffectsAssetRepository.directory(context), "resource-manifest.json")
        if (!manifest.isFile) return remote
        if (urls == null) urls = try { manifest.bufferedReader().use { input ->
            Json.parseToJsonElement(input.readText()).jsonArray.associate { entry ->
                val row = entry.jsonObject
                row.getValue("source").jsonPrimitive.content.substringBefore('?').replace("http:", "https:") to
                    row.getValue("path").jsonPrimitive.content
            }
        } } catch (_: IOException) { return remote }
        val path = urls!![remote.substringBefore('?').replace("http:", "https:")] ?: return remote
        return resolveLocal(context, "$LOCAL_ROOT$path") ?: remote
    }

    private fun resolveLocal(context: Context, source: String): String? {
        val relative = source.removePrefix(LOCAL_ROOT)
        if (relative.startsWith("/") || relative.contains("..")) return null
        val file = File(NeteaseEffectsAssetRepository.directory(context), relative)
        return file.takeIf { it.isFile }?.toURI()?.toString()
    }
}

@Composable internal fun EffectsArtwork(source: String, modifier: Modifier = Modifier, description: String? = null,
    scale: ContentScale = ContentScale.Crop, animated: Boolean = false) {
    val context = LocalContext.current
    val resolved by produceState<Pair<String, String?>?>(null, context, source) {
        if (!source.startsWith("file:") || NativeEffectsAssets.managed(source)) {
            value = source to withContext(Dispatchers.IO) { NativeEffectsAssets.image(context, source) }
        }
    }
    val imageUri = if (source.startsWith("file:") && !NativeEffectsAssets.managed(source)) source
        else resolved?.takeIf { it.first == source }?.second
    val request = remember(context, imageUri, animated) {
        ImageRequest.Builder(context).data(imageUri)
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
    val videoSource = if (video.contains("://")) video else NativeEffectsAssets.local(video)
    val resolvedVideo by produceState<Pair<String, String?>?>(null, context, videoSource, active) {
        if (active) value = videoSource to withContext(Dispatchers.IO) { NativeEffectsAssets.image(context, videoSource) }
    }
    val videoUri = resolvedVideo?.takeIf { it.first == videoSource }?.second
    val posterUri = if (poster.contains("://")) poster else NativeEffectsAssets.local(poster)
    var rendered by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val visible = active && LocalPageContainerSettled.current && state.isAtLeast(Lifecycle.State.RESUMED)
    // A use/cancel click changes the media item, not the renderer session.
    // Release can synchronously wait on the playback thread; keep it off composition.
    LaunchedEffect(visible, videoUri) {
        if (visible && videoUri != null) {
            val current = player ?: ExoPlayer.Builder(context).build().also { player = it }.apply {
                volume = 0f
                setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, false)
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener { override fun onRenderedFirstFrame() { rendered = true } })
            }
            if (current.currentMediaItem?.localConfiguration?.uri?.toString() != videoUri) {
                rendered = false
                current.setMediaItem(MediaItem.fromUri(videoUri))
                current.prepare()
            }
            current.playWhenReady = true
        } else player?.pause()
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    Box(modifier) {
        if (visible && player != null) {
            AndroidView(modifier = Modifier.matchParentSize(), factory = {
                (LayoutInflater.from(it).inflate(R.layout.effects_card_video, null, false) as PlayerView).apply {
                    this.player = player
                }
            }, update = { it.player = player }, onRelease = { it.player = null })
        }
        if (!visible || !rendered) EffectsArtwork(posterUri, Modifier.matchParentSize())
    }
}
