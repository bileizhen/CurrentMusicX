package io.github.currencortex.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import io.github.currencortex.music.core.media.HighSpecWarning
import io.github.currencortex.music.core.media.PlayerMode

data class LyricsUiState(val document: LyricsDocument = LyricsDocument(), val loading: Boolean = false, val error: String? = null,
    val issues: List<io.github.currencortex.music.feature.lyrics.data.LyricsIssue> = emptyList())
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
    private val _lyrics = MutableStateFlow(LyricsUiState())
    val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()
    init { viewModelScope.launch {
        queue.map { it.current?.takeUnless { song -> song.video } }.distinctUntilChanged().collectLatest { song ->
            if (song == null) { _lyrics.value = LyricsUiState(); return@collectLatest }
            _lyrics.value = LyricsUiState(loading = true)
            val result = container.lyricsRepository.load(song)
            _lyrics.value = LyricsUiState(result.document, error = result.error, issues = result.issues)
        }
    } }
    fun quality(value: io.github.currencortex.music.core.media.AudioQuality) = viewModelScope.launch {
        container.musicSettings.setQuality(value)
        player.qualityChanged()
    }
    fun suppressWarning() = viewModelScope.launch { container.musicSettings.setWarning(false); player.acceptHighSpec() }
    fun lyricsFontSize(value: Float) = viewModelScope.launch { container.musicSettings.setLyricsFontSize(value) }
    fun lyricsWeight(value: io.github.currencortex.music.data.settings.LyricsWeight) = viewModelScope.launch { container.musicSettings.setLyricsWeight(value) }
}
