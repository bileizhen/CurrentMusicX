package io.github.currencortex.music.feature.update

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.core.update.UpdatePromptState
import io.github.currencortex.music.ui.component.MusicDialogButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Automatic discovery asks first; only View opens the full update page. */
@Composable internal fun UpdateAvailablePrompt(prompt: UpdatePromptState,
    onIgnore: () -> Unit, onView: () -> Unit, onDismiss: () -> Unit) {
    UpdateOptionsDialog("发现新版本", onDismiss = { if (!prompt.saving) onDismiss() }, actions = {
        val colors = MiuixTheme.colorScheme
        MusicDialogButton(if (prompt.saving) "正在忽略…" else "忽略更新", onIgnore,
            Modifier.weight(1f).testTag("update_prompt_ignore"), enabled = !prompt.saving, minHeight = 48.dp,
            colors = ButtonDefaults.textButtonColors(color = colors.onBackground.copy(alpha = .07f),
                textColor = colors.onSurfaceVariantSummary))
        MusicDialogButton("查看", onView, Modifier.weight(1f).testTag("update_prompt_view"),
            enabled = !prompt.saving, minHeight = 48.dp,
            colors = ButtonDefaults.textButtonColors(color = colors.primary, textColor = colors.onPrimary))
    }) {
        Column(Modifier.fillMaxWidth().testTag("update_available_prompt"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CurrentMusic ${prompt.release.version} 已发布", fontSize = 17.sp)
            Text("查看更新内容并选择是否下载。", fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            prompt.error?.let { Text(it, fontSize = 13.sp, color = MiuixTheme.colorScheme.error) }
        }
    }
}
