package io.github.currencortex.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.currencortex.music.data.song.Song
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.squircle.squircleClip
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class SongRowUi(val song: Song, val title: String = song.name, val subtitle: String = listOf(song.artists, song.album).filter { it.isNotBlank() }.joinToString(" · "))
fun coverRequestUrl(url: String, pixels: Int): String {
    val parsed = url.toHttpUrlOrNull() ?: return url
    if (parsed.host != "music.126.net" && !parsed.host.endsWith(".music.126.net")) return url
    val size = pixels.coerceIn(32, 1200)
    return parsed.newBuilder().scheme("https").setQueryParameter("param", "${size}y$size").build().toString()
}
@Composable fun MusicCover(url: String, modifier: Modifier = Modifier, pixels: Int = 160,
    cornerRadius: androidx.compose.ui.unit.Dp = 22.dp) {
    val context = LocalContext.current
    var loading by remember(url, pixels) { mutableStateOf(url.isNotBlank()) }
    var failed by remember(url, pixels) { mutableStateOf(url.isBlank()) }
    Box(modifier.squircleClip(cornerRadius)) {
    if (loading) MusicPlaceholder(Modifier.matchParentSize())
    if (failed) Box(Modifier.matchParentSize().background(MiuixTheme.colorScheme.onSurface.copy(alpha = .06f)), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("♪", fontSize = 20.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .3f)) }
    AsyncImage(remember(context, url, pixels) { ImageRequest.Builder(context).data(coverRequestUrl(url, pixels)).size(pixels).build() },
        contentDescription = "歌曲封面", contentScale = ContentScale.Crop,
        onLoading = { loading = true; failed = false }, onSuccess = { loading = false; failed = false }, onError = { loading = false; failed = true },
        modifier = Modifier.matchParentSize())
    }
}
@Composable fun SongRow(row: SongRowUi, onPlay: () -> Unit, onNext: () -> Unit, onQueue: () -> Unit,
    extraActions: (@Composable () -> Unit)? = null) {
    var menu by remember { mutableStateOf<SongMenu?>(null) }
    val host = LocalSongMenu.current
    val request = LocalRoomSongRequest.current
    val primary = { if (request != null) request.request(row.song) else onPlay() }
    Row(Modifier.fillMaxWidth().clickable(enabled = request?.pending?.invoke(row.song.id) != true, onClick = primary).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            MusicCover(row.song.cover, Modifier.size(52.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(row.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (request?.pending?.invoke(row.song.id) == true) "正在提交点歌…" else row.subtitle,
                    fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.song.mv != 0L) Text("MV", fontSize = 10.sp, color = MiuixTheme.colorScheme.primary)
            }
            MusicTextAction("⋯", onClick = {
                val value = SongMenu(row.song, primary, onNext, onQueue, request != null, extraActions)
                if (host == null) menu = value else host(value)
            }, modifier = Modifier.testTag("song_menu_${row.song.id}"), enabled = request?.pending?.invoke(row.song.id) != true)
    }
    menu?.let { SongActionsSheet(it) { menu = null } }
}
