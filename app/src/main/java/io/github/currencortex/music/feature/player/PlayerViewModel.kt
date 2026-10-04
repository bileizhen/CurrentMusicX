package io.github.currencortex.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.local.CachedLyrics
import io.github.currencortex.music.data.song.LyricLine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import io.github.currencortex.music.core.media.HighSpecWarning
import io.github.currencortex.music.core.media.PlayerMode

data class LyricsUiState(val lines: List<LyricLine> = emptyList(), val loading: Boolean = false, val error: String? = null)
data class NavigationPlayback(val mode: PlayerMode, val error: String?, val warning: HighSpecWarning?)
class PlayerViewModel(private val container: AppContainer) : ViewModel() {
    val player = container.playerController
    val state = player.state
    val queue = player.queue.state
    // The navigation host needs only low-frequency state, never the playback position.
    val navigation = state.map { NavigationPlayback(it.mode, it.error, it.warning) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavigationPlayback(state.value.mode, state.value.error, state.value.warning))
    val currentSong = queue.map { it.current }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), queue.value.current)
    val settings = container.musicSettings.state
    val lyrics = MutableStateFlow(LyricsUiState())
    init { viewModelScope.launch {
        queue.map { it.current?.takeUnless { song -> song.video }?.id }.distinctUntilChanged().collectLatest { id ->
            if (id == null) { lyrics.value = LyricsUiState(); return@collectLatest }
            lyrics.value = LyricsUiState(loading = true)
            when (val result = container.musicRepository.lyrics(id)) {
                is AppResult.Success -> {
                    lyrics.value = LyricsUiState(result.value)
                    container.database.music().cacheLyrics(CachedLyrics(id, ApiJson.encodeToString(result.value)))
                }
                is AppResult.Failure -> {
                    val cached = container.database.music().lyrics(id)?.let { ApiJson.decodeFromString<List<LyricLine>>(it.payload) }.orEmpty()
                    lyrics.value = LyricsUiState(cached, error = if (cached.isEmpty()) result.kind.message else null)
                }
            }
        }
    } }
    fun quality(value: io.github.currencortex.music.core.media.AudioQuality) = viewModelScope.launch {
        container.musicSettings.setQuality(value)
        player.qualityChanged()
    }
    fun suppressWarning() = viewModelScope.launch { container.musicSettings.setWarning(false); player.acceptHighSpec() }
}
