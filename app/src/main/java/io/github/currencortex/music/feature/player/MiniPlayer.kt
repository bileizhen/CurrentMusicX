package io.github.currencortex.music.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.ui.component.MusicTextAction
import io.github.currencortex.music.ui.component.MusicTransportButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.component.MusicCover
import top.yukonga.miuix.kmp.basic.*

@Composable fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val song = queue.current
    if (song == null && state.mode == PlayerMode.LOCAL) return
    Card(modifier.fillMaxWidth().testTag("mini_player")) {
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(onClick = onOpen).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            MusicCover(song?.cover.orEmpty(), Modifier.size(42.dp))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(song?.name ?: "等待房间点歌", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(when (state.mode) { PlayerMode.ROOM -> "一起听 · ${if (state.canControlPlayback) "房间控制" else "跟随房间播放"}";
                    PlayerMode.CAST -> "正在投屏"; else -> song?.artists.orEmpty() }, fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            }
            MusicTransportButton(if (state.playing) "暂停" else "播放", onToggle, Modifier.testTag("mini_toggle"),
                enabled = state.canControlPlayback && song != null, playing = state.playing)
            MusicTransportButton("下一首", { vm.player.next() }, enabled = state.canControlPlayback && song != null, direction = 1)
        }
    }
}
