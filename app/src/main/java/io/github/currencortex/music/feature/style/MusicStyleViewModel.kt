package io.github.currencortex.music.feature.style

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.style.*
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StyleCatalogState(val loading: Boolean = true, val styles: List<MusicStyle> = emptyList(), val error: String? = null,
    val covers: Map<Long, String> = emptyMap())
data class StyleDetailState(val loading: Boolean = true, val title: String = "曲风", val description: StyleDescription? = null,
    val style: MusicStyle? = null, val songs: List<Song> = emptyList(), val sort: Int = 0, val cursor: Int = 0,
    val more: Boolean = false, val total: Int = 0, val error: String? = null, val detailError: String? = null,
    val heroCover: String = "")

class MusicStyleViewModel(val container: AppContainer, val id: Long) : ViewModel() {
    val state = MutableStateFlow(StyleDetailState())
    private var task: Job? = null
    private var generation = 0
    init {
        viewModelScope.launch {
            container.ready.await()
            container.musicSettings.state.map { it.server }.distinctUntilChanged().collect {
                task?.cancel(); state.value = StyleDetailState(); reload()
            }
        }
    }
    fun sort(value: Int) {
        if (value !in 0..1 || value == state.value.sort) return
        state.update { it.copy(sort = value, songs = emptyList(), cursor = 0, more = false, total = 0) }
        reload()
    }
    fun reload(more: Boolean = false, fresh: Boolean = false) {
        val before = state.value
        if (more && (before.loading || !before.more)) return
        task?.cancel()
        val request = ++generation
        state.update { it.copy(loading = true, error = null) }
        task = viewModelScope.launch {
            coroutineScope {
                if (!more) launch {
                    val list = appResult { container.musicStyles.list(fresh) }
                    val detail = appResult { container.musicStyles.detail(id, fresh) }
                    if (request != generation) return@launch
                    state.update { old ->
                        val style = (list as? AppResult.Success)?.value?.firstNotNullOfOrNull { it.find(id) }
                        val description = (detail as? AppResult.Success)?.value
                        old.copy(style = style ?: old.style, description = description ?: old.description,
                            title = description?.name ?: style?.tagName ?: old.title,
                            detailError = (detail as? AppResult.Failure)?.kind?.message)
                    }
                }
                when (val result = appResult { container.musicStyles.songs(id, before.sort, if (more) before.cursor else 0, fresh = fresh) }) {
                    is AppResult.Success -> if (request == generation) state.update {
                        val songs = ((if (more) before.songs else emptyList()) + result.value.songs).distinctBy(Song::id)
                        it.copy(songs = songs, cursor = result.value.nextCursor, total = result.value.total,
                            heroCover = if (more) it.heroCover else result.value.songs.firstOrNull()?.cover.orEmpty(),
                            more = result.value.more && (!more || songs.size > before.songs.size))
                    }
                    is AppResult.Failure -> if (request == generation) state.update { it.copy(error = result.kind.message) }
                }
            }
            if (request == generation) state.update { it.copy(loading = false) }
        }
    }
}
