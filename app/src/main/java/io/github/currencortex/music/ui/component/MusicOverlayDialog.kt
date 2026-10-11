package io.github.currencortex.music.ui.component

import androidx.compose.animation.EnterTransition
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.layout.WindowInsets
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.DialogLayout

/** The root host owns removal, so conditional callers may disappear without cancelling exit. */
@Composable
fun MusicOverlayDialog(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    summary: String? = null,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    insideMargin: DpSize = DialogDefaults.insideMargin,
    outsideMargin: DpSize = DialogDefaults.outsideMargin,
    content: @Composable () -> Unit,
) {
    val visible = remember { mutableStateOf(show) }
    SideEffect { visible.value = show }
    val dismiss by rememberUpdatedState(onDismissRequest)
    val colors = MiuixTheme.colorScheme
    val styles = MiuixTheme.textStyles
    // DialogLayout retains its root entry through exit even when this caller is disposed.
    // Native OverlayDialog keeps entrance/layout/predictive back; the root owns exit + dim.
    DialogLayout(visible = visible, enterTransition = EnterTransition.None,
        onDismissFinished = onDismissFinished) {
        MiuixTheme(colors = colors, textStyles = styles) {
            // A local host keeps native presentation inside the root's animated layer.
            Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
                OverlayDialog(show = true, modifier = modifier, title = title, summary = summary,
                    insideMargin = insideMargin, outsideMargin = outsideMargin,
                    enableWindowDim = false, renderInRootScaffold = false,
                    onDismissRequest = { if (visible.value) dismiss?.invoke() }) { content() }
            }
        }
    }
}
