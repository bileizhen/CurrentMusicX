package io.github.currencortex.music

import android.graphics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.PlaylistRecordShelf
import io.github.currencortex.music.feature.library.RecordShelfView
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class RecordShelfTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actualFramesDragSnapRandomSearchEmptyAndLifecycle() {
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val settings = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .associateWith { android.provider.Settings.Global.getString(context.contentResolver, it) }
        instrumentation.uiAutomation.setAnimationScale(1f)
        try {
            val songs = (0 until 40).map { index ->
                // Deterministic offline artwork exercises different textures and ambient colours.
                val bitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val hue = (index * 47f) % 360f
                paint.shader = LinearGradient(0f, 0f, 384f, 384f, Color.HSVToColor(floatArrayOf(hue, .65f, .8f)),
                    Color.HSVToColor(floatArrayOf((hue + 80) % 360, .8f, .13f)), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, 384f, 384f, paint); paint.shader = null
                paint.color = 0x55FFFFFF; canvas.drawCircle(190f, 160f, 115f, paint)
                paint.color = 0xFF071923.toInt(); canvas.drawCircle(218f, 137f, 100f, paint)
                paint.color = Color.WHITE; paint.textSize = 28f
                canvas.drawText("COLLECTION", 28f, 310f, paint)
                paint.textSize = 16f; canvas.drawText("VOLUME ${index + 1}", 30f, 340f, paint)
                val file = File(context.cacheDir, "shelf-fixture-$index.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                Song(index.toLong() + 1, "夜航 ${index + 1}", "CurrentMusic", "Volume ${index + 1}", file.toURI().toString())
            }
            var played = -1; var closed = false; var saved = -1L
            compose.setContent { LeiTheme(AppearanceSettings(), darkSystemBars = true) {
                top.yukonga.miuix.kmp.basic.Scaffold {
                    PlaylistRecordShelf(songs, true, { played = it }, { closed = true }, onSave = { saved = it.id })
                }
            } }
            compose.waitForIdle()
            lateinit var renderer: RecordShelfView
            lateinit var window: android.view.Window
            compose.runOnUiThread {
                fun find(view: android.view.View): RecordShelfView? {
                    if (view is RecordShelfView) return view
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
                window = activity.window
                renderer = requireNotNull(find(window.decorView))
                if (android.os.Build.VERSION.SDK_INT >= 33) assertTrue("The lit material must compile on this device", renderer.hasLitMaterial)
            }
            fun screenshot(name: String, scale: Int = 1) {
                // PixelCopy samples the actual committed window frame; Compose capture waits for
                // recomposition idle and can accidentally skip the whole moving part of the pick.
                val bitmap = Bitmap.createBitmap(window.decorView.width / scale, window.decorView.height / scale, Bitmap.Config.ARGB_8888)
                val copied = java.util.concurrent.CountDownLatch(1)
                var result = -1
                android.view.PixelCopy.request(window, bitmap, { result = it; copied.countDown() },
                    android.os.Handler(android.os.Looper.getMainLooper()))
                assertTrue(copied.await(3, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals(android.view.PixelCopy.SUCCESS, result)
                File(context.getExternalFilesDir(null), name).outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            // Queue copies on rendered frames; PNG compression must not make us skip the entrance.
            val sampleTimes = (0..15).map { .40f + it * .08f }
            val entryFrames = Array(sampleTimes.size) {
                Bitmap.createBitmap(window.decorView.width / 3, window.decorView.height / 3, Bitmap.Config.ARGB_8888)
            }
            val entryTimes = FloatArray(sampleTimes.size)
            val copyResults = IntArray(sampleTimes.size) { -1 }
            val entryCopied = java.util.concurrent.CountDownLatch(sampleTimes.size)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            compose.runOnUiThread {
                val choreographer = android.view.Choreographer.getInstance()
                var sample = 0
                val capture = object : android.view.Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        if (sample < sampleTimes.size && renderer.introTime >= sampleTimes[sample]) {
                            val index = sample++
                            entryTimes[index] = renderer.introTime
                            android.view.PixelCopy.request(window, entryFrames[index], {
                                copyResults[index] = it; entryCopied.countDown()
                            }, handler)
                        }
                        if (sample < sampleTimes.size) choreographer.postFrameCallback(this)
                    }
                }
                choreographer.postFrameCallback(capture)
            }
            assertTrue("Entrance frames must be captured", entryCopied.await(5, java.util.concurrent.TimeUnit.SECONDS))
            for (index in entryFrames.indices) {
                assertEquals(android.view.PixelCopy.SUCCESS, copyResults[index])
                File(context.getExternalFilesDir(null), "record-shelf-entry-$index.png").outputStream().use {
                    entryFrames[index].compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                entryFrames[index].recycle()
            }
            File(context.getExternalFilesDir(null), "record-shelf-entry-times.txt")
                .writeText(entryTimes.mapIndexed { index, time -> "$index: $time" }.joinToString("\n"))
            Thread.sleep(1300)
            screenshot("record-shelf-rest.png")
            val before = renderer.position
            compose.onNodeWithTag("record_shelf_canvas").performTouchInput { swipe(center, center.copy(x = width * .15f), 500) }
            Thread.sleep(1400)
            compose.runOnUiThread { assertTrue(kotlin.math.abs(renderer.position - before) > .5f) }
            assertEquals(-1, played)
            screenshot("record-shelf-drag.png")
            compose.mainClock.autoAdvance = false
            val pickButton = compose.onNodeWithTag("record_shelf_shuffle")
            pickButton.performClick()
            compose.mainClock.advanceTimeBy(128)
            val pulse = pickButton.fetchSemanticsNode().config[io.github.currencortex.music.feature.library.ShelfPickFeedbackKey]
            assertTrue("The pick button expands even after picking disables another click", pulse.scale > 1.06f)
            assertTrue(pulse.light > .9f)
            screenshot("record-shelf-button-peak.png")
            compose.mainClock.advanceTimeBy(176)
            assertTrue(pickButton.fetchSemanticsNode().config[io.github.currencortex.music.feature.library.ShelfPickFeedbackKey].scale < 1f)
            compose.mainClock.advanceTimeBy(300)
            assertEquals(1f, pickButton.fetchSemanticsNode().config[io.github.currencortex.music.feature.library.ShelfPickFeedbackKey].scale, .001f)
            compose.mainClock.autoAdvance = true
            Thread.sleep(140)
            screenshot("record-shelf-retract.png")
            Thread.sleep(420)
            screenshot("record-shelf-travel.png")
            Thread.sleep(650)
            screenshot("record-shelf-extract.png")
            Thread.sleep(950)
            assertEquals("Random choice does not start playback", -1, played)
            compose.onNodeWithTag("record_shelf_canvas").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .72f, height * .60f)) }
            compose.waitUntil(2000) { played >= 0 }
            compose.onNodeWithTag("record_shelf_save").performClick()
            assertEquals(songs[played].id, saved)
            assertTrue(played in songs.indices)
            screenshot("record-shelf-picked.png")
            var frames = 0L
            compose.runOnUiThread { renderer.setRunning(false); frames = renderer.renderedFrames }
            Thread.sleep(350)
            compose.runOnUiThread { assertTrue(renderer.renderedFrames - frames <= 1); renderer.setRunning(true) }
            compose.runOnUiThread { renderer.select(1, true) }
            Thread.sleep(200)
            compose.onNodeWithTag("record_shelf_search").performClick()
            compose.waitUntil(3000) { compose.onAllNodesWithTag("record_shelf_query").fetchSemanticsNodes().isNotEmpty() }
            val searchFrames = Array(2) { Bitmap.createBitmap(window.decorView.width / 3, window.decorView.height / 3, Bitmap.Config.ARGB_8888) }
            val searchCopied = java.util.concurrent.CountDownLatch(2)
            val searchCopyResults = IntArray(2) { -1 }
            val searchSampled = BooleanArray(2)
            val searchTrace = java.util.Collections.synchronizedList(mutableListOf<String>())
            val retainedXs = java.util.Collections.synchronizedList(mutableListOf<Float>())
            val rejectedXs = java.util.Collections.synchronizedList(mutableListOf<Float>())
            val setSearchText = requireNotNull(compose.onNodeWithTag("record_shelf_query").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.SetText].action)
            compose.runOnUiThread {
                val choreographer = android.view.Choreographer.getInstance()
                val expires = android.os.SystemClock.uptimeMillis() + 6000
                val capture = object : android.view.Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        searchTrace += "${android.os.SystemClock.uptimeMillis()},${renderer.displayedSongs.size},${renderer.replacingResults},${renderer.resultsExitTime},${renderer.introTime},${renderer.running},${renderer.isAttachedToWindow},${android.animation.ValueAnimator.areAnimatorsEnabled()},${renderer.renderedFrames}"
                        if (renderer.resultsExitTime > 0f) {
                            renderer.recordQuad(songs[1])?.let { retainedXs += it[0] }
                            renderer.recordQuad(songs[2])?.let { rejectedXs += it[0] }
                        }
                        val sample = when {
                            renderer.resultsExitTime >= .34f -> 1
                            renderer.resultsExitTime >= .14f -> 0
                            else -> -1
                        }
                        if (renderer.replacingResults && sample >= 0 && !searchSampled[sample]) {
                            searchSampled[sample] = true
                            android.view.PixelCopy.request(window, searchFrames[sample], {
                                searchCopyResults[sample] = it; searchCopied.countDown()
                            }, handler)
                        }
                        if (searchSampled.any { !it } && android.os.SystemClock.uptimeMillis() < expires)
                            choreographer.postFrameCallback(this)
                    }
                }
                choreographer.postFrameCallback(capture)
                // Start the input in this same main-thread operation. A test action that waits
                // for idle would otherwise wait for the frame sampler to expire before typing.
                assertTrue(setSearchText(androidx.compose.ui.text.AnnotatedString("夜航 2")))
            }
            // Commit the search recomposition on Compose's test clock before waiting on the
            // native display clock. Blocking on PixelCopy alone cannot advance recomposition.
            compose.mainClock.advanceTimeBy(64)
            val capturedSearchMotion = searchCopied.await(6, java.util.concurrent.TimeUnit.SECONDS)
            File(context.getExternalFilesDir(null), "record-shelf-search-motion.csv").writeText(searchTrace.joinToString("\n"))
            assertTrue("Contraction and rejected-record exit must run together: ${searchSampled.toList()}", capturedSearchMotion)
            assertTrue(retainedXs.size >= 3)
            assertTrue("Matching record must stay on screen throughout filtering", retainedXs.all { it in 0f..renderer.width.toFloat() })
            assertTrue("Matching extracted case retracts into the compact rack", retainedXs.last() < retainedXs.first())
            assertTrue("Rejected record moves off to the right concurrently", rejectedXs.last() > rejectedXs.first() + renderer.width * .3f)
            for (index in searchFrames.indices) {
                assertEquals(android.view.PixelCopy.SUCCESS, searchCopyResults[index])
                File(context.getExternalFilesDir(null), if (index == 0) "record-shelf-search-exit.png" else "record-shelf-search-entry.png")
                    .outputStream().use { searchFrames[index].compress(Bitmap.CompressFormat.PNG, 100, it) }
                searchFrames[index].recycle()
            }
            compose.waitUntil(4000) { !renderer.replacingResults }
            assertEquals(11, renderer.displayedSongs.size)
            assertEquals("Retained results must not replay the entrance", 1.85f, renderer.introTime, .001f)
            val clearSearchText = requireNotNull(compose.onNodeWithTag("record_shelf_query").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.SetText].action)
            compose.runOnUiThread { assertTrue(clearSearchText(androidx.compose.ui.text.AnnotatedString(""))) }
            compose.mainClock.advanceTimeBy(64)
            compose.runOnUiThread {
                assertEquals(40, renderer.displayedSongs.size)
                assertTrue("Clearing a nonempty search replays every record's entrance", renderer.introTime < .5f)
                assertTrue(renderer.replacingResults)
            }
            compose.waitUntil(4000) { !renderer.replacingResults }
            compose.onNodeWithTag("record_shelf_query").performTextClearance()
            compose.onNodeWithTag("record_shelf_query").performTextInput("夜航 3")
            compose.waitUntil(5000) {
                !renderer.replacingResults && renderer.displayedSongs.size == 11 && renderer.displayedSongs.first().id == 3L
            }
            compose.onNodeWithTag("record_shelf_query").performTextClearance()
            compose.onNodeWithTag("record_shelf_query").performTextInput("missing-song")
            compose.onNodeWithText("查看唱片").performClick()
            compose.waitUntil(4000) { renderer.displayedSongs.isEmpty() && !renderer.replacingResults }
            compose.onNodeWithTag("record_shelf_title").assertTextEquals("没有匹配的歌曲")
            // Clearing an empty result still replays the entry animation for the restored rack.
            compose.onNodeWithTag("record_shelf_search").performClick()
            compose.onNodeWithText("清空搜索").performClick()
            compose.waitUntil(4000) { renderer.displayedSongs.size == 40 && !renderer.replacingResults }
            compose.onNodeWithText("查看唱片").performClick()
            compose.onNodeWithTag("record_shelf_options").performClick()
            compose.onNodeWithTag("record_shelf_count").assertDoesNotExist()
            compose.onNodeWithTag("record_shelf_play").assertDoesNotExist()
            compose.onNodeWithText("搜索歌曲").assertDoesNotExist()
            compose.onNodeWithText("返回歌曲列表").performClick()
            assertTrue(closed)
        } finally {
            compose.mainClock.autoAdvance = true
            settings.forEach { (key, value) ->
                val command = if (value == null) "settings delete global $key" else "settings put global $key $value"
                android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
            }
        }
    }
}
