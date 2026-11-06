package io.github.currencortex.music.feature.lyrics

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.local.*
import io.github.currencortex.music.data.settings.LyricsSource
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CurrentMusicLyricsCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val song = Song(7, "Fixture")
    private val hour = 60 * 60 * 1000L
    private class Dao : MusicDao {
        val rows = mutableMapOf<Pair<Long, String>, CachedLyrics>()
        override suspend fun cacheLyrics(value: CachedLyrics) { rows[value.id to value.scope] = value }
        override suspend fun lyrics(id: Long, scope: String) = rows[id to scope]
        override suspend fun clearLyrics() { rows.clear() }
        override fun history() = flowOf(emptyList<SearchHistoryEntity>())
        override suspend fun remember(value: SearchHistoryEntity) = Unit
        override suspend fun deleteHistory(keyword: String) = Unit
        override suspend fun clearHistory() = Unit
        override suspend fun queue(): QueueSnapshotEntity? = null
        override suspend fun saveQueue(value: QueueSnapshotEntity) = Unit
        override suspend fun cacheSong(value: CachedSong) = Unit
    }

    @Test fun oldProjectCacheIsNeverReusedOrMigratedWhileNativeCacheSurvives() = runBlocking {
        val dao = Dao()
        val document = LyricsParser.lrc("[00:01]Old recording")
        for (scope in listOf("netease", "currentmusic:server-a", "currentmusic:server-b"))
            dao.cacheLyrics(CachedLyrics(song.id, ApiJson.encodeToString(document), scope))
        // A mismatched expected server fails before HTTP, making the offline path deterministic.
        val music = MusicRepository(ApiClient({ "http://127.0.0.1:1/" }, { null }, {}))
        for (server in listOf("server-a", "server-b")) {
            val provider = CurrentMusicLyricsProvider(music, dao, LyricsSource.CURRENT_MUSIC, { server })
            assertTrue(provider.findLyrics(song) is LyricsProviderResult.Failure)
        }
        val native = CurrentMusicLyricsProvider(music, dao, server = { "server-a" }).findLyrics(song)
        assertEquals(document, (native as LyricsProviderResult.Parsed).document)
        assertNull(native.expiresAtMillis)
        assertEquals(3, dao.rows.size)
    }

    @Test fun fallbackCacheHasTwoHourDeadlineAndOfflineReadsNeverExtendIt() = runBlocking {
        MockWebServer().use { server ->
            var online = true
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = if (online) MockResponse().setBody(
                    """{"source":"ncm:lrc","wordLevel":false,"lines":[{"t":1000,"txt":"Correct recording","wSynth":true,"words":[{"text":"Correct recording","start":1000,"end":2000}]}]}""")
                    else MockResponse().setResponseCode(503)
            }
            val url = server.url("/cm/").toString()
            val dao = Dao()
            var now = 100_000L
            val provider = CurrentMusicLyricsProvider(MusicRepository(ApiClient({ url }, { null }, {})),
                dao, LyricsSource.CURRENT_MUSIC, { url }, now = { now })
            val first = provider.findLyrics(song) as LyricsProviderResult.Parsed
            assertEquals(now + 2 * hour, first.expiresAtMillis)
            assertFalse(first.document.hasWordTiming)
            assertNotNull(dao.lyrics(song.id, "currentmusic:v2:$url"))
            online = false
            now += hour
            val offline = provider.findLyrics(song) as LyricsProviderResult.Parsed
            assertEquals(first.expiresAtMillis, offline.expiresAtMillis)
            now += hour
            assertTrue(provider.findLyrics(song) is LyricsProviderResult.Failure)
            now = 99_999L
            assertTrue(provider.findLyrics(song) is LyricsProviderResult.Failure)
        }
    }

    @Test fun confirmedSourcesHaveSevenDayDeadlineAndKeepTheirMetadata() = runBlocking {
        MockWebServer().use { server ->
            val url = server.url("/cm/").toString()
            val provider = CurrentMusicLyricsProvider(MusicRepository(ApiClient({ url }, { null }, {})),
                Dao(), LyricsSource.CURRENT_MUSIC, { url }, now = { 10_000L })
            for (source in listOf("ncm:yrc", "lddc:kg", "lddc:ne", "lddc:qm", "ncm:inst")) {
                server.enqueue(MockResponse().setBody("""{"source":"$source","wordLevel":false,"lines":[{"t":1000,"txt":"Fixture"}]}"""))
                val result = provider.findLyrics(song) as LyricsProviderResult.Parsed
                assertEquals(10_000L + 7 * 24 * hour, result.expiresAtMillis)
                assertEquals(source, result.document.metadata.source)
                assertEquals(false, result.document.metadata.wordLevel)
            }
        }
    }

    @Test fun memoryExpiresAtProviderDeadlineAndManualImportRemainsAuthoritative() = runBlocking {
        var now = 1000L
        var calls = 0
        val provider = LyricsProvider {
            calls++
            LyricsProviderResult.Parsed(LyricsParser.lrc("[00:01]Version $calls"), 2000L)
        }
        val store = SongLyricsStore(temporary.newFolder())
        val repo = LyricsRepository(provider, provider, LyricsCache(temporary.newFolder()),
            selection = { LyricsSelection(listOf("project"), provider) }, overrides = store, now = { now })
        assertEquals("Version 1", repo.load(song).document.lines.single().text)
        now = 1999
        assertEquals("Version 1", repo.load(song).document.lines.single().text)
        now = 2000
        assertEquals("Version 2", repo.load(song).document.lines.single().text)
        val imported = SavedLyrics(LyricsParser.lrc("[00:01]My import"), LyricsOrigin("Import", "import"))
        store.saveImported(song, imported, offsetMs = 400)
        now = Long.MAX_VALUE
        assertEquals(imported.document, repo.load(song).document)
        assertEquals(400L, store.settings(song).offsetMs)
        assertEquals(2, calls)
    }
}
