package io.github.currencortex.music.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.currencortex.music.core.media.PlayerController
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.ui.component.*
import top.yukonga.miuix.kmp.basic.*

@Composable fun SearchScreen(vm: SearchViewModel, player: PlayerController,
    actions: (@Composable (Song) -> Unit)? = null, roomName: String? = null, onBack: (() -> Unit)? = null,
    onPlay: (List<Song>, Int) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val bottomInset = LocalMusicBottomInset.current
    PreloadMusicCovers(state.songs.take(12).map { it.cover })
    fun submit(keyword: String = state.query) {
        focus.clearFocus()
        keyboard?.hide()
        vm.search(keyword)
    }
    LazyColumn(Modifier.fillMaxSize().testTag("search_screen"), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            onBack?.let { MusicTextAction("返回房间", it, Modifier.testTag("room_search_back")) }
            Text(if (roomName != null) "为房间点歌" else "搜索", fontSize = 28.sp)
            roomName?.let { Text("$it · 点击歌曲提交点歌", fontSize = 13.sp) }
        }
        item {
            TextField(state.query, vm::input, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("search_input"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }))
            TextButton("搜索歌曲", onClick = { submit() }, enabled = !state.loading, modifier = Modifier.testTag("submit_search"))
        }
        if (!state.searched) {
            item { Row { Text("搜索历史", Modifier.weight(1f)); TextButton("清空", onClick = { vm.clearHistory() }) } }
            itemsIndexed(history, key = { _, h -> h.keyword }) { _, h ->
                Row { TextButton(h.keyword, onClick = { submit(h.keyword) }, modifier = Modifier.weight(1f)); TextButton("删除", onClick = { vm.deleteHistory(h.keyword) }) }
            }
        }
        state.error?.let { error -> item { Text(error); TextButton("重试", onClick = { vm.search() }) } }
        if (state.loading) item { Text("正在搜索…") }
        if (state.loading && state.songs.isEmpty()) item { LoadingSongList(4) }
        if (state.searched && !state.loading && state.error == null) item { Text("找到 ${state.total} 首歌曲") }
        itemsIndexed(state.songs, key = { index, song -> "${song.id}-$index" }) { index, song ->
            SongRow(SongRowUi(song), { onPlay(state.songs, index) }, { player.add(song, true) }, { player.add(song) }) {
                actions?.invoke(song)
            }
        }
        if (state.more) item { TextButton("加载更多", onClick = { vm.search(more = true) }, enabled = !state.loading) }
    }
}
