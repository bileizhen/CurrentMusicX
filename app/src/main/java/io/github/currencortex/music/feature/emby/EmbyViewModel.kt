package io.github.currencortex.music.feature.emby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.emby.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class EmbyRoute(val kind: String = "albums", val id: String = "", val title: String = "Emby", val query: String = "")
data class EmbyUi(val route: EmbyRoute = EmbyRoute(), val page: EmbyPage = EmbyPage(),
    val loading: Boolean = false, val loaded: Boolean = false, val error: String? = null, val index: Int = 0, val offset: Int = 0)

class EmbyViewModel(val container: AppContainer) : ViewModel() {
    val repository = container.emby
    val account = repository.state
    val state = MutableStateFlow(EmbyUi())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private val history = mutableListOf<EmbyRoute>()
    private val cache = linkedMapOf<EmbyRoute, EmbyUi>()
    private var load: Job? = null
    private var generation = 0L
    val nested get() = history.isNotEmpty()
    init { viewModelScope.launch {
        repository.ready.await()
        account.map { it.revision }.distinctUntilChanged().collect {
            generation++; load?.cancel(); cache.clear(); history.clear(); state.value = EmbyUi()
            if (account.value.configured) refresh()
        }
    } }
    fun connect(server: String, username: String, password: String, done: () -> Unit) = action {
        repository.connect(server, username, password); done()
    }
    fun disconnect() = action { repository.disconnect() }
    private fun action(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch { try {
            val result = appResult { block() }
            if (result is AppResult.Failure) error.value = result.message
        } finally { busy.value = false } }
    }
    private fun show(route: EmbyRoute, root: Boolean = false, back: Boolean = false) {
        val old = state.value
        if (old.loaded) cache[old.route] = old.copy(loading = false)
        while (cache.size > 12) cache.remove(cache.keys.first())
        generation++; load?.cancel()
        if (root) history.clear() else if (!back) history.add(old.route)
        state.value = cache[route] ?: EmbyUi(route)
        if (!state.value.loaded) refresh()
    }
    fun tab(kind: String) { if (state.value.route != EmbyRoute(kind)) show(EmbyRoute(kind), root = true) }
    fun open(entry: EmbyEntry) = show(EmbyRoute(when (entry.type) {
        "MusicAlbum" -> "album"; "MusicArtist" -> "artist"; else -> "playlist"
    }, entry.id, entry.name))
    fun back(): Boolean {
        if (history.isEmpty()) return false
        show(history.removeAt(history.lastIndex), back = true); return true
    }
    fun search(query: String) {
        val value = query.trim(); if (value.isBlank()) return
        show(EmbyRoute("search", title = "搜索歌曲", query = value), back = state.value.route.kind == "search")
    }
    fun position(route: EmbyRoute, index: Int, offset: Int) {
        if (state.value.route == route) state.value = state.value.copy(index = index, offset = offset)
        else cache[route]?.let { cache[route] = it.copy(index = index, offset = offset) }
    }
    fun refresh(more: Boolean = false) {
        val old = state.value
        if (!account.value.configured || old.loading || more && !old.page.more) return
        val epoch = ++generation
        val expected = repository.session()
        state.value = old.copy(loading = true, error = null)
        load = viewModelScope.launch {
            val result = appResult { repository.browse(old.route.kind, old.route.id, old.route.query, if (more) old.page.nextOffset else 0) }
            if (epoch != generation || expected != repository.session()) return@launch
            state.value = when (result) {
                is AppResult.Success -> old.copy(loaded = true, loading = false, error = null, page = result.value.let { page ->
                    if (!more) page else page.copy(entries = old.page.entries + page.entries, songs = old.page.songs + page.songs)
                })
                is AppResult.Failure -> old.copy(loading = false, error = result.message)
            }
        }
    }
    fun toggleLike(song: Song) = action {
        repository.toggleLike(song)
        cache.keys.filter { it.kind == "likes" }.toList().forEach(cache::remove)
        if (state.value.route.kind == "likes") refresh()
    }
    fun enqueue(song: Song, next: Boolean) {
        if (container.playerController.state.value.mode != io.github.currencortex.music.core.media.PlayerMode.LOCAL) {
            error.value = "请先退出一起听或结束投屏，再播放 Emby 曲库"; return
        }
        container.playerController.add(song, next)
    }
}
