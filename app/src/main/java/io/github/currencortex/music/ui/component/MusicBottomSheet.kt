package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val MusicBottomSheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
private data class SheetMaterialBounds(val size: IntSize, val bodyOffset: IntOffset)

/** Native gestures and motion, with the same full-panel material as MusicOverlayDialog. */
@Composable internal fun MusicBottomSheet(show: Boolean, title: String?, onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val backdrop = LocalMusicDialogBackdrop.current
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var bodySize by remember { mutableStateOf(IntSize.Zero) }
    // The content is the final child of the native column, with no bottom margin.
    // Derive its inset from measured sizes: global coordinates include the native
    // entrance/drag translation, which would move the material a second time.
    val materialBounds = if (panelSize != IntSize.Zero && bodySize != IntSize.Zero)
        SheetMaterialBounds(panelSize, IntOffset((panelSize.width - bodySize.width) / 2,
            panelSize.height - bodySize.height)) else null
    MiuixTheme(colors = musicDialogColors()) {
        // Native paint covers the first layout and its elastic bottom fill. The sampled
        // material is added above it; a transparent native surface exposes a gap while settling.
        OverlayBottomSheet(show = show, title = title, backgroundColor = musicDialogFallbackColor(), cornerRadius = 32.dp,
            insideMargin = DpSize(24.dp, 0.dp), outsideMargin = DpSize(0.dp, 0.dp),
            modifier = modifier.onSizeChanged { panelSize = it },
            onDismissRequest = onDismissRequest, onDismissFinished = onDismissFinished) {
            // Draw before the native title/handle. This layer lives inside the native animated
            // panel, so its material follows both entrance/exit and drag transforms.
            Box(Modifier.fillMaxWidth().zIndex(-1f).onSizeChanged { bodySize = it }) {
                materialBounds?.let { bounds ->
                    Box(Modifier.matchParentSize().layout { measurable, constraints ->
                        val surface = measurable.measure(Constraints.fixed(bounds.size.width, bounds.size.height))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            surface.place(-bounds.bodyOffset.x, -bounds.bodyOffset.y)
                        }
                    }.testTag("bottom_sheet_material").musicDialogMaterial(backdrop, MusicBottomSheetShape, drawFallback = false))
                }
                content()
            }
        }
    }
}
