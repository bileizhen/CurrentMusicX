package io.github.currencortex.music

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.core.security.SecureTokenStore
import io.github.currencortex.music.ui.CurrentMusicApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeCapabilitiesTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private val container get() = app.container
    private lateinit var server: MockWebServer
    @Volatile private var searchCount = 0
    @Volatile private var lastSearchKeyword = ""
    @Volatile private var lastSearchOffset = ""
    private var highSpec = false
    private var prepared = false
    private lateinit var originalMusic: MusicSettings
    private lateinit var originalAppearance: AppearanceSettings
    private var originalAutoUpdate = false
    private lateinit var originalHistory: List<io.github.currencortex.music.data.local.SearchHistoryEntity>
    private lateinit var originalQueue: QueueSnapshot
    @Before fun prepare() = runBlocking {
        container.ready.await()
        container.sessionRestored.await()
        check(container.accountRepository.token == null && !container.playerController.state.value.playing) {
            "Use a logged-out test device with playback paused; existing account credentials are preserved."
        }
        originalMusic = container.musicSettings.snapshot()
        originalAppearance = container.settings.state.value
        originalAutoUpdate = container.updateSettings.snapshot().autoCheckOnLaunch
        originalHistory = container.database.music().history().first()
        originalQueue = container.playbackQueue.state.value
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when (path) {
                    "/cm/auth/login" -> json("""{"token":"instrumented-token","user":{"id":5,"username":"user","nickname":"Music user"}}""")
                    "/cm/auth/me" -> if (request.getHeader("Authorization") != null) json("""{"id":5,"nickname":"Music user"}""") else MockResponse().setResponseCode(401)
                    "/cm/auth/logout" -> json("{}")
                    "/cm/ncm/search" -> {
                        lastSearchKeyword = request.requestUrl!!.queryParameter("keywords").orEmpty()
                        lastSearchOffset = request.requestUrl!!.queryParameter("offset").orEmpty()
                        searchCount++
                        json("""{"songs":[{"ncm_id":1,"name":"Track one","artists":"Artist","album":"Album","duration":30000},{"ncm_id":2,"name":"Track two","artists":"Artist","duration":30000}],"totals":{"song":4},"hasMore":{"song":true}}""")
                    }
                    "/cm/ncm/song/url" -> {
                        val rate = if (highSpec && request.requestUrl!!.queryParameter("level") != "lossless") 192000 else 44100
                        json("""{"url":"${server.url("/audio.wav")}","level":"standard","sr":$rate,"ch":1}""")
                    }
                    "/cm/ncm/lyric" -> json("""{"lines":[{"t":0,"txt":"Line one"},{"t":10000,"txt":"Line two","trans":"Translation"}]}""")
                    "/audio.wav" -> MockResponse().setHeader("Content-Type", "audio/wav").setBody(Buffer().write(wav()))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        prepared = true
        container.accountRepository.clear()
        container.musicSettings.setServer(server.url("/cm/").toString())
        container.accountRepository.server = server.url("/cm/").toString()
        container.musicSettings.setQuality(AudioQuality.STANDARD)
        container.musicSettings.setWarning(false)
        container.updateSettings.setAutoCheck(false)
        container.settings.edit { AppearanceSettings(blur = false) }
        withContext(Dispatchers.Main) { container.playerController.clear() }
    }
    @After fun finish() = runBlocking {
        if (!prepared) return@runBlocking
        withContext(Dispatchers.Main) { container.playerController.clear(); container.playerController.disconnect() }
        app.stopService(android.content.Intent(app, MusicService::class.java))
        container.accountRepository.clear()
        container.musicSettings.setServer(originalMusic.server)
        container.accountRepository.server = originalMusic.server
        container.musicSettings.setQuality(originalMusic.quality)
        container.musicSettings.setWarning(originalMusic.warnHighSpec)
        container.musicSettings.setRestore(originalMusic.restoreQueue)
        container.musicSettings.setAccount(originalMusic.accountId, originalMusic.nickname)
        container.settings.edit { originalAppearance }
        container.updateSettings.setAutoCheck(originalAutoUpdate)
        container.database.music().clearHistory()
        originalHistory.forEach { container.database.music().remember(it) }
        container.playbackQueue.restore(originalQueue)
        container.database.music().saveQueue(io.github.currencortex.music.data.local.QueueSnapshotEntity(
            payload = io.github.currencortex.music.core.network.ApiJson.encodeToString(originalQueue)))
        server.shutdown()
    }
    @Test fun tabsLoginSearchSubmitAndSessionSurviveRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { CurrentMusicApp(container) }
        compose.onNodeWithTag("tab_3").performClick()
        compose.onNodeWithTag("login_username").performTextInput("user")
        compose.onNodeWithTag("login_password").performTextInput("pass")
        compose.onNodeWithTag("login_submit").performClick()
        compose.waitUntil(10000) { container.accountRepository.state.value.account != null }
        compose.onNodeWithTag("tab_2").performClick()
        compose.onNodeWithTag("search_input").performTextInput("test")
        assertEquals(0, searchCount)
        compose.onNodeWithTag("submit_search").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Track one").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("search_input").performTextReplacement("not submitted")
        assertEquals(1, searchCount)
        compose.onNodeWithText("加载更多").performScrollTo().performClick()
        compose.waitUntil(10000) { searchCount == 2 }
        assertEquals("test", lastSearchKeyword)
        assertEquals("2", lastSearchOffset)
        compose.onNodeWithTag("search_input").assertTextContains("not submitted")
        compose.onNodeWithTag("tab_0").performClick()
        compose.onNodeWithTag("tab_2").performClick()
        compose.onAllNodesWithText("Track one")[0].assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodesWithText("Track one")[0].assertExists()
        assertEquals(5L, container.accountRepository.state.value.account!!.id)
    }
    @Test fun mediaServiceQueueSeekLyricsAndControllerPauseResume() {
        compose.setContent { CurrentMusicApp(container) }
        compose.onNodeWithTag("tab_2").performClick()
        compose.onNodeWithTag("search_input").performTextInput("test")
        compose.onNodeWithTag("submit_search").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Track one").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Track one").performClick()
        if (compose.onAllNodesWithText("继续播放").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("继续播放").performClick()
        compose.waitUntil(20000) { container.playerController.state.value.playing }
        compose.onNodeWithTag("mini_player").performClick()
        compose.onNodeWithTag("player_seek").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.5f) }
        compose.waitUntil(10000) { container.playerController.state.value.positionMs > 12000 }
        compose.onNodeWithTag("lyrics_options").performClick()
        compose.onNodeWithTag("open_lyrics").performClick()
        compose.onNodeWithTag("lyrics_panel").assertExists()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Line two").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { withContext(Dispatchers.Main) { container.playerController.connect().pause() } }
        compose.waitUntil(10000) { !container.playerController.state.value.playing }
        runBlocking { withContext(Dispatchers.Main) { container.playerController.connect().play() } }
        compose.waitUntil(10000) { container.playerController.state.value.playing }
        runBlocking { withContext(Dispatchers.Main) { container.playerController.connect().seekToNextMediaItem() } }
        compose.waitUntil(10000) { container.playbackQueue.state.value.current?.id == 2L && container.playerController.state.value.playing }
        var platformToken: android.media.session.MediaSession.Token? = null
        compose.waitUntil(10000) {
            platformToken = app.getSystemService(android.app.NotificationManager::class.java).activeNotifications
                .firstOrNull { it.notification.extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) }
                ?.notification?.extras?.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION)
            platformToken != null
        }
        // This platform transport is the same session exposed to notification,
        // lock-screen and Bluetooth clients, independent from our UI controller.
        val platform = android.media.session.MediaController(app, platformToken!!)
        assertTrue(platform.playbackState!!.actions and android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
        platform.transportControls.pause()
        compose.waitUntil(10000) { !container.playerController.state.value.playing }
        platform.transportControls.play()
        compose.waitUntil(10000) { container.playerController.state.value.playing }
        platform.transportControls.skipToPrevious()
        compose.waitUntil(10000) { container.playbackQueue.state.value.current?.id == 1L && container.playerController.state.value.playing }
        // UI controller disconnection leaves the service as the player owner.
        runBlocking { withContext(Dispatchers.Main) { container.playerController.disconnect() } }
        compose.runOnIdle { }
        runBlocking { withContext(Dispatchers.Main) { val control = container.playerController.connect(); assertTrue(control.isPlaying) } }
    }
    @Test fun settingsAppearanceDarkModeGlassAndSystemBack() {
        lateinit var activity: ComponentActivity
        compose.setContent { activity = LocalContext.current.activity(); CurrentMusicApp(container) }
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("网络与播放").performClick()
        compose.onNodeWithTag("server_input").assertExists()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithText("外观").performScrollTo().performClick()
        compose.onNodeWithText("深色").performClick()
        compose.waitUntil(5000) { container.settings.state.value.themeMode == ThemeMode.DARK }
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("settings_screen").assertExists()
        runBlocking { container.settings.edit { it.copy(blur = true, liquidGlass = true) } }
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        if (android.os.Build.VERSION.SDK_INT >= 33)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("glass_floating_bar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_3").performClick()
        compose.onNodeWithTag("login_screen").assertExists()
    }
    @Test fun keystoreTokenIsEncryptedAndRestores() {
        val store = SecureTokenStore(app)
        try {
            store.write("instrumentation-secret")
            assertEquals("instrumentation-secret", SecureTokenStore(app).read())
            val encrypted = java.io.File(app.noBackupFilesDir, "session.aes")
            assertTrue(encrypted.renameTo(java.io.File(app.noBackupFilesDir, "session.aes.bak")))
            assertEquals("instrumentation-secret", SecureTokenStore(app).read())
            assertFalse(java.io.File(app.noBackupFilesDir, "session.aes").readBytes().toString(Charsets.UTF_8).contains("instrumentation-secret"))
        } finally { store.clear() }
    }
    @Test fun highSpecRequiresDecisionAndLosslessChoiceResumesPlayback() {
        highSpec = true
        runBlocking { container.musicSettings.setWarning(true) }
        compose.setContent { CurrentMusicApp(container) }
        compose.runOnUiThread { container.playerController.playList(listOf(io.github.currencortex.music.data.song.Song(1, "High spec")), 0) }
        compose.waitUntil(20000) { container.playerController.state.value.warning != null }
        assertFalse(container.playerController.state.value.playing)
        compose.onNodeWithText("切换无损").performClick()
        compose.waitUntil(20000) { container.playerController.state.value.playing }
        assertEquals(AudioQuality.LOSSLESS, container.musicSettings.state.value.quality)
        assertNull(container.playerController.state.value.warning)
    }
    @Test fun activityRecreationRetainsSearchAccountAndPlayingService() {
        runBlocking { container.authRepository.login("user", "pass") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("tab_2").performClick()
            compose.onNodeWithTag("search_input").performTextInput("rotation")
            compose.onNodeWithTag("submit_search").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("Track one").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnUiThread { container.playerController.playList(listOf(io.github.currencortex.music.data.song.Song(1, "Track one")), 0) }
            compose.waitUntil(20000) { container.playerController.state.value.playing }
            val requests = searchCount
            scenario.recreate()
            compose.onNodeWithTag("search_input").assertTextContains("rotation")
            compose.onNodeWithTag("mini_player").assertExists()
            assertEquals(requests, searchCount)
            assertEquals(5L, container.accountRepository.state.value.account!!.id)
            assertTrue(container.playerController.state.value.playing)
        }
    }
    @Test fun serverChangeClearsAccountAndNeverSendsOldTokenToNewServer() {
        runBlocking { container.authRepository.login("user", "pass") }
        MockWebServer().use { destination ->
            destination.enqueue(MockResponse().setResponseCode(401))
            destination.start()
            compose.setContent { CurrentMusicApp(container) }
            compose.onNodeWithText("设置").performClick()
            compose.onNodeWithText("网络与播放").performClick()
            compose.onNodeWithTag("server_input").performTextReplacement(destination.url("/new").toString())
            compose.onNodeWithText("保存服务器").performClick()
            compose.waitUntil(10000) { destination.requestCount == 1 }
            val request = destination.takeRequest()
            assertEquals("/new/auth/me", request.path)
            assertNull(request.getHeader("Authorization"))
            assertNull(container.accountRepository.token)
            assertNull(container.accountRepository.state.value.account)
            compose.waitUntil(5000) { container.musicSettings.state.value.server == destination.url("/new/").toString() }
        }
    }
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun wav(): ByteArray {
        val samples = 44100 * 30
        return ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(44100); putInt(88200); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples * 2)
            repeat(samples) { putShort(0) }
        }.array()
    }
}
private tailrec fun Context.activity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.activity()
    else -> error("No activity")
}
