package io.github.currencortex.music

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.PlaylistRecordShelf
import io.github.currencortex.music.feature.library.RecordShelfView
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real window rotations exercise the device's cutout and side navigation-bar insets. */
class RecordShelfLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun restoreOrientation() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    @Test fun portraitControlsKeepTheirCompactHeightAndRuler() = checkLayout(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    @Test fun landscapeControlsStayCompactAndAvoidSideInsets() = checkLayout(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    @Test fun reverseLandscapeControlsAvoidTheOppositeSideInsets() = checkLayout(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)

    private fun checkLayout(orientation: Int) {
        val expectedOrientation = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            compose.activity.requestedOrientation = orientation
        }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == expectedOrientation }
        val songs = List(40) { Song(it.toLong() + 1, "Layout track ${it + 1}", artists = "Layout fixture") }
        var density = 1f
        var safeLeft = 0f
        var safeRight = 0f
        var safeBottom = 0f
        compose.setContent {
            val localDensity = LocalDensity.current
            val direction = LocalLayoutDirection.current
            val insets = WindowInsets.safeDrawing
            val left = insets.getLeft(localDensity, direction).toFloat()
            val right = insets.getRight(localDensity, direction).toFloat()
            val bottom = insets.getBottom(localDensity).toFloat()
            SideEffect {
                density = localDensity.density
                safeLeft = left
                safeRight = right
                safeBottom = bottom
            }
            LeiTheme(AppearanceSettings()) {
                PlaylistRecordShelf(songs, true, {}, {}, onSave = {})
            }
        }
        compose.waitForIdle()
        val page = compose.onNodeWithTag("playlist_record_shelf").fetchSemanticsNode().boundsInRoot
        val save = compose.onNodeWithTag("record_shelf_save").fetchSemanticsNode().boundsInRoot
        val shuffle = compose.onNodeWithTag("record_shelf_shuffle").fetchSemanticsNode().boundsInRoot
        val options = compose.onNodeWithTag("record_shelf_options").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("record_shelf_save", "record_shelf_shuffle", "record_shelf_options", "record_shelf_search", "record_shelf_close")) {
            val node = compose.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$tag keeps at least a 48 dp touch target", bounds.height / density >= 47.5f)
            assertTrue("$tag must not grow with landscape width", bounds.height / density <= 56.5f)
            assertTrue("$tag clears the left safe area", bounds.left >= page.left + safeLeft - 1f)
            assertTrue("$tag clears the right safe area", bounds.right <= page.right - safeRight + 1f)
            assertTrue("$tag clears the bottom system area", bounds.bottom <= page.bottom - safeBottom + 1f)
        }
        assertEquals("Search remains a 48 dp circle", 48f,
            compose.onNodeWithTag("record_shelf_search").fetchSemanticsNode().boundsInRoot.width / density, .6f)
        assertEquals("Close remains a 48 dp circle", 48f,
            compose.onNodeWithTag("record_shelf_close").fetchSemanticsNode().boundsInRoot.width / density, .6f)
        assertEquals(save.height, shuffle.height, 1f)
        assertEquals(save.height, options.height, 1f)
        assertTrue("The footer fits a compact group", (options.right - save.left) / density <= 420.5f)
        assertTrue("The random button has a useful width", shuffle.width / density >= 130f)
        assertTrue("The random button stops growing on wide screens", shuffle.width / density <= 182f)
        assertTrue("Footer controls do not overlap", save.right < shuffle.left && shuffle.right < options.left)
        assertEquals("The group is centered in the safe width",
            (page.left + safeLeft + page.right - safeRight) / 2f, shuffle.center.x, 2f)

        lateinit var renderer: RecordShelfView
        var rulerY = 0f
        compose.runOnUiThread {
            fun find(view: View): RecordShelfView? {
                if (view is RecordShelfView) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                return null
            }
            renderer = requireNotNull(find(compose.activity.window.decorView))
            assertEquals("Renderer reserves the actual footer height", page.bottom - save.top, renderer.bottomInset, 2f)
            rulerY = renderer.height - renderer.bottomInset - 10f * density
            assertTrue("The record stage keeps at least 100 dp", (rulerY - renderer.topInset) / density - 100f >= 100f)
        }
        assertTrue("The ruler stays above all footer buttons", page.top + rulerY < save.top)
        compose.onNodeWithTag("record_shelf_canvas").performTouchInput {
            click(Offset(width * .75f, rulerY))
        }
        compose.runOnUiThread {
            assertTrue("The exposed ruler still scrubs the record collection", renderer.position > 25f)
        }
    }
}
