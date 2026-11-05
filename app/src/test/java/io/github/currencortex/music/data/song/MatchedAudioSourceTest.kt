package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class MatchedAudioSourceTest {
    @Test fun fallbackPreservesMeasuredMetadataAndRefreshUsesTheSameAuthenticatedEndpoint() = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/cm/").toString()
            val repository = MusicRepository(ApiClient({ base }, { "fixture" }, {}))
            server.enqueue(MockResponse().setBody("""{"url":"https://audio.example/full.m4a?sign=private",
                "level":"matched","source":"netease-match","requestedLevel":"jymaster","matchSource":"vkeys:qq:2",
                "br":48000,"type":"m4a","size":1820000,"sr":44100,"ch":2,"durationMs":299352,"expectedDurationMs":299000}"""))
            val source = repository.source(186001, AudioQuality.JYMASTER, RequestSession(base, "fixture"), refresh = true)
            assertTrue(source.matched); assertEquals("匹配音源", source.qualityLabel)
            assertEquals(120L, source.validForSeconds); assertEquals(48000L, source.bitrate)
            assertEquals(299352L, source.durationMs); assertEquals("vkeys:qq:2", source.matchSource)
            assertEquals("jymaster", source.requestedLevel); assertFalse(source.highSpec)
            val request = server.takeRequest()
            assertEquals("/cm/ncm/song/url", request.requestUrl!!.encodedPath)
            assertEquals("1", request.requestUrl!!.queryParameter("refresh"))
            assertEquals("jymaster", request.requestUrl!!.queryParameter("level"))
            assertEquals("Bearer fixture", request.getHeader("Authorization"))
            val info = AudioStreamInfo.from(source)
            assertEquals("匹配音源 · M4A · 48 kbps · 44100 Hz · 2 声道", info.summary)
            val sessionMetadata = ApiJson.encodeToString(info)
            assertFalse(sessionMetadata.contains("private")); assertFalse(sessionMetadata.contains("https://"))
            assertEquals(info, AudioStreamInfo.decode(sessionMetadata))
        }
    }
    @Test fun cacheSeparatesDifferentCandidatesEvenWithIdenticalAudioSpecifications() {
        val source = AudioSource("https://audio.example/a", "matched", 44100, 2)
        val different = source.copy(url = "https://audio.example/b")
        assertNotEquals(sourceCacheKey("request", source), sourceCacheKey("request", different))
        assertNotEquals("request", sourceCacheKey("request", source))
        val digest = "0123456789abcdef0123456789abcdef"
        assertEquals(sourceCacheKey("request", source.copy(md5 = digest)),
            sourceCacheKey("request", different.copy(md5 = digest.uppercase())))
        assertEquals("request", sourceCacheKey("request", source.copy(level = "lossless")))
        assertFalse(sourceCacheKey("request", source).contains("https://"))
    }
    @Test fun incompleteOrFutureMetadataDoesNotClaimHighestQuality() {
        val source = ApiJson.decodeFromString<AudioSource>("""{"url":"https://audio.example/a","level":"future","sampleRate":0,"channelCount":0}""")
        assertEquals("未知音质", source.qualityLabel)
        assertEquals("匹配音源", source.copy(source = "netease-match").qualityLabel)
        assertEquals("无损", source.copy(level = "lossless").qualityLabel)
        assertEquals("未知音质", AudioStreamInfo.from(source).summary)
    }
}
