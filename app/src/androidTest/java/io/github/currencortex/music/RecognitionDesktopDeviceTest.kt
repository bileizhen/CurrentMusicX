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
import io.github.currencortex.music.feature.lyrics.desktop.*
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
            Box(Modifier.width(320.dp)) { RecognitionIslandContent(state.value, DesktopLyricsSettings(), 320f, {}, {}, { opened++ }, { closed++ }) }
        } }
        compose.onNodeWithText("正在聆听").assertIsDisplayed()
        compose.onNodeWithTag("recognition_island_action").performClick()
        assertEquals(1, opened)
        compose.runOnIdle { state.value = RecognitionState(RecognitionPhase.RECOGNIZING) }
        compose.onNodeWithText("正在识别音乐…").assertIsDisplayed()
        compose.runOnIdle { state.value = RecognitionState(RecognitionPhase.MATCHED, songs = listOf(Song(29755418, "Rise to the Dark", "Russell Brower"))) }
        compose.onNodeWithText("Rise to the Dark").assertIsDisplayed()
        compose.onNodeWithTag("desktop_island_progress").assertExists()
        compose.onNodeWithTag("recognition_island_close").performClick()
        assertEquals(1, closed); assertEquals(1, opened)
    }

    @Test fun recognitionReusesTheLyricWindowAndPlaybackLeaseSurvivesRecognition(): Unit = runBlocking {
        assumeTrue(Settings.canDrawOverlays(context))
        lateinit var activity: Activity
        compose.setContent { activity = LocalActivity.current!! }
        val container = AppContainer(context, "shared-island-${java.util.UUID.randomUUID()}", useNativeNetease = false)
        var playback: AutoCloseable? = null
        var recognition: AutoCloseable? = null
        try {
            container.desktopLyrics.ready.await()
            container.desktopLyrics.edit { it.copy(enabled = true, locked = true) }
            withContext(Dispatchers.Main) { playback = DesktopLyricsHosts.acquire(context, container) }
            compose.mainClock.advanceTimeBy(1000)
            withTimeout(3000) { while (!container.desktopLyricsStatus.value.attached || container.desktopLyricsStatus.value.height == 0) delay(10) }
            val original = container.desktopLyricsStatus.value
            withContext(Dispatchers.Main) {
                RecognitionDesktopSession.publish(RecognitionState(RecognitionPhase.MATCHED,
                    songs = listOf(Song(29755418, "Rise to the Dark", "Russell Brower"))))
                RecognitionDesktopSession.attach()
                recognition = DesktopLyricsHosts.acquire(context, container)
                activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            compose.mainClock.advanceTimeBy(1000)
            withTimeout(3000) { while (!container.desktopLyricsStatus.value.islandTouchable) delay(10) }
            delay(650)
            val current = container.desktopLyricsStatus.value
            assertEquals(original.width, current.width)
            assertEquals(original.height, current.height)
            assertTrue("Same compact single-line frame", current.contentHeight / context.resources.displayMetrics.density < 48f)
            screenshot("recognition-island-result.png")
            withContext(Dispatchers.Main) { recognition!!.close(); recognition = null; RecognitionDesktopSession.detach() }
            assertTrue("Playback keeps its existing native window", container.desktopLyricsStatus.value.attached)
            assertTrue(container.desktopLyrics.state.value.enabled)
        } finally {
            withContext(Dispatchers.Main) { RecognitionDesktopSession.detach(); RecognitionDesktopSession.clearResult(); recognition?.close(); playback?.close() }
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
            compose.onNodeWithTag("recognition_source_MICROPHONE").performClick()
            compose.onNodeWithTag("recognition_desktop").performScrollTo().performClick()
            compose.waitForIdle() // Drive the handoff LaunchedEffect before leaving the Compose frame clock.
            withTimeout(3000) { while (!RecognitionDesktopSession.attached.value) delay(10) }
            withTimeout(2300) { while (RecognitionDesktopSession.state.value.seconds < 1) delay(10) }
            assertEquals(RecognitionPhase.RECORDING, RecognitionDesktopSession.state.value.phase)
            assertTrue(RecognitionDesktopSession.state.value.seconds < 3)
            assertFalse("Recording must survive the activity leaving the foreground", activity.hasWindowFocus())
            compose.mainClock.advanceTimeBy(64)
            instrumentation.waitForIdleSync()
            assertNotNull("The original native touch window must own recognition", findNode("正在聆听 · 1 秒，听歌识曲，点击识别或查看结果，长按关闭"))
            screenshot("recognition-island-recording.png")
            val close = findNode("正在聆听 ·") ?: error("Original island must expose its close action")
            assertTrue(close.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            withTimeout(2000) { while (RecognitionDesktopSession.attached.value) delay(10) }
            assertEquals(RecognitionPhase.IDLE, RecognitionDesktopSession.state.value.phase)
            assertNull(findNode("关闭听歌识曲"))
        } finally { withContext(Dispatchers.Main) { RecognitionDesktopService.stop(context) } }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 5000
        while (!condition()) { check(SystemClock.elapsedRealtime() < end) { "Timed out waiting for native overlay" }; SystemClock.sleep(50) }
    }
    /** Real system consent and real AudioPlaybackCapture. Cancel before any fingerprint/upload. */
    @Test fun systemAudioUsesProjectionAndTheOriginalIslandWithoutOpeningTheMicrophone(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recognitionSystemAudio") == "true")
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 29 && Settings.canDrawOverlays(context))
        assumeTrue(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        lateinit var activity: Activity
        compose.setContent { activity = LocalActivity.current!!; LeiTheme(AppearanceSettings()) { MusicRecognitionScreen({}, { _, _ -> }) } }
        try {
            compose.onNodeWithTag("recognition_capture_mode").performScrollTo().performClick()
            compose.onNodeWithTag("recognition_source_SYSTEM").assertIsDisplayed()
            compose.onNodeWithTag("recognition_desktop").performScrollTo().performClick()
            compose.waitForIdle()
            waitUntil { projectionButton(true) != null }
            assertFalse("No capture is allowed before system consent", RecognitionDesktopSession.running.value)
            assertTrue(projectionButton(true)!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            compose.waitForIdle()
            withTimeout(5000) { while (!RecognitionDesktopSession.running.value) { compose.mainClock.advanceTimeBy(16); delay(10) } }
            withTimeout(2500) { while (RecognitionDesktopSession.state.value.seconds < 1) delay(10) }
            assertEquals(RecognitionAudioSource.SYSTEM, RecognitionDesktopSession.state.value.source)
            assertEquals(RecognitionPhase.RECORDING, RecognitionDesktopSession.state.value.phase)
            val sources = context.getSystemService(android.media.AudioManager::class.java).activeRecordingConfigurations.map { it.clientAudioSource }
            // Playback mixes are omitted from this public list on some ROMs. Verify the
            // real projection token as well as the completed AudioRecord reads above.
            assertFalse("System capture cannot open a microphone input", android.media.MediaRecorder.AudioSource.MIC in sources)
            val projection = instrumentation.uiAutomation.executeShellCommand("dumpsys media_projection").use {
                java.io.FileInputStream(it.fileDescriptor).bufferedReader().use { input -> input.readText() }
            }
            assertTrue("System must own a live projection grant for this package", projection.contains(context.packageName))
            compose.mainClock.advanceTimeBy(64)
            assertFalse(activity.hasWindowFocus())
            screenshot("recognition-island-system-audio.png")
            val close = findNode("系统音频 ·") ?: error("System audio must render in the existing island")
            assertTrue(close.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            withTimeout(2000) { while (RecognitionDesktopSession.running.value) delay(10) }
        } finally { withContext(Dispatchers.Main) { RecognitionDesktopService.stop(context) } }
    }

    @Test fun decliningSystemConsentNeverStartsCapture(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recognitionSystemAudio") == "true")
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 29)
        compose.setContent { LeiTheme(AppearanceSettings()) { MusicRecognitionScreen({}, { _, _ -> }) } }
        try {
            compose.onNodeWithTag("recognition_capture_mode").performScrollTo().performClick()
            compose.onNodeWithTag("recognition_action").performClick()
            compose.waitForIdle()
            waitUntil { projectionButton(false) != null }
            assertTrue(projectionButton(false)!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            compose.waitForIdle()
            compose.onNodeWithTag("recognition_permission_message").assertIsDisplayed()
            assertFalse(RecognitionDesktopSession.running.value)
        } finally { withContext(Dispatchers.Main) { RecognitionDesktopService.stop(context) } }
    }

    @Test fun inPageSystemCaptureStopsWhenLeavingInsteadOfStartingADesktopWindow(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recognitionSystemAudio") == "true")
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 29)
        lateinit var activity: Activity
        compose.setContent { activity = LocalActivity.current!!; LeiTheme(AppearanceSettings()) { MusicRecognitionScreen({}, { _, _ -> }) } }
        try {
            compose.onNodeWithTag("recognition_capture_mode").performScrollTo().performClick()
            compose.onNodeWithTag("recognition_action").performClick()
            compose.waitForIdle()
            waitUntil { projectionButton(true) != null }
            assertTrue(projectionButton(true)!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            compose.waitForIdle()
            withTimeout(5000) { while (!RecognitionDesktopSession.running.value) { compose.mainClock.advanceTimeBy(16); delay(10) } }
            withTimeout(2500) { while (RecognitionDesktopSession.state.value.seconds < 1) delay(10) }
            assertEquals(RecognitionAudioSource.SYSTEM, RecognitionDesktopSession.state.value.source)
            assertFalse("Ordinary recognition never enables desktop mode", RecognitionDesktopSession.attached.value)
            withContext(Dispatchers.Main) {
                activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            withTimeout(2000) { while (RecognitionDesktopSession.running.value) delay(10) }
        } finally { withContext(Dispatchers.Main) { RecognitionDesktopService.stop(context) } }
    }

    private fun projectionButton(accept: Boolean): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val text = node.text?.toString().orEmpty()
            if (node.isClickable && node.packageName?.toString() == "com.android.systemui" &&
                (if (accept) text in setOf("立即开始", "开始", "Start now", "Start") else text in setOf("取消", "Cancel"))) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        return find(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun findNode(description: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.contentDescription?.toString()?.startsWith(description) == true) return node
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
