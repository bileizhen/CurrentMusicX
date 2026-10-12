package io.github.currencortex.music.feature.navidrome

import io.github.currencortex.music.data.navidrome.NavidromeEntry
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.flow.MutableStateFlow

data class NavidromePage(val kind: String = "albums", val id: String = "", val title: String = "曲库",
    val cover: String = "", val subtitle: String = "", val owned: Boolean = false) {
    val key get() = "$kind:$id"
}
data class NavidromeUi(val page: NavidromePage = NavidromePage(), val entries: List<NavidromeEntry> = emptyList(),
    val albums: List<NavidromeEntry> = emptyList(), val artists: List<NavidromeEntry> = emptyList(),
    val songs: List<Song> = emptyList(), val loading: Boolean = false, val more: Boolean = false,
    val error: String? = null, val owned: Boolean = false, val loaded: Boolean = false, val nextOffset: Int = 0)
data class NavidromeScroll(val index: Int = 0, val offset: Int = 0)
data class NavidromeRemoval(val playlist: NavidromePage, val song: Song, val occurrence: Int)

/** Page identity excludes presentation metadata. Returning restores data immediately. */
internal class NavidromeBrowser(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    val state = MutableStateFlow(NavidromeUi())
    private val cache = linkedMapOf<String, Pair<NavidromeUi, Long>>()
    private val history = mutableListOf<NavidromePage>()
    private val scroll = linkedMapOf<String, NavidromeScroll>()
    val nested get() = history.isNotEmpty()
    private fun save() { if (state.value.loaded) cache[state.value.page.key] = state.value.copy(loading = false) to (cache[state.value.page.key]?.second ?: now()) }
    fun needsLoad() = !state.value.loaded || now() - (cache[state.value.page.key]?.second ?: Long.MIN_VALUE / 2) >= 60_000
    fun show(page: NavidromePage, root: Boolean = false, replace: Boolean = false) {
        save()
        if (root) history.clear() else if (!replace && page.key != state.value.page.key) history.add(state.value.page)
        state.value = cache[page.key]?.first?.copy(page = page, owned = page.owned, loading = false, error = null)
            ?: NavidromeUi(page, owned = page.owned)
    }
    fun back(): Boolean {
        if (history.isEmpty()) return false
        val page = history.removeAt(history.lastIndex)
        show(page, replace = true); return true
    }
    fun finish(key: String, fetched: NavidromeUi): Boolean {
        if (state.value.page.key != key) return false
        fun <T> reuse(old: List<T>, next: List<T>): List<T> {
            if (old == next) return old
            if (old.isEmpty()) return next
            val previous = old.associateWith { it }
            return next.map { value -> previous[value] ?: value }
        }
        val old = state.value
        state.value = fetched.copy(loading = false, loaded = true, error = null,
            entries = reuse(old.entries, fetched.entries), albums = reuse(old.albums, fetched.albums),
            artists = reuse(old.artists, fetched.artists), songs = reuse(old.songs, fetched.songs))
        cache[key] = state.value to now()
        while (cache.size > 20) cache.remove(cache.keys.first())
        return true
    }
    fun invalidateLibraries() {
        cache.keys.toList().filter { it.substringBefore(':') in setOf("likes", "playlists", "playlist") }.forEach {
            cache[it]?.let { value -> cache[it] = value.first to (now() - 60_000) }
        }
    }
    fun rename(page: NavidromePage, name: String) {
        if (state.value.page.key == page.key) state.value = state.value.copy(page = state.value.page.copy(title = name))
        cache[page.key]?.let { cache[page.key] = it.first.copy(page = it.first.page.copy(title = name)) to it.second }
        history.indices.filter { history[it].key == page.key }.forEach { history[it] = history[it].copy(title = name) }
    }
    fun discard(page: NavidromePage) {
        cache.remove(page.key); history.removeAll { it.key == page.key }
        if (state.value.page.key == page.key) {
            state.value = state.value.copy(loaded = false)
            if (!back()) show(NavidromePage("playlists"), root = true)
            cache.remove(page.key)
        }
    }
    fun position(key: String) = scroll[key] ?: NavidromeScroll()
    fun position(key: String, value: NavidromeScroll) {
        scroll[key] = value
        while (scroll.size > 40) scroll.remove(scroll.keys.first())
    }
    fun clear() { cache.clear(); history.clear(); scroll.clear(); state.value = NavidromeUi() }
}
