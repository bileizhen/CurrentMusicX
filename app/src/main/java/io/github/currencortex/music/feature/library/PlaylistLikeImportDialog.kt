package io.github.currencortex.music.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.library.PlaylistLikeImportState
import io.github.currencortex.music.ui.component.MusicDialog
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun PlaylistLikeImportDialog(state: PlaylistLikeImportState,
    onClose: () -> Unit, onStop: () -> Unit, onRetry: () -> Unit) {
    MusicDialog(if (state.running) "正在导入网易云我喜欢" else "网易云导入结果", onDismiss = onClose) {
        Column(Modifier.testTag("playlist_like_import").heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.name, fontSize = 18.sp)
            Text(if (state.running && state.total == 0) "正在读取歌单与网易云账号…"
                else "${state.processed} / ${state.total} 首", fontSize = 14.sp)
            if (state.running) Box(Modifier.fillMaxWidth().height(6.dp)
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = .08f), RoundedCornerShape(3.dp))) {
                val progress = if (state.total > 0) state.processed.toFloat() / state.total else 0f
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight()
                    .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
            }
            Text("成功 ${state.imported} · 跳过 ${state.skipped} · 失败 ${state.failed}", fontSize = 15.sp)
            Text("跳过已喜欢、重复及非网易云歌曲。", fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
            state.error?.let { Text(it, fontSize = 14.sp) }
            if (state.processed < state.total && !state.running)
                Text("还有 ${state.total - state.processed} 首未处理，可重试补齐。", fontSize = 13.sp)
            state.failures.forEach { Text(it, fontSize = 13.sp) }
        }
        if (state.running) {
            Text("关闭面板后仍会继续导入；请保持应用运行。", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("后台导入", onClick = onClose, modifier = Modifier.testTag("playlist_import_background"))
                TextButton("停止导入", onClick = onStop, modifier = Modifier.testTag("playlist_import_stop"))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("关闭", onClick = onClose)
                if (state.error != null || state.failed > 0)
                    TextButton("重试补齐", onClick = onRetry, modifier = Modifier.testTag("playlist_import_retry"))
            }
        }
    }
}
