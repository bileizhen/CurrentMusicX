package io.github.currencortex.music.feature.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.library.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ArtistUiState(val loading: Boolean = true, val artist: Artist? = null, val error: String? = null,
    val songs: List<Song> = emptyList(), val songOffset: Int = 0, val moreSongs: Boolean = false,
    val songsLoading: Boolean = false, val songsError: String? = null,
    val albums: List<Album> = emptyList(), val albumOffset: Int = 0, val moreAlbums: Boolean = false,
    val albumsLoading: Boolean = false, val albumsError: String? = null,
    val albumOrder: String = "time",
    val profile: ArtistProfile? = null, val profileLoading: Boolean = false, val profileError: String? = null,
    val fans: ArtistFans? = null,
    val overview: ArtistOverview? = null,
    val mvs: List<MvDto> = emptyList(), val mvOffset: Int = 0, val moreMvs: Boolean = false,
    val mvsLoading: Boolean = false, val mvsError: String? = null,
    val similar: List<CatalogEntry> = emptyList(), val similarLoading: Boolean = false, val similarError: String? = null,
    val biography: ArtistBiography? = null, val biographyLoading: Boolean = true, val biographyError: String? = null) {
    val displayArtist: Artist? get() {
        val base = artist ?: profile?.let { Artist(0, it.name, it.cover, it.alias, emptyList(), false, emptyList(), it.albumTotal) }
        val details = profile ?: return base
        return base?.copy(name = details.name.ifBlank { base.name }, cover = details.cover.ifBlank { base.cover },
            description = details.alias.ifBlank { base.description }, briefDesc = details.briefDesc.ifBlank { base.briefDesc },
            songTotal = details.songTotal, albumTotal = details.albumTotal, mvTotal = details.mvTotal)
    }
}

