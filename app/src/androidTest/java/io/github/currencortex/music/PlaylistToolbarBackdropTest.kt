package io.github.currencortex.music

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.feature.library.PlaylistPageLayout
import io.github.currencortex.music.ui.component.nativeui.GlassIconButton
import io.github.currencortex.music.ui.component.nativeui.LiquidGlassOpticsKey
import io.github.currencortex.music.ui.theme.LeiTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class PlaylistToolbarBackdropTest {
    @get:Rule val compose = createComposeRule()

    @Test @SdkSuppress(minSdkVersion = 33)
    fun glassButtonSamplesCoverAtTopAndRowsOnceScrollingStarts() = withColoredPlaylist { list ->
        scroll(list, 0, 1)
        assertEquals(1, list.firstVisibleItemScrollOffset)
        assertTrue("Scrolling even within the first row must reveal its blue ink through the glass", ink("button").blueOverRed() > .1f)
        scroll(list, 0)
        assertTrue("Returning to offset zero must restore the red cover in the glass", ink("button").blueOverRed() < -.1f)
    }

    @Test @SdkSuppress(minSdkVersion = 33)
    fun rowGlassTracksArtworkVisibilityIndependentlyOfTheToolbar() = withColoredPlaylist { list ->
        fun assertRowOptics(index: Int) {
            val row = hasTestTag("row-$index")
            val glass = SemanticsMatcher.expectValue(LiquidGlassOpticsKey, true)
            compose.onNode(glass and (row or hasAnyAncestor(row)), useUnmergedTree = true).assertExists()
        }
        assertRowOptics(0)
        assertTrue("The first row must sample the red artwork", ink("row-0").blueOverRed() < -.1f)
        scroll(list, 5)
        assertEquals(5, list.firstVisibleItemIndex)
        assertRowOptics(5)
        val page = ink("row-5")
        assertTrue("The scrolled row must sample the light neutral page color, got $page",
            minOf(page.red, page.green, page.blue) > .55f && abs(page.blueOverRed()) < .1f)
        assertTrue("The toolbar must still sample the blue rows above it", ink("button").blueOverRed() > .1f)
        scroll(list, 0)
        assertRowOptics(0)
        assertTrue("Returning to the top must restore the artwork for the row", ink("row-0").blueOverRed() < -.1f)
        assertTrue("Returning to the top must restore the artwork for the toolbar", ink("button").blueOverRed() < -.1f)
    }

    private fun Color.blueOverRed() = blue - red

    private fun ink(tag: String): Color {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun scroll(list: LazyListState, index: Int, offset: Int = 0) {
        compose.runOnIdle { list.requestScrollToItem(index, offset) }
        compose.waitForIdle()
    }

    private fun withColoredPlaylist(checkPixels: (LazyListState) -> Unit) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val cover = File.createTempFile("toolbar-cover-", ".png", cache)
        try {
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.RED)
            cover.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
            lateinit var list: LazyListState
            lateinit var host: android.view.View
            compose.setContent {
                LeiTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, blur = true, liquidGlass = true)) {
                    list = rememberLazyListState()
                    host = LocalView.current
                    PlaylistPageLayout(cover.toURI().toString(), list, heroHeightPx = 0, tag = "playlist", toolbar = {
                        // A black toolbar isolates pixels supplied by the button's actual backdrop.
                        Box(Modifier.fillMaxWidth().background(Color.Black)) {
                            GlassIconButton({}, "Toolbar", Modifier.testTag("button")) {}
                        }
                    }) {
                        // Keep blue directly behind the toolbar at every offset, including the top.
                        LazyColumn(Modifier.fillMaxSize(), state = list) {
                            items(60) { index ->
                                Box(Modifier.fillMaxWidth().height(120.dp).background(Color.Blue), contentAlignment = Alignment.BottomEnd) {
                                    if (index == 0 || index == 5)
                                        GlassIconButton({}, "Row action", Modifier.testTag("row-$index")) {}
                                }
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            assumeTrue(host.isHardwareAccelerated)
            compose.waitUntil(10_000) { ink("button").blueOverRed() < -.1f } // Allow Coil to decode the red cover.
            checkPixels(list)
        } finally {
            cover.delete()
        }
    }
}
