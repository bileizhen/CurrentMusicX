package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val MusicBottomSheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
private data class SheetMaterialBounds(val size: IntSize, val bodyOffset: IntOffset)

/** Native gestures and motion, with the same full-panel material as MusicOverlayDialog. */
@Composable internal fun MusicBottomSheet(show: Boolean, title: String?, onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val backdrop = LocalMusicDialogBackdrop.current
    var panel by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var body by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var materialBounds by remember { mutableStateOf<SheetMaterialBounds?>(null) }
    fun updateBounds() {
        val surface = panel?.takeIf { it.isAttached } ?: return
        val inner = body?.takeIf { it.isAttached } ?: return
        val offset = surface.localPositionOf(inner, Offset.Zero)
        materialBounds = SheetMaterialBounds(surface.size, IntOffset(offset.x.roundToInt(), offset.y.roundToInt()))
    }
    MiuixTheme(colors = musicDialogColors()) {
        OverlayBottomSheet(show = show, title = title, backgroundColor = Color.Transparent, cornerRadius = 32.dp,
            modifier = modifier.onGloballyPositioned { panel = it; updateBounds() },
            onDismissRequest = onDismissRequest, onDismissFinished = onDismissFinished) {
            // Draw before the native title/handle. This layer lives inside the native animated
            // panel, so its material follows both entrance/exit and drag transforms.
            Box(Modifier.fillMaxWidth().zIndex(-1f).onGloballyPositioned { body = it; updateBounds() }) {
                materialBounds?.let { bounds ->
                    Box(Modifier.matchParentSize().layout { measurable, constraints ->
                        val surface = measurable.measure(Constraints.fixed(bounds.size.width, bounds.size.height))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            surface.place(-bounds.bodyOffset.x, -bounds.bodyOffset.y)
                        }
                    }.testTag("bottom_sheet_material").musicDialogMaterial(backdrop, MusicBottomSheetShape))
                }
                content()
            }
        }
    }
}
