package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.SongLyricsStore
import io.github.currencortex.music.ui.component.coverRequestUrl
import io.github.currencortex.music.ui.component.ListeningPairArtwork
import io.github.currencortex.music.core.media.ListenTogetherPair

internal data class DesktopIslandCoverPlacement(val height: Float, val diameter: Float, val center: Offset)

/** Layout and drawing share one uninterrupted clock, including a retarget mid-flight. */
internal class DesktopIslandCoverTransition(val from: Song?, var target: Song?, animate: Boolean,
    val start: DesktopIslandCoverPlacement?, private val startAngle: Float, val startSpin: Float,
    val startDrawingHeight: Int = 0) {
    val progress = Animatable(if (animate) 0f else 1f)
    var placement: DesktopIslandCoverPlacement? = start
    var translation by mutableStateOf(Offset.Zero)
    var spin = startSpin
    var drawingHeight = startDrawingHeight
    private val edge = if (startAngle < 0f) -90f else 90f
    val angle: Float get() {
        val p = progress.value
        return if (p < .5f) startAngle + (edge - startAngle) * p * 2f else -edge * (1f - p) * 2f
    }
    fun visibleSong() = if (progress.value < .5f) from else target
}
private class CoverTransitionHistory { var current: DesktopIslandCoverTransition? = null }

@Composable internal fun rememberIslandCoverTransition(song: Song?, motion: Boolean): DesktopIslandCoverTransition {
    val history = remember { CoverTransitionHistory() }
    val identity = remember(song) { song?.let(SongLyricsStore::identity) }
    val transition = remember(identity, motion) {
        val old = history.current
        val change = old?.target != null && song != null &&
            old.target?.let(SongLyricsStore::identity) != identity
        DesktopIslandCoverTransition(old?.visibleSong(), song, motion && change,
            if (motion && change) old?.placement else null, old?.angle ?: 0f, old?.spin ?: 0f,
            if (motion && change) old?.drawingHeight ?: 0 else 0)
            .also { history.current = it }
    }
    SideEffect { transition.target = song }
    LaunchedEffect(transition) {
        if (transition.progress.value < 1f) transition.progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    return transition
}

/** The outer layer flies and flips around Y; only the circular inner image spins around Z. */
@Composable internal fun DesktopIslandCover(song: Song?, coverPosition: State<Long>?, motion: Boolean,
    transition: DesktopIslandCoverTransition? = null, modifier: Modifier = Modifier,
    listenTogether: ListenTogetherPair? = null) {
    if (listenTogether != null) {
        ListeningPairArtwork(listenTogether, modifier.testTag("desktop_island_pair_artwork"))
        return
    }
    val travel = transition ?: rememberIslandCoverTransition(song, motion)
    val outgoing by remember(travel) { derivedStateOf { travel.progress.value < .5f } }
    Box(modifier.testTag("desktop_island_cover"), contentAlignment = Alignment.Center) {
        // Start loading the new image while the old circular face is still visible.
        if (outgoing) CoverFace(song, Modifier.fillMaxSize().clearAndSetSemantics {}.graphicsLayer { alpha = 0f }, { 0f })
        val face = if (outgoing) travel.from else song
        CoverFace(face, Modifier.fillMaxSize().graphicsLayer {
            val p = travel.progress.value
            val arc = 4f * p * (1f - p) * 10.dp.toPx()
            val offset = if (transition != null) travel.translation else Offset(arc * .5f, -arc)
            translationX = offset.x; translationY = offset.y
            rotationY = travel.angle
            cameraDistance = size.width * 8f
        }, {
            val spin = if (outgoing) travel.startSpin else if (motion && !face?.cover.isNullOrBlank())
                ((coverPosition?.value ?: 0L) % 20000L) / 20000f * 360f else 0f
            travel.spin = spin
            spin
        })
    }
}

@Composable private fun CoverFace(song: Song?, modifier: Modifier, spin: () -> Float) {
    val context = LocalContext.current
    val icon = remember(context) { context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap(256, 256).asImageBitmap() }
    val cover = song?.cover.orEmpty()
    Box(modifier.clip(CircleShape).background(Color(0xFF2B303B)).graphicsLayer {
        rotationZ = spin()
    }, contentAlignment = Alignment.Center) {
        Image(icon, if (song == null) "应用图标" else "占位封面", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (cover.isNotBlank()) AsyncImage(
            remember(context, cover) { ImageRequest.Builder(context).data(coverRequestUrl(cover, 256)).size(256).build() },
            contentDescription = "歌曲封面", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
