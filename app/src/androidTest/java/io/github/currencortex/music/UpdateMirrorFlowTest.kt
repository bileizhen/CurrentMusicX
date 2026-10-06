package io.github.currencortex.music

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.feature.update.UpdateDialogContent
import io.github.currencortex.music.ui.theme.LeiTheme
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.junit.*
import org.junit.Assert.*

/** Isolated dialog/transfer fixture; does not download or install a production APK. */
class UpdateMirrorFlowTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @After fun close() { scope.cancel() }

    @Test fun fallbackSourceIsVisibleAndInstallationRequiresExplicitAction() {
        val release = AppRelease("99.0.0", "Fixture update", "https://github.com/bileizhen/CurrentMusicX/releases/tag/v99.0.0",
            "https://github.com/bileizhen/CurrentMusicX/releases/download/v99.0.0/app.apk", "app.apk", false, 100, "0".repeat(64))
        val finish = CompletableDeferred<Unit>()
        val sources = mutableListOf<UpdateSource>()
        var installs = 0
        val transfer = UpdateTransfer(UpdateDownload { _, source, progress ->
            sources += source
            if (source != UpdateSource.DPIK) throw IOException("Fixture unavailable")
            progress(42, 100)
            finish.await()
            File("fixture-verified.apk")
        }, object : UpdateInstall {
            override fun canInstall() = true
            override suspend fun request(file: File, release: AppRelease): UpdateInstallResult {
                installs++
                return UpdateInstallResult.STARTED
            }
        }, scope)
        transfer.selectRelease(release)
        compose.setContent {
            val state by transfer.state.collectAsState()
            LeiTheme(AppearanceSettings(blur = false)) {
                top.yukonga.miuix.kmp.basic.Scaffold {
                UpdateDialogContent(UpdateState.Available(release), onDismiss = transfer::cancel,
                    onRetry = {}, onIgnore = {}, onOpenRelease = { error("Fixture must use native download") },
                    transferState = state, onDownload = transfer::download, onInstall = transfer::install)
                }
            }
        }
        compose.onNodeWithTag("update_download").performClick()
        val expected = UpdateSource.default.fallbacks()
        compose.onNodeWithTag("update_active_source").assertTextContains("已切换至 github.dpik.top（${expected.size}/${expected.size}）")
        compose.runOnIdle {
            assertEquals(expected, sources)
            assertEquals(0, installs)
            finish.complete(Unit)
        }
        compose.waitUntil(5000) { transfer.state.value.download is UpdateDownloadState.Ready }
        compose.onNodeWithTag("update_download").assertTextContains("请求安装").performClick()
        compose.runOnIdle { assertEquals(1, installs) }
    }

    /** Run only with explicit public release metadata; routine tests stay offline. */
    @Test fun liveMirrorFallbackVerifiesOfficialAssetWhenExplicitlyRequested() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("live_update_url")
        Assume.assumeTrue("Live network verification is opt-in", url != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification")) { "Live download requires the isolated test application" }
        val release = AppRelease(args.getString("live_update_version")!!, "", "https://github.com/bileizhen/CurrentMusicX/releases",
            url, "fixture.apk", false, args.getString("live_update_size")!!.toLong(), args.getString("live_update_sha256")!!)
        val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val directory = File(context.cacheDir, "update-mirror-live-${java.util.UUID.randomUUID()}")
        val downloader = UpdateDownloader(OkHttpClient(), directory)
        val transfer = UpdateTransfer(UpdateDownload { value, source, progress ->
            android.util.Log.i("UpdateMirrorTest", "Trying ${source.name}")
            try { downloader.download(value, source, progress) }
            catch (failure: IOException) {
                android.util.Log.i("UpdateMirrorTest", "Failed ${source.name}: ${failure.javaClass.simpleName}: ${failure.message?.take(160)}")
                throw failure
            }
        },
            object : UpdateInstall {
                override fun canInstall() = false
                override suspend fun request(file: File, release: AppRelease) = error("Live verification must not install")
            }, liveScope)
        try {
            transfer.selectRelease(release)
            val preferred = args.getString("live_update_source")?.let(UpdateSource::valueOf) ?: UpdateSource.default
            transfer.download(preferred)
            val result = withTimeout(240000) {
                transfer.state.first { it.download is UpdateDownloadState.Ready || it.download is UpdateDownloadState.Failed }
            }
            assertTrue("Live sources must deliver a verified APK: $result", result.download is UpdateDownloadState.Ready)
            if (args.getString("live_require_source") == "true") assertEquals(preferred, result.activeSource)
            assertTrue(UpdateDownloader.matches((result.download as UpdateDownloadState.Ready).file, release))
            android.util.Log.i("UpdateMirrorTest", "Verified source=${result.activeSource}, attempt=${result.attempt}, bytes=${release.size}")
        } finally { liveScope.cancel() }
    }
}
