package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.domain.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class LyricsRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun raw(id: String = "123") = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:amll="http://www.example.com/ns/amll"><head><metadata><amll:meta key="ncmMusicId" value="$id"/></metadata></head><body><div><p begin="1" end="3"><span begin="1" end="2">你</span><span begin="2" end="3">好</span></p></div></body></tt>"""
    private val missing = LyricsProvider { LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "missing") }
    private fun cache() = LyricsCache(temporary.newFolder())
    private val fallback = LyricsProvider { LyricsProviderResult.Parsed(LyricsParser.lrc("[00:01]旧歌词")) }
    @Test fun idAddressedFetchAndRestartedOfflineRawCacheWin() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(raw()))
            server.start()
            val cache = cache()
            val repo = LyricsRepository(AmllLyricsProvider(GithubTtmlDataSource(server.url("/").toString())), missing, cache)
            val first = repo.load(Song(123, "Song"))
            assertTrue(first.document.hasWordTiming)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/ncm-lyrics/123.ttml", request.path)
            assertNull(request.getHeader("Authorization"))
            var calls = 0
            val offline = LyricsProvider { calls++; LyricsProviderResult.Failure(LyricsErrorCode.NETWORK_ERROR, "offline") }
            val restarted = LyricsRepository(offline, offline, cache).load(Song(123, "Song"))
            assertEquals(first.document, restarted.document)
            assertEquals(0, calls)
        } finally { server.shutdown() }
    }
    @Test fun missingAndNetworkFailuresFallBackWithoutPlayerError() = runBlocking {
        for (code in listOf(404, 503)) {
            val server = MockWebServer()
            try {
                server.enqueue(MockResponse().setResponseCode(code)); server.start()
                val result = LyricsRepository(AmllLyricsProvider(GithubTtmlDataSource(server.url("/").toString())), fallback, cache()).load(Song(123, "Song"))
                assertEquals("旧歌词", result.document.lines.single().text)
                assertNull(result.error)
                assertEquals(if (code == 404) LyricsErrorCode.NO_LYRICS else LyricsErrorCode.NETWORK_ERROR, result.issues.single().code)
            } finally { server.shutdown() }
        }
    }
    @Test fun corruptCacheAndRemoteTimelineAreDiagnosedAndRecovered() = runBlocking {
        val cache = cache()
        cache.write(LyricsKey(MusicSource.NETEASE, "123"), "<tt>")
        val bad = LyricsProvider { LyricsProviderResult.Ttml(raw().replace("end=\"3\"", "end=\"0\""), LyricsKey(MusicSource.NETEASE, "123")) }
        val result = LyricsRepository(bad, fallback, cache).load(Song(123, "Song"))
        assertEquals(listOf(LyricsErrorCode.PARSE_ERROR, LyricsErrorCode.INVALID_TIMELINE), result.issues.map { it.code })
        assertEquals("旧歌词", result.document.lines.single().text)
        assertNull(cache.read(LyricsKey(MusicSource.NETEASE, "123")))
    }
    @Test fun mismatchedMetadataNeverDisplaysAnotherSongsLyrics() = runBlocking {
        val provider = LyricsProvider { LyricsProviderResult.Ttml(raw("999"), LyricsKey(MusicSource.NETEASE, "123")) }
        val result = LyricsRepository(provider, missing, cache()).load(Song(123, "Song"))
        assertTrue(result.document.lines.isEmpty())
        assertEquals("暂无歌词", result.error)
        assertEquals(LyricsErrorCode.UNSUPPORTED_FORMAT, result.issues.first().code)
    }
    @Test fun platformIdsPreferExplicitSourceAndNeverTreatQqAsNetease() = runBlocking {
        val song = Song(999, "Same title", musicSource = MusicSource.QQ_MUSIC, externalIds = SongExternalIds(qqMusicId = "qq123", spotifyId = "sp123"))
        val keys = LyricsMatcher.candidates(song)
        assertEquals(listOf(LyricsKey(MusicSource.QQ_MUSIC, "qq123"), LyricsKey(MusicSource.SPOTIFY, "sp123")), keys)
        assertTrue(LyricsMatcher.candidates(Song(0, "No id")).isEmpty())
        assertTrue(LyricsMatcher.candidates(Song(0, "Bad", externalIds = SongExternalIds(neteaseId = "../../secret"))).isEmpty())
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(404)); server.enqueue(MockResponse().setBody(raw().replace("ncmMusicId", "spotifyId").replace("123", "sp123"))); server.start()
            val result = LyricsRepository(AmllLyricsProvider(GithubTtmlDataSource(server.url("/").toString())), missing, cache()).load(song)
            assertTrue(result.document.hasWordTiming)
            assertEquals("/qq-lyrics/qq123.ttml", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
            assertEquals("/spotify-lyrics/sp123.ttml", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
        } finally { server.shutdown() }
    }
    @Test fun cancellationDuringResponseBodyDoesNotTriggerFallback() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(raw()).setBodyDelay(2, TimeUnit.SECONDS)); server.start()
            var fallbackCalls = 0
            val repo = LyricsRepository(AmllLyricsProvider(GithubTtmlDataSource(server.url("/").toString())), LyricsProvider { fallbackCalls++; LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "missing") }, cache())
            val job = launch(Dispatchers.Default) { repo.load(Song(123, "Song")) }
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            delay(100)
            withTimeout(1500) { job.cancelAndJoin() }
            assertEquals(0, fallbackCalls)
        } finally { server.shutdown() }
    }
}
