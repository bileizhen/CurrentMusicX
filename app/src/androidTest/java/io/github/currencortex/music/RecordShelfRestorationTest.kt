package io.github.currencortex.music

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.library.PlaylistRecordShelf
import io.github.currencortex.music.feature.library.RecordShelfView
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecordShelfRestorationTest {
    @get:Rule val compose = createComposeRule()

    private fun renderer(): RecordShelfView {
        lateinit var result: RecordShelfView
        compose.runOnUiThread {
            fun find(view: android.view.View): RecordShelfView? {
                if (view is RecordShelfView) return view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount)
                    find(view.getChildAt(i))?.let { return it }
                return null
            }
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            result = requireNotNull(find(activity.window.decorView))
        }
        return result
    }

    private fun awaitTitle(title: String) {
        compose.waitUntil(5000) {
            compose.onNodeWithTag("record_shelf_title").fetchSemanticsNode().config[SemanticsProperties.Text].first().text == title
        }
        compose.onNodeWithTag("record_shelf_title").assertTextEquals(title)
    }

    private fun select(index: Int, title: String) {
        compose.onNodeWithTag("record_shelf_canvas").performSemanticsAction(SemanticsActions.ScrollToIndex) { assertTrue(it(index)) }
        awaitTitle(title)
    }

    @Test fun restoresIdentityAndSourceAfterReorderingAndDelayedPlaylistArrival() {
        val first = Song(1, "First")
        val netease = Song(7, "NetEase record")
        val qq = Song(7, "QQ record", musicSource = MusicSource.QQ_MUSIC)
        val songs = mutableStateOf(listOf(first, netease, qq))
        val played = mutableListOf<Song>()
        val previews = java.util.concurrent.CopyOnWriteArrayList<Song>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            LeiTheme(AppearanceSettings()) {
                top.yukonga.miuix.kmp.basic.Scaffold {
                    PlaylistRecordShelf(songs.value, true, { played += songs.value[it] }, {},
                        onPreview = { it?.let(previews::add) }, collectionKey = "playlist-a")
                }
            }
        }
        select(2, "QQ record")
        compose.runOnUiThread { songs.value = emptyList(); previews.clear() }
        restoration.emulateSavedInstanceStateRestore()
        awaitTitle("没有匹配的歌曲")
        compose.runOnUiThread { songs.value = listOf(netease, qq, first) }
        awaitTitle("QQ record")
        val restored = renderer()
        compose.runOnUiThread { assertEquals(1f, restored.position, .001f) }
        compose.waitUntil(5000) { previews.isNotEmpty() }
        assertTrue("Restoring must not briefly audition the first record", previews.all { it.id == 7L && it.musicSource == MusicSource.QQ_MUSIC })
        assertTrue("Restoring a view must not replace the playback queue", played.isEmpty())
        compose.onNodeWithTag("record_shelf_canvas").performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        assertEquals(listOf(qq), played)
    }

    @Test fun removedSelectionFallsBackAndAnotherPlaylistDoesNotInheritIt() {
        val first = Song(1, "First")
        val second = Song(2, "Second")
        val removed = Song(3, "Removed")
        val songs = mutableStateOf(listOf(first, second, removed))
        val collection = mutableStateOf("playlist-a")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            LeiTheme(AppearanceSettings()) {
                top.yukonga.miuix.kmp.basic.Scaffold {
                    PlaylistRecordShelf(songs.value, true, {}, {}, collectionKey = collection.value)
                }
            }
        }
        select(2, "Removed")
        compose.runOnUiThread { songs.value = emptyList() }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnUiThread { songs.value = listOf(first, second) }
        awaitTitle("First")
        val fallback = renderer()
        compose.runOnUiThread { assertEquals(0f, fallback.position, .001f) }
        select(1, "Second")
        compose.runOnUiThread {
            collection.value = "playlist-b"
            songs.value = listOf(Song(99, "Other playlist first"), second)
        }
        awaitTitle("Other playlist first")
        val next = renderer()
        compose.runOnUiThread { assertEquals(0f, next.position, .001f) }
        restoration.emulateSavedInstanceStateRestore()
        awaitTitle("Other playlist first")
        compose.onNodeWithTag("record_shelf_close").assertIsDisplayed()
    }

    @Test fun restoredFilterWithoutMatchesStaysEmptyAndCanBeCleared() {
        val songs = mutableStateOf(listOf(Song(1, "First"), Song(2, "Target")))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            LeiTheme(AppearanceSettings()) {
                top.yukonga.miuix.kmp.basic.Scaffold {
                    PlaylistRecordShelf(songs.value, true, {}, {}, collectionKey = "playlist-a")
                }
            }
        }
        select(1, "Target")
        compose.onNodeWithTag("record_shelf_search").performClick()
        compose.onNodeWithTag("record_shelf_query").performTextInput("Target")
        compose.onNodeWithText("查看唱片").performClick()
        val filtered = renderer()
        compose.waitUntil(5000) { filtered.displayedSongs.size == 1 && !filtered.replacingResults }
        compose.runOnUiThread { songs.value = emptyList() }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnUiThread { songs.value = listOf(Song(1, "First"), Song(3, "Other")) }
        awaitTitle("没有匹配的歌曲")
        compose.onNodeWithTag("record_shelf_canvas").performSemanticsAction(SemanticsActions.OnClick) { assertFalse(it()) }
        compose.onNodeWithTag("record_shelf_search").performClick()
        compose.onNodeWithTag("record_shelf_query").assertTextContains("Target")
        compose.onNodeWithText("清空搜索").performClick()
        compose.onNodeWithText("查看唱片").performClick()
        awaitTitle("First")
    }
}
