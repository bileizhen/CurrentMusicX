package io.github.currencortex.music

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.currencortex.music.core.update.*
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.update.UpdateDialogContent
import io.github.currencortex.music.ui.theme.LeiTheme
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.TimeUnit
import top.yukonga.miuix.kmp.basic.Scaffold

/** Only release-note images, retry and dialog actions; production account/data stay untouched. */
class UpdateImagesTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: MockWebServer
    private lateinit var previous: ImageLoader
    private lateinit var context: Context
    private lateinit var banner: ByteArray
    private var downloads = 0
    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".verification"))
        banner = InstrumentationRegistry.getInstrumentation().context.assets.open("release-preview.png").use { it.readBytes() }
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        previous = SingletonImageLoader.get(context)
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }.build())
    }
    @After fun close() { SingletonImageLoader.setUnsafe(previous); server.shutdown() }
    private fun show(mode: ThemeMode, html: Boolean = false) {
        val url = server.url("/preview-${mode.name}.png").toString()
        val image = if (html) "<img alt=\"版本横幅\" src=\"$url\" width=1400 />" else "![版本横幅]($url)"
        val release = AppRelease("1.1.2", "$image\n## 本次更新\n- 更新说明支持图片\n- 镜像失败自动回退", "https://github.com/bileizhen/CurrentMusicX/releases/tag/v1.1.2",
            "https://github.com/bileizhen/CurrentMusicX/releases/download/v1.1.2/CurrentMusic-Android-v1.1.2.apk", "fixture.apk", false, 100, "0".repeat(64))
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = mode, blur = false)) {
            Scaffold { UpdateDialogContent(UpdateState.Available(release), {}, {}, {}, {},
                onDownload = { downloads++ }) }
        } }
    }
    @Test fun markdownBannerLoadsInLightThemeAndActionsRemainVisible() {
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(banner)))
        show(ThemeMode.LIGHT)
        compose.waitUntil(10000) { compose.onAllNodesWithText("图片加载中…").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("update_note_image").assertIsDisplayed()
        compose.onNodeWithTag("update_note_image_error").assertDoesNotExist()
        compose.onNodeWithText("本次更新").assertIsDisplayed()
        compose.onNodeWithTag("update_download").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, downloads) }
        assertNull(server.takeRequest(5, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        capture("update-images-light.png")
    }
    @Test fun failedHtmlImageCanRetryInDarkThemeWithoutBlockingDownload() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(banner)))
        show(ThemeMode.DARK, html = true)
        compose.waitUntil(10000) { compose.onAllNodesWithTag("update_note_image_error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("update_download").assertIsDisplayed()
        compose.onNodeWithText("重新加载图片").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("update_note_image_error").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithText("图片加载中…").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("update_note_image").assertIsDisplayed()
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        capture("update-images-dark.png")
    }
    private fun capture(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