/** Public artist data: separate biography and pagination failures never hide playable songs. */
class ArtistViewModel(val container: AppContainer, val id: Long) : ViewModel() {
    val state = MutableStateFlow(ArtistUiState())
    private val tasks = mutableMapOf<String, Job>()
    private var generation = 0
    private var resetAlbums = true
    private var resetMvs = true
    init { viewModelScope.launch {
        container.ready.await()
        container.musicSettings.state.map { it.server }.distinctUntilChanged().collect {
            state.value = ArtistUiState(); reload()
        }
    } }
    fun reload() {
        tasks.values.forEach(Job::cancel); tasks.clear()
        val epoch = ++generation
        state.update { it.copy(loading = true, error = null, songsLoading = false, albumsLoading = false,
            songsError = null, albumsError = null) }
        tasks["main"] = viewModelScope.launch {
            when (val result = appResult { container.libraryRepository.artist(id) }) {
                is AppResult.Success -> if (epoch == generation) state.update { old ->
                    val artist = result.value
                    old.copy(loading = false, artist = artist, songs = artist.songs.distinctBy(Song::id), songOffset = artist.songs.size,
                        moreSongs = artist.more && artist.songs.isNotEmpty())
                }
                is AppResult.Failure -> if (epoch == generation) state.update { it.copy(loading = false, error = result.kind.message) }
            }
        }
        loadBiography(fresh = true)
        loadAlbums(reset = true)
        loadMvs(reset = true)
        loadSimilar()
        loadProfile()
        tasks["fans"] = viewModelScope.launch {
            when (val result = appResult { container.libraryRepository.artistFans(id) }) {
                is AppResult.Success -> if (epoch == generation) state.update { it.copy(fans = result.value) }
                is AppResult.Failure -> Unit // Optional public counts; never replace unknown data with zero.
            }
        }
    }
    fun loadBiography(fresh: Boolean = true) {
        tasks["bio"]?.cancel()
        val epoch = generation
        state.update { it.copy(biographyLoading = true, biographyError = null) }
        tasks["bio"] = viewModelScope.launch {
            when (val result = appResult { container.libraryRepository.artistBiography(id, fresh) }) {
                is AppResult.Success -> if (epoch == generation) state.update { it.copy(biography = result.value, biographyLoading = false) }
                is AppResult.Failure -> if (epoch == generation) state.update { it.copy(biographyLoading = false, biographyError = result.kind.message) }
            }
        }
    }
    fun moreSongs() {
        val before = state.value
        if (before.loading || before.songsLoading || !before.moreSongs) return
        val epoch = generation
        state.update { it.copy(songsLoading = true, songsError = null) }
        tasks["songs"] = viewModelScope.launch {
            when (val result = appResult { container.libraryRepository.artist(id, before.songOffset) }) {
                is AppResult.Success -> if (epoch == generation) state.update {
                    val songs = (before.songs + result.value.songs).distinctBy(Song::id)
                    it.copy(songs = songs, songOffset = before.songOffset + result.value.songs.size, songsLoading = false,
                        moreSongs = result.value.more && result.value.songs.isNotEmpty() && songs.size > before.songs.size)
                }
                is AppResult.Failure -> if (epoch == generation) state.update { it.copy(songsLoading = false, songsError = result.kind.message) }
            }
        }
    }
    fun moreAlbums() = loadAlbums(reset = resetAlbums)
    fun setAlbumOrder(order: String) {
        require(order in setOf("time", "hot"))
        if (order == state.value.albumOrder) return
        tasks["albums"]?.cancel()
        state.update { it.copy(albumOrder = order, albums = emptyList(), albumOffset = 0, moreAlbums = false) }
        loadAlbums(reset = true)
    }
    private fun loadAlbums(reset: Boolean) {
        val before = state.value
        if (!reset && (before.albumsLoading || (!before.moreAlbums && before.albumsError == null))) return
        tasks["albums"]?.cancel()
        resetAlbums = reset
        val offset = if (reset) 0 else before.albumOffset
        val epoch = generation
        state.update { it.copy(albumsLoading = true, albumsError = null) }
        tasks["albums"] = viewModelScope.launch {
            when (val result = appResult { container.libraryRepository.artistAlbums(id, offset, before.albumOrder) }) {
                is AppResult.Success -> if (epoch == generation && state.value.albumOrder == before.albumOrder) state.update {
                    resetAlbums = false
                    val albums = ((if (reset) emptyList() else before.albums) + result.value.first).filter { it.id > 0 }.distinctBy(Album::id)
                    it.copy(albums = albums, albumOffset = offset + result.value.first.size, albumsLoading = false,
                        moreAlbums = result.value.second && result.value.first.isNotEmpty())
                }
                is AppResult.Failure -> if (epoch == generation && state.value.albumOrder == before.albumOrder) state.update { it.copy(albumsLoading = false, albumsError = result.kind.message) }
            }
        }
    }
    fun moreMvs() = loadMvs(reset = resetMvs)
    private fun loadMvs(reset: Boolean) {
        val before = state.value
        if (!reset && (before.mvsLoading || (!before.moreMvs && before.mvsError == null))) return
        tasks["mvs"]?.cancel()
        resetMvs = reset
        val epoch = generation
        val offset = if (reset) 0 else before.mvOffset
        state.update { it.copy(mvsLoading = true, mvsError = null) }
        tasks["mvs"] = viewModelScope.launch {
            when (val result = appResult { container.libraryRepository.artistMvs(id, offset) }) {
                is AppResult.Success -> if (epoch == generation) state.update {
                    resetMvs = false
                    it.copy(mvs = ((if (reset) emptyList() else before.mvs) + result.value.mvs)
                        .filter { mv -> mv.id > 0 }.distinctBy { mv -> mv.id },
                        mvOffset = offset + result.value.mvs.size, mvsLoading = false,
                        moreMvs = result.value.more && result.value.mvs.isNotEmpty())
                }
                is AppResult.Failure -> if (epoch == generation) state.update { it.copy(mvsLoading = false, mvsError = result.kind.message) }
            }
        }
    }
    fun loadSimilar() {
        tasks["similar"]?.cancel()
        val epoch = generation
        state.update { it.copy(similarLoading = true, similarError = null) }
        tasks["similar"] = viewModelScope.launch {
            when (val result = appResult { container.libraryRepository.artistOverview(id) }) {
                is AppResult.Success -> if (epoch == generation) state.update { it.copy(overview = result.value, similar = result.value.artists, similarLoading = false) }
                is AppResult.Failure -> if (epoch == generation) state.update { it.copy(similarLoading = false, similarError = result.kind.message) }
            }
        }
    }
    fun loadProfile() {
        tasks["profile"]?.cancel()
        val epoch = generation
        state.update { it.copy(profileLoading = true, profileError = null) }
        tasks["profile"] = viewModelScope.launch {
            when (val result = appResult { container.libraryRepository.artistProfile(id) }) {
                is AppResult.Success -> if (epoch == generation) state.update { it.copy(profile = result.value, profileLoading = false) }
                is AppResult.Failure -> if (epoch == generation) state.update { it.copy(profileLoading = false, profileError = result.kind.message) }
            }
        }
    }
}
