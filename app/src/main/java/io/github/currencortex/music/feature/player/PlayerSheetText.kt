package io.github.currencortex.music.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text

internal data class PlayerSheetTextFrame(val progress: Float, val bounds: Rect, val start: Rect, val end: Rect)
internal val PlayerSheetTextGeometry = SemanticsPropertyKey<PlayerSheetTextFrame>("PlayerSheetTextGeometry")
internal val PlayerMiniTextVisible = SemanticsPropertyKey<Boolean>("PlayerMiniTextVisible")

/** Freeze the mini's position before the dock folds; all reverse/held gestures reuse it. */
internal class PlayerSheetTextTransition(val progress: () -> Float) {
    val mini = mutableStateMapOf<Boolean, LayoutCoordinates>()
    val snapshots = mutableStateMapOf<Boolean, PlayerSharedTextSnapshot>()
    var sourceLayouts by mutableStateOf<Map<Boolean, TextLayoutResult>>(emptyMap())
        private set
    var origins by mutableStateOf<Map<Boolean, Rect>>(emptyMap())
        private set
    var target by mutableStateOf<PlayerPagerArtworkTransition?>(null)
    var sheet by mutableStateOf<LayoutCoordinates?>(null)

    fun captureOrigin() {
        captureLayouts()
        origins = mini.mapNotNull { (subtitle, coordinates) ->
            if (!coordinates.isAttached) null else subtitle to Rect(coordinates.localToRoot(Offset.Zero),
                coordinates.localToRoot(Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())))
        }.toMap()
    }
    fun captureLayouts() {
        sourceLayouts = snapshots.mapNotNull { (subtitle, snapshot) ->
            snapshot.layout?.takeIf { snapshot.ready }?.let { subtitle to it }
        }.toMap()
    }
    fun moving(subtitle: Boolean): Boolean {
        val pager = target ?: return false
        val role = if (pager.queueReady && pager.queueProgress() > .5f) PlayerPagerArtworkRole.QUEUE
            else if (pager.progress() > .5f) PlayerPagerArtworkRole.LYRICS else PlayerPagerArtworkRole.COVER
        val destination = (if (subtitle) pager.subtitleLayouts else pager.titleLayouts)[role]
        // A live lyric and an artist label are different elements; do not blend their glyphs.
        return progress() > 0f && progress() < 1f && origins[subtitle] != null &&
            sourceLayouts[subtitle]?.layoutInput?.text?.text == destination?.text && sourceLayouts[subtitle] != null &&
            pager.textReady(subtitle) && sheet?.isAttached == true && pager.root?.isAttached == true
    }

    fun frame(subtitle: Boolean, viewport: Rect, bend: Float): PlayerSheetTextFrame? {
        if (!moving(subtitle)) return null
        val pager = target ?: return null
        val root = pager.root ?: return null
        val host = sheet ?: return null
        val end = pager.textFrame(subtitle, bend)?.translate(pager.paneOffset + host.localPositionOf(root, Offset.Zero)) ?: return null
        val start = origins[subtitle]?.translate(-viewport.topLeft) ?: return null
        val p = progress().coerceIn(0f, 1f)
        return PlayerSheetTextFrame(p, playerSheetElementArc(start, end, p, anchorTopLeft = true), start, end)
    }
    fun weights(): List<Float> {
        val p = progress().coerceIn(0f, 1f).let { it * it }
        val pager = target
        val lyric = pager?.progress?.invoke()?.coerceIn(0f, 1f) ?: 0f
        val queue = if (pager?.queueReady == true) pager.queueProgress().coerceIn(0f, 1f) else 0f
        return listOf(1 - p, p * (1 - lyric) * (1 - queue), p * lyric * (1 - queue), p * queue)
    }
}
internal val LocalPlayerSheetText = staticCompositionLocalOf<PlayerSheetTextTransition?> { null }

/** The mini retains native layout/accessibility while the overlay owns the moving pixels. */
@Composable internal fun MiniPlayerSongText(text: String, modifier: Modifier, subtitle: Boolean = false,
    color: Color, fontSize: TextUnit, fontWeight: FontWeight = FontWeight.Normal) {
    val transfer = LocalPlayerSheetText.current
    val queue = LocalMiniQueueTransition.current
    val snapshot = remember(transfer, subtitle) { PlayerSharedTextSnapshot(text) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    SideEffect { snapshot.text = text; transfer?.snapshots?.set(subtitle, snapshot) }
    DisposableEffect(transfer, subtitle, snapshot) { onDispose {
        if (transfer != null) {
            if (transfer.snapshots[subtitle] === snapshot) transfer.snapshots.remove(subtitle)
            if (transfer.mini[subtitle] === coordinates) transfer.mini.remove(subtitle)
        }
    } }
    Text(text, modifier.onGloballyPositioned { coordinates = it; transfer?.mini?.set(subtitle, it) }
        .playerMotionDiagnostics { this[PlayerMiniTextVisible] = transfer?.moving(subtitle) != true && queue?.textMoving(subtitle) != true }
        .graphicsLayer { alpha = if (transfer?.moving(subtitle) == true || queue?.textMoving(subtitle) == true) 0f else 1f },
        color = color, fontSize = fontSize, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis,
        onTextLayout = {
            snapshot.layout = it
            if (transfer != null && (transfer.progress() <= 0f || transfer.progress() >= 1f)) transfer.captureLayouts()
        })
}

@Composable internal fun PlayerSheetSongTextFlight(transfer: PlayerSheetTextTransition, viewport: Rect?) {
    val window = viewport ?: return
    val bend = with(LocalDensity.current) { 20.dp.toPx() }
    SideEffect { if (transfer.progress() >= 1f) transfer.captureLayouts() }
    for (subtitle in listOf(false, true)) {
        val paint = remember { Paint() }
        val metrics = remember(transfer, subtitle) { derivedStateOf {
            val pager = transfer.target
            val layouts = if (subtitle) pager?.subtitleLayouts else pager?.titleLayouts
            playerTextDrawMetrics(listOf(transfer.sourceLayouts[subtitle]) + PlayerPagerArtworkRole.entries.map { layouts?.get(it)?.layout })
        } }
        val moving by remember(transfer, subtitle) { derivedStateOf { transfer.moving(subtitle) } }
        if (!moving) continue
        Canvas(Modifier.fillMaxSize().testTag(if (subtitle) "player_sheet_subtitle_flight" else "player_sheet_title_flight")
            .semantics { hideFromAccessibility() }.playerMotionDiagnostics {
                transfer.frame(subtitle, window, bend)?.let { this[PlayerSheetTextGeometry] = it }
                this[PlayerSharedTextLayouts] = metrics.value.layouts.filterNotNull()
                this[PlayerSharedTextWeights] = transfer.weights()
            }) {
            val frame = transfer.frame(subtitle, window, bend) ?: return@Canvas
            val data = metrics.value
            val weights = transfer.weights()
            val index = weights.drop(1).withIndex().maxByOrNull { it.value }?.index?.plus(1) ?: 1
            val startColor = data.layouts.firstOrNull()?.layoutInput?.style?.color ?: Color.Unspecified
            val endColor = data.layouts.getOrNull(index)?.layoutInput?.style?.color ?: Color.Unspecified
            val color = if (startColor != Color.Unspecified && endColor != Color.Unspecified)
                lerp(startColor, endColor, playerSheetSurfaceAlpha(frame.progress)) else Color.Unspecified
            drawPlayerSharedText(frame.bounds, data, weights, paint, color = color)
        }
    }
}
