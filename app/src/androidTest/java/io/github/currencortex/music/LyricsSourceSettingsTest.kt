package io.github.currencortex.music

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.netease.NeteaseEndpoints
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.local.*
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import io.github.currencortex.music.feature.settings.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import top.yukonga.miuix.kmp.basic.Scaffold
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class LyricsSourceSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()

    @Test fun selectionReloadsCurrentLyricsWithoutChangingAudioOrQueue() = runBlocking<Unit> {
        check(context.packageName.endsWith(".verification"))
        val paths = CopyOnWriteArrayList<String>()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    paths += request.path.orEmpty()
                    return when {
                        request.path?.startsWith("/eapi/song/lyric/v1") == true -> MockResponse().setBody("""{"code":200,"lrc":{"lyric":"[00:01]Native lyrics"}}""")
                        request.path?.startsWith("/cm/ncm/lyric?") == true -> MockResponse().setBody("""{"lines":[{"t":1000,"end":3000,"txt":"Project lyrics","wSynth":true,"w":[[1000,"Project "],[2000,"lyrics"]]}]}""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        val endpoint = server.url("/").toString()
        val c = AppContainer(context, "lyrics-source-${UUID.randomUUID()}", useNativeNetease = true,
            neteaseEndpoints = NeteaseEndpoints(endpoint, endpoint, endpoint))
        try {
            c.ready.await(); c.sessionRestored.await()
            c.accountRepository.server = server.url("/cm/").toString()
            c.audioSettings.select(AudioProvider.NETEASE)
            assertEquals(LyricsSource.AUTO, c.musicSettings.snapshot().lyricsSource)
            c.musicSettings.setLyricsSource(LyricsSource.NETEASE)
            val song = Song(7, "Fixture")
            c.playbackQueue.replace(listOf(song), 0)
            val vm = MusicSettingsViewModel(c)
            compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold {
                MusicSettingsScreen(vm) {}
            } } }
            compose.waitUntil(10000) { c.playbackLyrics.state.value.document.lines.firstOrNull()?.text == "Native lyrics" }
            compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("lyrics_source"))
            compose.onNodeWithTag("lyrics_source").performClick()
            compose.onNodeWithTag("lyrics_source_current_music").performClick()
            compose.waitUntil(10000) { c.playbackLyrics.state.value.document.lines.firstOrNull()?.text == "Project lyrics" }
            assertFalse(c.playbackLyrics.state.value.document.hasWordTiming)
            assertTrue(paths.any { it.startsWith("/cm/ncm/lyric?") })
            assertEquals(AudioProvider.NETEASE, c.audioSettings.access().provider)
            assertEquals(listOf(song), c.playbackQueue.state.value.songs)
            compose.onNodeWithTag("lyrics_source").performClick()
            compose.onNodeWithTag("lyrics_source_netease").performClick()
            compose.waitUntil(5000) { c.playbackLyrics.state.value.document.lines.firstOrNull()?.text == "Native lyrics" }
            assertNotNull(c.database.music().lyrics(7, "netease"))
            assertNotNull(c.database.music().lyrics(7, "currentmusic:${c.accountRepository.server}"))
        } finally { c.close(); server.shutdown() }
    }

    @Test fun preferenceSurvivesStoreRestartAndUnknownValuesKeepAutomaticDefault() = runBlocking<Unit> {
        assertEquals(LyricsSource.AUTO, LyricsSource.from(null))
        assertEquals(LyricsSource.AUTO, LyricsSource.from("unknown"))
        val file = File(context.cacheDir, "lyrics-source-${UUID.randomUUID()}.preferences_pb")
        var owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var repo = MusicSettingsRepository(PreferenceDataStoreFactory.create(scope = owner) { file }, owner)
            assertEquals(LyricsSource.AUTO, repo.snapshot().lyricsSource)
            repo.setLyricsSource(LyricsSource.NETEASE)
            owner.coroutineContext[Job]!!.cancelAndJoin()
            owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repo = MusicSettingsRepository(PreferenceDataStoreFactory.create(scope = owner) { file }, owner)
            assertEquals(LyricsSource.NETEASE, repo.snapshot().lyricsSource)
            repo.setLyricsSource(LyricsSource.CURRENT_MUSIC)
            assertEquals(LyricsSource.CURRENT_MUSIC, repo.snapshot().lyricsSource)
        } finally { owner.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }

    @Test fun automaticDefaultCanBeSelectedInTheRealSourceSettingsDialog() = runBlocking<Unit> {
        check(context.packageName.endsWith(".verification"))
        val c = AppContainer(context, "lyrics-auto-ui-${UUID.randomUUID()}", ttmlProvider = LyricsProvider {
            error("No song is queued, so settings must not request lyrics")
        })
        try {
            c.ready.await(); c.sessionRestored.await()
            assertEquals(LyricsSource.AUTO, c.musicSettings.snapshot().lyricsSource)
            c.musicSettings.setLyricsSource(LyricsSource.NETEASE)
            val vm = MusicSettingsViewModel(c)
            compose.setContent { LeiTheme(AppearanceSettings(blur = false)) { Scaffold {
                MusicSettingsScreen(vm) {}
            } } }
            compose.onNodeWithTag("network_settings").performScrollToNode(hasTestTag("lyrics_source"))
            compose.onNodeWithTag("lyrics_source").performClick()
            compose.onNodeWithTag("lyrics_source_auto").assertExists().assertIsNotSelected()
                .assertTextContains(LyricsSource.AUTO.label)
                .assertTextContains("以网易云歌词为参考")
                .assertTextContains("内容和时间轴").performClick()
            compose.waitUntil(5000) { c.musicSettings.state.value.lyricsSource == LyricsSource.AUTO }
            compose.onNodeWithTag("lyrics_source").assertTextContains(LyricsSource.AUTO.label).performClick()
            compose.onNodeWithTag("lyrics_source_auto").assertIsSelected()
            compose.onNodeWithTag("lyrics_source_netease").assertIsNotSelected()
            compose.onNodeWithTag("lyrics_source_current_music").assertIsNotSelected()
            assertTrue(c.playbackQueue.state.value.songs.isEmpty())
        } finally { c.close() }
    }

    @Test fun automaticContainerWiringPrefersFreshWinnerOverNonemptyLegacyProvidersAndCaches() = runBlocking<Unit> {
        check(context.packageName.endsWith(".verification"))
        val lyricPaths = setOf("/cm/ncm/lyric", "/cm/ncm/lyric/new", "/eapi/song/lyric/v1")
        val paths = CopyOnWriteArrayList<String>()
        val calls = AtomicInteger()
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    paths += path
                    return when (path) {
                        "/cm/ncm/lyric", "/cm/ncm/lyric/new" -> MockResponse().setBody(
                            """{"lines":[{"t":1000,"end":3000,"txt":"Old project lyrics"}]}""")
                        "/eapi/song/lyric/v1" -> MockResponse().setBody(
                            """{"code":200,"lrc":{"lyric":"[00:01.00]Old native lyrics"}}""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        val endpoint = server.url("/").toString()
        val c = AppContainer(context, "auto-container-${UUID.randomUUID()}", ttmlProvider = null, useNativeNetease = true,
            neteaseEndpoints = NeteaseEndpoints(endpoint, endpoint, endpoint), automaticLyricsProvider = { requested ->
                val generation = calls.incrementAndGet()
                SavedLyrics(LyricsParser.lrc("[00:01.00]Automatic winner $generation"),
                    LyricsOrigin("网易云自动候选 $generation", "netease", "${requested.id}:$generation"))
            })
        try {
            c.ready.await(); c.sessionRestored.await(); c.neteaseSessions.ready.await()
            c.accountRepository.server = server.url("/cm/").toString()
            c.updateSettings.setAutoCheck(false)
            c.audioSettings.select(AudioProvider.NETEASE)
            assertEquals(LyricsSource.AUTO, c.musicSettings.snapshot().lyricsSource)
            val song = Song(17, "Automatic wiring fixture", "Fixture singer", durationMs = 9_000)
            withContext(Dispatchers.Main) {
                c.playbackQueue.replace(listOf(song), 0)
                c.playerController.state.value = PlayerState(song = song, positionMs = 2_000, durationMs = 9_000)
            }
            val first = withTimeout(5_000) { c.playbackLyrics.state.first {
                it.matches(song) && !it.loading && it.document.lines.firstOrNull()?.text == "Automatic winner 1"
            } }
            assertEquals("netease", first.origin?.provider)
            assertEquals("网易云自动候选 1", first.origin?.label)
            assertEquals(1, calls.get())
            assertEquals("AUTO must not ask either nonempty legacy lyric provider", 0, paths.count { it in lyricPaths })

            c.musicSettings.setLyricsSource(LyricsSource.CURRENT_MUSIC)
            withTimeout(5_000) { c.playbackLyrics.state.first {
                it.matches(song) && !it.loading && it.document.lines.firstOrNull()?.text == "Old project lyrics"
            } }
            val legacyReads = paths.count { it in lyricPaths }
            assertTrue(legacyReads > 0)
            assertTrue(paths.any { it == "/cm/ncm/lyric" || it == "/cm/ncm/lyric/new" })
            assertEquals(1, calls.get())

            c.musicSettings.setLyricsSource(LyricsSource.AUTO)
            val refreshed = withTimeout(5_000) { c.playbackLyrics.state.first {
                it.matches(song) && !it.loading && it.document.lines.firstOrNull()?.text == "Automatic winner 2"
            } }
            assertEquals(2, calls.get())
            assertEquals("netease", refreshed.origin?.provider)
            assertEquals("网易云自动候选 2", refreshed.origin?.label)
            assertEquals("Neither the old provider nor its cached result can replace the fresh AUTO winner", legacyReads,
                paths.count { it in lyricPaths })
            assertNull(c.songLyrics.settings(song).matchedOrigin)
            assertNull(c.songLyrics.settings(song).importedOrigin)
            assertEquals(listOf(song), c.playbackQueue.state.value.songs)
            assertEquals(AudioProvider.NETEASE, c.audioSettings.access().provider)
            assertEquals(song, c.playerController.state.value.song)
            assertEquals(2_000L, c.playerController.state.value.positionMs)
            assertFalse(c.playerController.state.value.playing)
        } finally { c.close(); server.shutdown() }
    }

    @Test fun databaseMigrationPreservesQueueAndSeparatesOfflineSourcesAndServers() = runBlocking<Unit> {
        val name = "lyrics-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE SearchHistoryEntity (keyword TEXT NOT NULL PRIMARY KEY, usedAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE QueueSnapshotEntity (id INTEGER NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE CachedSong (id INTEGER NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE CachedLyrics (id INTEGER NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
            db.execSQL("INSERT INTO QueueSnapshotEntity VALUES (1, 'keep-queue')")
            db.execSQL("INSERT INTO CachedLyrics VALUES (7, '{\"lines\":[{\"startTimeMs\":1000,\"endTimeMs\":3000,\"text\":\"Native cached\"}]}')")
            db.version = 1
        }
        val db = Room.databaseBuilder(context, MusicDatabase::class.java, name).addMigrations(MusicDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("keep-queue", db.music().queue()!!.payload)
            assertNull(db.music().lyrics(7))
            assertNotNull(db.music().lyrics(7, "legacy"))
            db.music().cacheLyrics(CachedLyrics(7, "{\"lines\":[{\"startTimeMs\":1000,\"endTimeMs\":3000,\"text\":\"Native cached\"}]}"))
            db.music().cacheLyrics(CachedLyrics(7, "{\"lines\":[{\"startTimeMs\":1000,\"endTimeMs\":3000,\"text\":\"Project cached\"}]}", "currentmusic:server-a"))
            val music = MusicRepository(ApiClient({ "http://127.0.0.1:1/" }, { null }, {}))
            suspend fun read(source: LyricsSource, server: String) = CurrentMusicLyricsProvider(music, db.music(), source, { server }).findLyrics(Song(7, "Fixture"))
            assertEquals("Native cached", (read(LyricsSource.NETEASE, "server-a") as LyricsProviderResult.Parsed).document.lines.single().text)
            assertEquals("Project cached", (read(LyricsSource.CURRENT_MUSIC, "server-a") as LyricsProviderResult.Parsed).document.lines.single().text)
            assertTrue(read(LyricsSource.CURRENT_MUSIC, "server-b") is LyricsProviderResult.Failure)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
