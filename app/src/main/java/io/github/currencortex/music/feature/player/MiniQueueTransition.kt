package io.github.currencortex.music.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.SongLyricsStore
import io.github.currencortex.music.ui.component.MusicCover
import top.yukonga.miuix.kmp.basic.Text

internal val LocalMiniQueueTransition = staticCompositionLocalOf<MiniQueueTransition?> { null }
internal val MiniQueueArtworkGeometry = SemanticsPropertyKey<PlayerSheetTextFrame>("MiniQueueArtworkGeometry")

/** Captured before opening; destination bounds exclude the queue's translating layer. */
internal class MiniQueueTransition {
    var motion by mutableStateOf<QueuePageMotion?>(null)
    var viewport by mutableStateOf<Rect?>(null)
    var page by mutableStateOf<LayoutCoordinates?>(null)
    var cover by mutableStateOf<LayoutCoordinates?>(null)
    var headerKey by mutableStateOf<String?>(null)
    private var sourceKey by mutableStateOf<String?>(null)
    var origin by mutableStateOf<PlayerArtworkOrigin?>(null)
        private set
    var url by mutableStateOf("")
        private set
    private var textOrigins by mutableStateOf<Map<Boolean, Rect>>(emptyMap())
    val sourceLayouts = mutableStateMapOf<Boolean, androidx.compose.ui.text.TextLayoutResult>()
    val targets = mutableStateMapOf<Boolean, LayoutCoordinates>()
    val snapshots = mutableStateMapOf<Boolean, PlayerSharedTextSnapshot>()
    val progress get() = motion?.progress?.coerceIn(0f, 1f) ?: 1f
    private val active get() = sourceKey != null && sourceKey == headerKey && progress < 1f

    fun capture(song: Song?, artwork: PlayerArtworkOrigin?, text: PlayerSheetTextTransition) {
        text.captureOrigin()
        sourceKey = song?.let(SongLyricsStore::identity)
        origin = artwork
        url = song?.cover.orEmpty()
        textOrigins = text.origins.toMap()
        sourceLayouts.clear(); sourceLayouts.putAll(text.sourceLayouts)
    }
    fun clear() {
        motion = null; page = null; cover = null; headerKey = null; sourceKey = null
        targets.clear(); snapshots.clear(); sourceLayouts.clear(); textOrigins = emptyMap(); origin = null
    }
    private fun destination(node: LayoutCoordinates?): Rect? {
        val root = page?.takeIf { it.isAttached } ?: return null
        val child = node?.takeIf { it.isAttached } ?: return null
        if (root.findRootCoordinates() !== child.findRootCoordinates()) return null
        return Rect(root.localPositionOf(child, Offset.Zero), Size(child.size.width.toFloat(), child.size.height.toFloat()))
    }
    val coverMoving get() = active && origin != null && viewport != null && destination(cover) != null
    fun textMoving(subtitle: Boolean) = active && viewport != null && textOrigins[subtitle] != null &&
        sourceLayouts[subtitle] != null && snapshots[subtitle]?.ready == true && destination(targets[subtitle]) != null
    fun coverFrame(): PlayerSheetTextFrame? {
        if (!coverMoving) return null
        val start = origin!!.bounds.translate(-viewport!!.topLeft)
        val end = destination(cover) ?: return null
        return PlayerSheetTextFrame(progress, playerSheetElementArc(start, end, progress), start, end)
    }
    fun textFrame(subtitle: Boolean): PlayerSheetTextFrame? {
        if (!textMoving(subtitle)) return null
        val start = textOrigins[subtitle]!!.translate(-viewport!!.topLeft)
        val end = destination(targets[subtitle]) ?: return null
        return PlayerSheetTextFrame(progress, playerSheetElementArc(start, end, progress, anchorTopLeft = true), start, end)
    }
}

@Composable internal fun MiniQueueHeaderText(transfer: MiniQueueTransition, text: String,
    modifier: Modifier, subtitle: Boolean = false) {
    val snapshot = remember(transfer, subtitle) { PlayerSharedTextSnapshot(text) }
    SideEffect { snapshot.text = text; transfer.snapshots[subtitle] = snapshot }
    Text(text, modifier.onGloballyPositioned { transfer.targets[subtitle] = it }
        .graphicsLayer { alpha = if (transfer.textMoving(subtitle)) 0f else 1f },
        color = Color.White.copy(alpha = if (subtitle) .5f else 1f), fontSize = if (subtitle) 13.sp else 20.sp,
        maxLines = 1, overflow = TextOverflow.Ellipsis, onTextLayout = { snapshot.layout = it })
}

/** Uses the sheet's existing arc and cached-glyph renderer, including finger-controlled reversal. */
@Composable internal fun MiniQueueSharedElements(transfer: MiniQueueTransition) {
    val density = LocalDensity.current
    val artwork by remember(transfer) { derivedStateOf { transfer.coverFrame() } }
    artwork?.let { frame ->
        val width = with(density) { frame.end.width.toDp() }
        val radius = frame.start.width / 2 * (1 - frame.progress * frame.progress) +
            with(density) { 8.dp.toPx() } * frame.progress * frame.progress
        MusicCover(transfer.url, Modifier.requiredSize(width).testTag("mini_queue_artwork_flight")
            .semantics { this[MiniQueueArtworkGeometry] = frame; hideFromAccessibility() }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = frame.bounds.left; translationY = frame.bounds.top
                scaleX = frame.bounds.width / frame.end.width; scaleY = scaleX
            }.graphicsLayer {
                rotationZ = (((transfer.origin?.rotation ?: 0f) + 180f) % 360f - 180f) * (1 - frame.progress)
                shape = RoundedCornerShape(with(density) { (radius / (frame.bounds.width / frame.end.width)).toDp() }); clip = true
            }.drawWithContent {
                drawContent()
                val scale = frame.bounds.width / frame.end.width
                val stroke = 2.dp.toPx() * (1 - frame.progress) / scale
                if (stroke > 0f) drawRoundRect(transfer.origin!!.borderColor, topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke), cornerRadius = CornerRadius(radius / scale), style = Stroke(stroke))
            }, cornerRadius = 0.dp)
    }
    for (subtitle in listOf(false, true)) {
        val paint = remember { Paint() }
        val frame by remember(transfer, subtitle) { derivedStateOf { transfer.textFrame(subtitle) } }
        val metrics by remember(transfer, subtitle) { derivedStateOf {
            playerTextDrawMetrics(listOf(transfer.sourceLayouts[subtitle], transfer.snapshots[subtitle]?.layout))
        } }
        if (frame == null) continue
        Canvas(Modifier.fillMaxSize().testTag(if (subtitle) "mini_queue_subtitle_flight" else "mini_queue_title_flight")
            .semantics { hideFromAccessibility() }.semantics {
                frame?.let { this[PlayerSheetTextGeometry] = it }
                this[PlayerSharedTextLayouts] = metrics.layouts.filterNotNull()
            }) {
            val current = frame ?: return@Canvas
            val p = current.progress * current.progress
            val startColor = metrics.layouts.firstOrNull()?.layoutInput?.style?.color ?: Color.White
            val endColor = Color.White.copy(alpha = if (subtitle) .5f else 1f)
            drawPlayerSharedText(current.bounds, metrics, listOf(1 - p, p), paint,
                color = lerp(startColor, endColor, playerSheetSurfaceAlpha(current.progress)))
        }
    }
}
