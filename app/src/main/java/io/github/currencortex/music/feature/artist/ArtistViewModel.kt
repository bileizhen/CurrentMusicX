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
    val biography: ArtistBiography? = null, val biographyLoading: Boolean = true, val biographyError: String? = null)

/** Public artist data: separate biography and pagination failures never hide playable songs. */
class ArtistViewModel(val container: AppContainer, val id: Long) : ViewModel() {
    val state = MutableStateFlow(ArtistUiState())
    private val tasks = mutableMapOf<String, Job>()
    private var generation = 0
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
                        moreSongs = artist.more && artist.songs.isNotEmpty(), albums = artist.albums.distinctBy(Album::id),
                        albumOffset = artist.albums.size, moreAlbums = artist.albumTotal > artist.albums.size)
                }
                is AppResult.Failure -> if (epoch == generation) state.update { it.copy(loading = false, error = result.kind.message) }
            }
        }
        loadBiography(fresh = true)
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
    fun moreAlbums() {
        val before = state.value
        if (before.loading || before.albumsLoading || !before.moreAlbums) return
        val epoch = generation
        state.update { it.copy(albumsLoading = true, albumsError = null) }
        tasks["albums"] = viewModelScope.launch {
            when (val result = appResult { container.libraryRepository.artistAlbums(id, before.albumOffset) }) {
                is AppResult.Success -> if (epoch == generation) state.update {
                    val albums = (before.albums + result.value.first).distinctBy(Album::id)
                    it.copy(albums = albums, albumOffset = before.albumOffset + result.value.first.size, albumsLoading = false,
                        moreAlbums = result.value.second && result.value.first.isNotEmpty() && albums.size > before.albums.size)
                }
                is AppResult.Failure -> if (epoch == generation) state.update { it.copy(albumsLoading = false, albumsError = result.kind.message) }
            }
        }
    }
}
