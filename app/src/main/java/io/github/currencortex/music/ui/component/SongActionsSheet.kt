package io.github.currencortex.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.data.song.Song
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class SongMenu(val song: Song, val play: () -> Unit, val next: () -> Unit, val queue: () -> Unit,
    val roomRequest: Boolean = false, val extra: (@Composable () -> Unit)? = null, val transport: Boolean = true)
data class RoomSongRequest(val request: (Song) -> Unit, val pending: (Long) -> Boolean)
val LocalSongMenu = staticCompositionLocalOf<((SongMenu) -> Unit)?> { null }
val LocalDismissSongMenu = staticCompositionLocalOf<() -> Unit> { {} }
val LocalRoomSongRequest = staticCompositionLocalOf<RoomSongRequest?> { null }

@Composable fun SongActionsSheet(menu: SongMenu, onDownload: ((Song) -> Unit)? = null, onDismiss: () -> Unit) {
    MusicDialog(menu.song.name, onDismiss) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).testTag("song_actions_sheet")) {
            Text(menu.song.artists, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
            if (menu.transport) MusicDestinationRow(if (menu.roomRequest) "为房间点歌" else "立即播放", { onDismiss(); menu.play() }, chevron = false)
            if (menu.transport && !menu.roomRequest) {
                MusicDestinationRow("下一首播放", { onDismiss(); menu.next() }, chevron = false)
                MusicDestinationRow("加入队列", { onDismiss(); menu.queue() }, chevron = false)
            }
            if (!menu.song.video && onDownload != null) MusicDestinationRow("下载歌曲", {
                onDismiss(); onDownload(menu.song)
            }, modifier = Modifier.testTag("song_download"), summary = "保存歌曲信息、封面与歌词", chevron = false)
            CompositionLocalProvider(LocalDismissSongMenu provides onDismiss) { menu.extra?.invoke() }
        }
    }
}
