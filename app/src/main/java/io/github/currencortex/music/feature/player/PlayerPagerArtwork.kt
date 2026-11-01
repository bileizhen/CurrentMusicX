package io.github.currencortex.music.feature.player

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.MusicCover
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.round

internal enum class PlayerPagerArtworkRole { COVER, LYRICS, QUEUE }
internal val PlayerPagerArtworkGeometry = SemanticsPropertyKey<PlayerArtworkFrame>("PlayerPagerArtworkGeometry")
internal val PlayerSharedTitleGeometry = SemanticsPropertyKey<Rect>("PlayerSharedTitleGeometry")
internal val PlayerSharedSubtitleGeometry = SemanticsPropertyKey<Rect>("PlayerSharedSubtitleGeometry")
internal val PlayerSharedTextLayouts = SemanticsPropertyKey<List<TextLayoutResult>>("PlayerSharedTextLayouts")
internal val PlayerSharedTextWeights = SemanticsPropertyKey<List<Float>>("PlayerSharedTextWeights")

internal class PlayerSharedTextSnapshot(text: String) {
    var text by mutableStateOf(text)
    var layout by mutableStateOf<TextLayoutResult?>(null)
    val ready get() = layout?.layoutInput?.text?.text == text
}

/** The same quadratic arc is used for all three elements and both page transitions. */
internal fun playerElementArc(start: Rect, end: Rect, progress: Float, bend: Float): Rect {
    val p = progress.coerceIn(0f, 1f)
    val arc = 4 * p * (1 - p) * bend
    val center = start.center * (1 - p) + end.center * p + Offset(arc * .5f, -arc)
    val size = Size(start.width * (1 - p) + end.width * p, start.height * (1 - p) + end.height * p)
    return Rect(center - Offset(size.width / 2, size.height / 2), size)
}

internal class PlayerPagerArtworkTransition(val progress: () -> Float, val queueProgress: () -> Float = { 0f }) {
    var root by mutableStateOf<LayoutCoordinates?>(null)
    var mainPage by mutableStateOf<LayoutCoordinates?>(null)
    var queuePage by mutableStateOf<LayoutCoordinates?>(null)
    var container by mutableStateOf<LayoutCoordinates?>(null)
    var coverPage by mutableStateOf<LayoutCoordinates?>(null)
    var lyricsPage by mutableStateOf<LayoutCoordinates?>(null)
    var cover by mutableStateOf<LayoutCoordinates?>(null)
    var lyrics by mutableStateOf<LayoutCoordinates?>(null)
    var queue by mutableStateOf<LayoutCoordinates?>(null)
    val titles = mutableStateMapOf<PlayerPagerArtworkRole, LayoutCoordinates>()
    val subtitles = mutableStateMapOf<PlayerPagerArtworkRole, LayoutCoordinates>()
    val titleLayouts = mutableStateMapOf<PlayerPagerArtworkRole, PlayerSharedTextSnapshot>()
    val subtitleLayouts = mutableStateMapOf<PlayerPagerArtworkRole, PlayerSharedTextSnapshot>()
    private var endpoints by mutableStateOf<Pair<Rect, Rect>?>(null)
    private var titleEndpoints by mutableStateOf<Pair<Rect, Rect>?>(null)
    private var subtitleEndpoints by mutableStateOf<Pair<Rect, Rect>?>(null)
    private var queueBounds by mutableStateOf<Rect?>(null)
    private var queueTitle by mutableStateOf<Rect?>(null)
    private var queueSubtitle by mutableStateOf<Rect?>(null)
    var paneOffset by mutableStateOf(Offset.Zero)
        private set
    val ready get() = root?.isAttached == true && mainPage?.isAttached == true && container?.isAttached == true &&
        coverPage?.isAttached == true && lyricsPage?.isAttached == true && cover?.isAttached == true && lyrics?.isAttached == true
    val queueReady get() = ready && queuePage?.isAttached == true && queue?.isAttached == true && queueBounds != null
    fun textReady(subtitle: Boolean): Boolean {
        val layouts = if (subtitle) subtitleLayouts else titleLayouts
        return ready && (if (subtitle) subtitleEndpoints else titleEndpoints) != null &&
            layouts[PlayerPagerArtworkRole.COVER]?.ready == true && layouts[PlayerPagerArtworkRole.LYRICS]?.ready == true &&
            (queueProgress() <= 0f || queueReady && (if (subtitle) queueSubtitle else queueTitle) != null &&
                layouts[PlayerPagerArtworkRole.QUEUE]?.ready == true)
    }

