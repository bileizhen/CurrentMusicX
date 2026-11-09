package io.github.currencortex.music.feature.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.R

internal enum class PlayerSharedControl { TOGGLE, QUEUE }
internal data class PlayerControlFrame(val progress: Float, val bounds: Rect, val start: Rect, val end: Rect)
internal val PlayerControlGeometry = SemanticsPropertyKey<PlayerControlFrame>("PlayerControlGeometry")
internal val PlayerControlVisible = SemanticsPropertyKey<Boolean>("PlayerControlVisible")

internal class PlayerControlEndpoint(val glyph: State<Float>) {
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    var color by mutableStateOf(Color.White)
}

/** Freeze the dock endpoints before it folds, retaining them when a gesture reverses. */
internal class PlayerSheetControlsTransition(val progress: () -> Float) {
    val mini = mutableStateMapOf<PlayerSharedControl, PlayerControlEndpoint>()
    val targets = mutableStateMapOf<PlayerSharedControl, PlayerControlEndpoint>()
    var origins by mutableStateOf<Map<PlayerSharedControl, Rect>>(emptyMap())
        private set
    var sheet by mutableStateOf<LayoutCoordinates?>(null)

    fun captureOrigin() {
        origins = mini.mapNotNull { (kind, endpoint) ->
            endpoint.coordinates?.takeIf { it.isAttached }?.let {
                kind to Rect(it.localToRoot(Offset.Zero), Size(it.size.width.toFloat(), it.size.height.toFloat()))
            }
        }.toMap()
    }

    fun moving(kind: PlayerSharedControl): Boolean {
        val host = sheet ?: return false
        val target = targets[kind]?.coordinates ?: return false
        return progress() > 0f && progress() < 1f && origins[kind] != null && mini[kind] != null &&
            host.isAttached && target.isAttached && host.findRootCoordinates() === target.findRootCoordinates() &&
            target.size.width > 0 && target.size.height > 0
    }

    fun frame(kind: PlayerSharedControl, viewport: Rect): PlayerControlFrame? {
        if (!moving(kind)) return null
        val host = sheet ?: return null
        val target = targets[kind]?.coordinates ?: return null
        val start = origins[kind]?.translate(-viewport.topLeft) ?: return null
        val end = Rect(host.localPositionOf(target, Offset.Zero), Size(target.size.width.toFloat(), target.size.height.toFloat()))
        val p = progress().coerceIn(0f, 1f)
        return PlayerControlFrame(p, playerSheetElementArc(start, end, p), start, end)
    }
}

internal val LocalPlayerSheetControls = staticCompositionLocalOf<PlayerSheetControlsTransition?> { null }

/** The same vector glyphs are used at both endpoints and in the moving layer. */
@Composable internal fun PlayerSharedControlIcon(kind: PlayerSharedControl, color: Color, modifier: Modifier,
    playing: Boolean = false, mini: Boolean = false) {
    val transfer = LocalPlayerSheetControls.current
    val glyph = animateFloatAsState(if (playing) 1f else 0f, tween(180, easing = LinearEasing), label = "shared play pause")
    val endpoint = remember { PlayerControlEndpoint(glyph) }
    val endpoints = if (mini) transfer?.mini else transfer?.targets
    SideEffect { endpoint.color = color; endpoints?.set(kind, endpoint) }
    DisposableEffect(endpoints, kind, endpoint) { onDispose {
        if (endpoints?.get(kind) === endpoint) endpoints.remove(kind)
    } }
    Box(modifier.onGloballyPositioned { endpoint.coordinates = it }
        .playerMotionDiagnostics { this[PlayerControlVisible] = transfer?.moving(kind) != true }
        .graphicsLayer { alpha = if (transfer?.moving(kind) == true) 0f else 1f }) {
        PlayerControlGlyph(kind, { color }, { glyph.value }, Modifier.fillMaxSize())
    }
}

@Composable private fun PlayerControlGlyph(kind: PlayerSharedControl, color: () -> Color, glyph: () -> Float,
    modifier: Modifier) {
    // Tint is read in drawing so the flight does not recompose its vector images every frame.
    val play = painterResource(R.drawable.player_symbol_play_arrow_fill1)
    val pause = painterResource(R.drawable.player_symbol_pause_fill1)
    val queue = painterResource(R.drawable.player_symbol_queue_music)
    androidx.compose.foundation.Canvas(modifier) {
        val tint = ColorFilter.tint(color())
        if (kind == PlayerSharedControl.QUEUE) with(queue) { draw(size, colorFilter = tint) }
        else {
            with(play) { draw(size, alpha = 1f - glyph(), colorFilter = tint) }
            with(pause) { draw(size, alpha = glyph(), colorFilter = tint) }
        }
    }
}

@Composable internal fun PlayerSheetControlFlights(transfer: PlayerSheetControlsTransition, viewport: Rect?) {
    val window = viewport ?: return
    for (kind in PlayerSharedControl.entries) {
        val frame = remember(transfer, kind, window) { derivedStateOf { transfer.frame(kind, window) } }
        val moving by remember(transfer, kind) { derivedStateOf { transfer.moving(kind) } }
        if (!moving) continue
        PlayerControlGlyph(kind, color = {
            val p = frame.value?.progress ?: 0f
            lerp(transfer.mini[kind]?.color ?: Color.White, transfer.targets[kind]?.color ?: Color.White,
                playerSheetSurfaceAlpha(p))
        }, glyph = { transfer.mini[kind]?.glyph?.value ?: 0f }, modifier = Modifier.requiredSize(40.dp)
            .testTag("player_sheet_${kind.name.lowercase()}_flight").semantics { hideFromAccessibility() }
            .playerMotionDiagnostics { frame.value?.let { this[PlayerControlGeometry] = it } }
            .graphicsLayer {
                val bounds = frame.value?.bounds
                alpha = if (bounds != null) 1f else 0f
                if (bounds != null) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = bounds.left; translationY = bounds.top
                    scaleX = bounds.width / size.width; scaleY = bounds.height / size.height
                }
            })
    }
}
