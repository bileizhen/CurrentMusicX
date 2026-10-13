package io.github.currencortex.music

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.currencortex.music.core.download.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.mv.MvDownloadDialog
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import top.yukonga.miuix.kmp.basic.Scaffold

class MvDownloadUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun portrait() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
    }
    @After fun restoreOrientation() { compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    @Test fun qualityRetryBackgroundCompletionAndReopenUseRealWork(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val video = InstrumentationRegistry.getInstrumentation().context.assets.open("download/fixture.mp4").use { it.readBytes() }
        val server = MockWebServer().apply { start(); enqueue(MockResponse().setResponseCode(503)); enqueue(MockResponse().setBody(Buffer().write(video))) }
        val temp = File(context.cacheDir, "mv-work-${UUID.randomUUID()}")
        val resolution = AtomicInteger()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, name: String, params: WorkerParameters): ListenableWorker? {
                if (name != MvDownloadWorker::class.java.name) return null
                return object : MvDownloadWorker(appContext, params) {
                    override suspend fun createEngine() = MvDownloadEngine(temp, { _, quality -> resolution.set(quality); server.url("/video").toString() }, SongDownloadStore(appContext))
                }
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
        val work = WorkManager.getInstance(context)
        val container = AppContainer(context, "mv-work-${UUID.randomUUID()}")
        val song = Song(-998908, "后台 MV 下载验证", "测试歌手", mv = 998908, video = true)
        val shown = mutableStateOf(true)
        val saved = mutableSetOf<String>()
        try {
            container.ready.await(); container.sessionRestored.await()
            compose.setContent { LeiTheme(AppearanceSettings()) { Scaffold { Box(Modifier.fillMaxSize()) {
                if (shown.value) MvDownloadDialog(song, container) { shown.value = false }
            } } } }
            compose.onNodeWithTag("mv_download_quality").performScrollTo().performClick()
            compose.onNodeWithTag("mv_quality_480").performClick()
            compose.onNodeWithTag("mv_download_start").performClick()
            fun runPending() {
                compose.waitUntil(5000) { work.getWorkInfosByTag("mv-download").get().any { !it.state.isFinished } }
                val pending = work.getWorkInfosByTag("mv-download").get().first { !it.state.isFinished }
                WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(pending.id)
            }
            runPending()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("mv_download_error").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("mv_download_start").performClick()
            // Closing the sheet does not own or cancel the download.
            compose.runOnIdle { shown.value = false }
            runPending()
            compose.waitUntil(8000) { work.getWorkInfosByTag("mv-download").get().any { it.state == WorkInfo.State.SUCCEEDED } }
            val complete = work.getWorkInfosByTag("mv-download").get().first { it.state == WorkInfo.State.SUCCEEDED }
            saved += complete.outputData.getString("uri")!!
            assertEquals(480, resolution.get())
            compose.runOnIdle { shown.value = true }
            compose.onNodeWithTag("mv_download_complete").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("打开视频").assertIsDisplayed()
            compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap -> File(context.externalCacheDir, "mv-download.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            } }
        } finally {
            work.cancelAllWorkByTag("mv-download").result.get()
            saved.forEach { context.contentResolver.delete(android.net.Uri.parse(it), null, null) }
            container.close(); server.shutdown(); temp.deleteRecursively()
        }
    }
}
