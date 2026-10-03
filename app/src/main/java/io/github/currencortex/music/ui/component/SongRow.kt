package io.github.currencortex.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.currencortex.music.data.song.Song
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.squircle.squircleClip

data class SongRowUi(val song: Song, val title: String = song.name, val subtitle: String = listOf(song.artists, song.album).filter { it.isNotBlank() }.joinToString(" · "))
@Composable fun MusicCover(url: String, modifier: Modifier = Modifier, pixels: Int = 160) {
    AsyncImage(ImageRequest.Builder(LocalContext.current).data(url).size(pixels).build(),
        contentDescription = "歌曲封面", contentScale = ContentScale.Crop,
        modifier = modifier.squircleClip(22.dp))
}
@Composable fun SongRow(row: SongRowUi, onPlay: () -> Unit, onNext: () -> Unit, onQueue: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            MusicCover(row.song.cover, Modifier.size(52.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.song.mv != 0L) Text("MV")
            }
            TextButton("⋯", onClick = { menu = !menu }, modifier = Modifier.testTag("song_menu_${row.song.id}"))
        }
        if (menu) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton("立即播放", onClick = { menu = false; onPlay() })
            TextButton("下一首播放", onClick = { menu = false; onNext() })
            TextButton("加入队列", onClick = { menu = false; onQueue() })
        }
    }
}
