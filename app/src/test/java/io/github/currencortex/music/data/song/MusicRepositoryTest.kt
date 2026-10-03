package io.github.currencortex.music.data.song
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import okhttp3.mockwebserver.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class MusicRepositoryTest {
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
