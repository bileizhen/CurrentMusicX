package io.github.currencortex.music

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.emby.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Fixture mutations stay local. Optional real-server test only authenticates, reads and decodes. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EmbyTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private val track = """{"Id":"track-1","Name":"Emby 测试歌曲","Type":"Audio","Artists":["测试歌手"],"Album":"测试专辑","RunTimeTicks":12500000,"Container":"flac","MediaSources":[{"Id":"media-1"}]}"""
    @Test fun loginBrowseAndCachedDecoderRespectLogout(): Unit = runBlocking {
        val audio = InstrumentationRegistry.getInstrumentation().context.assets.open("download/fixture.flac").use { it.readBytes() }
        val reads = AtomicInteger()
        val server = MockWebServer().apply {
            dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    if (path.endsWith("AuthenticateByName")) return MockResponse().setBody("""{"AccessToken":"fixture-token","User":{"Id":"user-1","Policy":{"EnableMediaPlayback":true}}}""")
                    if (request.getHeader("X-Emby-Token") != "fixture-token") return MockResponse().setResponseCode(401)
                    if (path.endsWith("Logout")) return MockResponse().setResponseCode(204)
                    if (path.endsWith("stream")) return MockResponse().setHeader("Content-Type", "audio/flac").setBody(Buffer().write(audio))
                    if (path.endsWith("Items")) {
                        reads.incrementAndGet()
                        return if (request.requestUrl!!.queryParameter("IncludeItemTypes") == "MusicAlbum")
                            MockResponse().setBody("""{"Items":[{"Id":"album-1","Name":"测试专辑","Type":"MusicAlbum"}],"TotalRecordCount":1}""")
                        else MockResponse().setBody("""{"Items":[$track],"TotalRecordCount":1}""")
                    }
                    return MockResponse().setResponseCode(404)
                }
            }
            start()
        }
        val container = AppContainer(context, "emby-fixture-${UUID.randomUUID()}", useNativeNetease = false)
        var player: ExoPlayer? = null
        var vm: EmbyViewModel? = null
        try {
            container.ready.await()
            vm = withContext(Dispatchers.Main) { EmbyViewModel(container) }
            compose.setContent { LeiTheme(AppearanceSettings()) { EmbyScreen(vm!!, { _, _ -> }, {}) } }
            compose.onNodeWithTag("emby_server").performTextInput(server.url("/").toString())
            compose.onNodeWithTag("emby_username").performTextInput("fixture")
            compose.onNodeWithTag("emby_password").performTextInput("fixture-password")
            compose.onNodeWithTag("emby_connect").performScrollTo().performClick()
            compose.waitUntil(15000) { container.emby.state.value.configured && vm!!.state.value.loaded }
            compose.onNodeWithText("测试专辑").performClick()
            compose.waitUntil(15000) { vm!!.state.value.page.songs.isNotEmpty() }
            compose.onNodeWithText("Emby 测试歌曲").assertIsDisplayed()
            val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
            java.io.File(context.getExternalFilesDir(null), "emby-verification.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            val before = reads.get()
            withContext(Dispatchers.Main) { assertTrue(vm!!.back()) }
            compose.waitUntil(5000) { vm!!.state.value.route.kind == "albums" }
            assertEquals(before, reads.get())
            val song = container.emby.browse("songs").songs.single()
            val request = container.audioRequest(song, AudioQuality.AUTO)
            val resolved = container.audioSources.resolve(request)
            container.audioCache.preload(resolved.key, resolved.source, 1024 * 1024)
            assertNotNull(container.audioCache.completeSource(resolved.key))
            val cached = container.audioSources.resolve(request)
            assertTrue(cached.source.url.startsWith("cache://audio/"))
            withContext(Dispatchers.Main) {
                player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(container.audioCache.dataSourceFactory)).build().apply {
                    volume = 0f; setMediaItem(MediaItemFactory.create(song, cached.source.url, cached.key)); prepare()
                }
            }
            withTimeout(15000) { while (withContext(Dispatchers.Main) { player!!.playbackState != Player.STATE_READY }) delay(100) }
            container.emby.disconnect()
            try { container.audioSources.resolve(request); fail("Logged out account reused cached Emby audio") }
            catch (e: ApiException) { assertEquals(ErrorKind.Unauthorized, e.kind) }
        } finally {
            withContext(Dispatchers.Main) { player?.release(); vm?.viewModelScope?.cancel() }
            container.close(); server.shutdown()
        }
    }
    @Test fun providedRealServerReadOnlyDecodeAndSeek(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val server = args.getString("emby_server")
        Assume.assumeTrue("Real-server credentials were not supplied", server != null)
        val user = requireNotNull(args.getString("emby_user"))
        val password = requireNotNull(args.getString("emby_password"))
        val container = AppContainer(context, "emby-live-${UUID.randomUUID()}", useNativeNetease = false)
        var player: ExoPlayer? = null
        try {
            container.ready.await(); container.emby.connect(server!!, user, password)
            assertTrue(container.emby.browse("albums").entries.isNotEmpty())
            assertTrue(container.emby.browse("artists").entries.isNotEmpty())
            container.emby.browse("playlists")
            val result = container.emby.browse("search", query = args.getString("emby_query") ?: "晴天")
            assertTrue(result.songs.isNotEmpty())
            val song = result.songs.first()
            if (song.cover.isNotBlank()) withContext(Dispatchers.IO) {
                container.navidromeMedia.newCall(Request.Builder().url(song.cover).build()).execute().use { assertTrue(it.isSuccessful); assertTrue(it.body!!.bytes().size > 100) }
            }
            val resolved = container.audioSources.resolve(container.audioRequest(song, AudioQuality.AUTO))
            withContext(Dispatchers.Main) {
                player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(container.audioCache.dataSourceFactory)).build().apply {
                    volume = 0f; setMediaItem(MediaItemFactory.create(song, resolved.source.url, resolved.key)); prepare(); play()
                }
            }
            withTimeout(60000) { while (withContext(Dispatchers.Main) { player!!.currentPosition < 1000 }) {
                withContext(Dispatchers.Main) { assertNull(player!!.playerError) }; delay(100)
            } }
            withContext(Dispatchers.Main) { assertTrue(player!!.isCurrentMediaItemSeekable); player!!.seekTo(10000) }
            withTimeout(45000) { while (withContext(Dispatchers.Main) { player!!.currentPosition < 11000 }) {
                withContext(Dispatchers.Main) { assertNull(player!!.playerError) }; delay(100)
            } }
        } finally {
            withContext(Dispatchers.Main) { player?.release() }
            container.emby.disconnect(); container.close()
        }
    }
}
