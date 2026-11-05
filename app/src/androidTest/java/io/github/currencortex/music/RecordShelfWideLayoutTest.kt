package io.github.currencortex.music

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Wide records are checked after their real horizontal edge fade, then activated through pointer input. */
class RecordShelfWideLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var renderer: RecordShelfView
    private var playedIndex = -1
    private var previewed: Song? = null

    @After fun restoreOrientation() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    private fun show(songs: List<Song>) {
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(10000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        compose.setContent {
            LeiTheme(AppearanceSettings()) {
                // MusicOverlayDialog registers its retained entry with the root scaffold.
                // Match the production host and the existing restoration/search fixture.
                top.yukonga.miuix.kmp.basic.Scaffold {
                    PlaylistRecordShelf(songs, true, { playedIndex = it }, {}, onPreview = { previewed = it })
                }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            fun find(view: View): RecordShelfView? {
                if (view is RecordShelfView) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                return null
            }
            renderer = requireNotNull(find(compose.activity.window.decorView))
            assertTrue("This fixture must exercise a wide viewport", renderer.width > renderer.height * 1.5f)
        }
        awaitCollection(songs.size)
    }

    private fun openSearch() {
        compose.onNodeWithTag("record_shelf_search").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("record_shelf_query").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("record_shelf_query").assertIsDisplayed()
    }

    private fun closeSearch() {
        compose.onNodeWithText("查看唱片").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("record_shelf_query").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun awaitCollection(count: Int) {
        compose.waitUntil(10000) {
            var ready = false
            compose.runOnUiThread {
                assertTrue("Even overlapping old/new result sets must stay within the rendering budget",
                    renderer.renderedRecordIds.size <= 58)
                ready = renderer.displayedSongs.size == count && !renderer.replacingResults && renderer.introTime >= 1.8f
            }
            ready
        }
        compose.waitForIdle()
    }

    private fun drawRecords(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            bitmap = Bitmap.createBitmap(renderer.width, renderer.height, Bitmap.Config.ARGB_8888)
            // The transparent native layer excludes ambient page artwork. This executes the
            // production draw path and horizontal edge fade, so off-screen quads cannot pass the check.
            renderer.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun assertPaintedToBothEdges(name: String) {
        val bitmap = drawRecords()
        try {
            File(compose.activity.externalCacheDir, "record-shelf-wide-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            captureWindow(name)
            assertStableRecordBudget()
            // The diagonal rack legitimately reaches the upper-right and lower-left corners.
            // Inspect the whole native viewport; it contains no Compose title or footer buttons.
            val top = 0
            val bottom = bitmap.height
            val bands = listOf((bitmap.width * .01f).toInt() until (bitmap.width * .06f).toInt(),
                (bitmap.width * .94f).toInt() until (bitmap.width * .99f).toInt())
            for ((side, band) in bands.withIndex()) {
                var paintedPixels = 0
                var paintedColumns = 0
                for (x in band) {
                    var columnPainted = false
                    for (y in top until bottom) {
                        val pixel = bitmap.getPixel(x, y)
                        // Ignore low-opacity shadows. The ruler only spans 8%..92% of the
                        // viewport, so these outer bands contain record artwork alone.
                        if (Color.alpha(pixel) >= 96 && Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) > 220) {
                            paintedPixels++
                            columnPainted = true
                        }
                    }
                    if (columnPainted) paintedColumns++
                }
                assertTrue("${if (side == 0) "Left" else "Right"} edge needs actual visible record faces, not clipped geometry",
                    paintedPixels > band.count() * (bottom - top) * .001f)
                assertTrue("Visible records must extend across the outer edge band", paintedColumns >= band.count() / 2)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun captureWindow(name: String) {
        lateinit var bitmap: Bitmap
        var result = -1
        val copied = CountDownLatch(1)
        compose.runOnUiThread {
            val window = compose.activity.window
            bitmap = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(window, bitmap, { status -> result = status; copied.countDown() }, Handler(Looper.getMainLooper()))
        }
        try {
            assertTrue("The committed wide-layout frame must be available", copied.await(3, TimeUnit.SECONDS))
            assertEquals(PixelCopy.SUCCESS, result)
            File(compose.activity.externalCacheDir, "record-shelf-wide-$name-window.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertStableRecordBudget() {
        compose.runOnUiThread {
            val ids = renderer.renderedRecordIds
            assertTrue("A nonempty collection must draw records", ids.isNotEmpty())
            assertTrue("Off-screen records may be culled, but the rendering window must remain bounded",
                ids.size <= minOf(29, renderer.displayedSongs.size))
            if (renderer.displayedSongs.size <= 3) {
                assertEquals("All records in the small fixture must remain visible", renderer.displayedSongs.size, ids.size)
            }
            assertEquals("A short collection must not be padded with duplicate record faces", ids.size, ids.distinct().size)
            assertTrue("Every visible record must belong to the displayed collection", ids.all { id -> renderer.displayedSongs.any { it.id == id } })
        }
    }

    private fun selectAndActivate(song: Song, visibleIndex: Int, originalIndex: Int) {
        compose.runOnUiThread { renderer.select(visibleIndex, immediate = true) }
        drawRecords().recycle()
        assertStableRecordBudget()
        compose.onNodeWithTag("record_shelf_title").assertTextEquals(song.name)
        var point = Offset.Zero
        compose.runOnUiThread {
            val quad = requireNotNull(renderer.recordQuad(song))
            val u = .85f
            val v = .25f
            point = Offset(quad[0] + (quad[2] - quad[0]) * u + (quad[6] - quad[0]) * v,
                quad[1] + (quad[3] - quad[1]) * u + (quad[7] - quad[1]) * v)
            val ruler = renderer.height - renderer.bottomInset - 10f * renderer.resources.displayMetrics.density
            assertTrue("The extracted record must remain hittable inside the viewport", point.x in 0f..renderer.width.toFloat())
            assertTrue("Record activation must be above the ruler hit area", point.y < ruler - 32f * renderer.resources.displayMetrics.density)
            playedIndex = -1
        }
        compose.onNodeWithTag("record_shelf_canvas").performTouchInput { click(point) }
        compose.waitUntil(3000) {
            var activated = false
            compose.runOnUiThread { activated = playedIndex >= 0 }
            activated
        }
        compose.runOnIdle {
            assertEquals("The extracted record must activate its original collection entry", originalIndex, playedIndex)
            assertEquals("Preview and activation must refer to the same visible song", song.id, previewed?.id)
        }
    }

    @Test fun wideCollectionPaintsBothEdgesAndKeepsSelectionCorrectAfterFilteringAndClearing() {
        val songs = List(180) { index -> Song(index.toLong() + 1, "Wide track ${index + 1}",
            artists = if (index % 3 == 0) "Cohort fixture" else "Other fixture") }
        show(songs)
        assertPaintedToBothEdges("full")
        selectAndActivate(songs[60], visibleIndex = 60, originalIndex = 60)
        openSearch()
        compose.onNodeWithTag("record_shelf_query").performTextInput("Cohort")
        closeSearch()
        awaitCollection(60)
        compose.runOnIdle { assertEquals(songs.filter { it.artists == "Cohort fixture" }.map { it.id }, renderer.displayedSongs.map { it.id }) }
        assertPaintedToBothEdges("filtered")
        selectAndActivate(songs[15], visibleIndex = 5, originalIndex = 15)
        openSearch()
        compose.onNodeWithText("清空搜索").performClick()
        closeSearch()
        awaitCollection(songs.size)
        compose.runOnIdle { assertEquals(songs.map { it.id }, renderer.displayedSongs.map { it.id }) }
        assertPaintedToBothEdges("cleared")
        selectAndActivate(songs[90], visibleIndex = 90, originalIndex = 90)
    }

    @Test fun smallCollectionsDoNotFillTheWidthWithDuplicateSongs() {
        val songs = List(3) { Song(it.toLong() + 1, "Small track ${it + 1}", artists = "Small fixture") }
        show(songs)
        for (index in songs.indices) selectAndActivate(songs[index], visibleIndex = index, originalIndex = index)
    }
}
