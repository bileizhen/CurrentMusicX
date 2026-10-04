package io.github.currencortex.music.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.ui.component.*

@Composable fun PlaybackQueueSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val editable = state.mode == PlayerMode.LOCAL
    MusicDialog("播放队列 · ${queue.songs.size}", onDismiss) {
        MusicTextAction("清空队列", { vm.player.clear(); onDismiss() }, enabled = editable)
        LazyColumn(Modifier.heightIn(max = 360.dp).testTag("mini_queue_sheet")) {
            itemsIndexed(queue.songs, key = { index, song -> "${song.id}-$index" }) { index, song ->
                MusicDestinationRow((if (index == queue.index) "▶ " else "") + song.name,
                    { vm.player.select(index); onDismiss() }, summary = song.artists, enabled = editable, chevron = false)
            }
        }
    }
}
