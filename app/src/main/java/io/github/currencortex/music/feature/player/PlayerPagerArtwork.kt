package io.github.currencortex.music.feature.player

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.component.MusicCover

internal enum class PlayerPagerArtworkRole { COVER, LYRICS }
internal val PlayerPagerArtworkGeometry = SemanticsPropertyKey<PlayerArtworkFrame>("PlayerPagerArtworkGeometry")
internal class PlayerPagerArtworkTransition(val progress: () -> Float) {
    var container by mutableStateOf<LayoutCoordinates?>(null)
    var coverPage by mutableStateOf<LayoutCoordinates?>(null)
    var lyricsPage by mutableStateOf<LayoutCoordinates?>(null)
    var cover by mutableStateOf<LayoutCoordinates?>(null)
    var lyrics by mutableStateOf<LayoutCoordinates?>(null)
    val ready get() = container?.isAttached == true && coverPage?.isAttached == true &&
        lyricsPage?.isAttached == true && cover?.isAttached == true && lyrics?.isAttached == true

    fun frame(bend: Float, rotation: Float): PlayerArtworkFrame? {
        if (!ready) return null
        val large = cover!!; val small = lyrics!!
        // Measure inside each page: neither endpoint includes the pager's scrolling offset.
        val start = Rect(coverPage!!.localPositionOf(large, Offset.Zero), Size(large.size.width.toFloat(), large.size.height.toFloat()))
        val end = Rect(lyricsPage!!.localPositionOf(small, Offset.Zero), Size(small.size.width.toFloat(), small.size.height.toFloat()))
        val p = progress().coerceIn(0f, 1f)
        val arc = 4 * p * (1 - p) * bend
        val center = start.center * (1 - p) + end.center * p + Offset(arc * .5f, -arc)
        val diameter = start.width * (1 - p) + end.width * p
        return PlayerArtworkFrame(p, Rect(center - Offset(diameter / 2, diameter / 2), Size(diameter, diameter)), rotation)
    }
}
internal val LocalPlayerPagerArtwork = staticCompositionLocalOf<PlayerPagerArtworkTransition?> { null }

/** One image bridges the two preloaded pages, and supplies the sheet's current destination. */
@Composable internal fun PlayerPagerArtwork(transfer: PlayerPagerArtworkTransition, url: String) {
    val sheet = LocalPlayerArtworkTransition.current
    val rotation = LocalPlayerArtworkRotation.current
    val density = LocalDensity.current
    val bend = with(density) { 20.dp.toPx() }
    val geometry by remember(transfer, bend) { derivedStateOf { transfer.frame(bend, 0f) } }
    val destination = remember(transfer, sheet, bend) {
        {
            val root = sheet?.sheet?.takeIf { it.isAttached }
            val pane = transfer.container?.takeIf { it.isAttached }
            if (root == null || pane == null) null
            else transfer.frame(bend, 0f)?.bounds?.translate(root.localPositionOf(pane, Offset.Zero))
        }
    }
    SideEffect {
        if (transfer.ready) { sheet?.target = transfer.container; sheet?.destination = destination }
    }
    DisposableEffect(transfer, sheet) { onDispose {
        if (sheet?.destination === destination) { sheet?.destination = null; sheet?.target = null }
    } }
    val frame = geometry ?: return
    val width = transfer.cover!!.size.width
    if (width <= 0) return
    MusicCover(url, Modifier.requiredSize(with(density) { width.toDp() }).testTag("player_pager_artwork")
        .semantics {
            this[PlayerPagerArtworkGeometry] = frame.copy(rotation = rotation?.value ?: 0f)
            this[CoverRotation] = rotation?.value ?: 0f
        }
        .graphicsLayer {
            alpha = if (sheet?.moving == true) 0f else 1f
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = geometry?.bounds?.left ?: 0f
            translationY = geometry?.bounds?.top ?: 0f
            scaleX = (geometry?.bounds?.width ?: 0f) / width; scaleY = scaleX
        }.graphicsLayer { rotationZ = rotation?.value ?: 0f }.clip(CircleShape), pixels = 800)
}
