package io.github.currencortex.music.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Confirm after dismissal so the keyboard does not resize the following version morph. */
@Composable
fun UpdatePreviewDialog(initialVersion: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf(TextFieldValue(initialVersion, TextRange(0, initialVersion.length))) }
    var confirmed by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    val candidate = input.text.trim()
    val valid = candidate.length in 1..32 && candidate.none { it.isWhitespace() || it.isISOControl() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val confirm by rememberUpdatedState(onConfirm)
    fun dismiss() { keyboard?.hide(); onDismiss() }
    fun submit() {
        if (valid && confirmed == null) { confirmed = candidate; dismiss() }
    }
    UpdateOptionsDialog(title = "版本形变预览",
        summary = "输入想看的版本，例如 1.1.15、2.0、17 或 2.0-beta。",
        onDismiss = ::dismiss,
        onDismissFinished = { confirmed?.let { version -> confirmed = null; confirm(version) } },
        actions = {
            UpdateDialogAction("取消", ::dismiss, Modifier.weight(1f).testTag("update_preview_cancel"))
            UpdateDialogAction("开始预览", ::submit, Modifier.weight(1f).testTag("update_preview_confirm"), enabled = valid)
        }) {
        val colors = MiuixTheme.colorScheme
        Column(Modifier.fillMaxWidth().testTag("update_preview_dialog")) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(input, { input = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    textStyle = TextStyle(color = colors.onBackground, fontSize = 19.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { focused = it.isFocused }
                        .testTag("update_preview_version").semantics { contentDescription = "目标版本" })
                Text("${input.text.length}/32", Modifier.padding(start = 8.dp).testTag("update_preview_count"),
                    fontSize = 12.sp, color = if (input.text.length > 32) colors.error else colors.onSurfaceVariantSummary)
                Box(Modifier.padding(start = 4.dp).size(44.dp).clip(CircleShape)
                    .clickable(enabled = input.text.isNotEmpty(), role = Role.Button) {
                        input = TextFieldValue(""); focus.requestFocus(); keyboard?.show()
                    }.testTag("update_preview_clear"), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(colors.onSurfaceVariantSummary.copy(alpha = .65f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, "清空版本", Modifier.size(14.dp), tint = colors.background)
                    }
                }
            }
            Spacer(Modifier.fillMaxWidth().height(if (focused) 1.5.dp else 1.dp)
                .background(if (focused) colors.primary else colors.onSurfaceVariantSummary.copy(alpha = .3f)))
            Text(if (input.text.length > 32) "版本文字最多 32 个字符"
                else if (candidate.isNotEmpty() && !valid) "版本号不能包含空白或控制字符"
                else "仅预览数字形变，不会更改实际版本或下载内容。",
                Modifier.padding(top = 12.dp), fontSize = 12.sp, lineHeight = 19.sp,
                color = if (candidate.isNotEmpty() && !valid) colors.error else colors.onSurfaceVariantSummary)
        }
        LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus(); keyboard?.show() }
    }
}
