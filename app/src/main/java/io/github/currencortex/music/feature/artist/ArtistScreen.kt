package io.github.currencortex.music.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currencortex.music.core.media.PlaybackMode
import io.github.currencortex.music.core.media.PlayerMode
import io.github.currencortex.music.data.library.Album
import io.github.currencortex.music.data.library.Artist
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.LibrarySongActions
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.ui.component.*
import io.github.currencortex.music.ui.util.collectAsPageState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable private fun ArtistHero(artist: Artist?) {
    Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(28.dp)).testTag("artist_hero")) {
        if (artist == null) MusicPlaceholder(Modifier.matchParentSize())
        else {
            MusicCover(artist.cover, Modifier.matchParentSize().clearAndSetSemantics { contentDescription = "${artist.name} 的艺人封面" },
                900, cornerRadius = 28.dp)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(artist.name, Modifier.testTag("artist_name"), fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (artist.description.isNotBlank()) Text(artist.description, fontSize = 13.sp, color = Color.White.copy(alpha = .8f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun ArtistAlbumCard(album: Album, modifier: Modifier, navigate: (String) -> Unit) {
    Column(modifier.clickable(enabled = album.id > 0, role = Role.Button) { navigate("lib/album/${album.id}") }
        .testTag("catalog_entry_${album.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MusicCover(album.cover, Modifier.fillMaxWidth().aspectRatio(1f), 480)
        Text(album.name, Modifier.heightIn(min = 38.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        val date = remember(album.publishTime) {
            if (album.publishTime > 0) Instant.ofEpochMilli(album.publishTime).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) else ""
        }
        val caption = listOf(date, if (album.size > 0) "${album.size} 首" else "").filter(String::isNotBlank).joinToString(" · ")
        if (caption.isNotEmpty()) Text(caption, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
    }
}

@Composable fun ArtistScreen(vm: ArtistViewModel, library: LibraryViewModel, onBack: () -> Unit,
    navigate: (String) -> Unit, play: (List<Song>, Int) -> Unit) {
    val state by vm.state.collectAsPageState()
    val playback by vm.container.playerController.state.collectAsPageState()
    val room = LocalRoomSongRequest.current
    val colors = MiuixTheme.colorScheme
    var tab by rememberSaveable(vm.id) { mutableStateOf("songs") }
    LaunchedEffect(state.songs) { library.refreshStatuses(state.songs) }
    PreloadMusicCovers(if (tab == "albums") state.albums.take(10).map { it.cover } else state.songs.take(12).map { it.cover })
    MusicPullToRefresh(state.loading, vm::reload, Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(Modifier.fillMaxSize().testTag("artist_home"), contentPadding = musicScrollPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onBack).testTag("artist_back"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.onSurface)
                    Text("艺人主页", fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
                ArtistHero(state.artist)
            }
            state.artist?.let { artist -> item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${maxOf(artist.songTotal, state.songs.size)} 首歌曲 · ${maxOf(artist.albumTotal, state.albums.size)} 张专辑",
                        Modifier.weight(1f), fontSize = 12.sp, color = colors.onSurface.copy(alpha = .6f))
                    MusicTextAction("艺人简介 ›", { tab = "about" }, Modifier.testTag("artist_open_about"))
                }
            } }
            state.error?.let { error -> item {
                Text(error, fontSize = 14.sp)
                MusicTextAction("重试艺人主页", vm::reload, Modifier.testTag("artist_retry"), enabled = !state.loading)
            } }
            item { MusicCategoryTabs(listOf("songs" to "歌曲", "albums" to "专辑", "about" to "简介"), tab, { tab = it }, "artist_tab") }
            if (state.loading && state.artist == null && tab != "about") item { LoadingSongList(4) }
            when (tab) {
                "songs" -> {
                    if (state.songs.isNotEmpty()) item {
                        val enabled = room == null && playback.mode == PlayerMode.LOCAL
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button) {
                            vm.container.playerController.setMode(PlaybackMode.LIST); play(state.songs, 0)
                        }.testTag("play_library_all"), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp), tint = colors.primary.copy(alpha = if (enabled) 1f else .35f))
                            Text("播放全部", Modifier.padding(start = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                color = colors.onSurface.copy(alpha = if (enabled) 1f else .4f))
                        }
                    }
                    if (room != null) item { Text("点击歌曲，为当前房间点歌", fontSize = 13.sp) }
                    itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
                        Box(Modifier.testTag("artist_song_${song.id}")) {
                            SongRow(SongRowUi(song), { play(state.songs, index) }, { vm.container.playerController.add(song, true) },
                                { vm.container.playerController.add(song) }) { LibrarySongActions(library, song, navigate) }
                        }
                    }
                    if (state.songsLoading) item { LoadingSongList(2) }
                    state.songsError?.let { error -> item {
                        Text(error, fontSize = 13.sp)
                        MusicTextAction("重试歌曲", vm::moreSongs, Modifier.testTag("artist_songs_retry"))
                    } }
                    if (state.moreSongs && state.songsError == null) item {
                        MusicTextAction("加载更多歌曲", vm::moreSongs, Modifier.fillMaxWidth().testTag("artist_more_songs"), enabled = !state.songsLoading)
                    }
                    if (!state.loading && state.songs.isEmpty() && state.error == null) item { Text("暂无歌曲", fontSize = 14.sp) }
                }
                "albums" -> {
                    items(state.albums.chunked(2), key = { it.first().id }) { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            pair.forEach { ArtistAlbumCard(it, Modifier.weight(1f), navigate) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (state.albumsLoading) item { LoadingSongList(2) }
                    state.albumsError?.let { error -> item {
                        Text(error, fontSize = 13.sp)
                        MusicTextAction("重试专辑", vm::moreAlbums, Modifier.testTag("artist_albums_retry"))
                    } }
                    if (state.moreAlbums && state.albumsError == null) item {
                        MusicTextAction("更多专辑", vm::moreAlbums, Modifier.fillMaxWidth().testTag("artist_more_albums"), enabled = !state.albumsLoading)
                    }
                    if (!state.loading && state.albums.isEmpty() && state.error == null) item { Text("暂无专辑", fontSize = 14.sp) }
                }
                "about" -> {
                    if (state.biographyLoading && state.biography == null) item { LoadingSongList(3) }
                    state.biographyError?.let { error -> item {
                        Text("简介加载失败：$error", fontSize = 13.sp)
                        MusicTextAction("重试简介", { vm.loadBiography() }, Modifier.testTag("artist_bio_retry"), enabled = !state.biographyLoading)
                    } }
                    state.biography?.briefDesc?.takeIf(String::isNotBlank)?.let { description -> item {
                        Text(description, fontSize = 15.sp, lineHeight = 25.sp, color = colors.onSurface.copy(alpha = .8f))
                    } }
                    items(state.biography?.introduction.orEmpty().filter { it.text.isNotBlank() }) { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (section.title.isNotBlank()) Text(section.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(section.text, fontSize = 15.sp, lineHeight = 25.sp, color = colors.onSurface.copy(alpha = .8f))
                        }
                    }
                    if (!state.biographyLoading && state.biographyError == null && state.biography?.briefDesc.isNullOrBlank() &&
                        state.biography?.introduction.orEmpty().none { it.text.isNotBlank() }) item { Text("暂无艺人简介", fontSize = 14.sp) }
                }
            }
        }
    }
}