    /** Measure within unscrolled pages so layer updates cannot feed motion back into its endpoints. */
    fun updateBounds() {
        if (!ready) { endpoints = null; titleEndpoints = null; subtitleEndpoints = null; return }
        paneOffset = mainPage!!.localPositionOf(container!!, Offset.Zero)
        fun bounds(page: LayoutCoordinates?, child: LayoutCoordinates?): Rect? {
            if (page?.isAttached != true || child?.isAttached != true) return null
            return Rect(page.localPositionOf(child, Offset.Zero), Size(child.size.width.toFloat(), child.size.height.toFloat()))
        }
        fun pair(nodes: Map<PlayerPagerArtworkRole, LayoutCoordinates>): Pair<Rect, Rect>? {
            val start = bounds(coverPage, nodes[PlayerPagerArtworkRole.COVER]) ?: return null
            val end = bounds(lyricsPage, nodes[PlayerPagerArtworkRole.LYRICS]) ?: return null
            return start to end
        }
        endpoints = bounds(coverPage, cover)!! to bounds(lyricsPage, lyrics)!!
        titleEndpoints = pair(titles)
        subtitleEndpoints = pair(subtitles)
        // Queue coordinates are relative to its full-size translating layer, not the window.
        queueBounds = bounds(queuePage, queue)?.translate(-paneOffset)
        queueTitle = bounds(queuePage, titles[PlayerPagerArtworkRole.QUEUE])?.translate(-paneOffset)
        queueSubtitle = bounds(queuePage, subtitles[PlayerPagerArtworkRole.QUEUE])?.translate(-paneOffset)
    }

    private fun element(pair: Pair<Rect, Rect>?, destination: Rect?, bend: Float): Rect? {
        val (start, end) = pair ?: return null
        if (!ready) return null
        val pager = playerElementArc(start, end, progress(), bend)
        val q = queueProgress().coerceIn(0f, 1f)
        return if (queueReady && destination != null) playerElementArc(pager, destination, q, bend)
            // An empty or different queue has no matching destination; leave with the source page.
            else pager.translate(Offset(0f, -(mainPage?.size?.height ?: 0) * q))
    }
    fun frame(bend: Float, rotation: Float): PlayerArtworkFrame? {
        val bounds = element(endpoints, queueBounds, bend) ?: return null
        return PlayerArtworkFrame(progress().coerceIn(0f, 1f), bounds, rotation)
    }
    fun textFrame(subtitle: Boolean, bend: Float): Rect? = if (subtitle)
        element(subtitleEndpoints, queueSubtitle, bend) else element(titleEndpoints, queueTitle, bend)
}
internal val LocalPlayerPagerArtwork = staticCompositionLocalOf<PlayerPagerArtworkTransition?> { null }

/** Native text supplies layout and accessibility; only the moving visual is shared. */
internal fun Modifier.playerSharedText(role: PlayerPagerArtworkRole?, subtitle: Boolean = false): Modifier = composed {
    val transfer = LocalPlayerPagerArtwork.current
    if (transfer == null || role == null) return@composed this
    val nodes = if (subtitle) transfer.subtitles else transfer.titles
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(transfer, role, subtitle) { onDispose {
        if (nodes[role] === coordinates) { nodes.remove(role); transfer.updateBounds() }
    } }
    onGloballyPositioned { coordinates = it; nodes[role] = it; transfer.updateBounds() }
        .graphicsLayer { alpha = if (transfer.textReady(subtitle)) 0f else 1f }
}

/** Capture each endpoint's real font, line breaks and ellipsis once during normal measurement. */
@Composable internal fun PlayerSongText(text: String, modifier: Modifier = Modifier,
    role: PlayerPagerArtworkRole? = null, subtitle: Boolean = false, color: Color, fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal, maxLines: Int = 1, overflow: TextOverflow = TextOverflow.Ellipsis) {
    val transfer = LocalPlayerPagerArtwork.current
    val snapshots = if (subtitle) transfer?.subtitleLayouts else transfer?.titleLayouts
    val snapshot = remember(transfer, role, subtitle) { PlayerSharedTextSnapshot(text) }
    SideEffect {
        snapshot.text = text
        if (role != null) snapshots?.set(role, snapshot)
    }
    DisposableEffect(snapshots, role, snapshot) { onDispose {
        if (role != null && snapshots?.get(role) === snapshot) snapshots.remove(role)
    } }
    Text(text, modifier.playerSharedText(role, subtitle), color = color, fontSize = fontSize,
        fontWeight = fontWeight, maxLines = maxLines, overflow = overflow, onTextLayout = { snapshot.layout = it })
}

