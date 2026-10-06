package io.github.currencortex.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.library.CatalogEntry
import io.github.currencortex.music.data.local.SearchHistoryEntity
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

enum class SearchCategory(val label: String, val route: String) {
    ARTIST("作者", "artist"), ALBUM("作者专辑", "album"), SONG("歌曲", "song")
}
data class SearchCategoryPage(val songs: List<Song> = emptyList(), val entries: List<CatalogEntry> = emptyList(),
    val total: Int = 0, val nextOffset: Int = 0, val more: Boolean = false, val loading: Boolean = false,
    val loaded: Boolean = false, val error: String? = null) {
    val count get() = songs.size + entries.size
}
data class SearchUiState(val query: String = "", val submitted: String = "", val category: SearchCategory = SearchCategory.SONG,
    val pages: Map<SearchCategory, SearchCategoryPage> = emptyMap()) {
    val page get() = pages[category] ?: SearchCategoryPage()
    val songs get() = page.songs
    val total get() = page.total
    val more get() = page.more
    val loading get() = page.loading
    val searched get() = submitted.isNotBlank()
    val error get() = page.error
}

data class HotSearchWord(val text: String, val description: String = "")
data class SearchLandingState(val words: List<HotSearchWord> = emptyList(), val loading: Boolean = false, val loaded: Boolean = false, val error: String? = null)

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(SearchUiState())
    val landing = MutableStateFlow(SearchLandingState())
    private var landingJob: Job? = null
    val history = container.database.music().history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val requests = mutableMapOf<SearchCategory, Job>()
    private var generation = 0
    init { viewModelScope.launch {
        container.ready.await()
        container.musicSettings.state.map { it.server }.distinctUntilChanged().drop(1).collect {
            cancelRequests()
            landingJob?.cancel(); landing.value = SearchLandingState()
            state.value = SearchUiState(query = state.value.query, category = state.value.category)
        }
    } }
    private fun cancelRequests() {
        ++generation
        requests.values.forEach(Job::cancel)
        requests.clear()
    }
    fun input(value: String) {
        if (value.isEmpty()) { cancelRequests(); state.value = SearchUiState(category = state.value.category) }
        else state.update { it.copy(query = value) }
    }
    fun loadLanding(retry: Boolean = false) {
        if (landingJob?.isActive == true || (landing.value.loaded && !retry)) return
        landing.value = landing.value.copy(loading = true, error = null)
        landingJob = viewModelScope.launch {
            container.ready.await()
            val server = container.musicSettings.state.value.server
            val result = appResult {
                val expected = RequestSession(container.accountRepository.server, container.accountRepository.token)
                val data = container.apiClient.request("GET", "ncm/search/hot/detail", authenticated = expected.token != null, expectedSession = expected).jsonObject
                if (data["code"]?.jsonPrimitive?.intOrNull?.let { it != 200 } == true) throw ApiException(ErrorKind.Server)
                (data["data"] as? JsonArray ?: throw ApiException(ErrorKind.Unknown)).mapNotNull { item ->
                    val word = item.jsonObject["searchWord"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    word.takeIf(String::isNotBlank)?.let { HotSearchWord(it, item.jsonObject["content"]?.jsonPrimitive?.contentOrNull.orEmpty()) }
                }.distinctBy { it.text }
            }
            if (server != container.musicSettings.state.value.server) return@launch
            landing.value = when (result) {
                is AppResult.Success -> SearchLandingState(words = result.value, loaded = true)
                is AppResult.Failure -> SearchLandingState(loaded = true, error = result.kind.message)
            }
        }
    }
    fun select(category: SearchCategory) {
        if (category == state.value.category) return
        state.update { it.copy(category = category) }
        val page = state.value.pages[category]
        // A tab uses the submitted keyword, never a half-edited search field.
        if (state.value.searched && page == null) load(category)
    }
    fun search(value: String = state.value.query, more: Boolean = false) {
        if (more) {
            val before = state.value
            if (before.searched && before.more && !before.loading) load(before.category, append = true)
            return
        }
        val query = value.trim()
        if (query.isBlank()) return
        cancelRequests()
        state.update { SearchUiState(query = query, submitted = query, category = it.category) }
        load(state.value.category, rememberHistory = true)
    }
    fun retry() {
        val before = state.value
        if (before.searched && !before.loading) load(before.category, append = before.page.loaded && before.more)
    }
    private suspend fun fetch(category: SearchCategory, query: String, offset: Int): AppResult<SearchCategoryPage> =
        if (category == SearchCategory.SONG) when (val result = container.musicRepository.search(query, offset)) {
            is AppResult.Success -> AppResult.Success(SearchCategoryPage(songs = result.value.songs,
                total = result.value.total, nextOffset = offset + result.value.songs.size, more = result.value.hasMore))
            is AppResult.Failure -> result
        } else when (val result = appResult { container.libraryRepository.catalog(query, category == SearchCategory.ALBUM, offset) }) {
            is AppResult.Success -> AppResult.Success(SearchCategoryPage(entries = result.value.entries,
                total = result.value.total, nextOffset = offset + result.value.entries.size, more = result.value.more))
            is AppResult.Failure -> result
        }
    private fun load(category: SearchCategory, append: Boolean = false, rememberHistory: Boolean = false) {
        val before = state.value
        if (before.submitted.isBlank() || requests[category]?.isActive == true) return
        val old = if (append) before.pages[category] ?: SearchCategoryPage() else SearchCategoryPage()
        val requestGeneration = generation
        state.update { it.copy(pages = it.pages + (category to old.copy(loading = true, error = null))) }
        requests[category] = viewModelScope.launch {
            container.ready.await()
            if (rememberHistory) container.database.music().remember(SearchHistoryEntity(before.submitted, System.currentTimeMillis()))
            val result = fetch(category, before.submitted, old.nextOffset)
            ensureActive()
            if (requestGeneration != generation) return@launch
            state.update { current ->
                val page = when (result) {
                    is AppResult.Success -> {
                        val songs = (old.songs + result.value.songs).distinctBy(Song::id)
                        val entries = (old.entries + result.value.entries).distinctBy(CatalogEntry::id)
                        val count = songs.size + entries.size
                        result.value.copy(songs = songs, entries = entries, total = maxOf(result.value.total, count),
                            more = result.value.more && result.value.count > 0 && (!append || count > old.count), loaded = true)
                    }
                    is AppResult.Failure -> old.copy(loading = false, error = result.kind.message)
                }
                current.copy(pages = current.pages + (category to page))
            }
        }
    }
    fun deleteHistory(value: String) = viewModelScope.launch { container.database.music().deleteHistory(value) }
    fun clearHistory() = viewModelScope.launch { container.database.music().clearHistory() }
}
