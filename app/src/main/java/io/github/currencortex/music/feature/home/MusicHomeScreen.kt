package io.github.currencortex.music.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.feature.library.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun MusicHomeScreen(vm: LibraryViewModel, onSearch: () -> Unit, onSettings: () -> Unit,
    navigate: (String) -> Unit, play: (List<Song>, Int) -> Unit) {
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val request = LocalRoomSongRequest.current
    val bottomInset = LocalMusicBottomInset.current
    val waiting = account.loading || home.loading
    PreloadMusicCovers(home.daily.take(3).map { it.cover })
    PreloadMusicCovers(home.forYou.take(8).map { it.cover }, 320)
    PreloadMusicCovers(home.playlists.take(4).map { it.cover }, 320)
    MusicPullToRefresh(home.loading, vm::refresh, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("music_home"), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CurrentMusic", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                        Text(account.account?.let { "欢迎，${it.nickname}" } ?: "音乐，从这里开始", fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                    }
                    MusicTextAction("设置", onSettings)
                }
            }
            item { Card(Modifier.fillMaxWidth(), onClick = onSearch, showIndication = true) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = "搜索", modifier = Modifier.size(20.dp), tint = MiuixTheme.colorScheme.primary)
                    Text("搜索歌曲、歌手", Modifier.padding(start = 12.dp), fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
            } }
            item { LibraryLinks(navigate) }
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (request == null) "一起听" else "正在一起听", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                MusicTextAction(if (request == null) "进入房间 ›" else "回到房间 ›", { navigate("room/list") }, Modifier.testTag("open_rooms"))
            } }
            if (account.loading) item { Text("正在恢复登录…", fontSize = 13.sp) }
            else if (account.account == null) item { Text("登录后查看每日推荐、最近播放与歌单", fontSize = 13.sp) }
            if (home.loading) item { Text("正在加载音乐库…", fontSize = 13.sp) }
            item { MusicSectionHeader("每日推荐", "查看全部") { navigate("lib/daily") } }
            if (waiting && home.daily.isEmpty()) item { Box(Modifier.testTag("home_loading")) { LoadingSongList() } }
            home.errors["每日推荐"]?.let { item { Text(it, fontSize = 13.sp) } }
            if (home.daily.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth(), onClick = { navigate("lib/daily") }, showIndication = true) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MusicCover(home.daily.first().cover, Modifier.size(82.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("今天，听点喜欢的", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("${home.daily.size} 首每日推荐", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                            Text("打开推荐歌单 ›", fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (!account.loading && !home.loading && home.daily.isEmpty() && home.errors["每日推荐"] == null) item { Text("今日推荐还没有内容", fontSize = 13.sp) }
            itemsIndexed(home.daily.take(3), key = { _, song -> "daily-${song.id}" }) { index, song ->
                SongRow(SongRowUi(song), { play(home.daily, index) }, { vm.container.playerController.add(song, true) }, { vm.container.playerController.add(song) }) {
                    LibrarySongActions(vm, song, navigate)
                }
            }
            item { MusicSectionHeader("猜你喜欢", "查看全部") { navigate("lib/foryou") } }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(home.forYou.take(8), key = { _, song -> song.id }) { index, song ->
                    Card(Modifier.width(132.dp), onClick = { if (request != null) request.request(song) else play(home.forYou, index) }, showIndication = true) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            MusicCover(song.cover, Modifier.fillMaxWidth().aspectRatio(1f), pixels = 320)
                            Text(song.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artists, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                        }
                    }
                }
            } }
            if (!account.loading && !home.loading && home.forYou.isEmpty() && home.errors["每日推荐"] == null) item { Text("暂无推荐", fontSize = 13.sp) }
            item { MusicSectionHeader("最近播放", "查看全部") { navigate("lib/recent") } }
            home.errors["最近播放"]?.let { item { Text(it, fontSize = 13.sp) } }
            itemsIndexed(home.recent.take(3), key = { _, song -> "recent-${song.id}" }) { index, song ->
                SongRow(SongRowUi(song), { play(home.recent, index) }, { vm.container.playerController.add(song, true) }, { vm.container.playerController.add(song) }) {
                    LibrarySongActions(vm, song, navigate)
                }
            }
            if (!account.loading && !home.loading && home.recent.isEmpty() && home.errors["最近播放"] == null) item { Text("开始听歌后，在这里找到最近播放", fontSize = 13.sp) }
            item { MusicSectionHeader("我的歌单", "管理") { navigate("lib/playlists") } }
            home.errors["我的歌单"]?.let { item { Text(it, fontSize = 13.sp) } }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(home.playlists.take(8), key = { it.id }) { playlist ->
                    Card(Modifier.width(132.dp), onClick = { navigate("lib/playlist/${playlist.id}") }, showIndication = true) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            MusicCover(playlist.cover, Modifier.fillMaxWidth().aspectRatio(1f), pixels = 320)
                            Text(playlist.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${playlist.count} 首", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                        }
                    }
                }
            } }
            if (!account.loading && !home.loading && home.playlists.isEmpty() && home.errors["我的歌单"] == null) item { Text("创建歌单，整理喜欢的音乐", fontSize = 13.sp) }
            item { MusicTextAction("刷新推荐", vm::refresh, Modifier.testTag("refresh_home"), !home.loading) }
            account.error?.let { item { Text(it, fontSize = 13.sp) } }
        }
    }
}
