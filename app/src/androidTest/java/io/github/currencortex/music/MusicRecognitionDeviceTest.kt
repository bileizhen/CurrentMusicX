package io.github.currencortex.music

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.home.HomeLibraryShortcuts
import io.github.currencortex.music.feature.recognition.*
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.ui.CurrentMusicApp
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.auth.UserDto
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

class MusicRecognitionDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>().also { check(it.packageName.endsWith(".verification")) }

    @Test fun fullAppRecognitionKeepsToolbarBelowSystemBarsAndContentAboveMiniPlayer(): Unit = runBlocking {
        val server = MockWebServer().apply {
            dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = MockResponse().setHeader("Content-Type", "application/json")
                    .setBody(when (request.requestUrl!!.encodedPath) {
                        "/cm/daily" -> """{"daily":[],"forYou":[],"artists":[]}"""
                        "/cm/plays/recent" -> """{"songs":[]}"""
                        "/cm/playlists" -> """{"playlists":[]}"""
                        "/cm/ncmbind" -> """{"bound":false}"""
                        else -> """{"code":200}"""
                    })
            }
            start()
        }
        val container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "recognition-layout-${UUID.randomUUID()}")
        try {
            container.ready.await(); container.sessionRestored.await()
            val endpoint = server.url("/cm/").toString()
            container.musicSettings.setServer(endpoint); container.accountRepository.server = endpoint
            container.accountRepository.save("isolated-recognition-layout", UserDto(7, nickname = "布局验证"))
            container.updateSettings.setAutoCheck(false)
            container.desktopLyrics.markRecommendationSeen()
            container.settings.edit { it.copy(blur = true, themeMode = io.github.currencortex.music.data.settings.ThemeMode.LIGHT) }
            val paused = Song(55, "Paused recognition fixture", "CurrentMusic")
            container.playerController.queue.replace(listOf(paused), 0)
            container.playerController.state.value = PlayerState(song = paused)
            lateinit var activity: ComponentActivity
            var openRecognition by mutableIntStateOf(0)
            compose.setContent {
                activity = LocalActivity.current as ComponentActivity
                LaunchedEffect(activity) { activity.enableEdgeToEdge() }
                CurrentMusicApp(container, openRecognitionRequest = openRecognition)
            }
            compose.waitUntil(15000) { compose.onAllNodesWithTag("open_recognition").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("open_recognition").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onNodeWithTag("recognition_header").isDisplayed() }
            compose.onNodeWithTag("recognition_header").assertIsDisplayed()
            compose.onNodeWithTag("mini_player").assertIsDisplayed()
            val safeTop = ViewCompat.getRootWindowInsets(activity.window.decorView)!!.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            val header = compose.onNodeWithTag("recognition_header").fetchSemanticsNode().boundsInRoot
            assertTrue("Toolbar must respect real system insets: $header / $safeTop", header.top >= safeTop - 1)
            val mini = compose.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
            val action = compose.onNodeWithTag("recognition_action").fetchSemanticsNode().boundsInRoot
            val status = compose.onNodeWithTag("recognition_status").fetchSemanticsNode().boundsInRoot
            assertTrue("Listening controls must fit above the floating player", action.bottom < mini.top && status.bottom < mini.top)
            compose.onNodeWithTag("recognition_disclosure").performScrollTo()
            val disclosure = compose.onNodeWithTag("recognition_disclosure").fetchSemanticsNode().boundsInRoot
            assertTrue("The last text must clear the mini player: $disclosure / $mini", disclosure.bottom <= mini.top)
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                File(context.getExternalFilesDir(null), "recognition-full-app-aligned.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.onNodeWithTag("recognition_history").performClick()
            assertTrue(compose.onNodeWithTag("recognition_header").fetchSemanticsNode().boundsInRoot.top >= safeTop - 1)
            compose.onNodeWithTag("recognition_back").performClick()
            compose.onNodeWithTag("recognition_back").performClick()
            compose.waitUntil(5000) { compose.onNodeWithTag("open_recognition").isDisplayed() }
            compose.runOnIdle { openRecognition++ }
            compose.waitUntil(5000) { compose.onNodeWithTag("recognition_header").isDisplayed() }
        } finally {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                File(context.getExternalFilesDir(null), "recognition-layout-diagnostic.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            container.close(); server.shutdown()
        }
    }

    @Test fun historyPersistsOnlySongMetadataAndDeduplicatesRecentMatches() {
        val suffix = "-recognition-test-${UUID.randomUUID()}"
        val original = context
        val isolated = object : ContextWrapper(original) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(name + suffix, mode)
        }
        try {
            val history = RecognitionHistory(isolated)
            history.add(Song(1, "First")); history.add(Song(2, "Second")); history.add(Song(1, "First again"))
            val restored = RecognitionHistory(isolated).read()
            assertEquals(listOf(1L, 2L), restored.map { it.song.id })
            assertEquals("First again", restored.first().song.name)
            assertTrue(original.getSharedPreferences("music-recognition$suffix", Context.MODE_PRIVATE).all.keys == setOf("matches"))
            history.clear(); assertTrue(history.read().isEmpty())
        } finally { original.deleteSharedPreferences("music-recognition$suffix") }
    }

    @Test fun homeShortcutOpensRecognitionWithoutStartingTheMicrophoneAndHistoryCanReturn() {
        var route by mutableStateOf("home")
        compose.setContent {
            LeiTheme(AppearanceSettings(blur = false)) {
                if (route == "home") HomeLibraryShortcuts({ route = it }, false)
                else MusicRecognitionScreen({ route = "home" }, { _, _ -> error("No playback requested") })
            }
        }
        compose.onNodeWithTag("open_recognition").assertTextContains("听歌识曲").performClick()
        compose.onNodeWithTag("recognition_status").assertTextEquals("点击开始听歌识曲")
        compose.onNodeWithTag("recognition_history").performClick()
        compose.onNodeWithTag("recognition_history_list").assertIsDisplayed()
        compose.onNodeWithTag("recognition_back").performClick()
        compose.onNodeWithTag("recognition_status").assertTextEquals("点击开始听歌识曲")
        screenshot("recognition-idle.png")
        compose.onNodeWithTag("recognition_back").performClick()
        compose.onNodeWithTag("open_recognition").assertIsDisplayed()
    }

    @Test fun permissionDenialAndMatchedSongPlaybackAreVisibleAndDoNotAutoPlay() {
        var state by mutableStateOf(RecognitionState())
        var actions = 0; var played: Song? = null
        val song = Song(7, "识别到的音乐", "歌手", "专辑")
        compose.setContent {
            LeiTheme(AppearanceSettings(blur = false)) {
                MusicRecognitionContent(state, "需要麦克风权限才能识曲，可在系统设置中开启", 0.dp,
                    { actions++ }, {}, { songs, index -> played = songs[index] })
            }
        }
        compose.onNodeWithTag("recognition_permission_message").assertIsDisplayed()
        compose.onNodeWithTag("recognition_action").performClick()
        compose.runOnIdle { assertEquals(1, actions); state = RecognitionState(RecognitionPhase.MATCHED, songs = listOf(song)) }
        compose.onNodeWithTag("recognition_result_0").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertNull(played) }
        compose.onNodeWithTag("recognition_result_0").performClick()
        compose.runOnIdle { assertEquals(song, played) }
    }

    @Test fun microphoneCaptureCanBeCancelledAndReopenedWithoutKeepingAudio(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recognitionMicrophone") == "true")
        var opened by mutableStateOf(true)
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            if (opened) MusicRecognitionScreen({ opened = false }, { _, _ -> error("No ambient clip should be uploaded") })
        } }
        compose.onNodeWithTag("recognition_action").performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 5000
        fun allowButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.viewIdResourceName?.endsWith(":id/permission_allow_foreground_only_button") == true) return node
            for (index in 0 until node.childCount) allowButton(node.getChild(index))?.let { return it }
            return null
        }
        while (SystemClock.uptimeMillis() < deadline) {
            val node = allowButton(automation.rootInActiveWindow)
            if (node != null) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); break }
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) break
            SystemClock.sleep(100)
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("正在聆听 · 1 秒").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("recognition_back").performClick()
        compose.onNodeWithTag("music_recognition").assertDoesNotExist()
        delay(200)
        val recorder = MicrophoneRecorder()
        val started = CompletableDeferred<Unit>()
        val first = launch { recorder.record(AtomicBoolean(false)) { _, _ -> started.complete(Unit) } }
        withTimeout(5000) { started.await() }
        withTimeout(2000) { first.cancelAndJoin() }
        val stop = AtomicBoolean(false)
        val samples = withTimeout(5000) { recorder.record(stop) { seconds, _ -> if (seconds >= 1) stop.set(true) } }
        assertTrue(samples.size in 8000..10000)
        samples.fill(0)
    }

    /** Audio is pushed into the isolated app's files directory explicitly; never bundled in the APK. */
    @Test fun localFingerprintAndPhoneNetworkRecognizeARealMusicClip(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("recognitionLive") == "true")
        val directory = checkNotNull(context.getExternalFilesDir(null)).canonicalFile
        val input = File(directory, "recognition-test.pcm").canonicalFile
        check(input.parentFile == directory && input.length() in 48_000..160_000)
        val bytes = input.readBytes()
        val pcm = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        val fingerprint = withTimeout(15000) { NeteaseFingerprint(context).encode(pcm) }
        val songs = NeteaseRecognitionRepository().recognize(fingerprint, pcm.size / 8000.0)
        pcm.fill(0); bytes.fill(0)
        assertTrue("The official endpoint must identify the validation clip", songs.any { it.id == 29755418L })
        compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
            MusicRecognitionContent(RecognitionState(RecognitionPhase.MATCHED, songs = songs), null, 0.dp, {}, {}, { _, _ -> })
        } }
        compose.onNodeWithTag("recognition_result_0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rise to the Dark").assertIsDisplayed()
        screenshot("recognition-live-result.png")
    }

    private fun screenshot(name: String) {
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
