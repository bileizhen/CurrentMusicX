package io.github.currencortex.music

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.library.MvDto
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.library.LibraryDetailState
import io.github.currencortex.music.feature.mv.*
import io.github.currencortex.music.ui.component.LocalMusicBottomInset
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import top.yukonga.miuix.kmp.basic.Scaffold
import java.io.File
import java.util.UUID

class MvDetailUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var ownedContainer: AppContainer? = null
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private fun rotate(landscape: Boolean) {
        compose.runOnUiThread { compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == if (landscape) 2 else 1 }
    }
    @After fun restore() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        ownedContainer?.close()
    }
    private fun fixture(): MvDto {
        val cover = File(context.cacheDir, "mv-detail-fixture.png")
        InstrumentationRegistry.getInstrumentation().context.assets.open("release-preview.png").use { input -> cover.outputStream().use { input.copyTo(it) } }
        return MvDto(992387, "Shape Of You", cover.toURI().toString(), "Ed Sheeran", 991, "黄老板 Ed Sheeran 冠单《Shape Of You》正式版 MV 发布！", 263000)
    }
    private fun capture(name: String) {
        compose.onNodeWithTag("mv_detail").captureToImage().asAndroidBitmap().let { image ->
            File(context.externalCacheDir, name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun detailOpensDownloadWithoutStartingVideoAndKeepsNavigationWorking() = runBlocking {
        rotate(false)
        val container = AppContainer(context, "mv-detail-${UUID.randomUUID()}").also { ownedContainer = it }
        container.ready.await(); container.sessionRestored.await()
        val mv = fixture()
        var plays = 0; var backs = 0; var artist = 0L; var dialog = false
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT)) { Scaffold {
            CompositionLocalProvider(LocalMusicBottomInset provides 100.dp) {
                MvDetailScreen(LibraryDetailState(loading = false, mv = mv), container, { backs++ },
                    { plays++ }, { artist = it }, {}, { dialog = it })
            }
        } } }
        val cover = compose.onNodeWithTag("mv_detail_cover").getUnclippedBoundsInRoot()
        assertEquals(16f / 9, (cover.right - cover.left).value / (cover.bottom - cover.top).value, .03f)
        compose.onNodeWithText("视频简介").assertIsDisplayed()
        capture("mv-detail-portrait.png")
        compose.onNodeWithTag("mv_detail_download").performClick()
        compose.onNodeWithTag("mv_download_start").assertIsDisplayed()
        compose.runOnIdle { assertTrue(dialog); assertEquals(0, plays) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5000) { !dialog }
        compose.onNodeWithTag("mv_detail_cover").performClick()
        compose.onNodeWithTag("play_mv").performClick()
        compose.onNodeWithTag("mv_detail_artist").performClick()
        compose.onNodeWithTag("mv_detail_back").performClick()
        compose.runOnIdle { assertEquals(2, plays); assertEquals(991L, artist); assertEquals(1, backs) }
    }
    @Test fun landscapeUsesTwoColumnsAndLongDescriptionCanExpandAndRecoverFromError() {
        rotate(true)
        val state = mutableStateOf(LibraryDetailState(loading = false, mv = fixture().copy(desc = (1..16).joinToString("\n") { "视频简介第 $it 行，查看完整音乐故事。" })))
        var retries = 0
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.DARK)) {
            MvDetailContent(state.value, "下载中 42%", {}, {}, {}, {}, { retries++ })
        } }
        val cover = compose.onNodeWithTag("mv_detail_cover").getUnclippedBoundsInRoot()
        val play = compose.onNodeWithTag("play_mv").getUnclippedBoundsInRoot()
        assertTrue(play.left > cover.right)
        compose.onNodeWithText("下载中 42%").assertIsDisplayed()
        capture("mv-detail-landscape.png")
        compose.onNodeWithTag("mv_detail_expand").performScrollTo().performClick()
        compose.onNodeWithText("收起简介").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("mv_detail_expand").performClick()
        compose.onNodeWithText("展开简介").assertIsDisplayed()
        compose.runOnIdle { state.value = LibraryDetailState(loading = false, error = "网络暂时不可用") }
        compose.onNodeWithTag("mv_detail_retry").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