/** One artwork and one pair of text layers bridge cover, lyrics and queue, including reversed drags. */
@Composable internal fun PlayerPagerArtwork(transfer: PlayerPagerArtworkTransition, song: Song?) {
    val sheet = LocalPlayerArtworkTransition.current
    val sheetText = LocalPlayerSheetText.current
    SideEffect { sheetText?.target = transfer }
    DisposableEffect(sheetText, transfer) { onDispose { if (sheetText?.target === transfer) sheetText.target = null } }
    val sheetProgress = LocalPlayerSheetProgress.current
    val rotation = LocalPlayerArtworkRotation.current
    val density = LocalDensity.current
    val bend = with(density) { 20.dp.toPx() }
    val geometry = remember(transfer, bend) { derivedStateOf { transfer.frame(bend, 0f) } }
    val ready by remember(geometry) { derivedStateOf { geometry.value != null } }
    val destination = remember(transfer, sheet, bend) {
        {
            val root = sheet?.sheet?.takeIf { it.isAttached }
            val pane = transfer.root?.takeIf { it.isAttached }
            if (root == null || pane == null) null
            else transfer.frame(bend, 0f)?.bounds?.translate(transfer.paneOffset + root.localPositionOf(pane, Offset.Zero))
        }
    }
    SideEffect {
        if (transfer.ready) { sheet?.target = transfer.root; sheet?.destination = destination }
    }
    DisposableEffect(transfer, sheet) { onDispose {
        if (sheet?.destination === destination) { sheet?.destination = null; sheet?.target = null }
    } }
    if (!ready) return
    val width = transfer.cover!!.size.width
    if (width <= 0) return
    MusicCover(song?.cover.orEmpty(), Modifier.requiredSize(with(density) { width.toDp() }).testTag("player_pager_artwork")
        .semantics {
            geometry.value?.let { this[PlayerPagerArtworkGeometry] = it.copy(rotation = rotation?.value ?: 0f) }
            this[CoverRotation] = rotation?.value ?: 0f
        }
        .graphicsLayer {
            alpha = if (sheet?.moving == true) 0f else playerSheetContentAlpha(sheetProgress())
            transformOrigin = TransformOrigin(0f, 0f)
            val frame = geometry.value
            translationX = (frame?.bounds?.left ?: 0f) + transfer.paneOffset.x
            translationY = (frame?.bounds?.top ?: 0f) + transfer.paneOffset.y
            scaleX = (frame?.bounds?.width ?: 0f) / width; scaleY = scaleX
        }.graphicsLayer {
            val frame = geometry.value
            val scale = (frame?.bounds?.width ?: 0f) / width
            val q = if (transfer.queueReady) transfer.queueProgress().coerceIn(0f, 1f) else 0f
            val angle = rotation?.value ?: 0f
            rotationZ = if (q == 0f) angle else (angle - round(angle / 360f) * 360f) * (1 - q)
            val radius = (frame?.bounds?.width ?: 0f) / 2 * (1 - q) + with(density) { 8.dp.toPx() } * q
            shape = RoundedCornerShape(with(density) { (radius / scale.coerceAtLeast(.001f)).toDp() })
            clip = true
        }, pixels = 800, cornerRadius = 0.dp)
    PlayerSharedSongText(transfer, false, bend)
    PlayerSharedSongText(transfer, true, bend)
}

internal data class PlayerTextDrawMetrics(val layouts: List<TextLayoutResult?>, val advances: List<Float>?)

/** Native fonts round glyph advances at each size; normalize their shared prefix before blending. */
internal fun playerTextDrawMetrics(layouts: List<TextLayoutResult?>): PlayerTextDrawMetrics {
    val present = layouts.filterNotNull()
    var end = present.minOfOrNull { it.getLineEnd(0, visibleEnd = true) } ?: 0
    val reference = present.firstOrNull()?.layoutInput?.text?.text.orEmpty()
    for (index in 0 until end) {
        if (present.any { it.layoutInput.text.text[index] != reference[index] }) { end = index; break }
    }
    // Preserve native direction/indent handling for RTL and explicitly aligned paragraphs.
    if (end < 2 || present.any { it.getHorizontalPosition(0, true) != 0f }) return PlayerTextDrawMetrics(layouts, null)
    val advances = layouts.map { layout ->
        if (layout == null) 0f else layout.getBoundingBox(end - 1).right - layout.getBoundingBox(0).left
    }
    return PlayerTextDrawMetrics(layouts, advances.takeIf { widths -> layouts.indices.all { layouts[it] == null || widths[it] > 0f } })
}

