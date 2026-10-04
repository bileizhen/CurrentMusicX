package io.github.currencortex.music.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.ui.component.MusicCover
import top.yukonga.miuix.kmp.basic.*

@Composable fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val song = queue.current ?: return
    Card(modifier.fillMaxWidth().testTag("mini_player")) {
        Row(Modifier.clickable(onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MusicCover(song.cover, Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(if (state.playing) "暂停" else "播放", onClick = onToggle, enabled = state.canControlPlayback, modifier = Modifier.testTag("mini_toggle"))
            TextButton("下一首", onClick = { vm.player.next() }, enabled = state.canControlPlayback)
        }
    }
}
