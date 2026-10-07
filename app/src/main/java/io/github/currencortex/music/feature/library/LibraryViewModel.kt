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
    val recent: List<Song> = emptyList(), val playlists: List<Playlist> = emptyList(), val errors: Map<String, String> = emptyMap(),
    val loaded: Boolean = false)

class LibraryViewModel(val container: AppContainer) : ViewModel() {
    val home = MutableStateFlow(LibraryHomeState())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val selectedSong = MutableStateFlow<Song?>(null)
    val availablePlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlistError = MutableStateFlow<String?>(null)
    val primaryLikeBusy = MutableStateFlow(false)
    val statuses = container.libraryRepository.statuses
    val likes = io.github.currencortex.music.data.song.SongLikeController(container.libraryRepository,
        container.neteaseSongActions, viewModelScope) { RequestSession(container.accountRepository.server, container.accountRepository.token) }
    private val refreshRequest = MutableStateFlow(0)
    private var prefetchJob: Job? = null
    private var playlistLoad: Job? = null
    init {
        viewModelScope.launch {
            container.sessionRestored.await()
            launch { container.accountRepository.sessionRevision.drop(1).collect {
                likes.dismiss(); playlistLoad?.cancel(); selectedSong.value = null
                availablePlaylists.value = emptyList(); playlistError.value = null
            } }
            var identity: List<Any?>? = null
            combine(container.accountRepository.state.map { it.account?.id }.distinctUntilChanged(),
                container.musicSettings.state.map { it.server }.distinctUntilChanged(), container.libraryRepository.revision,
                refreshRequest, container.primaryLibrary.identity) { id, server, revision, refresh, primary -> listOf(id, server, revision, refresh, primary) }
                .collectLatest { keys ->
                    prefetchJob?.cancel()
                    val sameAccount = identity == keys.take(2)
                    if (!sameAccount) likes.dismiss()
                    identity = keys.take(2)
                    home.value = if (sameAccount) home.value.copy(loading = true, errors = emptyMap()) else LibraryHomeState(loading = true)
                    loadHome()
                    if (container.accountRepository.token != null) prefetchJob = viewModelScope.launch {
                        launch { appResult { container.primaryLibrary.likedPlaylist() ?: container.libraryRepository.likedSongs() } }
                        home.value.playlists.firstOrNull()?.let { playlist -> launch { appResult {
                            if (playlist.source == "netease") container.neteaseLibrary.playlist(playlist.id)
                            else container.libraryRepository.playlist(playlist.id)
                        } } }
                    }
                }
        }
    }
    fun refresh() { container.libraryRepository.clearReads(); container.neteaseLibrary.invalidate(); refreshRequest.update { it + 1 } }
    private suspend fun loadHome() = coroutineScope {
        if (container.accountRepository.token == null) { home.value = LibraryHomeState(loaded = true); return@coroutineScope }
        suspend fun <T> section(name: String, request: suspend () -> T, update: (LibraryHomeState, T) -> LibraryHomeState) {
            when (val r = appResult { request() }) {
                is AppResult.Success -> home.update { update(it, r.value) }
                is AppResult.Failure -> home.update { it.copy(errors = it.errors + (name to r.kind.message)) }
            }
        }
        listOf(
            launch { section("每日推荐", { container.libraryRepository.daily() }) { s, d -> s.copy(daily = d.songs, forYou = d.forYou) } },
            launch { section("最近播放", { container.libraryRepository.recent() }) { s, d -> s.copy(recent = d) } },
            launch { section("我的歌单", { container.primaryLibrary.playlists() }) { s, d -> s.copy(playlists = d) } },
        ).joinAll()
        home.update { it.copy(loading = false, loaded = true) }
    }
    fun toggle(song: Song) = viewModelScope.launch {
        when (val result = container.libraryRepository.toggleLike(song)) {
            is AppResult.Failure -> message.value = result.kind.message
            is AppResult.Success -> message.value = "点赞已更新"
        }
    }
    fun choosePlaylist(song: Song) {
        if (busy.value) return
        likes.dismiss()
        selectedSong.value = song
        availablePlaylists.value = emptyList()
        playlistError.value = null
        busy.value = true
        val expected = RequestSession(container.accountRepository.server, container.accountRepository.token)
        playlistLoad = viewModelScope.launch {
            try {
                val current = appResult { container.libraryRepository.playlists().filter { it.editable(container.accountRepository.state.value.account?.id ?: 0) } }
                val native = appResult {
                    if (container.bindingRepository.cachedStatus().bound) container.neteaseLibrary.playlists(fresh = true).filter { it.nativeOwned }
                    else emptyList()
                }
                if (expected != RequestSession(container.accountRepository.server, container.accountRepository.token)) return@launch
                availablePlaylists.value = (native as? AppResult.Success)?.value.orEmpty() + (current as? AppResult.Success)?.value.orEmpty()
                playlistError.value = listOfNotNull((native as? AppResult.Failure)?.kind?.message,
                    (current as? AppResult.Failure)?.kind?.message).distinct().joinToString("；").ifBlank { null }
            }
            finally { busy.value = false }
        }
    }
    fun addToPlaylist(playlist: Playlist) {
        val song = selectedSong.value ?: return
        if (playlist !in availablePlaylists.value) return
        action("已加入歌单") {
            if (playlist.source == "netease") container.neteaseLibrary.add(playlist.id, song)
            else container.libraryRepository.add(playlist.id, listOf(song))
            container.libraryRepository.invalidate(); selectedSong.value = null
        }
    }
    fun togglePrimaryLike(song: Song) {
        if (primaryLikeBusy.value || song.video) return
        primaryLikeBusy.value = true
        val expected = RequestSession(container.accountRepository.server, container.accountRepository.token)
        viewModelScope.launch {
            try {
                when (val result = appResult {
                    val native = container.primaryLibrary.resolveNetease()
                    if (expected != RequestSession(container.accountRepository.server, container.accountRepository.token)) throw ApiException(ErrorKind.Unauthorized)
                    if (native) {
                        val id = io.github.currencortex.music.data.song.NeteaseSongActionsRepository.songId(song) ?: throw ApiException(ErrorKind.NotFound)
                        container.neteaseSongActions.toggleLiked(id, song)
                    } else when (val write = container.libraryRepository.toggleLike(song)) {
                        is AppResult.Success -> container.libraryRepository.statuses.value[song.id]?.liked == true
                        is AppResult.Failure -> throw ApiException(write.kind)
                    }
                }) {
                    is AppResult.Success -> if (expected == RequestSession(container.accountRepository.server, container.accountRepository.token)) {
                        container.libraryRepository.invalidate()
                        message.value = if (result.value) "已加入我喜欢" else "已取消喜欢"
                    }
                    is AppResult.Failure -> if (expected == RequestSession(container.accountRepository.server, container.accountRepository.token)) message.value = result.kind.message
                }
            } finally { primaryLikeBusy.value = false }
        }
    }
    fun create(name: String, description: String, done: () -> Unit) = action("歌单已创建") {
        container.libraryRepository.create(name, description); done()
    }
    fun action(success: String, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        val expected = RequestSession(container.accountRepository.server, container.accountRepository.token)
        viewModelScope.launch {
            try {
                when (val r = appResult {
                    if (expected != RequestSession(container.accountRepository.server, container.accountRepository.token)) throw ApiException(ErrorKind.Unauthorized)
                    block()
                }) {
                    is AppResult.Success -> if (expected == RequestSession(container.accountRepository.server, container.accountRepository.token)) message.value = success
                    is AppResult.Failure -> if (expected == RequestSession(container.accountRepository.server, container.accountRepository.token)) message.value = r.kind.message
                }
            } finally { busy.value = false }
        }
    }
    fun refreshStatuses(songs: List<Song>) = viewModelScope.launch { appResult { container.libraryRepository.refreshStatus(songs.filterNot { it.video }.map { it.id }) } }
}

