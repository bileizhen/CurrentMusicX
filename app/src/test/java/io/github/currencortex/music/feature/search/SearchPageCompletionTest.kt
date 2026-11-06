package io.github.currencortex.music.feature.search

import io.github.currencortex.music.core.network.AppResult
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.data.song.SearchLyricContext
import io.github.currencortex.music.data.song.Song
import org.junit.Assert.*
import org.junit.Test

class SearchPageCompletionTest {
    private val hit = SearchLyricContext(null, "命中", null)
    private val filled = hit.copy(before = "上一句", after = "下一句")
    private val loading = SearchCategoryPage(songs = listOf(Song(1, "first")), nextOffset = 1,
        more = true, loading = true, loaded = true, matchedLyrics = mapOf(1L to hit))

    @Test fun appendPreservesBackfillCompletedWhileNextPageWasLoading() {
        val current = loading.copy(matchedLyrics = mapOf(1L to filled))
        val next = SearchCategoryPage(songs = listOf(Song(2, "second")), total = 3,
            nextOffset = 2, more = true, matchedLyrics = mapOf(2L to hit))
        val result = completeSearchPage(current, AppResult.Success(next), append = true)
        assertEquals(filled, result.matchedLyrics[1L])
        assertEquals(hit, result.matchedLyrics[2L])
        assertEquals(listOf(1L, 2L), result.songs.map { it.id })
        assertEquals(2, result.nextOffset)
        assertTrue(result.more)
        assertFalse(result.loading)
    }

    @Test fun failedAppendKeepsBackfillAndRetryOffset() {
        val current = loading.copy(matchedLyrics = mapOf(1L to filled))
        val result = completeSearchPage(current, AppResult.Failure(ErrorKind.Server), append = true)
        assertEquals(filled, result.matchedLyrics[1L])
        assertEquals(1, result.nextOffset)
        assertTrue(result.more)
        assertFalse(result.loading)
        assertNotNull(result.error)
    }

    @Test fun overlappingPageDoesNotDiscardCompletedContextOrDuplicateSongs() {
        val current = loading.copy(matchedLyrics = mapOf(1L to filled))
        val result = completeSearchPage(current, AppResult.Success(loading.copy(loading = false)), append = true)
        assertEquals(filled, result.matchedLyrics[1L])
        assertEquals(1, result.songs.size)
        assertFalse(result.more)
    }

    @Test fun replacementSearchDoesNotReusePreviousQueriesContext() {
        val current = loading.copy(matchedLyrics = mapOf(1L to filled))
        val result = completeSearchPage(current, AppResult.Success(loading.copy(loading = false)), append = false)
        assertEquals(hit, result.matchedLyrics[1L])
    }

    @Test fun changedMatchDoesNotInheritNeighboursFromAnotherLine() {
        val current = loading.copy(matchedLyrics = mapOf(1L to filled))
        val changed = SearchLyricContext(null, "另一句", null)
        val result = completeSearchPage(current, AppResult.Success(loading.copy(matchedLyrics = mapOf(1L to changed))), append = true)
        assertEquals(changed, result.matchedLyrics[1L])
    }
    @Test fun duplicateOnlyPageKeepsPagingWhenUpstreamOffsetAdvances() {
        val result = completeSearchPage(loading, AppResult.Success(loading.copy(nextOffset = 2)), append = true)
        assertEquals(1, result.songs.size)
        assertEquals(2, result.nextOffset)
        assertTrue(result.more)
    }
}
