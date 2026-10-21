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
    private val browser = NavidromeBrowser()
    val state = browser.state
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val actionError = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")
    private var load: Job? = null
    private var libraryRefreshPending = false
    init { viewModelScope.launch {
        repository.ready.await()
        launch {
            account.map { it.revision to it.configured }.distinctUntilChanged().collect {
                load?.cancel(); libraryRefreshPending = false; browser.clear(); query.value = ""
                if (it.second) refresh()
            }
        }
        launch {
            repository.revision.drop(1).collect {
                browser.invalidateLibraries()
                if (account.value.configured && state.value.page.kind in setOf("likes", "playlists", "playlist")) {
                    if (state.value.loading) libraryRefreshPending = true else refresh()
                }
            }
        }
    } }
    fun connect(server: String, username: String, password: String, done: () -> Unit) {
        val error = when {
            runCatching { io.github.currencortex.music.core.navidrome.normalizeNavidromeServer(server) }.isFailure -> "请输入完整的 http 或 https 服务器地址"
            username.isBlank() -> "请输入用户名"
            password.isEmpty() -> "请输入密码"
            else -> null
        }
        if (error != null) { actionError.value = error; return }
        action("已连接 Navidrome", { repository.connect(server, username, password) }) { if (it) done() }
    }
    fun disconnect(done: (Boolean) -> Unit = {}) = action("已断开 Navidrome", { repository.disconnect() }, done)
    fun home(value: Boolean) = action(null, { repository.home(value) })
    private fun show(page: NavidromePage, root: Boolean = false, replace: Boolean = false) {
        load?.cancel(); libraryRefreshPending = false; browser.show(page, root, replace)
        query.value = if (page.kind == "search") page.id else ""
        if (browser.needsLoad()) refresh()
    }
    fun tab(kind: String) { if (state.value.page.kind != kind || nested) show(NavidromePage(kind), root = true) }
    fun open(kind: String, entry: NavidromeEntry) = show(NavidromePage(kind, entry.id, entry.name, entry.cover, entry.subtitle, entry.owned))
    fun back(): Boolean {
        load?.cancel(); libraryRefreshPending = false
        if (!browser.back()) return false
        query.value = if (state.value.page.kind == "search") state.value.page.id else ""
        if (browser.needsLoad()) refresh()
        return true
    }
    val nested get() = browser.nested
    fun position(key: String) = browser.position(key)
    fun savePosition(key: String, index: Int, offset: Int) = browser.position(key, NavidromeScroll(index, offset))
    fun search(input: String) {
        val value = input.trim(); if (value.isEmpty()) return
        if (state.value.page.kind == "search" && state.value.page.id == value) return
        show(NavidromePage("search", value, "搜索结果"), replace = state.value.page.kind == "search")
    }
    fun refresh(more: Boolean = false) {
        val old = state.value
        if (old.loading || more && !old.more || !account.value.configured) return
        load?.cancel()
        val expected = repository.session(); val key = old.page.key
        state.value = old.copy(loading = true, error = null)
        load = viewModelScope.launch {
            val result = appResult {
                when (old.page.kind) {
                    "albums" -> {
                        val entries = mutableListOf<NavidromeEntry>()
                        var offset = if (more) old.nextOffset else 0
                        var hasMore = false
                        do {
                            val page = repository.albums(offset); entries.addAll(page); offset += page.size; hasMore = page.size == 40
                        } while (!more && hasMore && offset < maxOf(40, old.nextOffset))
                        old.copy(entries = ((if (more) old.entries else emptyList()) + entries).distinctBy { it.id }, more = hasMore, nextOffset = offset)
                    }
                    "artists" -> old.copy(entries = repository.artists())
                    "playlists" -> old.copy(entries = repository.playlists())
                    "likes" -> old.copy(songs = repository.starred())
                    "artist" -> old.copy(entries = repository.artistAlbums(old.page.id))
                    "album" -> old.copy(songs = repository.album(old.page.id))
                    "playlist" -> old.copy(songs = repository.playlist(old.page.id))
                    "search" -> {
                        val songs = mutableListOf<Song>()
                        var offset = if (more) old.nextOffset else 0
                        var first: NavidromeSearch? = null
                        var hasMore = false
                        do {
                            val page = repository.search(old.page.id, offset)
                            if (first == null) first = page
                            songs.addAll(page.songs); offset += page.songs.size; hasMore = page.more
                        } while (!more && hasMore && offset < maxOf(40, old.nextOffset))
                        old.copy(songs = ((if (more) old.songs else emptyList()) + songs).distinctBy(Song::id),
                            albums = if (more) old.albums else first?.albums.orEmpty(), artists = if (more) old.artists else first?.artists.orEmpty(),
                            more = hasMore, nextOffset = offset)
                    }
                    else -> old
                }
            }
            when (result) {
                is AppResult.Success -> if (expected == repository.session()) browser.finish(key, result.value.copy(page = state.value.page))
                is AppResult.Failure -> if (expected == repository.session() && state.value.page.key == key) state.value = old.copy(loading = false, error = result.message)
            }
            if (libraryRefreshPending && expected == repository.session() && state.value.page.key == key) {
                libraryRefreshPending = false; load = null; refresh()
            }
        }
    }
    fun enqueue(song: Song, next: Boolean = false) {
        if (container.playerController.state.value.mode != io.github.currencortex.music.core.media.PlayerMode.LOCAL) {
            actionError.value = "请先退出一起听或结束投屏，再播放私有曲库"; return
        }
        container.playerController.add(song, next); message.value = if (next) "已设为下一首" else "已加入播放队列"
    }
    fun create(name: String, done: (Boolean) -> Unit = {}) = action("歌单已创建", { repository.create(name) }, done)
    fun rename(page: NavidromePage, name: String, done: (Boolean) -> Unit = {}) = action("歌单已重命名", {
        repository.rename(page.id, name); browser.rename(page, name.trim())
    }, done)
    fun delete(page: NavidromePage, done: (Boolean) -> Unit = {}) = action("歌单已删除", {
        repository.delete(page.id); browser.discard(page); refresh()
    }, done)
    fun removal(song: Song, index: Int): NavidromeRemoval = NavidromeRemoval(state.value.page, song,
        state.value.songs.take(index).count { it.navidrome?.id == song.navidrome?.id })
    fun remove(target: NavidromeRemoval, done: (Boolean) -> Unit = {}) = action("歌曲已移出歌单", {
        val expected = repository.session()
        val fresh = repository.playlist(target.playlist.id)
        val index = fresh.indices.filter { fresh[it].navidrome?.id == target.song.navidrome?.id }.getOrNull(target.occurrence)
            ?: throw ApiException(ErrorKind.NotFound)
        if (expected != repository.session()) throw io.github.currencortex.music.core.navidrome.navidromeUnauthorized()
        repository.remove(target.playlist.id, index)
    }, done)
    fun clearError() { actionError.value = null }
    private fun action(success: String?, block: suspend () -> Unit, done: (Boolean) -> Unit = {}) {
        if (busy.value) return
        busy.value = true; message.value = null; actionError.value = null
        viewModelScope.launch {
            try { when (val result = appResult { block() }) {
                is AppResult.Success -> { message.value = success; done(true) }
                is AppResult.Failure -> { actionError.value = result.message; done(false) }
            } } finally { busy.value = false }
        }
    }
}
