package io.github.currencortex.music.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.ui.util.showMusicMessage
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/** Same destination picker as a single song; no new queue or search screen. */
@Composable internal fun NativeQueueCollectionDialog(vm: PlayerViewModel, songs: List<Song>, show: Boolean, onDismiss: () -> Unit) {
    NeteaseSongsCollectionDialog(songs, show, onDismiss, vm::queuePlaylists, vm::collectQueue)
}

@Composable internal fun NeteaseSongsCollectionDialog(songs: List<Song>, show: Boolean, onDismiss: () -> Unit,
    loadPlaylists: suspend () -> List<Playlist>, collect: suspend (Long, List<Song>) -> Unit) {
    var lists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(show, retry) {
        if (!show) return@LaunchedEffect
        busy = true; error = null; lists = emptyList()
        when (val result = appResult { loadPlaylists() }) {
            is AppResult.Success -> lists = result.value
            is AppResult.Failure -> error = result.kind.message
        }
        busy = false
    }
    MusicOverlayDialog(show = show, title = "收藏 ${songs.size} 首到歌单", onDismissRequest = { if (!busy) onDismiss() }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy) Text("正在处理…")
            error?.let { Text(it); MusicDialogButton("重试", { retry++ }, enabled = !busy) }
            if (!busy && lists.isEmpty() && error == null) Text("没有可编辑的网易云歌单，请先创建歌单")
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(lists, key = { it.id }) { playlist ->
                    MusicDestinationRow(playlist.name, {
                        if (!busy) { busy = true; error = null; scope.launch {
                            when (val result = appResult { collect(playlist.id, songs) }) {
                                is AppResult.Success -> { context.showMusicMessage("已收藏到${playlist.name}"); onDismiss() }
                                is AppResult.Failure -> error = result.kind.message
                            }
                            busy = false
                        } }
                    }, Modifier.testTag("queue_collect_playlist_${playlist.id}"), summary = "网易云音乐", enabled = !busy, chevron = false)
                }
            }
            MusicDialogButton("取消", onDismiss, enabled = !busy)
        }
    }
}
