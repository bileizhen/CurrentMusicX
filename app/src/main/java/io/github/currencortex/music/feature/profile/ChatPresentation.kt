package io.github.currencortex.music.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.component.nativeui.GlassIconButton
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun ChatSendAction(onClick: () -> Unit, modifier: Modifier = Modifier,
    busy: Boolean, enabled: Boolean) {
    val colors = MiuixTheme.colorScheme
    GlassIconButton(onClick, if (busy) "发送中" else "发送", modifier, enabled = enabled, surface = false) {
        Box(Modifier.size(36.dp).background(colors.primary.copy(alpha = if (enabled) 1f else .10f), CircleShape),
            contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), size = 18.dp, strokeWidth = 2.dp)
            else Icon(MusicIcons.ArrowUp, null, Modifier.size(21.dp),
                tint = if (enabled) colors.onPrimary else colors.onSurfaceVariantSummary.copy(alpha = .4f))
        }
    }
}

@Composable internal fun ChatEmptyState(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(MusicIcons.Headphones, null, Modifier.size(36.dp), tint = MiuixTheme.colorScheme.primary)
        Text("还没有消息", fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text("从一首喜欢的歌聊起", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable internal fun ChatLoadingMessages() {
    MusicPlaceholderScope {
        Column(Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "正在加载聊天记录"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            for (mine in listOf(false, true)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Top) {
                    if (!mine) { MusicPlaceholder(Modifier.size(36.dp), shape = CircleShape); Spacer(Modifier.width(10.dp)) }
                    Column(Modifier.fillMaxWidth(.72f).background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
                        .padding(12.dp)) { LoadingTextLine(.85f); LoadingTextLine(.56f) }
                    if (mine) { Spacer(Modifier.width(10.dp)); MusicPlaceholder(Modifier.size(36.dp), shape = CircleShape) }
                }
            }
        }
    }
}
