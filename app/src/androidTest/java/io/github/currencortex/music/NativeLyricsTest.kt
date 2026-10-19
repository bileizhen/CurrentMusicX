package io.github.currencortex.music

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.local.CachedLyrics
import io.github.currencortex.music.data.local.MusicDatabase
import io.github.currencortex.music.data.song.MusicRepository
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.CurrentMusicLyricsProvider
import io.github.currencortex.music.feature.lyrics.data.LyricsProviderResult
import io.github.currencortex.music.feature.lyrics.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class NativeLyricsTest {
    @Test fun offlineCacheMigrationRemovesEstimatedWordsAndPersistsCleanLyrics() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java).build()
        try {
            val original = LyricsDocument(listOf(LyricLine(1000, 3000, "夜に",
                listOf(LyricWord("夜", 1000, 2000, 0, 1), LyricWord("に", 2000, 3000, 1, 2)),
                translation = "夜晚", romanization = "yo ni", wordsEstimated = true)))
            database.music().cacheLyrics(CachedLyrics(7, ApiJson.encodeToString(original)))
            val repository = MusicRepository(ApiClient({ "http://127.0.0.1:1/cm/" }, { null }, {}))
            val provider = CurrentMusicLyricsProvider(repository, database.music())
            val document = (provider.findLyrics(Song(7, "fixture")) as LyricsProviderResult.Parsed).document
            assertFalse(document.hasWordTiming)
            assertEquals("夜に", document.lines.single().text)
            assertEquals("夜晚", document.lines.single().translation)
            assertEquals("yo ni", document.lines.single().romanization)
            assertEquals(ApiJson.encodeToString(document), database.music().lyrics(7)!!.payload)
        } finally { database.close() }
    }

    /** Explicit anonymous, read-only endpoint probe; production login/queue stay untouched. */
    @Test fun liveLyricsUseNeteaseEvenWhenProjectAudioIsSelected() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("nativeLyricsLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val vault = object : TokenStore {
            override fun read(): String? = null
            override fun write(token: String) = Unit
            override fun clear() = Unit
        }
        try {
            val sessions = NeteaseSessionStore(vault, scope)
            sessions.ready.await()
            val requests = mutableListOf<String>()
            val gateway = NeteaseGateway(NeteaseTransport(sessions, log = { requests += it }))
            val api = ApiClient({ "http://127.0.0.1:1/cm/" }, { null }, {}, netease = gateway,
                nativeMedia = { false })
            val report = mutableListOf<String>()
            for (songId in listOf(347230L, 418602088L)) {
                val result = MusicRepository(api).lyricsDocument(songId)
                assertTrue("Native lyric endpoint must return lyrics: $result", result is AppResult.Success)
                val document = (result as AppResult.Success).value
                assertTrue(document.lines.isNotEmpty())
                assertTrue(document.lines.none { it.wordsEstimated })
                if (songId == 418602088L) assertTrue("Known YRC track must retain real word timing", document.hasWordTiming)
                report += "song=$songId lines=${document.lines.size} wordTimedLines=${document.lines.count { it.words.isNotEmpty() }}"
            }
            assertTrue(requests.any { "/api/song/lyric/v1 HTTP 200" in it })
            File(context.getExternalFilesDir(null), "native-lyrics-live.txt").writeText(
                report.joinToString("\n", postfix = "\n") +
                    "projectServer=unreachable\nnativeMedia=false\nanonymous=true\n" + requests.joinToString("\n"))
        } finally { scope.cancel() }
    }
}
