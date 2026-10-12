package io.github.currencortex.music.feature.navidrome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.navidrome.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class NavidromeViewModel(val container: AppContainer) : ViewModel() {
    val repository = container.navidrome
    val account = repository.state
    val state = MutableStateFlow(NavidromeUi())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private val history = mutableListOf<Pair<NavidromePage, Boolean>>()
    private var load: Job? = null
    private var previousRevision = ""
    private var playlistOwner = false
    init { viewModelScope.launch {
        repository.ready.await()
        combine(account, repository.revision) { a, _ -> a }.collect { a ->
            if (previousRevision != a.revision) {
                previousRevision = a.revision; history.clear(); load?.cancel(); state.value = NavidromeUi()
            }
            if (a.configured) refresh()
        }
    } }
    fun connect(server: String, username: String, password: String, done: () -> Unit) = action("已连接 Navidrome") {
        repository.connect(server, username, password); done()
    }
    fun disconnect() = action("已断开 Navidrome") { repository.disconnect() }
    fun home(value: Boolean) = action(null) { repository.home(value) }
    fun tab(kind: String) { history.clear(); playlistOwner = false; state.value = NavidromeUi(NavidromePage(kind)); refresh() }
    fun open(kind: String, entry: NavidromeEntry) {
        history.add(state.value.page to state.value.owned); playlistOwner = entry.owned
        state.value = NavidromeUi(NavidromePage(kind, entry.id, entry.name), owned = entry.owned); refresh()
    }
    fun back(): Boolean {
        if (history.isEmpty()) return false
        val (page, owned) = history.removeAt(history.lastIndex)
        playlistOwner = owned; state.value = NavidromeUi(page, owned = owned); refresh(); return true
    }
    val nested get() = history.isNotEmpty()
    fun search(query: String) {
        if (query.isBlank()) return
        history.add(state.value.page to state.value.owned); state.value = NavidromeUi(NavidromePage("search", query.trim(), "搜索结果")); refresh()
    }
    fun refresh(more: Boolean = false) {
        if (more && (state.value.loading || !state.value.more)) return
        load?.cancel()
        val old = state.value; val expected = repository.session()
        state.value = old.copy(loading = true, error = null)
        load = viewModelScope.launch {
            when (val result = appResult {
                when (old.page.kind) {
                    "albums" -> repository.albums(if (more) old.entries.size else 0).let { page ->
                        old.copy(entries = if (more) (old.entries + page).distinctBy { it.id } else page, more = page.size == 40) }
                    "artists" -> old.copy(entries = repository.artists())
                    "playlists" -> old.copy(entries = repository.playlists())
                    "likes" -> old.copy(songs = repository.starred())
                    "artist" -> old.copy(entries = repository.artistAlbums(old.page.id))
                    "album" -> old.copy(songs = repository.album(old.page.id))
                    "playlist" -> old.copy(songs = repository.playlist(old.page.id), owned = playlistOwner)
                    "search" -> repository.search(old.page.id, if (more) old.songs.size else 0).let {
                        old.copy(songs = if (more) (old.songs + it.songs).distinctBy(Song::id) else it.songs,
                            albums = if (more) old.albums else it.albums, artists = if (more) old.artists else it.artists, more = it.more) }
                    else -> old
                }
            }) {
                is AppResult.Success -> if (expected == repository.session()) state.value = result.value.copy(loading = false, error = null)
                is AppResult.Failure -> if (expected == repository.session()) state.value = old.copy(loading = false, error = result.kind.message)
            }
        }
    }
    fun enqueue(song: Song, next: Boolean = false) {
        if (container.playerController.state.value.mode != io.github.currencortex.music.core.media.PlayerMode.LOCAL) {
            message.value = "请先退出一起听或结束投屏，再播放私有曲库"; return
        }
        container.playerController.add(song, next)
    }
    fun create(name: String) = action("歌单已创建") { repository.create(name) }
    fun rename(name: String) = action("歌单已重命名") { repository.rename(state.value.page.id, name) }
    fun delete() = action("歌单已删除") { repository.delete(state.value.page.id); back() }
    fun remove(index: Int) = action("歌曲已移出歌单") { repository.remove(state.value.page.id, index) }
    private fun action(success: String?, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; message.value = null
        viewModelScope.launch {
            try { when (val result = appResult { block() }) {
                is AppResult.Success -> message.value = success
                is AppResult.Failure -> message.value = result.kind.message
            } } finally { busy.value = false }
        }
    }
}
