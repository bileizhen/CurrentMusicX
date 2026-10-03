package io.github.currencortex.music.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.feature.library.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*

@Composable fun MusicHomeScreen(vm: LibraryViewModel, onSearch: () -> Unit, onSettings: () -> Unit,
    navigate: (String) -> Unit, play: (List<Song>, Int) -> Unit) {
    val account by vm.container.accountRepository.state.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    PullToRefresh(home.loading, vm::refresh, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.testTag("music_home"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row { Text("CurrentMusic", fontSize = 30.sp, modifier = Modifier.weight(1f)); TextButton("设置", onClick = onSettings) }
                Text(account.account?.let { "欢迎，${it.nickname}" } ?: "音乐，从这里开始", fontSize = 22.sp)
                TextButton("搜索音乐", onClick = onSearch)
                LibraryLinks(navigate)
            }
            item { TextButton("刷新推荐", onClick = vm::refresh, enabled = !home.loading, modifier = Modifier.testTag("refresh_home")) }
            if (account.loading) item { Text("正在恢复登录…") }
            else if (account.account == null) item { Text("登录后查看每日推荐、最近播放与歌单") }
            if (home.loading) item { Text("正在加载音乐库…") }
            fun LazyListScope.section(title: String, songs: List<Song>, route: String, error: String? = null) {
                item { Row { Text(title, fontSize = 23.sp, modifier = Modifier.weight(1f)); TextButton("查看全部", onClick = { navigate(route) }) } }
                if (error != null) item { Text(error) }
                else if (!home.loading && songs.isEmpty()) item { Text("暂无内容") }
                itemsIndexed(songs.take(5), key = { _, song -> "$title-${song.id}" }) { index, song ->
                    SongRow(SongRowUi(song), { play(songs, index) }, { vm.container.playerController.add(song, true) }, { vm.container.playerController.add(song) }) {
                        LibrarySongActions(vm, song, navigate)
                    }
                }
            }
            section("每日推荐", home.daily, "lib/daily", home.errors["每日推荐"])
            section("猜你喜欢", home.forYou, "lib/foryou", home.errors["每日推荐"])
            section("最近播放", home.recent, "lib/recent", home.errors["最近播放"])
            item { Row { Text("我的歌单", fontSize = 23.sp, modifier = Modifier.weight(1f)); TextButton("管理", onClick = { navigate("lib/playlists") }) } }
            home.errors["我的歌单"]?.let { error -> item { Text(error) } }
            items(home.playlists.take(6), key = { "playlist-${it.id}" }) { playlist -> PlaylistCard(playlist) { navigate("lib/playlist/${playlist.id}") } }
            account.error?.let { error -> item { Text(error) } }
        }
    }
}
