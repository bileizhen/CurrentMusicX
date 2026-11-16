package io.github.currencortex.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.library.CatalogEntry
import io.github.currencortex.music.data.local.SearchHistoryEntity
import io.github.currencortex.music.data.song.SearchLyricContext
import io.github.currencortex.music.data.song.SearchBackend
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.settings.MusicPlatform
import io.github.currencortex.music.data.settings.LyricsSource
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

enum class SearchCategory(val label: String, val route: String) {
    SONG("歌曲", "song"), ALBUM("专辑", "album"), ARTIST("作者", "artist"), PLAYLIST("歌单", "playlist"), LYRIC("歌词", "lyric")
}
data class SearchCategoryPage(val songs: List<Song> = emptyList(), val entries: List<CatalogEntry> = emptyList(),
    val total: Int = 0, val nextOffset: Int = 0, val more: Boolean = false, val loading: Boolean = false,
    val loaded: Boolean = false, val error: String? = null, val matchedLyrics: Map<Long, SearchLyricContext> = emptyMap(),
    val backend: SearchBackend? = null) {
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

internal fun searchCategoriesFor(platform: MusicPlatform, roomSearch: Boolean = false): List<SearchCategory> =
    if (roomSearch) listOf(SearchCategory.SONG, SearchCategory.LYRIC)
    else if (platform == MusicPlatform.QQ_MUSIC)
        listOf(SearchCategory.SONG, SearchCategory.PLAYLIST, SearchCategory.ALBUM, SearchCategory.ARTIST)
    else SearchCategory.entries

internal fun SearchCategory.labelFor(platform: MusicPlatform): String =
    if (platform == MusicPlatform.QQ_MUSIC && this == SearchCategory.ARTIST) "歌手" else label

data class HotSearchWord(val text: String, val description: String = "", val displayText: String = text)
data class SearchLandingState(val words: List<HotSearchWord> = emptyList(), val loading: Boolean = false, val loaded: Boolean = false, val error: String? = null)

data class SearchSuggestions(val query: String = "", val words: List<String> = emptyList(), val loading: Boolean = false)

private data class SearchOwner(val server: String, val platform: MusicPlatform, val qqRevision: Long)

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(SearchUiState())
    private var activeOwner = SearchOwner(
        container.musicSettings.state.value.server,
        container.musicSettings.state.value.musicPlatform,
        container.qqMusic.state.value.revision,
    )
    private val mutablePlatform = MutableStateFlow(activeOwner.platform)
    val platform: StateFlow<MusicPlatform> = mutablePlatform.asStateFlow()
    private var neteaseRoomMode = false
    private val mutableOwnerRevision = MutableStateFlow(0L)
    val ownerRevision: StateFlow<Long> = mutableOwnerRevision.asStateFlow()
    val suggestions = MutableStateFlow(SearchSuggestions())
    private var suggestionJob: Job? = null
    val charts = MutableStateFlow(SearchChartsState())
    private var chartsJob: Job? = null
    private val chartJobs = mutableMapOf<String, Job>()
    val landing = MutableStateFlow(SearchLandingState())
    private var landingJob: Job? = null
    val history = container.database.music().history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val requests = mutableMapOf<SearchCategory, Job>()
    private val backfills = mutableListOf<Job>()
    private val lyricOffsets = mutableMapOf<Long, Int>()
    private val lyricRetries = mutableMapOf<Int, Job>()
    private var generation = 0
    init { viewModelScope.launch {
        container.ready.await()
        var previousOwner: SearchOwner? = null
        combine(
            container.musicSettings.state.map { it.server to it.musicPlatform }.distinctUntilChanged(),
            container.qqMusic.state.map { it.revision }.distinctUntilChanged(),
        ) { settings, qqRevision ->
            SearchOwner(settings.first, settings.second, qqRevision)
        }.distinctUntilChanged().collect { owner ->
            val previous = previousOwner
            previousOwner = owner
            if (previous == null) {
                activeOwner = owner
                mutablePlatform.value = currentPlatform()
                return@collect
            }
            val platformChanged = previous.platform != owner.platform
            val serverChanged = previous.server != owner.server
            val qqRevisionChanged = previous.qqRevision != owner.qqRevision
            if (!platformChanged && !serverChanged && !qqRevisionChanged) return@collect
            cancelRequests()
            suggestionJob?.cancel(); suggestions.value = SearchSuggestions()
            landingJob?.cancel(); landing.value = SearchLandingState()
            chartsJob?.cancel(); chartJobs.values.forEach(Job::cancel); chartJobs.clear(); charts.value = SearchChartsState()
            val resetCategory = platformChanged ||
                (owner.platform == MusicPlatform.QQ_MUSIC && qqRevisionChanged)
            state.value = SearchUiState(query = state.value.query,
                category = if (resetCategory) SearchCategory.SONG else state.value.category)
            // Publish the new owner only after stale work and results have been cleared. The
            // screen can then restart landing work without racing this collector.
            activeOwner = owner
            mutablePlatform.value = currentPlatform()
            mutableOwnerRevision.value++
        }
    } }
    private fun currentPlatform() = if (neteaseRoomMode) MusicPlatform.NETEASE else activeOwner.platform
    private fun currentOwner() = activeOwner
    fun setNeteaseRoomMode(enabled: Boolean) {
        if (neteaseRoomMode == enabled) return
        neteaseRoomMode = enabled
        cancelRequests()
        suggestionJob?.cancel(); suggestions.value = SearchSuggestions()
        landingJob?.cancel(); landing.value = SearchLandingState()
        chartsJob?.cancel(); chartJobs.values.forEach(Job::cancel); chartJobs.clear(); charts.value = SearchChartsState()
        state.value = SearchUiState()
        mutablePlatform.value = currentPlatform()
        mutableOwnerRevision.value++
    }
    private fun cancelRequests() {
        ++generation
        requests.values.forEach(Job::cancel)
        requests.clear()
        backfills.forEach(Job::cancel)
        backfills.clear()
        lyricOffsets.clear()
        lyricRetries.values.forEach(Job::cancel)
        lyricRetries.clear()
    }
    fun input(value: String) {
        if (value.isEmpty()) { cancelRequests(); state.value = SearchUiState(category = state.value.category) }
        else state.update { it.copy(query = value) }
        suggestionJob?.cancel()
        val query = value.trim()
        suggestions.value = SearchSuggestions(query, loading = query.isNotBlank() && query != state.value.submitted)
        if (query.isBlank() || query == state.value.submitted) return
        suggestionJob = viewModelScope.launch {
            delay(250)
            container.ready.await()
            val expectedOwner = currentOwner()
            val platform = currentPlatform()
            suggestions.value = SearchSuggestions(query, loading = true)
            val result = if (platform == MusicPlatform.QQ_MUSIC)
                appResult { container.qqMusic.suggestions(query).distinct().take(8) }
            else container.musicRepository.searchSuggestions(query)
            ensureActive()
            if (state.value.query.trim() == query && expectedOwner == currentOwner() && platform == currentPlatform())
                suggestions.value = SearchSuggestions(query, (result as? AppResult.Success)?.value.orEmpty())
        }
    }
    fun loadCharts(retry: Boolean = false) {
        if (currentPlatform() == MusicPlatform.QQ_MUSIC) {
            if (retry || !charts.value.loaded) charts.value = SearchChartsState(loaded = true)
            return
        }
        if (chartsJob?.isActive == true || (charts.value.loaded && !retry)) return
        charts.value = charts.value.copy(loading = true, error = null)
        chartsJob = viewModelScope.launch {
            container.ready.await()
            val expected = container.musicSession()
            val result = appResult {
                parseSearchCharts(container.apiClient.request("GET", "ncm/search/chart/list", expectedSession = RequestSession(expected.server, null, expected.neteaseRevision), callTimeoutMillis = 15_000))
            }
            ensureActive()
            if (expected != container.musicSession()) return@launch
            charts.value = when (result) {
                is AppResult.Success -> result.value.copy(loaded = true)
                is AppResult.Failure -> charts.value.copy(loading = false, loaded = true, error = result.kind.message)
            }
        }
    }
    fun loadChart(id: String, retry: Boolean = false) {
        if (currentPlatform() == MusicPlatform.QQ_MUSIC) return
        if (chartJobs[id]?.isActive == true || (charts.value.pages[id]?.loaded == true && !retry)) return
        charts.update { it.copy(pages = it.pages + (id to SearchChartPage(loading = true))) }
        chartJobs[id] = viewModelScope.launch {
            container.ready.await()
            val expected = container.musicSession()
            val result = appResult {
                parseSearchChartWords(container.apiClient.request("GET", "ncm/search/chart/detail", mapOf("id" to id),
                    expectedSession = RequestSession(expected.server, null, expected.neteaseRevision), callTimeoutMillis = 15_000))
            }
            ensureActive()
            if (expected != container.musicSession()) return@launch
            charts.update { current -> current.copy(pages = current.pages + (id to when (result) {
                is AppResult.Success -> SearchChartPage(words = result.value, loaded = true)
                is AppResult.Failure -> SearchChartPage(error = result.kind.message)
            })) }
        }
    }
    fun loadLanding(retry: Boolean = false) {
        val expectedOwner = currentOwner()
        val platform = currentPlatform()
        if (platform == MusicPlatform.QQ_MUSIC) {
            if (retry || !charts.value.loaded) charts.value = SearchChartsState(loaded = true)
        } else loadCharts(retry)
        if (landingJob?.isActive == true || (landing.value.loaded && !retry)) return
        landing.value = landing.value.copy(loading = true, error = null)
        landingJob = viewModelScope.launch {
            container.ready.await()
            val result = if (platform == MusicPlatform.QQ_MUSIC) appResult {
                container.qqMusic.hotSearch().distinct().map { HotSearchWord(it) }
            } else appResult {
                val expected = container.musicSession()
                val data = container.apiClient.request("GET", "ncm/search/hot/detail", authenticated = expected.token != null, expectedSession = expected).jsonObject
                if (data["code"]?.jsonPrimitive?.intOrNull?.let { it != 200 } == true) throw ApiException(ErrorKind.Server)
                (data["data"] as? JsonArray ?: throw ApiException(ErrorKind.Unknown)).mapNotNull { item ->
                    val word = item.jsonObject["searchWord"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    word.takeIf(String::isNotBlank)?.let { HotSearchWord(it, item.jsonObject["content"]?.jsonPrimitive?.contentOrNull.orEmpty()) }
                }.distinctBy { it.text }
            }
            ensureActive()
            if (expectedOwner != currentOwner() || platform != currentPlatform()) return@launch
            landing.value = when (result) {
                is AppResult.Success -> SearchLandingState(words = result.value, loaded = true)
                is AppResult.Failure -> SearchLandingState(loaded = true, error = result.kind.message)
            }
        }
    }
    fun select(category: SearchCategory) {
        val target = category.takeIf { it in searchCategoriesFor(currentPlatform()) } ?: return
        if (target == state.value.category) return
        state.update { it.copy(category = target) }
        val page = state.value.pages[target]
        // A tab uses the submitted keyword, never a half-edited search field.
        if (state.value.searched && page == null) load(target)
    }
    fun search(value: String = state.value.query, more: Boolean = false) {
        val platform = currentPlatform()
        val category = state.value.category.takeIf { it in searchCategoriesFor(platform) } ?: SearchCategory.SONG
        if (more) {
            val before = state.value
            val target = before.category.takeIf { it in searchCategoriesFor(platform) } ?: SearchCategory.SONG
            if (before.searched && before.more && !before.loading) load(target, append = true)
            return
        }
        val query = value.trim()
        if (query.isBlank()) return
        cancelRequests()
        suggestionJob?.cancel(); suggestions.value = SearchSuggestions()
        state.update { SearchUiState(query = query, submitted = query, category = category) }
        load(state.value.category, rememberHistory = true)
    }
    fun retry() {
        val before = state.value
        val category = before.category.takeIf { it in searchCategoriesFor(currentPlatform()) } ?: SearchCategory.SONG
        if (before.searched && !before.loading) load(category, append = before.page.loaded && before.more)
    }
    private suspend fun fetch(category: SearchCategory, query: String, offset: Int, backend: SearchBackend?,
        platform: MusicPlatform): AppResult<SearchCategoryPage> =
        if (platform == MusicPlatform.QQ_MUSIC) when (val result = appResult {
            container.qqMusic.search(query, offset, category.route)
        }) {
            is AppResult.Success -> AppResult.Success(SearchCategoryPage(
                songs = result.value.songs,
                entries = result.value.entries.map { entry ->
                    CatalogEntry(0L, entry.name, entry.cover, entry.subtitle, entry.id)
                },
                total = result.value.total,
                nextOffset = offset + 30,
                more = result.value.hasMore,
            ))
            is AppResult.Failure -> result
        } else if (category == SearchCategory.SONG || category == SearchCategory.LYRIC) when (val result =
            if (category == SearchCategory.LYRIC) container.musicRepository.searchLyrics(query, offset, backend) else container.musicRepository.search(query, offset, backend)) {
            is AppResult.Success -> AppResult.Success(SearchCategoryPage(songs = result.value.songs,
                total = result.value.total, nextOffset = offset + result.value.upstreamCount, more = result.value.hasMore,
                matchedLyrics = result.value.matchedLyrics, backend = result.value.backend))
            is AppResult.Failure -> result
        } else when (val result = appResult {
            if (category == SearchCategory.PLAYLIST) container.libraryRepository.searchPlaylists(query, offset)
            else container.libraryRepository.catalog(query, category == SearchCategory.ALBUM, offset)
        }) {
            is AppResult.Success -> AppResult.Success(SearchCategoryPage(entries = result.value.entries,
                total = result.value.total, nextOffset = offset + result.value.entries.size, more = result.value.more))
            is AppResult.Failure -> result
        }
    private fun load(category: SearchCategory, append: Boolean = false, rememberHistory: Boolean = false) {
        val platform = currentPlatform()
        val target = category.takeIf { it in searchCategoriesFor(platform) } ?: SearchCategory.SONG
        val before = state.value
        if (before.submitted.isBlank() || requests[target]?.isActive == true) return
        val old = if (append) before.pages[target] ?: SearchCategoryPage() else SearchCategoryPage()
        val requestGeneration = generation
        state.update { it.copy(category = target, pages = it.pages + (target to old.copy(loading = true, error = null))) }
        requests[target] = viewModelScope.launch {
            container.ready.await()
            if (rememberHistory) container.database.music().remember(SearchHistoryEntity(before.submitted, System.currentTimeMillis()))
            val result = fetch(target, before.submitted, old.nextOffset, old.backend, platform)
            ensureActive()
            if (requestGeneration != generation) return@launch
            state.update { current ->
                // Backfills may finish while fetch is suspended. Merge against the latest page,
                // including on failure, rather than restoring the pre-request snapshot.
                val latest = current.pages[target] ?: old
                val page = completeSearchPage(latest, result, append)
                current.copy(pages = current.pages + (target to page))
            }
            if (target == SearchCategory.LYRIC && result is AppResult.Success) {
                result.value.songs.forEach { lyricOffsets[it.id] = old.nextOffset }
                backfillLyricContexts(before.submitted, requestGeneration, result.value.songs, result.value.matchedLyrics)
            }
        }
    }
    fun retryLyricContext(songId: Long) {
        val offset = lyricOffsets[songId] ?: return
        if (lyricRetries[offset]?.isActive == true) return
        val before = state.value
        val page = before.pages[SearchCategory.LYRIC] ?: return
        val epoch = generation
        lyricRetries[offset] = viewModelScope.launch {
            val result = container.musicRepository.searchLyrics(before.submitted, offset, page.backend)
            ensureActive()
            if (epoch != generation) return@launch
            state.update { current ->
                val latest = current.pages[SearchCategory.LYRIC] ?: return@update current
                val contexts = if (result is AppResult.Success) result.value.matchedLyrics.filterKeys { it in latest.matchedLyrics }
                    else mapOf(songId to (latest.matchedLyrics[songId] ?: return@update current).copy(status = "error", retryable = true))
                current.copy(pages = current.pages + (SearchCategory.LYRIC to latest.copy(matchedLyrics = latest.matchedLyrics + contexts)))
            }
        }
    }
    /** The native search never ships a preceding line; read the full lyric and take its neighbours. */
    private fun backfillLyricContexts(query: String, requestGeneration: Int, songs: List<Song>, contexts: Map<Long, SearchLyricContext>) {
        val targets = songs.filter { song -> contexts[song.id]?.let { it.status == null && (it.before == null || it.after == null) } == true }
        if (targets.isEmpty()) return
        backfills += viewModelScope.launch {
            val limiter = Semaphore(4)
            coroutineScope {
                targets.forEach { song ->
                    launch {
                        limiter.withPermit {
                            if (requestGeneration != generation) return@launch
                            val matched = state.value.pages[SearchCategory.LYRIC]?.matchedLyrics?.get(song.id)?.matched ?: return@launch
                            val document = (container.musicRepository.lyricsForMatching(song.id, LyricsSource.NETEASE)
                                as? AppResult.Success)?.value ?: return@launch
                            val extracted = lyricContextFromDocument(document, matched) ?: return@launch
                            if (requestGeneration != generation || state.value.submitted != query) return@launch
                            state.update { current ->
                                val page = current.pages[SearchCategory.LYRIC]
                                val existing = page?.matchedLyrics?.get(song.id)
                                if (page == null || existing == null) return@update current
                                current.copy(pages = current.pages + (SearchCategory.LYRIC to page.copy(
                                    matchedLyrics = page.matchedLyrics + (song.id to existing.copy(
                                        before = existing.before ?: extracted.before,
                                        after = existing.after ?: extracted.after)))))
                            }
                        }
                    }
                }
            }
        }
    }
    fun deleteHistory(value: String) = viewModelScope.launch { container.database.music().deleteHistory(value) }
    fun clearHistory() = viewModelScope.launch { container.database.music().clearHistory() }
}

internal fun completeSearchPage(current: SearchCategoryPage, result: AppResult<SearchCategoryPage>, append: Boolean): SearchCategoryPage =
    when (result) {
        is AppResult.Failure -> current.copy(loading = false, error = result.kind.message)
        is AppResult.Success -> {
            val previous = if (append) current else SearchCategoryPage()
            val incoming = result.value
            val songs = (previous.songs + incoming.songs).distinctBy(Song::id)
            val entries = (previous.entries + incoming.entries).distinctBy(CatalogEntry::stableId)
            val lyrics = previous.matchedLyrics.toMutableMap()
            incoming.matchedLyrics.forEach { (id, context) ->
                val existing = lyrics[id]?.takeIf { context.status == null && it.matched == context.matched }
                lyrics[id] = context.copy(before = context.before ?: existing?.before,
                    after = context.after ?: existing?.after)
            }
            val count = songs.size + entries.size
            incoming.copy(songs = songs, entries = entries, matchedLyrics = lyrics,
                total = maxOf(incoming.total, count), loading = false, loaded = true,
                more = incoming.more && incoming.nextOffset > previous.nextOffset)
        }
    }

/** Locate the matched line inside a full lyric document and take its immediate neighbours. */
internal fun lyricContextFromDocument(document: LyricsDocument, matched: String): SearchLyricContext? {
    fun normalize(value: String) = value.trim().replace(Regex("\\s+"), " ")
    val segments = matched.split(" · ").map(::normalize).filter(String::isNotBlank)
    if (segments.isEmpty() || document.lines.isEmpty()) return null
    val hitStart = document.lines.indexOfFirst { line -> segments.any { normalize(line.text).contains(it) } }
    if (hitStart < 0) return null
    // A joined two-hit match spans consecutive rows; the neighbour follows the whole span.
    var hitEnd = hitStart
    while (hitEnd + 1 < document.lines.size && segments.any { normalize(document.lines[hitEnd + 1].text).contains(it) }) hitEnd++
    return SearchLyricContext(
        document.lines.getOrNull(hitStart - 1)?.text?.let(::normalize)?.takeIf(String::isNotBlank),
        matched,
        document.lines.getOrNull(hitEnd + 1)?.text?.let(::normalize)?.takeIf(String::isNotBlank))
}
