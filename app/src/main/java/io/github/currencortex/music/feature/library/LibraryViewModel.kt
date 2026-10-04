package io.github.currencortex.music.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.library.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LibraryHomeState(val loading: Boolean = false, val daily: List<Song> = emptyList(), val forYou: List<Song> = emptyList(),
    val recent: List<Song> = emptyList(), val playlists: List<Playlist> = emptyList(), val errors: Map<String, String> = emptyMap())

class LibraryViewModel(val container: AppContainer) : ViewModel() {
    val home = MutableStateFlow(LibraryHomeState())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val selectedSong = MutableStateFlow<Song?>(null)
    val availablePlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val statuses = container.libraryRepository.statuses
    private val refreshRequest = MutableStateFlow(0)
    private var prefetchJob: Job? = null
    init {
        viewModelScope.launch {
            container.sessionRestored.await()
            var identity: List<Any?>? = null
            combine(container.accountRepository.state.map { it.account?.id }.distinctUntilChanged(),
                container.musicSettings.state.map { it.server }.distinctUntilChanged(), container.libraryRepository.revision,
                refreshRequest) { id, server, revision, refresh -> listOf(id, server, revision, refresh) }
                .collectLatest { keys ->
                    prefetchJob?.cancel()
                    val sameAccount = identity == keys.take(2)
                    identity = keys.take(2)
                    home.value = if (sameAccount) home.value.copy(loading = true, errors = emptyMap()) else LibraryHomeState(loading = true)
                    loadHome()
                    if (container.accountRepository.token != null) prefetchJob = viewModelScope.launch {
                        launch { appResult { container.libraryRepository.likedSongs() } }
                        home.value.playlists.firstOrNull()?.let { playlist -> launch { appResult { container.libraryRepository.playlist(playlist.id) } } }
                    }
                }
        }
    }
    fun refresh() { container.libraryRepository.clearReads(); refreshRequest.update { it + 1 } }
    private suspend fun loadHome() = coroutineScope {
        if (container.accountRepository.token == null) { home.value = LibraryHomeState(); return@coroutineScope }
        suspend fun <T> section(name: String, request: suspend () -> T, update: (LibraryHomeState, T) -> LibraryHomeState) {
            when (val r = appResult { request() }) {
                is AppResult.Success -> home.update { update(it, r.value) }
                is AppResult.Failure -> home.update { it.copy(errors = it.errors + (name to r.kind.message)) }
            }
        }
        listOf(
            launch { section("每日推荐", container.libraryRepository::daily) { s, d -> s.copy(daily = d.songs, forYou = d.forYou) } },
            launch { section("最近播放", container.libraryRepository::recent) { s, d -> s.copy(recent = d) } },
            launch { section("我的歌单", container.libraryRepository::playlists) { s, d -> s.copy(playlists = d) } },
        ).joinAll()
        home.update { it.copy(loading = false) }
    }
    fun toggle(song: Song) = viewModelScope.launch {
        when (val result = container.libraryRepository.toggleLike(song)) {
            is AppResult.Failure -> message.value = result.kind.message
            is AppResult.Success -> message.value = "点赞已更新"
        }
    }
    fun choosePlaylist(song: Song) {
        if (busy.value) return
        action("歌单已加载") {
            availablePlaylists.value = container.libraryRepository.playlists().filter { it.editable(container.accountRepository.state.value.account?.id ?: 0) }
            selectedSong.value = song
        }
    }
    fun addToPlaylist(id: Long) {
        val song = selectedSong.value ?: return
        action("已加入歌单") { container.libraryRepository.add(id, listOf(song)); selectedSong.value = null }
    }
    fun create(name: String, description: String, done: () -> Unit) = action("歌单已创建") {
        container.libraryRepository.create(name, description); done()
    }
    fun action(success: String, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                when (val r = appResult { block() }) {
                    is AppResult.Success -> message.value = success
                    is AppResult.Failure -> message.value = r.kind.message
                }
            } finally { busy.value = false }
        }
    }
    fun refreshStatuses(songs: List<Song>) = viewModelScope.launch { appResult { container.libraryRepository.refreshStatus(songs.filterNot { it.video }.map { it.id }) } }
}

data class LibraryDetailState(val loading: Boolean = true, val title: String = "", val description: String = "", val cover: String = "",
    val songs: List<Song> = emptyList(), val playlist: Playlist? = null, val albums: List<Album> = emptyList(),
    val more: Boolean = false, val moreAlbums: Boolean = false, val mv: MvDto? = null, val error: String? = null)

