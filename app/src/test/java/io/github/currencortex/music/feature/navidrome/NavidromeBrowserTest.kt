package io.github.currencortex.music.feature.navidrome

import io.github.currencortex.music.data.navidrome.NavidromeEntry
import org.junit.Assert.*
import org.junit.Test

class NavidromeBrowserTest {
    @Test fun backAndTabsRestoreLoadedRangeAndScrollWithoutNetwork() {
        var time = 1000L
        val browser = NavidromeBrowser { time }
        val albums = List(80) { NavidromeEntry("$it", "Album $it") }
        browser.finish("albums:", browser.state.value.copy(entries = albums, more = true, nextOffset = 80))
        browser.position("route:albums:", NavidromeScroll(45, 70))
        browser.show(NavidromePage("album", "45", "Album 45"))
        browser.finish("album:45", browser.state.value)
        assertTrue(browser.back())
        assertSame(albums, browser.state.value.entries)
        assertEquals(80, browser.state.value.nextOffset)
        assertEquals(NavidromeScroll(45, 70), browser.position("route:albums:"))
        assertFalse(browser.needsLoad())
        browser.show(NavidromePage("artists"), root = true)
        browser.show(NavidromePage(), root = true)
        assertFalse(browser.needsLoad())
        time += 60_000
        assertTrue(browser.needsLoad())
        assertEquals(80, browser.state.value.entries.size)
    }
    @Test fun refreshKeepsEqualItemsAndRejectsLateResultsFromOtherPages() {
        val browser = NavidromeBrowser()
        val album = NavidromeEntry("a", "Album")
        browser.finish("albums:", browser.state.value.copy(entries = listOf(album)))
        val original = browser.state.value.entries
        browser.finish("albums:", browser.state.value.copy(entries = listOf(album.copy())))
        assertSame(original, browser.state.value.entries)
        browser.finish("albums:", browser.state.value.copy(entries = listOf(NavidromeEntry("b", "New"), album.copy())))
        assertSame(album, browser.state.value.entries[1])
        browser.show(NavidromePage("artists"), root = true)
        assertFalse(browser.finish("albums:", NavidromeUi(entries = listOf(album))))
        assertEquals("artists", browser.state.value.page.kind)
    }
    @Test fun repeatedSearchReplacesQueryWithoutBuildingABackStack() {
        val browser = NavidromeBrowser()
        browser.finish("albums:", browser.state.value)
        browser.show(NavidromePage("search", "first"))
        browser.show(NavidromePage("search", "second"), replace = true)
        assertTrue(browser.back())
        assertEquals("albums", browser.state.value.page.kind)
        assertFalse(browser.back())
    }
    @Test fun mutationsInvalidateLibraryButKeepVisibleContentAndOwnership() {
        val browser = NavidromeBrowser()
        val page = NavidromePage("playlist", "p", "Mine", owned = true)
        browser.show(page)
        browser.finish(page.key, browser.state.value.copy(entries = listOf(NavidromeEntry("a", "Music"))))
        browser.show(NavidromePage("albums"), root = true)
        browser.invalidateLibraries()
        browser.show(page)
        assertTrue(browser.needsLoad())
        assertTrue(browser.state.value.owned)
        assertEquals(1, browser.state.value.entries.size)
        browser.rename(page, "Renamed")
        assertEquals("Renamed", browser.state.value.page.title)
        browser.discard(page)
        assertEquals("albums", browser.state.value.page.kind)
        browser.show(page)
        assertFalse(browser.state.value.loaded)
    }
    @Test fun deletingCapturedTargetNeverClosesAnotherPlaylist() {
        val browser = NavidromeBrowser()
        val first = NavidromePage("playlist", "p1", "First")
        val second = NavidromePage("playlist", "p2", "Second")
        browser.show(first); browser.finish(first.key, browser.state.value)
        browser.show(second); browser.finish(second.key, browser.state.value)
        browser.discard(first)
        assertEquals(second, browser.state.value.page)
        assertTrue(browser.back())
        assertEquals("albums", browser.state.value.page.kind)
    }
    @Test fun accountSwitchErasesHistoryScrollAndPrivatePages() {
        val browser = NavidromeBrowser()
        browser.finish("albums:", browser.state.value.copy(entries = listOf(NavidromeEntry("a", "Private"))))
        browser.position("private", NavidromeScroll(10, 35))
        browser.show(NavidromePage("playlist", "p"))
        browser.clear()
        assertFalse(browser.nested)
        assertTrue(browser.state.value.entries.isEmpty())
        assertEquals(NavidromeScroll(), browser.position("private"))
        browser.show(NavidromePage())
        assertTrue(browser.needsLoad())
    }
}
