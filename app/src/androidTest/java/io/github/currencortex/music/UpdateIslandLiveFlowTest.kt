package io.github.currencortex.music

import android.app.NotificationManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import io.github.currencortex.music.core.media.IslandNoticeKind
import io.github.currencortex.music.core.media.SoundEffectNoticePhase
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.update.UpdateDialogContent
import io.github.currencortex.music.ui.theme.LeiTheme
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real WorkManager/download/overlay test. It never requests APK installation. */
class UpdateIslandLiveFlowTest {
    @Test fun downloadFromButtonContinuesOnHomeAndReturnsFromCompletionNotification(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Live download requires explicit public asset metadata", args.getString("live_update_url") != null)
        val app = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        check(app.packageName.endsWith(".verification"))
        assertTrue("Isolated app needs overlay permission", Settings.canDrawOverlays(app))
        val container = app.container
        withTimeout(15_000) { container.ready.await(); container.desktopLyrics.ready.await() }
        val saved = container.desktopLyrics.state.value
        val release = AppRelease("99.0.1", "真机验证：关闭弹窗及返回桌面后继续下载，不安装。",
            "https://github.com/bileizhen/CurrentMusicX/releases", args.getString("live_update_url"), "live-verified.apk", false,
            args.getString("live_update_size")!!.toLong(), args.getString("live_update_sha256")!!)
        val visible = androidx.compose.runtime.mutableStateOf(true)
        val directory = File(app.getExternalFilesDir(null), "update-island-realtime").apply { mkdirs() }
        fun screenshot(name: String) {
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            java.io.FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
        suspend fun awaitCondition(label: String = "condition", condition: () -> Boolean) {
            try { withTimeout(20_000) { while (!condition()) delay(25) } }
            catch (error: TimeoutCancellationException) {
                screenshot("timeout-$label")
                throw AssertionError("$label: ${container.desktopLyricsStatus.value}; ${container.islandNotices.value}", error)
            }
        }
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        fun findText(text: String): AccessibilityNodeInfo? {
            fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (node.text?.contains(text) == true || node.contentDescription?.contains(text) == true) return node
                for (index in 0 until node.childCount) node.getChild(index)?.let { find(it)?.let { found -> return found } }
                return null
            }
            return instrumentation.uiAutomation.windows.asSequence().mapNotNull { it.root }
                .filter { it.packageName == app.packageName }.mapNotNull(::find).firstOrNull()
        }
        fun containsText(text: String) = findText(text) != null
        suspend fun clickText(text: String) {
            awaitCondition("click-$text") { containsText(text) }
            val found = requireNotNull(findText(text))
            var target: AccessibilityNodeInfo? = found
            while (target != null && !target.isClickable) target = target.parent
            assertTrue("Clickable $text", target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        }
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        try {
            withContext(Dispatchers.Main) { container.updateTransfer.cancel(); container.updateTransfer.selectRelease(release); container.playerController.connect() }
            container.desktopLyrics.edit { it.copy(enabled = true, locked = true, islandY = .035f) }
            awaitCondition("initial-overlay") { container.desktopLyricsStatus.value.visible && container.desktopLyricsStatus.value.islandTouchable }
            val baseline = container.desktopLyricsStatus.value
            // Use the real display clock. ComposeTestRule freezes overlay recompositions while
            // its activity is backgrounded, so it cannot prove the desktop's painted progress.
            scenario.onActivity { activity -> activity.setContent {
                val state by container.updateTransfer.state.collectAsState()
                LeiTheme(AppearanceSettings(blur = false)) { top.yukonga.miuix.kmp.basic.Scaffold {
                    if (visible.value) UpdateDialogContent(UpdateState.Available(release), onDismiss = { visible.value = false },
                        onRetry = {}, onIgnore = {}, onOpenRelease = {}, transferState = state,
                        onDownload = container.updateTransfer::download, onCancel = container.updateTransfer::cancel,
                        onChangeSource = container.updateTransfer::changeSource)
                } }
            } }
            delay(500)
            screenshot("00-fixture")
            clickText("下载更新")
            awaitCondition { container.islandNotices.value?.kind == IslandNoticeKind.UPDATE_DOWNLOAD }
            delay(650)
            assertEquals(baseline.contentHeight.toFloat(), container.desktopLyricsStatus.value.contentHeight.toFloat(), 1f)
            screenshot("01-download-dialog")
            clickText("后台下载")
            assertTrue(container.updateTransfer.state.value.download is UpdateDownloadState.Downloading)
            screenshot("02-dialog-closed")
            shell("input keyevent 3")
            delay(1000)
            screenshot("03-home-progress")
            assertTrue(container.desktopLyricsStatus.value.visible)
            assertTrue(container.updateTransfer.state.value.download !is UpdateDownloadState.Idle)
            val result = withTimeout(240_000) {
                var checkpoint = 0
                while (container.updateTransfer.state.value.download is UpdateDownloadState.Downloading) {
                    val percent = container.islandNotices.value?.progress ?: 0
                    if (percent >= checkpoint + 20) {
                        checkpoint = percent
                        delay(200)
                        screenshot("03-home-progress-$percent")
                    }
                    delay(50)
                }
                container.updateTransfer.state.value
            }
            assertTrue("Real WorkManager download must complete with verification: $result", result.download is UpdateDownloadState.Ready)
            assertTrue(UpdateDownloader.matches((result.download as UpdateDownloadState.Ready).file, release))
            awaitCondition { container.islandNotices.value?.phase == SoundEffectNoticePhase.READY }
            delay(650)
            screenshot("04-completed")
            assertFalse(container.updateTransfer.state.value.installing)
            delay(5_000)
            assertEquals(SoundEffectNoticePhase.READY, container.islandNotices.value?.phase)
            assertTrue(container.desktopLyricsStatus.value.islandTouchable)
            screenshot("05-ready-persistent")
            val notification = app.getSystemService(NotificationManager::class.java).activeNotifications
                .first { it.id == 4202 }.notification
            scenario.close()
            notification.contentIntent.send()
            awaitCondition { containsText("请求安装") }
            screenshot("06-notification-opened")
            withContext(Dispatchers.Main) { container.updateTransfer.cancel(); container.updates.dismissDialog() }
            awaitCondition { container.islandNotices.value == null && container.desktopLyricsStatus.value.islandTouchable }
            delay(650)
            screenshot("07-explicit-clear-restores-fps")
            File(directory, "result.txt").writeText("Verified ${release.size} bytes; source=${result.activeSource}; progress on home; persistent install notice; notification reopened; explicit clear restored FPS; no installation requested.\n")
        } finally {
            scenario.close()
            withContext(Dispatchers.Main) { container.updateTransfer.cancel(); container.updates.dismissDialog() }
            container.desktopLyrics.edit { saved }
            // Never leave the synthetic future version available on the next real app launch.
            val work = WorkManager.getInstance(app)
            val tasks = work.getWorkInfosForUniqueWork(WorkManagerUpdateBackground.NAME).await()
            work.cancelUniqueWork(WorkManagerUpdateBackground.NAME).result.await()
            work.pruneWork().result.await()
            for (task in tasks) if (task.tags.any { it.startsWith("update-asset:") && it.contains("\"version\":\"99.0.") }) {
                File(app.cacheDir, "updates/${task.id}").deleteRecursively()
            }
            app.getSystemService(NotificationManager::class.java).cancel(4202)
        }
    }
}
