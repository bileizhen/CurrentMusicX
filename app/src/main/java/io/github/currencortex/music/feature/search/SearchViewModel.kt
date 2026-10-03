package io.github.currencortex.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.data.local.SearchHistoryEntity
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SearchUiState(val query: String = "", val songs: List<Song> = emptyList(), val total: Int = 0,
                         val more: Boolean = false, val loading: Boolean = false, val searched: Boolean = false,
                         val error: String? = null)
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(SearchUiState())
    val history = container.database.music().history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var request: Job? = null
    private var submittedQuery = ""
    init { viewModelScope.launch {
        container.musicSettings.state.map { it.server }.distinctUntilChanged().drop(1).collect {
            request?.cancel(); state.value = SearchUiState(query = state.value.query)
        }
    } }
    fun input(value: String) { state.value = state.value.copy(query = value) }
    fun search(value: String = state.value.query, more: Boolean = false) {
        val query = if (more) submittedQuery else value.trim()
        if (query.isBlank() || (more && state.value.loading)) return
        request?.cancel()
        request = viewModelScope.launch {
            container.ready.await()
            val old = if (more) state.value.songs else emptyList()
            submittedQuery = query
            state.value = state.value.copy(query = if (more) state.value.query else query, songs = old,
                total = if (more) state.value.total else 0, more = more && state.value.more,
                loading = true, searched = true, error = null)
            container.database.music().remember(SearchHistoryEntity(query, System.currentTimeMillis()))
            when (val result = container.musicRepository.search(query, old.size)) {
                is AppResult.Success -> state.value = state.value.copy(songs = old + result.value.songs, total = result.value.total,
                    more = result.value.hasMore, loading = false)
                is AppResult.Failure -> state.value = state.value.copy(loading = false, error = result.kind.message)
            }
        }
    }
    fun deleteHistory(value: String) = viewModelScope.launch { container.database.music().deleteHistory(value) }
    fun clearHistory() = viewModelScope.launch { container.database.music().clearHistory() }
}
