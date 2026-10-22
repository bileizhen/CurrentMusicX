package io.github.currencortex.music

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.network.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Opt-in public endpoint check with isolated storage; no login or library writes. */
class SearchSuggestionsLiveTest {
    @Test fun officialSearchChartsAndGenreWordsLoadWithoutAnAccount() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("searchLive") == "true")
        AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(),
            "search-charts-live-${UUID.randomUUID()}", useNativeNetease = true).use { container ->
            container.ready.await(); container.sessionRestored.await()
            val state = io.github.currencortex.music.feature.search.parseSearchCharts(
                container.apiClient.request("GET", "ncm/search/chart/list"))
            assertTrue(state.charts.size > 2)
            assertTrue(state.pages.values.any { it.words.isNotEmpty() })
            for (id in listOf("HOT_SEARCH_STYLE_ACG#@#", "HOT_SEARCH_STYLE_JAPAN#@#", "HOT_SEARCH_STYLE_NEW_SONG#@#")) {
                assertTrue("Official search chart: $id", state.charts.any { it.id == id })
                val words = io.github.currencortex.music.feature.search.parseSearchChartWords(
                    container.apiClient.request("GET", "ncm/search/chart/detail", mapOf("id" to id)))
                assertTrue("$id has actual keywords", words.isNotEmpty())
                assertTrue(words.all { it.text.isNotBlank() })
            }
        }
    }
    @Test fun anonymousSuggestionsAndLyricMatchesWorkAgainstNetease() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("searchLive") == "true")
        AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(),
            "search-live-${UUID.randomUUID()}", useNativeNetease = true).use { container ->
            container.ready.await(); container.sessionRestored.await()
            val suggestions = container.musicRepository.searchSuggestions("海阔")
            assertTrue("Suggestion request: $suggestions", suggestions is AppResult.Success)
            assertTrue((suggestions as AppResult.Success).value.isNotEmpty())
            val lyrics = container.musicRepository.searchLyrics("原谅我这一生不羁放纵爱自由")
            assertTrue("Lyric request: $lyrics", lyrics is AppResult.Success)
            val page = (lyrics as AppResult.Success).value
            assertTrue(page.songs.isNotEmpty())
            assertTrue(page.matchedLyrics.values.any(String::isNotBlank))
        }
    }
}
