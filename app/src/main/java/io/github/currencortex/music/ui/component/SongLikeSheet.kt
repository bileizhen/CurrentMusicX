package io.github.currencortex.music.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.R
import io.github.currencortex.music.data.song.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun SongLikeSheet(selection: SongLikeSelection, toggle: (LikeDestination) -> Unit, dismiss: () -> Unit) {
    MusicDialog("收录到哪个「我喜欢」？", dismiss, footer = { TextButton("关闭", onClick = dismiss) }) {
        Column(Modifier.testTag("song_like_sheet")) {
            LikeRow("CurrentMusic · 我喜欢的音乐", selection.currentMusic, LikeDestination.CURRENT_MUSIC, toggle)
            LikeRow("网易云音乐 APP · 我喜欢的音乐", selection.netease, LikeDestination.NETEASE, toggle)
        }
    }
}

@Composable private fun LikeRow(title: String, state: LikeDestinationState, target: LikeDestination, toggle: (LikeDestination) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val ink = if (state.liked == true) colors.primary else colors.onSurface
    val summary = when {
        state.busy -> if (state.liked == null) "正在读取收录状态…" else "正在更新…"
        state.error != null -> state.error + "，点击重试"
        state.liked == true -> "已收录，点击移出"
        else -> "未收录，点击加入"
    } + if (target == LikeDestination.NETEASE && !state.busy && state.error == null) " · 红心同步到网易云 APP" else ""
    Row(Modifier.fillMaxWidth().testTag("like_destination_${target.name}")
        .clickable(enabled = !state.busy, role = Role.Button) { toggle(target) }
        .semantics { selected = state.liked == true }.heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Image(painterResource(if (state.liked == true) R.drawable.player_symbol_favorite_fill1 else R.drawable.player_symbol_favorite),
            null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(ink))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 15.sp)
            Text(summary, fontSize = 12.sp, color = colors.onSurface.copy(alpha = .6f))
        }
        if (!state.busy) Image(if (state.liked == true) Icons.Default.Check else Icons.Default.Add, null,
            Modifier.size(22.dp), colorFilter = ColorFilter.tint(ink))
    }
}
