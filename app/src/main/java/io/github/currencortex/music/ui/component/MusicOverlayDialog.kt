package io.github.currencortex.music.ui.component

import androidx.compose.animation.EnterTransition
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
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
    insideMargin: DpSize = DpSize(20.dp, 20.dp),
    outsideMargin: DpSize = DialogDefaults.outsideMargin,
    alignContent: Boolean = true,
    content: @Composable () -> Unit,
) {
    val visible = remember { mutableStateOf(show) }
    SideEffect { visible.value = show }
    val dismiss = rememberUpdatedState(onDismissRequest)
    val colors = rememberUpdatedState(MiuixTheme.colorScheme)
    val styles = rememberUpdatedState(MiuixTheme.textStyles)
    val currentModifier = rememberUpdatedState(modifier)
    val currentTitle = rememberUpdatedState(title)
    val currentSummary = rememberUpdatedState(summary)
    val currentInsideMargin = rememberUpdatedState(insideMargin)
    val currentOutsideMargin = rememberUpdatedState(outsideMargin)
    val aligned = rememberUpdatedState(alignContent)
    val body = rememberUpdatedState(content)
    // DialogLayout retains its root entry through exit even when this caller is disposed.
    // Native OverlayDialog keeps entrance/layout/predictive back; the root owns exit + dim.
    // The root host stores this lambda in snapshot state. Keep its identity stable so
    // updating caller content cannot feed back into registering the same root overlay.
    val retainedContent: @Composable () -> Unit = remember {
        {
            MiuixTheme(colors = colors.value, textStyles = styles.value) {
                // A local host keeps native presentation inside the root's animated layer.
                Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
                    OverlayDialog(show = true, modifier = currentModifier.value,
                        title = currentTitle.value.takeUnless { aligned.value },
                        summary = currentSummary.value.takeUnless { aligned.value },
                        insideMargin = currentInsideMargin.value, outsideMargin = currentOutsideMargin.value,
                        enableWindowDim = false, renderInRootScaffold = false,
                        onDismissRequest = { if (visible.value) dismiss.value?.invoke() }) {
                        if (!aligned.value) body.value() else CompositionLocalProvider(LocalMusicDialogContent provides true) {
                            Column(Modifier.fillMaxWidth()) {
                                if (currentTitle.value != null || currentSummary.value != null) {
                                    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        currentTitle.value?.let { Text(it, Modifier.fillMaxWidth().testTag("music_dialog_title"),
                                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                                        currentSummary.value?.let { Text(it, Modifier.fillMaxWidth(), fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                                    }
                                }
                                body.value()
                            }
                        }
                    }
                }
            }
        }
    }
    DialogLayout(visible = visible, enterTransition = EnterTransition.None,
        onDismissFinished = onDismissFinished, content = retainedContent)
}
