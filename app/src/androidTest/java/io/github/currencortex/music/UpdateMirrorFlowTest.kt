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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.*
import org.junit.Assert.*
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import okhttp3.tls.HeldCertificate
import okhttp3.tls.HandshakeCertificates
import okio.Buffer
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

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

    @Test fun dedicatedMirrorBelowSpeedThresholdStillFinishesAndVerifies() = runBlocking<Unit> {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false); server.start()
            val bytes = ByteArray(512 * 1024) { (it % 251).toByte() }.apply {
                byteArrayOf(0x50, 0x4b, 0x03, 0x04).copyInto(this)
            }
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)).throttleBody(4096, 250, TimeUnit.MILLISECONDS))
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val release = AppRelease("99.0.0", "", "https://github.com/bileizhen/CurrentMusicX/releases", null, null, false,
                bytes.size.toLong(), digest)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.cacheDir, "slow-mirror-${java.util.UUID.randomUUID()}")
            val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
            val downloader = UpdateDownloader(client, directory) { _, _ -> server.url("/asset").toString() }
            var last = 0L
            val file = withTimeout(60_000) { downloader.download(release, UpdateSource.CURRENTMUSIC) { received, _ ->
                assertTrue(received >= last); last = received
            } }
            assertEquals(release.size, last)
            assertTrue(UpdateDownloader.matches(file, release))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun liveSignedMirrorPrefixMatchesPublishedApkWhenExplicitlyRequested() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("live_update_url")
        val expected = args.getString("live_update_prefix_sha256")
        Assume.assumeTrue("Live signed range verification is opt-in", url != null && expected != null)
        val release = AppRelease(args.getString("live_update_version")!!, "", "https://github.com/bileizhen/CurrentMusicX/releases",
            url, "fixture.apk", false, args.getString("live_update_size")!!.toLong(), args.getString("live_update_sha256")!!)
        val target = UpdateSource.CURRENTMUSIC.url(release)
        val range = "bytes=0-65535"
        val request = okhttp3.Request.Builder().url(target).header("Range", range)
        val parsed = target.toHttpUrl()
        UpdateProxy.current.headers(parsed, range = range).forEach { (name, value) -> request.header(name, value) }
        withContext(Dispatchers.IO) {
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
                .newCall(request.build()).execute().use { response ->
                    assertEquals(206, response.code)
                    val bytes = response.body!!.bytes()
                    assertEquals(65536, bytes.size)
                    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    assertEquals(expected, digest)
                }
        }
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
