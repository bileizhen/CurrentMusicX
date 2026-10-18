package io.github.currencortex.music.feature.update

import io.github.currencortex.music.ui.component.MusicDialogActions
import io.github.currencortex.music.ui.component.MusicDialogButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import io.github.currencortex.music.ui.component.MusicOverlayDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Isolated preview builds only. Confirm after dismissal so the keyboard does not resize the morph. */
@Composable
fun UpdatePreviewDialog(initialVersion: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf(TextFieldValue(initialVersion, TextRange(0, initialVersion.length))) }
    var confirmed by remember { mutableStateOf<String?>(null) }
    val candidate = input.text.trim()
    val valid = candidate.length in 1..32 && candidate.none { it.isWhitespace() || it.isISOControl() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val confirm by rememberUpdatedState(onConfirm)
    fun submit() {
        if (valid && confirmed == null) {
            confirmed = candidate
            keyboard?.hide()
            onDismiss()
        }
    }
    MusicOverlayDialog(show = true, title = "版本形变预览",
        summary = "输入想看的版本，例如 1.1.15、2.0、17 或 2.0-beta。",
        onDismissRequest = { keyboard?.hide(); onDismiss() },
        onDismissFinished = {
            confirmed?.let { version -> confirmed = null; confirm(version) }
        }) {
        Column(Modifier.fillMaxWidth().testTag("update_preview_dialog"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicTextField(input, { input = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("update_preview_version")
                    .semantics { contentDescription = "目标版本" },
                decorationBox = { field ->
                    Column(Modifier.background(MiuixTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("目标版本", fontSize = 12.sp)
                        field()
                    }
                })
            Text(if (input.text.length > 32) "版本文字最多 32 个字符" else "仅预览数字形变，实际安装版本和下载包保持不变。", fontSize = 12.sp)
            MusicDialogActions(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MusicDialogButton("取消", modifier = Modifier.weight(1f).testTag("update_preview_cancel"),
                    onClick = { keyboard?.hide(); onDismiss() })
                MusicDialogButton("开始预览", enabled = valid, modifier = Modifier.weight(1f).testTag("update_preview_confirm"),
                    onClick = { submit() })
            }
            LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus(); keyboard?.show() }
        }
    }
}
