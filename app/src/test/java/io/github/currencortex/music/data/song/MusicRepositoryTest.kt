package io.github.currencortex.music.data.song
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import okhttp3.mockwebserver.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class MusicRepositoryTest {
    @Test fun newLyricsUseRealWordTimesAndCurrentMusicProxy() = runBlocking {
        MockWebServer().use { server ->
            val repo = MusicRepository(ApiClient({ server.url("/cm/").toString() }, { "private-token" }, {}))
            server.enqueue(MockResponse().setBody("""{"code":200,"yrc":{"lyric":"{\"metadata\":true}\n[1000,2000](1000,700,0)Hello (1700,1300,0)world"},"ytlrc":{"lyric":"[00:01.00]你好世界"}}"""))
            val document = (repo.lyricsDocument(418602088) as AppResult.Success).value
            val line = document.lines.single()
            assertTrue(document.hasWordTiming)
            assertEquals("Hello world", line.text)
            assertEquals("你好世界", line.translation)
            assertEquals(listOf(1000L, 1700L), line.words.map { it.startTimeMs })
            assertEquals(listOf(1700L, 3000L), line.words.map { it.endTimeMs })
            val request = server.takeRequest()
            assertEquals("/cm/ncm/lyric/new", request.requestUrl!!.encodedPath)
            assertEquals("418602088", request.requestUrl!!.queryParameter("id"))
            assertNull("Public lyrics must not expose the account token", request.getHeader("Authorization"))
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun songsWithoutYrcKeepOrdinaryLyricsFromNewEndpoint() = runBlocking {
        MockWebServer().use { server ->
            val repo = MusicRepository(ApiClient({ server.url("/cm/").toString() }, { null }, {}))
            server.enqueue(MockResponse().setBody("""{"code":200,"yrc":{"lyric":""},"lrc":{"lyric":"[00:01.00]普通歌词"},"tlyric":{"lyric":"[00:01.00]Translation"}}"""))
            val document = (repo.lyricsDocument(7) as AppResult.Success).value
            assertFalse(document.hasWordTiming)
            assertEquals("Translation", document.lines.single().translation)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun olderServersFallBackToNormalizedLyrics() = runBlocking {
        MockWebServer().use { server ->
            val repo = MusicRepository(ApiClient({ server.url("/custom/").toString() }, { null }, {}))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("""{"lines":[{"t":1000,"txt":"旧服务歌词","trans":"Translation"}]}"""))
            val document = (repo.lyricsDocument(7) as AppResult.Success).value
            assertEquals("旧服务歌词", document.lines.single().text)
            assertEquals("/custom/ncm/lyric/new", server.takeRequest().requestUrl!!.encodedPath)
            assertEquals("/custom/ncm/lyric", server.takeRequest().requestUrl!!.encodedPath)
        }
    }
    @Test fun emptyMalformedOrRejectedNewLyricsFallBack() = runBlocking {
        MockWebServer().use { server ->
            val repo = MusicRepository(ApiClient({ server.url("/cm/").toString() }, { null }, {}))
            for (body in listOf("""{"code":200,"yrc":{"lyric":""}}""", "invalid-json", """{"code":500,"lrc":{"lyric":"[00:01.00]Rejected"}}""")) {
                server.enqueue(MockResponse().setBody(body))
                server.enqueue(MockResponse().setBody("""{"lines":[{"t":1000,"txt":"可用的旧歌词"}]}"""))
                assertEquals("可用的旧歌词", (repo.lyricsDocument(7) as AppResult.Success).value.lines.single().text)
            }
            assertEquals(6, server.requestCount)
        }
    }
    @Test fun bothEndpointsFailWithoutInventingLyrics() = runBlocking {
        MockWebServer().use { server ->
            val repo = MusicRepository(ApiClient({ server.url("/cm/").toString() }, { null }, {}))
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setResponseCode(503))
            val result = repo.lyricsDocument(7) as AppResult.Failure
            assertEquals(ErrorKind.Server, result.kind)
            assertEquals(503, result.status)
        }
    }
    @Test fun consumesLegacyNormalizedContractsAndDoesNotCacheUrls() = runBlocking {
        MockWebServer().use { server ->
            val repo = MusicRepository(ApiClient({ server.url("/cm/").toString() }, { null }, {}))
            server.enqueue(MockResponse().setBody("""{"songs":[{"ncm_id":7,"name":"song","artists":"artist","pic":"http://example.com/cover","duration":1200}],"totals":{"song":44},"hasMore":{"song":true}}"""))
            val page = (repo.search("曲") as AppResult.Success).value
            assertEquals(44, page.total); assertTrue(page.hasMore)
            assertEquals(7L, page.songs.first().id); assertEquals("https://example.com/cover", page.songs.first().cover)
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"lines":[{"t":1000,"txt":"line","trans":"translation"}]}"""))
            assertEquals(1000L, (repo.lyrics(7) as AppResult.Success).value.first().t)
            server.takeRequest()
            repeat(2) {
                server.enqueue(MockResponse().setBody("""{"url":"https://example.com/audio$it","sr":192000,"ch":6,"level":"jymaster"}"""))
                val source = repo.source(7, AudioQuality.AUTO)
                assertTrue(source.highSpec); assertTrue(source.url.endsWith("$it"))
                assertEquals("auto", server.takeRequest().requestUrl!!.queryParameter("level"))
            }
        }
    }
}