data class LibraryDetailState(val loading: Boolean = true, val title: String = "", val description: String = "", val cover: String = "",
    val songs: List<Song> = emptyList(), val playlist: Playlist? = null, val albums: List<Album> = emptyList(),
    val more: Boolean = false, val moreAlbums: Boolean = false, val mv: MvDto? = null, val error: String? = null,
    val loaded: Boolean = false, val refreshing: Boolean = false, val refreshError: String? = null)

class LibraryDetailViewModel(val container: AppContainer, val route: String) : ViewModel() {
    val state = MutableStateFlow(LibraryDetailState())
    val playbackMode = container.playerController.state.map { it.mode }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), container.playerController.state.value.mode)
    private var task: Job? = null
    private val kind = route.split('/')[1]
    val id = route.substringAfterLast('/').toLongOrNull() ?: 0
    init {
        viewModelScope.launch {
            container.sessionRestored.await()
            var identity: List<Any?>? = null
            combine(container.accountRepository.state.map { it.account?.id }.distinctUntilChanged(),
                container.musicSettings.state.map { it.server }.distinctUntilChanged(), container.libraryRepository.revision,
                container.primaryLibrary.identity, container.neteaseLibrary.revision, container.neteaseSongActions.revision) { keys -> keys.toList() }
                .collectLatest { keys ->
                    if (identity != keys.take(2)) {
                        task?.cancel()
                        state.value = LibraryDetailState()
                    }
                    identity = keys.take(2)
                    reload()
                }
        }
    }
    fun reload(more: Boolean = false, force: Boolean = false) {
        // Repeated pulls share the in-flight request; refresh only this resource's cache.
        if (force && task?.isActive == true) return
        task?.cancel()
        val previous = state.value
        state.value = previous.copy(loading = !previous.loaded || more,
            refreshing = previous.loaded && !more, error = null, refreshError = null)
        task = viewModelScope.launch {
            when (val result = appResult {
                when (kind) {
                    "daily", "foryou" -> container.libraryRepository.daily(fresh = force).let { previous.copy(title = if (kind == "daily") "每日推荐" else "猜你喜欢", songs = if (kind == "daily") it.songs else it.forYou) }
                    "likes" -> container.primaryLibrary.likedPlaylist(force)?.let {
                        previous.copy(title = it.name, description = it.description, cover = it.cover, songs = it.songs, playlist = it)
                    } ?: previous.copy(title = "我喜欢的音乐", cover = "", description = "", playlist = null,
                        songs = container.libraryRepository.likedSongs(fresh = force))
                    "recent" -> previous.copy(title = "最近播放", songs = container.libraryRepository.recent(fresh = force))
                    "ncmplaylist" -> container.neteaseLibrary.playlist(id, fresh = force).let { previous.copy(title = it.name,
                        description = it.description, cover = it.cover, songs = it.songs, playlist = it) }
                    "playlist" -> container.libraryRepository.playlist(id, fresh = force).let { previous.copy(title = it.name, description = it.description,
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
                is AppResult.Success -> {
                    val merged = withContext(Dispatchers.Default) { previous.mergeFetched(result.value) }
                    ensureActive()
                    state.value = merged.copy(loading = false, loaded = true, refreshing = false, error = null, refreshError = null)
                }
                is AppResult.Failure -> {
                    ensureActive()
                    state.update { it.copy(loading = false, refreshing = false,
                        error = result.kind.message.takeUnless { previous.loaded },
                        refreshError = result.kind.message.takeIf { previous.loaded }) }
                }
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
