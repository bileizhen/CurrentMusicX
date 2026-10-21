package io.github.currencortex.music.feature.update

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Update-specific layout using the same material, actions and choices as every app dialog. */
@Composable internal fun UpdateOptionsDialog(title: String, summary: String? = null,
    onDismiss: () -> Unit, backdrop: Backdrop? = null, onDismissFinished: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null, choiceLayout: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val colors = musicDialogColors()
    MiuixTheme(colors = colors) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
        MusicOverlayDialog(show = true, onDismissRequest = onDismiss, onDismissFinished = onDismissFinished,
            matchPanelMotion = true,
            backgroundColor = Color.Transparent, insideMargin = DpSize(0.dp, 0.dp),
            outsideMargin = DpSize(16.dp, 12.dp)) {
            // The material belongs inside the native animated surface, alongside its content.
            // An outer OverlayDialog modifier is drawn before the native entrance transform.
            Box(Modifier.fillMaxWidth().testTag("update_options_surface").musicDialogMaterial(backdrop)) {
            Column(Modifier.fillMaxWidth().then(if (choiceLayout) Modifier.padding(top = 24.dp, bottom = 16.dp)
                else Modifier.padding(24.dp))
                .testTag("update_options_sheet")) {
                Text(title, Modifier.fillMaxWidth().padding(horizontal = if (choiceLayout) 24.dp else 0.dp).testTag("update_options_title"),
                    color = colors.onBackground, fontSize = 20.sp, lineHeight = 27.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                summary?.let {
                    Text(it, color = colors.onSurfaceVariantSummary, fontSize = 14.sp, lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = if (choiceLayout) 24.dp else 0.dp).padding(top = 12.dp))
                }
                Column(Modifier.fillMaxWidth()
                    .padding(top = if (choiceLayout) 8.dp else 20.dp).heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()), content = content)
                Row(Modifier.fillMaxWidth().padding(horizontal = if (choiceLayout) 16.dp else 0.dp)
                    .padding(top = if (choiceLayout) 20.dp else 24.dp).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (actions != null) actions() else UpdateDialogAction("完成", onDismiss,
                        Modifier.weight(1f).semantics { contentDescription = "关闭$title" })
                }
            }
            }
        }
        }
    }
}

@Composable internal fun UpdateDialogAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, destructive: Boolean = false, compact: Boolean = false) {
    MusicDialogButton(label, onClick, modifier, enabled = enabled, destructive = destructive,
        minHeight = if (compact) 44.dp else 48.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = if (compact) 10.dp else 13.dp))
}

@Composable internal fun UpdateOptionGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), content = content)
}

@Composable internal fun UpdateChoiceRow(label: String, selected: Boolean, showDivider: Boolean = false, onClick: () -> Unit) {
    MusicDialogChoiceRow(label, selected, onClick, showDivider = showDivider)
}

@Composable internal fun UpdateAutoCheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val press = rememberMusicDialogPress()
    val clickInteractions = remember { MutableInteractionSource() }
    val ink = MiuixTheme.colorScheme.onBackground
    Row(Modifier.fillMaxWidth().testTag("update_auto_check")
        .drawBehind { drawRect(ink.copy(alpha = .06f * press.amount.value)) }
        .musicDialogRowPress(press)
        .toggleable(checked, role = Role.Switch, interactionSource = clickInteractions, indication = null, onValueChange = onCheckedChange)
        .heightIn(min = 48.dp).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("启动时自动检查", color = MiuixTheme.colorScheme.onBackground, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable internal fun UpdateOptionSectionLabel(label: String) {
    Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp,
        modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 4.dp))
}