class LibraryDetailViewModel(val container: AppContainer, val route: String) : ViewModel() {
    val state = MutableStateFlow(LibraryDetailState())
    private var task: Job? = null
    private val kind = route.split('/')[1]
    val id = route.substringAfterLast('/').toLongOrNull() ?: 0
    init {
        viewModelScope.launch {
            container.sessionRestored.await()
            combine(container.accountRepository.state.map { it.account?.id }.distinctUntilChanged(),
                container.musicSettings.state.map { it.server }.distinctUntilChanged(), container.libraryRepository.revision) { id, server, revision -> listOf(id, server, revision) }
                .collectLatest { state.value = LibraryDetailState(); reload() }
        }
    }
    fun reload(more: Boolean = false, force: Boolean = false) {
        if (force) container.libraryRepository.clearReads()
        task?.cancel()
        task = viewModelScope.launch(Dispatchers.Default) {
            state.update { it.copy(loading = true, error = null) }
            val previous = state.value
            when (val result = appResult {
                when (kind) {
                    "daily", "foryou" -> container.libraryRepository.daily().let { previous.copy(title = if (kind == "daily") "每日推荐" else "猜你喜欢", songs = if (kind == "daily") it.songs else it.forYou) }
                    "likes", "recent" -> previous.copy(title = when (kind) { "likes" -> "我喜欢的音乐"; else -> "最近播放" },
                        songs = if (kind == "recent") container.libraryRepository.recent() else container.libraryRepository.likedSongs())
                    "playlist" -> container.libraryRepository.playlist(id).let { previous.copy(title = it.name, description = it.description,
                        cover = it.cover, songs = it.songs, playlist = it) }
                    "artist" -> container.libraryRepository.artist(id, if (more) previous.songs.size else 0).let { previous.copy(title = it.name,
                        description = it.description, cover = it.cover, songs = if (more) (previous.songs + it.songs).distinctBy(Song::id) else it.songs,
                        albums = if (more) previous.albums else it.albums, more = it.more, moreAlbums = if (more) previous.moreAlbums else it.albumTotal > it.albums.size) }
                    "album" -> container.libraryRepository.album(id).let { previous.copy(title = it.name, description = listOf(it.artist, it.description).filter(String::isNotBlank).joinToString("\n"),
                        cover = it.cover, songs = it.songs) }
                    "mv" -> container.libraryRepository.mv(id).let { previous.copy(title = it.name, cover = it.cover, description = listOf(it.artistName, it.desc.orEmpty()).filter(String::isNotBlank).joinToString("\n"), mv = it) }
                    else -> throw ApiException(ErrorKind.NotFound)
                }
            }) {
                is AppResult.Success -> { ensureActive(); state.value = result.value.copy(loading = false) }
                is AppResult.Failure -> { ensureActive(); state.update { it.copy(loading = false, error = result.kind.message) } }
            }
        }
    }
    fun moreAlbums() {
        if (state.value.loading) return
        task = viewModelScope.launch {
            state.update { it.copy(loading = true) }
            when (val result = appResult { container.libraryRepository.artistAlbums(id, state.value.albums.size) }) {
                is AppResult.Success -> state.update { it.copy(loading = false, albums = (it.albums + result.value.first).distinctBy(Album::id), moreAlbums = result.value.second) }
                is AppResult.Failure -> state.update { it.copy(loading = false, error = result.kind.message) }
            }
        }
    }
    override fun onCleared() { task?.cancel() }
}

data class CatalogState(val query: String = "", val submitted: String = "", val albums: Boolean = false,
    val loading: Boolean = false, val entries: List<CatalogEntry> = emptyList(), val more: Boolean = false, val error: String? = null)
class CatalogViewModel(private val container: AppContainer, albums: Boolean, query: String) : ViewModel() {
    val state = MutableStateFlow(CatalogState(query = query, albums = albums))
    private var task: Job? = null
    init {
        viewModelScope.launch { container.musicSettings.state.map { it.server }.distinctUntilChanged().drop(1).collect { task?.cancel(); state.value = CatalogState(albums = albums) } }
        if (query.isNotBlank()) search()
    }
    fun input(query: String) { state.update { it.copy(query = query) } }
    fun search(more: Boolean = false) {
        val before = state.value
        val query = if (more) before.submitted else before.query.trim()
        if (query.isBlank() || (more && before.loading)) return
        task?.cancel()
        state.update { it.copy(loading = true, error = null, submitted = query, entries = if (more) it.entries else emptyList()) }
        task = viewModelScope.launch {
            when (val result = appResult { container.libraryRepository.catalog(query, before.albums, if (more) before.entries.size else 0) }) {
                is AppResult.Success -> state.update { it.copy(loading = false, entries = (if (more) before.entries else emptyList()) + result.value.entries, more = result.value.more) }
                is AppResult.Failure -> state.update { it.copy(loading = false, error = result.kind.message) }
            }
        }
    }
}
