package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.*
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.library.LibraryViewModel
import io.github.currencortex.music.feature.navidrome.*
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Isolated package/container. Only the local fixture receives mutations. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NavidromeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private fun fixtureServer(): MockWebServer {
        val server = MockWebServer()
        val starred = AtomicBoolean(false)
        val audio = testContext.assets.open("download/fixture.flac").use { it.readBytes() }
        val bitmap = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val cover = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val song = { """{"id":"track-uuid","title":"自建曲库歌曲","artist":"曲库艺术家","album":"曲库专辑","coverArt":"cover-uuid","duration":1.25,"suffix":"flac","starred":"${if (starred.get()) "2026-10-12" else ""}"}""" }
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath.substringAfterLast('/')
                if (path.endsWith(".view")) assertNotNull(request.requestUrl!!.queryParameter("t"))
                assertNull(request.getHeader("Token"))
                if (path == "getCoverArt.view") return MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(cover))
                if (path == "stream.view" || path == "download.view") {
                    val start = Regex("bytes=(\\d+)").find(request.getHeader("Range").orEmpty())?.groupValues?.get(1)?.toInt() ?: 0
                    return MockResponse().setHeader("Content-Type", "audio/flac").setBody(Buffer().write(audio, start, audio.size - start)).apply {
                        if (start > 0) setResponseCode(206).setHeader("Content-Range", "bytes $start-${audio.lastIndex}/${audio.size}")
                    }
                }
                val data = when (path) {
                    "ping.view" -> "\"serverVersion\":\"fixture\""
                    "getUser.view" -> "\"user\":{\"downloadRole\":true}"
                    "getAlbumList2.view" -> """"albumList2":{"album":[{"id":"album-uuid","name":"曲库专辑","artist":"曲库艺术家"}]}"""
                    "getAlbum.view" -> "\"album\":{\"song\":[${song()}]}"
                    "getArtists.view" -> """"artists":{"index":[{"artist":[{"id":"artist-uuid","name":"曲库艺术家"}]}]}"""
                    "getArtist.view" -> """"artist":{"album":[{"id":"album-uuid","name":"曲库专辑"}]}"""
                    "getPlaylists.view" -> """"playlists":{"playlist":[{"id":"list-uuid","name":"自己的歌单","owner":"fixture"}]}"""
                    "getPlaylist.view" -> "\"playlist\":{\"entry\":[${song()}]}"
                    "getSong.view" -> "\"song\":${song()}"
                    "getStarred2.view" -> "\"starred2\":{\"song\":[${song()}]}"
                    "search3.view" -> "\"searchResult3\":{\"song\":[${song()}]}"
                    "getLyricsBySongId.view" -> """"lyricsList":{"structuredLyrics":[{"synced":true,"line":[{"start":0,"value":"内嵌同步歌词"}]}]}"""
                    "star.view" -> { starred.set(true); "" }
                    "unstar.view" -> { starred.set(false); "" }
                    "updatePlaylist.view", "createPlaylist.view", "deletePlaylist.view" -> ""
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setBody("""{"subsonic-response":{"status":"ok"${if (data.isEmpty()) "" else ",$data"}}}""")
            }
        }
        server.start(); return server
    }
    @Test fun privateDownloadEmbedsServerMetadataCoverAndLyrics(): Unit = runBlocking {
        val server = fixtureServer()
        val container = AppContainer(context, "nd-download-${UUID.randomUUID()}", useNativeNetease = false)
        val temp = File(context.cacheDir, "nd-download-${UUID.randomUUID()}")
        var saved: io.github.currencortex.music.core.download.SavedSongDownload? = null
        try {
            container.ready.await(); container.navidrome.connect(server.url("/music/").toString(), "fixture", "fixture-password")
            val song = container.navidrome.album("album-uuid").single()
            assertEquals(1250L, song.durationMs)
            val engine = io.github.currencortex.music.core.download.SongDownloadEngine(temp,
                resolve = { track, quality -> container.navidrome.source(track, quality, download = true) },
                detail = { container.musicRepository.detail(it) }, lyrics = { container.lyricsRepository.load(it).document },
                store = io.github.currencortex.music.core.download.SongDownloadStore(context), http = container.navidromeMedia)
            val result = engine.download(song, AudioQuality.AUTO, null, UUID.randomUUID().toString()) {}
            saved = result.saved
            assertEquals("", result.warning)
            val file = File(temp, "verified.flac").apply {
                parentFile!!.mkdirs(); context.contentResolver.openInputStream(result.saved.audioUri)!!.use { writeBytes(it.readBytes()) }
            }
            val tag = org.jaudiotagger.audio.AudioFileIO.read(file).tag
            assertEquals(song.name, tag.getFirst(org.jaudiotagger.tag.FieldKey.TITLE))
            assertEquals(song.artists, tag.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST))
            assertEquals(song.album, tag.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM))
            assertTrue(tag.getFirst(org.jaudiotagger.tag.FieldKey.LYRICS).contains("内嵌同步歌词"))
            assertTrue(tag.firstArtwork.binaryData.isNotEmpty())
        } finally {
            saved?.files?.forEach { context.contentResolver.delete(it, null, null) }
            container.close(); server.shutdown()
            check(temp.parentFile == context.cacheDir && temp.name.startsWith("nd-download-")); temp.deleteRecursively()
        }
    }
    @Test fun loginBrowseSearchFavoriteAndPlaylistPickerUseSelfHostedLibrary(): Unit = runBlocking {
        val server = fixtureServer()
        val container = AppContainer(context, "nd-ui-${UUID.randomUUID()}", useNativeNetease = false)
        try {
            container.ready.await(); container.sessionRestored.await()
            val vm = NavidromeViewModel(container); val library = LibraryViewModel(container)
            var played = emptyList<io.github.currencortex.music.data.song.Song>()
            compose.setContent { LeiTheme(AppearanceSettings()) {
                top.yukonga.miuix.kmp.basic.Scaffold { NavidromeScreen(vm, library, {}, { songs, _ -> played = songs }) }
            } }
            compose.onNodeWithTag("navidrome_server").performTextInput(server.url("/music/").toString())
            compose.onNodeWithTag("navidrome_username").performTextInput("fixture")
            compose.onNodeWithTag("navidrome_password").performTextInput("fixture-password")
            compose.onNodeWithTag("navidrome_connect").performScrollTo().performClick()
            compose.waitUntil(15000) { container.navidrome.state.value.configured && vm.state.value.entries.isNotEmpty() }
            compose.onNodeWithTag("navidrome_entry_album-uuid").performScrollTo().performClick()
            compose.waitUntil(10000) { vm.state.value.songs.isNotEmpty() }
            compose.onNodeWithText("自建曲库歌曲").performScrollTo().performClick()
            assertEquals("track-uuid", played.single().navidrome!!.id)
            val song = played.single()
            library.togglePrimaryLike(song)
            compose.waitUntil(10000) { container.navidrome.likes.value[song.id] == true && !library.primaryLikeBusy.value }
            assertTrue(container.libraryRepository.statuses.value.isEmpty())
            lateinit var playerVm: PlayerViewModel
            compose.runOnUiThread { container.playbackQueue.replace(listOf(song), 0); playerVm = PlayerViewModel(container) }
            compose.waitUntil(10000) { playerVm.actions.value.songId == song.id && playerVm.actions.value.liked == true }
            compose.runOnUiThread { playerVm.togglePrimaryLike() }
            compose.waitUntil(10000) { playerVm.actions.value.liked == false && !playerVm.actions.value.liking }
            assertNull(playerVm.actions.value.likeCount); assertNull(playerVm.actions.value.commentCount)
            compose.runOnUiThread { library.choosePlaylist(song) }
            compose.waitUntil(10000) { library.availablePlaylists.value.isNotEmpty() && !library.busy.value }
            assertEquals("list-uuid", library.availablePlaylists.value.single().remoteId)
            compose.runOnUiThread { library.addToPlaylist(library.availablePlaylists.value.single()) }
            compose.waitUntil(10000) { library.selectedSong.value == null && !library.busy.value }
            compose.onNodeWithTag("navidrome_back").performScrollTo().performClick()
            compose.onNodeWithTag("navidrome_tab_artists").performScrollTo().performClick()
            compose.waitUntil(10000) { vm.state.value.entries.any { it.id == "artist-uuid" } }
            compose.onNodeWithTag("navidrome_entry_artist-uuid").performScrollTo().performClick()
            compose.waitUntil(10000) { vm.state.value.entries.any { it.id == "album-uuid" } }
            compose.onNodeWithTag("navidrome_query").performScrollTo().performTextInput("自建")
            compose.onNodeWithTag("navidrome_search").performClick()
            compose.waitUntil(10000) { vm.state.value.page.kind == "search" && vm.state.value.songs.isNotEmpty() }
            compose.onNodeWithText("自建曲库歌曲").assertExists()
            assertNull(library.message.value?.takeIf { it.contains("失败") })
        } finally { container.close(); server.shutdown() }
    }
    @Test fun actualDecoderPrefetchOfflineReplayAndLogoutGuardUsePrivateStream(): Unit = runBlocking {
        val server = fixtureServer()
        val container = AppContainer(context, "nd-audio-${UUID.randomUUID()}", useNativeNetease = false)
        var player: ExoPlayer? = null
        try {
            container.ready.await(); container.navidrome.connect(server.url("/music/").toString(), "fixture", "fixture-password")
            val song = container.navidrome.album("album-uuid").single()
            val request = container.audioRequest(song, AudioQuality.AUTO)
            val resolved = container.audioSources.resolve(request)
            assertTrue(resolved.source.url.contains("_cm_navidrome=")); assertFalse(resolved.source.url.contains("fixture-password"))
            container.audioCache.preload(resolved.key, resolved.source, 1024 * 1024)
            assertNotNull(container.audioCache.completeSource(resolved.key))
            val doc = container.lyricsRepository.load(song).document
            assertEquals("内嵌同步歌词", doc.lines.single().text)
            val cached = container.audioSources.resolve(request)
            assertTrue(cached.source.url.startsWith("cache://audio/"))
            withContext(Dispatchers.Main) {
                player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(container.audioCache.dataSourceFactory)).build().apply {
                    volume = 0f; setMediaItem(MediaItemFactory.create(song, cached.source.url, cached.key)); prepare()
                }
            }
            withTimeout(15000) { while (withContext(Dispatchers.Main) { player!!.playbackState != Player.STATE_READY }) delay(100) }
            val bytes = container.audioCache.bytes.value
            assertTrue(bytes > 0)
            val old = container.navidrome.session()
            container.navidrome.disconnect()
            assertNotEquals(old, container.navidrome.session())
            try { container.audioSources.resolve(request); fail("Signed out account must not reuse private cache") } catch (e: ApiException) { assertEquals(ErrorKind.Unauthorized, e.kind) }
        } finally { withContext(Dispatchers.Main) { player?.release() }; container.close(); server.shutdown() }
    }
    @Test fun providedRealServerReadOnlyLoginAlbumsArtLyricsAndDecoder(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val server = args.getString("nd_server") ?: return@runBlocking
        val user = args.getString("nd_user") ?: return@runBlocking
        val password = args.getString("nd_password") ?: return@runBlocking
        val container = AppContainer(context, "nd-live-${UUID.randomUUID()}", useNativeNetease = false)
        var player: ExoPlayer? = null
        try {
            container.ready.await(); container.navidrome.connect(server, user, password)
            assertTrue(container.navidrome.state.value.configured)
            val albums = container.navidrome.albums(); assertTrue(albums.isNotEmpty())
            assertTrue(container.navidrome.artists().isNotEmpty())
            val song = container.navidrome.album(albums.first().id).first()
            assertTrue(song.durationMs > 0)
            if (song.cover.isNotBlank()) withContext(Dispatchers.IO) {
                container.navidromeMedia.newCall(Request.Builder().url(song.cover).build()).execute().use { assertTrue(it.isSuccessful); assertTrue(it.body!!.bytes().size > 100) }
            }
            // No star, playlist edit or scrobble calls against the user's server.
            container.navidrome.lyrics(song)
            val audio = container.audioSources.resolve(container.audioRequest(song, AudioQuality.AUTO))
            withContext(Dispatchers.Main) {
                player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(container.audioCache.dataSourceFactory)).build().apply {
                    volume = 0f; setMediaItem(MediaItemFactory.create(song, audio.source.url, audio.key)); prepare()
                }
            }
            withTimeout(45000) { while (withContext(Dispatchers.Main) { player!!.playbackState != Player.STATE_READY }) {
                withContext(Dispatchers.Main) { assertNull(player!!.playerError) }; delay(100)
            } }
        } finally { withContext(Dispatchers.Main) { player?.release() }; container.navidrome.disconnect(); container.close() }
    }
}
