package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.visualizer.*
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.PlayerViewModel
import io.github.currencortex.music.feature.visualizer.*
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Actual Android dialog under explicitly simulated content bounds, no audio or simulated FFT. */
class VisualizerPreviewLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var container: AppContainer
    private lateinit var vm: PlayerViewModel
    @Before fun setup() = runBlocking {
        container = AppContainer(ApplicationProvider.getApplicationContext<CurrentMusicApplication>(), "preview-layout-${UUID.randomUUID()}")
        container.ready.await(); container.sessionRestored.await()
        container.playbackQueue.replace(listOf(Song(-96001,"布局验证歌曲标题",artists="仅用于布局的文字")),0)
        vm = PlayerViewModel(container)
    }
    @After fun cleanup() { vm.audioAnalysis.close(); container.close() }
    @Test fun narrowShortViewportKeepsSquareStageAndReachableControls() = verify(DpSize(320.dp,400.dp),1f)
    @Test fun largeFontLandscapeKeepsSquareStageAndReachableControls() = verify(DpSize(620.dp,320.dp),1.8f)
    private fun verify(size: DpSize, fontScale: Float) {
        compose.setContent { LeiTheme(AppearanceSettings(blur=false)) {
            val base=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density,fontScale)) {
                VisualizerPreviewDialog(vm,VisualizerRenderState(),true,CaptureStatus.DISABLED.label,false,false,{},{},{},{},{},viewport=size)
            }
        } }
        compose.onNodeWithTag("visualizer_preview_close").assertIsDisplayed()
        val box=compose.onNodeWithTag("visualizer_preview_container").fetchSemanticsNode().boundsInRoot
        val stage=compose.onNodeWithTag("visualizer_preset_stage").fetchSemanticsNode().boundsInRoot
        assertEquals(stage.width,stage.height,2f)
        assertTrue(stage.top>=box.top && stage.bottom<=box.bottom && stage.left>=box.left && stage.right<=box.right)
        for (preset in VisualizerPreset.entries) {
            compose.onNodeWithTag("visualizer_preset_${preset.name}").performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(5000) { vm.settings.value.visualizerEffects.presetId==preset }
        }
        compose.onNodeWithTag("visualizer_toggle").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("visualizer_details").performScrollTo().assertIsDisplayed()
        assertEquals(0,vm.audioAnalysis.frames.value.sampleRateHz)
        val bitmap=compose.onNodeWithTag("visualizer_preview_container").captureToImage().asAndroidBitmap()
        val context=ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        java.io.File(context.getExternalFilesDir(null), "m27-layout-${if(fontScale>1) "large-font" else "narrow-short"}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
}