/** Keep one renderer at rest and in motion. Progress changes drawing transforms, never text layout. */
@Composable private fun PlayerSharedSongText(transfer: PlayerPagerArtworkTransition, subtitle: Boolean, bend: Float) {
    val sheetProgress = LocalPlayerSheetProgress.current
    val sheetText = LocalPlayerSheetText.current
    val snapshots = if (subtitle) transfer.subtitleLayouts else transfer.titleLayouts
    val paint = remember { Paint() }
    val metrics = remember(snapshots) { derivedStateOf {
        playerTextDrawMetrics(PlayerPagerArtworkRole.entries.map { snapshots[it]?.layout })
    } }
    fun weights(): List<Float> {
        val p = transfer.progress().coerceIn(0f, 1f)
        val q = if (transfer.queueReady) transfer.queueProgress().coerceIn(0f, 1f) else 0f
        return listOf((1 - p) * (1 - q), p * (1 - q), q)
    }
    Canvas(Modifier.fillMaxSize().testTag(if (subtitle) "player_shared_subtitle" else "player_shared_title")
        .semantics {
            hideFromAccessibility()
            transfer.textFrame(subtitle, bend)?.let {
                if (subtitle) this[PlayerSharedSubtitleGeometry] = it else this[PlayerSharedTitleGeometry] = it
            }
            this[PlayerSharedTextLayouts] = PlayerPagerArtworkRole.entries.mapNotNull { snapshots[it]?.layout }
            this[PlayerSharedTextWeights] = weights()
        }) {
        if (!transfer.textReady(subtitle) || sheetText?.moving(subtitle) == true) return@Canvas
        val rect = transfer.textFrame(subtitle, bend)?.translate(transfer.paneOffset) ?: return@Canvas
        drawPlayerSharedText(rect, metrics.value, weights(), paint, playerSheetContentAlpha(sheetProgress()))
    }
}

/** Both sheet and pager flights blend cached endpoint glyphs, without laying out every frame. */
internal fun DrawScope.drawPlayerSharedText(rect: Rect, metrics: PlayerTextDrawMetrics,
    opacity: List<Float>, paint: Paint, alpha: Float = 1f, color: Color = Color.Unspecified) {
        val (layouts, advances) = metrics
        val font = layouts.indices.sumOf { index ->
            ((layouts[index]?.layoutInput?.style?.fontSize?.value ?: 0f) * opacity[index]).toDouble()
        }.toFloat()
        val advance = advances?.let { widths -> layouts.indices.sumOf { index ->
            (widths[index] * font / (layouts[index]?.layoutInput?.style?.fontSize?.value ?: 1f) * opacity[index]).toDouble()
        }.toFloat() }
        val baseline = layouts.indices.sumOf { index ->
            ((layouts[index]?.firstBaseline ?: 0f) * opacity[index]).toDouble()
        }.toFloat()
        // Add weighted glyph coverage in a bounded layer. SrcOver would dim identical letters
        // halfway through a crossfade, creating another visible pulse at the handoff.
        drawContext.canvas.saveLayer(rect, paint)
        clipRect(rect.left, rect.top, rect.right, rect.bottom) {
            layouts.forEachIndexed { index, layout ->
                if (layout != null && opacity[index] > 0f) {
                    val scale = font / layout.layoutInput.style.fontSize.value
                    // Endpoint styles can retain different line-height leading at each font size.
                    // Align baselines before blending, otherwise the same glyph is drawn twice apart.
                    val top = rect.top + baseline - layout.firstBaseline * scale
                    val horizontalScale = if (advance != null && advances != null) advance / advances[index] else scale
                    withTransform({ translate(rect.left, top); scale(horizontalScale, scale, Offset.Zero) }) {
                        drawText(layout, color = color, alpha = opacity[index] * alpha, blendMode = BlendMode.Plus)
                    }
                }
            }
        }
        drawContext.canvas.restore()
}
