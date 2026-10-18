package io.github.currencortex.music

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.player.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Real queue operations with an isolated presentation; never starts an audio service. */
class PlaybackQueuePageTest {
    @get:Rule val compose = createComposeRule()
    private val queue = PlaybackQueue()
    private lateinit var activity: ComponentActivity
    private var dismissed = false
    private var downloaded = 0
    private var collected = 0
    private var menuSong: Song? = null
    private val recommendations = mutableListOf<Boolean>()
    private fun show(songs: List<Song>, index: Int = 0, shared: Boolean = false) {
        queue.replace(songs, index)
        queue.state.value = queue.state.value.copy(positionMs = 1500)
        compose.setContent {
            activity = LocalActivity.current as ComponentActivity
            val snapshot by queue.state.collectAsState()
            var shown by remember { mutableStateOf(true) }
            val motion = remember { QueuePageMotion() }
            MiuixTheme { Scaffold { Box(Modifier.fillMaxSize()) {
                if (shown) PlaybackQueueContent(snapshot, true, false, true, null, motion, true, "test_queue",
                    queue::select, { queue.remove(it) }, queue::clear, queue::setMode,
                    { dismissed = true; shown = false }, shared = shared,
                    nativeActions = if (shared) NativeQueueActions(true, "根据双方口味，心动推荐",
                        { recommendations += it }, { downloaded++ }, { collected++ }, { menuSong = it }) else null)
            } } }
        }
        compose.waitForIdle()
    }
    @Test fun nativeSharedQueueUsesExistingRowsModesAndClearConfirmation() {
        show(listOf(Song(10, "一起听歌曲", "歌手"), Song(20, "下一首", "歌手")), shared = true)
        compose.onNodeWithText("一起听歌单").assertIsDisplayed()
        compose.onNodeWithTag("queue_position").assertTextEquals("1 / 2")
        compose.onNodeWithTag("queue_song_0").assertIsSelected()
        compose.onNodeWithTag("queue_recommendation").assertIsOn().performClick()
        compose.runOnIdle { assertEquals(listOf(false), recommendations); assertEquals(0, downloaded); assertEquals(0, collected) }
        compose.onNodeWithTag("queue_download_all").performClick()
        compose.onNodeWithTag("queue_collect_all").performClick()
        compose.onNodeWithTag("queue_song_1").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, downloaded); assertEquals(1, collected); assertEquals(20L, menuSong?.id) }
        compose.onNodeWithTag("queue_song_1").performClick()
        compose.onNodeWithTag("queue_song_1").assertIsSelected()
        compose.onNodeWithTag("open_player_mode").performClick()
        compose.onNodeWithTag("playback_mode_HEART").assertDoesNotExist()
        compose.onNodeWithTag("playback_mode_SHUFFLE").performClick()
        compose.onNodeWithTag("queue_clear").performClick()
        compose.onNodeWithText("会同步清空双方的歌单并暂停播放，一起听会话仍会保留。").assertIsDisplayed()
        compose.onNodeWithTag("queue_clear_cancel").performClick()
        compose.onNodeWithTag("queue_position").assertTextEquals("2 / 2")
        compose.runOnIdle { assertFalse(dismissed) }
    }
    @Test fun rowsModesRemovalAndClearKeepTheQueuePageOpen() {
        show(listOf(Song(1, "Letting Go", "蔡健雅", "说到爱"), Song(2, "小半", "陈粒", "Nagram"),
            Song(3, "小半", "陈粒", "小梦大半"), Song(4, "下一首", "歌手", "专辑")), 2)
        compose.onNodeWithTag("queue_position").assertTextEquals("3 / 4")
        compose.onNodeWithTag("queue_song_2").assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(1500L, queue.state.value.positionMs) }
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        compose.onNodeWithTag("test_queue").captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(context.externalCacheDir, "queue-reference-layout.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("queue_song_1").performClick()
        compose.onNodeWithTag("queue_song_1").assertIsSelected()
        compose.onNodeWithTag("test_queue").assertIsDisplayed()
        compose.onNodeWithTag("queue_remove_0").performClick()
        compose.runOnIdle { assertEquals(2L, queue.state.value.current!!.id); assertEquals(0, queue.state.value.index) }
        compose.onNodeWithTag("queue_position").assertTextEquals("1 / 3")
        compose.onNodeWithTag("open_player_mode").performClick()
        PlaybackMode.entries.forEach { compose.onNodeWithTag("playback_mode_${it.name}").assertIsDisplayed() }
        compose.onNodeWithTag("playback_mode_SHUFFLE").performClick()
        compose.runOnIdle { assertEquals(PlaybackMode.SHUFFLE, queue.state.value.mode) }
        compose.onNodeWithTag("test_queue").assertIsDisplayed()
        compose.onNodeWithTag("queue_clear").performClick()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("queue_clear_confirm").assertDoesNotExist()
        compose.runOnIdle { assertEquals(3, queue.state.value.songs.size); assertFalse(dismissed) }
        compose.onNodeWithTag("queue_clear").performClick()
        compose.onNodeWithTag("queue_clear_confirm").performClick()
        compose.onNodeWithText("播放队列为空").assertIsDisplayed()
        compose.onNodeWithTag("queue_clear").assertIsNotEnabled()
        compose.onNodeWithTag("queue_position").assertTextEquals("0 / 0")
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(dismissed) }
    }
    @Test fun listScrollingSmallDragAndFullDragHaveSeparateOutcomes() {
        show((1..50).map { Song(it.toLong(), "歌曲 $it", "歌手", "专辑") })
        compose.onNodeWithTag("queue_drag_handle").performTouchInput {
            swipe(center, center + Offset(0f, 40f), durationMillis = 800)
        }
        compose.runOnIdle { assertFalse(dismissed) }
        compose.onNodeWithTag("queue_songs").performTouchInput { swipeUp() }
        compose.runOnIdle { assertFalse(dismissed) }
        compose.onNodeWithTag("queue_song_0").assertIsNotDisplayed()
        compose.onNodeWithTag("queue_songs").performScrollToIndex(0)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("queue_drag_handle").performTouchInput { down(center); moveBy(Offset(0f, 180f)) }
        compose.mainClock.advanceTimeByFrame()
        val progress = compose.onNodeWithTag("test_queue").fetchSemanticsNode().config[QueuePageProgress]
        assertTrue("Dragging follows the finger before release", progress > 0 && progress < 1)
        compose.onNodeWithTag("queue_drag_handle").performTouchInput { moveBy(Offset(0f, 600f)); up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
