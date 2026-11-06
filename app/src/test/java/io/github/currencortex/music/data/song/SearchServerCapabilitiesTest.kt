package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class SearchServerCapabilitiesTest {
    @Test fun catalogDuplicatesDoNotConsumeUpstreamOffsets() = runBlocking {
        MockWebServer().use { server ->
            val repo = MusicRepository(ApiClient({ server.url("/cm/").toString() }, { "private" }, {}))
            server.enqueue(MockResponse().setBody("""{"catalogSongs":[{"ncm_id":9,"name":"Original"},{"ncm_id":1,"name":"Exact"}],"songs":[{"ncm_id":1,"name":"Ordinary"},{"ncm_id":2,"name":"Other"}],"totals":{"song":4},"hasMore":{"song":true}}"""))
            val first = (repo.search("Original") as AppResult.Success).value
            assertEquals(listOf(9L, 1L, 2L), first.songs.map { it.id })
            assertEquals(2, first.upstreamCount)
            assertEquals(4, first.total)
            assertNull(server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"catalogSongs":[{"ncm_id":9,"name":"Ignored"}],"songs":[{"ncm_id":3,"name":"Third"}]}"""))
            val second = (repo.search("Original", first.upstreamCount, first.backend) as AppResult.Success).value
            assertEquals(listOf(3L), second.songs.map { it.id })
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("offset"))
        }
    }
    @Test fun lyricStatesPreserveHitAndNeverTreatStatusTextAsLyrics() = runBlocking {
        MockWebServer().use { server ->
            val repo = MusicRepository(ApiClient({ server.url("/cm/").toString() }, { null }, {}))
            for (status in listOf("pending", "error", "missing", "unmatched", "single_line")) {
                server.enqueue(MockResponse().setBody("""{"songs":[{"ncm_id":1,"name":"Song","lyric":"不可用提示","lyricMatch":"实际命中","lyricContextStatus":"$status","lyricContextRetryable":${status in setOf("pending", "error")}}]}"""))
                val hit = (repo.searchLyrics("歌词") as AppResult.Success).value.matchedLyrics[1L]!!
                assertEquals("实际命中", hit.matched); assertEquals(status, hit.status)
                assertEquals(status in setOf("pending", "error"), hit.retryable)
                assertNull(hit.before); assertNull(hit.after)
            }
        }
    }
    @Test fun contextAllowsUntimedAndMoreThanThreeLines() {
        val dto = ApiJson.decodeFromString<SongDto>("""{"ncm_id":1,"name":"Song","lyricContextStatus":"complete","lyricContext":[{"t":null,"txt":"Before"},{"t":null,"txt":"Hit one","matched":true},{"t":null,"txt":"Hit two","matched":true},{"t":null,"txt":"After"}]}""")
        val hit = searchLyricContextOf(dto)!!
        assertEquals("Before", hit.before); assertEquals("Hit one · Hit two", hit.matched)
        assertEquals("After", hit.after); assertEquals("complete", hit.status)
    }
}
