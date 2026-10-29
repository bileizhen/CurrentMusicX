package io.github.currencortex.music

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.recognition.*
import io.github.currencortex.music.ui.theme.LeiTheme
import io.github.currencortex.music.data.settings.AppearanceSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.io.File

class RecognitionDesktopDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>().also { check(it.packageName.endsWith(".verification")) }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var accessibilityFlags = 0
    @Before fun inspectOverlayWindows() {
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            accessibilityFlags = flags
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }
    @After fun restoreAccessibilityFlags() {
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply { flags = accessibilityFlags }
    }

    @Test fun islandShowsActualResultMetadataAndCloseIsIndependentOfOpening() {
        var opened = 0; var closed = 0
        val state = mutableStateOf(RecognitionState(RecognitionPhase.RECORDING, seconds = 5))
        compose.setContent { LeiTheme(AppearanceSettings()) {
            Box(Modifier.width(320.dp)) { RecognitionIslandPill(state.value, { opened++ }, { closed++ }) }
        } }
        compose.onNodeWithText("正在聆听 · 5 秒").assertIsDisplayed()
        compose.onNodeWithTag("recognition_island_action").performClick()
        assertEquals(1, opened)
        compose.runOnIdle { state.value = RecognitionState(RecognitionPhase.RECOGNIZING) }
        compose.onNodeWithText("正在识别音乐…").assertIsDisplayed()
        compose.runOnIdle { state.value = RecognitionState(RecognitionPhase.MATCHED, songs = listOf(Song(29755418, "Rise to the Dark", "Russell Brower"))) }
        compose.onNodeWithText("Rise to the Dark").assertIsDisplayed()
        compose.onNodeWithText("Russell Brower").assertIsDisplayed()
        compose.onNodeWithTag("recognition_island_close").performClick()
        assertEquals(1, closed); assertEquals(1, opened)
    }

    @Test fun realOverlayShowsResultOnDesktopAndClosesWithoutTakingOverTheScreen(): Unit = runBlocking {
        assumeTrue(Settings.canDrawOverlays(context))
        lateinit var activity: Activity
        compose.setContent { activity = LocalActivity.current!! }
        var island: RecognitionIslandWindow? = null
        val container = AppContainer(context, "recognition-desktop-${java.util.UUID.randomUUID()}", useNativeNetease = false)
        var lyricsHost: io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsHost? = null
        var opened = false; var closed = false
        val state = MutableStateFlow(RecognitionState(RecognitionPhase.MATCHED,
            songs = listOf(Song(29755418, "Rise to the Dark", "Russell Brower"))))
        try {
            container.desktopLyrics.ready.await()
            container.desktopLyrics.edit { it.copy(enabled = true, locked = true) }
            withContext(Dispatchers.Main) {
                lyricsHost = io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsHost(context,
                    MutableStateFlow(io.github.currencortex.music.feature.lyrics.data.PlaybackLyricsState()),
                    MutableStateFlow(io.github.currencortex.music.core.media.PlayerState(song = Song(2, "Paused music"))),
                    container.desktopLyrics, container.desktopLyricsStatus)
            }
            withTimeout(3000) { while (!container.desktopLyricsStatus.value.visible) delay(10) }
            withContext(Dispatchers.Main) {
                island = RecognitionIslandWindow(context, state,
                    { io.github.currencortex.music.feature.lyrics.desktop.DesktopLyricsSettings() },
                    { opened = true }, { closed = true; island?.close(); RecognitionDesktopSession.detach() })
                island!!.show()
                RecognitionDesktopSession.attach()
                activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            withTimeout(3000) { while (container.desktopLyricsStatus.value.visible) delay(10) }
            assertFalse("Lyric island cannot steal recognition input", container.desktopLyricsStatus.value.islandTouchable)
            waitUntil { findNode("Rise to the Dark，Russell Brower") != null }
            delay(650) // Wait for the launcher's return animation before inspecting native input.
            screenshot("recognition-island-result.png")
            assertFalse(opened)
            assertTrue(findNode("关闭听歌识曲")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitUntil { closed }
            waitUntil { findNode("Rise to the Dark，Russell Brower") == null }
            withTimeout(3000) { while (!container.desktopLyricsStatus.value.visible) delay(10) }
            assertTrue("Recognition cannot disable the saved lyric preference", container.desktopLyrics.state.value.enabled)
        } finally {
            withContext(Dispatchers.Main) { island?.close(); RecognitionDesktopSession.detach(); RecognitionDesktopSession.clearResult(); lyricsHost?.close() }
            container.close()
        }
    }

    /** Explicit opt-in: records under 3 seconds, so cancellation cannot upload ambient audio. */
    @Test fun foregroundMicrophoneContinuesOnHomeAndCloseReleasesTheService(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recognitionMicrophone") == "true")
        assumeTrue(Settings.canDrawOverlays(context))
        assumeTrue(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        lateinit var activity: Activity
        compose.setContent { activity = LocalActivity.current!!; LeiTheme(AppearanceSettings()) { MusicRecognitionScreen({}, { _, _ -> }) } }
        try {
            compose.onNodeWithTag("recognition_desktop").performScrollTo().performClick()
            compose.waitForIdle() // Drive the handoff LaunchedEffect before leaving the Compose frame clock.
            withTimeout(3000) { while (!RecognitionDesktopSession.attached.value) delay(10) }
            withTimeout(2300) { while (RecognitionDesktopSession.state.value.seconds < 1) delay(10) }
            assertEquals(RecognitionPhase.RECORDING, RecognitionDesktopSession.state.value.phase)
            assertTrue(RecognitionDesktopSession.state.value.seconds < 3)
            assertFalse("Recording must survive the activity leaving the foreground", activity.hasWindowFocus())
            compose.mainClock.advanceTimeBy(64)
            instrumentation.waitForIdleSync()
            assertNotNull("The background capsule must render elapsed capture time", findNode("正在聆听 · 1 秒，靠近音源 · 最长 10 秒"))
            screenshot("recognition-island-recording.png")
            val close = findNode("关闭听歌识曲") ?: error("Real overlay must expose a close action")
            assertTrue(close.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            withTimeout(2000) { while (RecognitionDesktopSession.attached.value) delay(10) }
            assertEquals(RecognitionPhase.IDLE, RecognitionDesktopSession.state.value.phase)
            assertNull(findNode("关闭听歌识曲"))
        } finally { withContext(Dispatchers.Main) { RecognitionDesktopService.stop(context) } }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 5000
        while (!condition()) { check(SystemClock.elapsedRealtime() < end) { "Timed out waiting for native overlay" }; SystemClock.sleep(50) }
    }
    private fun findNode(description: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.contentDescription?.toString() == description) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        for (window in instrumentation.uiAutomation.windows) find(window.root)?.let { return it }
        return find(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun screenshot(name: String) {
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
