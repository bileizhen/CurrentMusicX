package io.github.currencortex.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.content.pm.ActivityInfo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.mv.MvPlayerContent
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

class MvPlayerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun portrait() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
    }
    @After fun restoreOrientation() { compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    @Test fun controlsSeekClampHidePauseAndRenderPortraitAndFullscreen() {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val state = mutableStateOf(PlayerState(Song(-8, "夜空中的回声", "CurrentMusic · 视频演示", mv = 8, video = true), positionMs = 5000, durationMs = 120000))
        val full = mutableStateOf(false)
        var downloads = 0; var qualities = 0; var retry = 0
        val bitmap = InstrumentationRegistry.getInstrumentation().context.assets.open("release-preview.png").use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap()
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.DARK)) {
            Box(if (full.value) Modifier.fillMaxWidth().height(340.dp) else Modifier.fillMaxSize()) {
                MvPlayerContent(state.value, full.value, full.value, "下载 MV", { full.value = false },
                    { state.value = state.value.copy(playing = !state.value.playing, playRequested = !state.value.playing) },
                    { state.value = state.value.copy(positionMs = it) }, { full.value = !full.value }, { downloads++ }, { qualities++ }, { retry++ }) {
                    Image(bitmap, null, it, contentScale = ContentScale.Crop)
                }
            }
        } }
        fun capture(name: String) = compose.onNodeWithTag("mv_player").captureToImage().asAndroidBitmap().let {
            File(context.externalCacheDir, name).outputStream().use { out -> it.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
        }
        compose.onNodeWithTag("mv_download").performClick(); assertEquals(1, downloads)
        compose.onNodeWithTag("mv_quality").performClick(); assertEquals(1, qualities)
        compose.onNodeWithTag("mv_back_10").performClick(); assertEquals(0L, state.value.positionMs)
        compose.onNodeWithTag("mv_forward_10").performClick(); assertEquals(10000L, state.value.positionMs)
        compose.onNodeWithTag("player_seek").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.5f) }
        assertEquals(60000L, state.value.positionMs)
        capture("mv-portrait.png")
        compose.onNodeWithTag("mv_fullscreen").performClick()
        compose.onNodeWithTag("mv_back").assertIsDisplayed()
        capture("mv-expanded.png")
        compose.onNodeWithTag("mv_back").performClick(); assertFalse(full.value)
        compose.onNodeWithTag("mv_toggle").performClick(); assertTrue(state.value.playing)
        compose.mainClock.advanceTimeBy(4000)
        compose.onNodeWithTag("mv_toggle").assertDoesNotExist()
        compose.onNodeWithTag("mv_gestures").performTouchInput { doubleClick(center) }
        compose.waitForIdle(); assertFalse(state.value.playing)
        compose.onNodeWithTag("mv_toggle").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(resolving = true, loading = true) }
        compose.onNodeWithTag("player_seek").assertIsNotEnabled()
        compose.onNodeWithTag("mv_forward_10").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(error = "MV 暂时无法播放") }
        compose.onNodeWithTag("mv_retry").performClick(); assertEquals(1, retry)
    }
    @Test fun landscapeControlsFitInsideTheVideoStage() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val bitmap = InstrumentationRegistry.getInstrumentation().context.assets.open("release-preview.png").use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap()
        compose.setContent { LeiTheme(AppearanceSettings(themeMode = ThemeMode.DARK)) {
            MvPlayerContent(PlayerState(Song(-8, "夜空中的回声", "CurrentMusic", mv = 8, video = true), positionMs = 60000, durationMs = 120000),
                true, true, "下载 MV", {}, {}, {}, {}, {}, {}, {}) { Image(bitmap, null, it, contentScale = ContentScale.Fit) }
        } }
        listOf("mv_back", "mv_download", "mv_toggle", "mv_quality", "mv_fullscreen", "player_seek").forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        val stage = compose.onNodeWithTag("mv_stage").getUnclippedBoundsInRoot()
        assertTrue(stage.right - stage.left > stage.bottom - stage.top)
        compose.onNodeWithTag("mv_player").captureToImage().asAndroidBitmap().let { bitmap -> File(context.externalCacheDir, "mv-fullscreen.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        } }
    }
}
