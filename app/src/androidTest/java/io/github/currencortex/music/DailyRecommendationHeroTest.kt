package io.github.currencortex.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.home.DailyRecommendationHero
import io.github.currencortex.music.feature.player.LocalPlayerArtworkRotation
import io.github.currencortex.music.ui.component.RoomSongRequest
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

class DailyRecommendationHeroTest {
    @get:Rule val compose = createComposeRule()
    private val songs = listOf(Song(11, "First recommendation", "First artist"),
        Song(22, "Second recommendation", "Second artist"), Song(33, "Third recommendation", "Third artist"))
    private fun settle() { compose.waitForIdle() }
    private fun browseNext() = compose.onNodeWithTag("home_daily_deck").performTouchInput {
        swipe(Offset(center.x + width * .15f, center.y), Offset(center.x - width * .15f, center.y), 600)
    }

    @Composable private fun Host(theme: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
        LeiTheme(AppearanceSettings(themeMode = theme, blur = false)) {
            Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).padding(20.dp).testTag("hero_fixture")) {
                CompositionLocalProvider(LocalPlayerArtworkRotation provides remember { mutableFloatStateOf(37f) }) { content() }
            }
        }
    }

    @Test fun swipingOnlyBrowsesAndTappingTheSelectedCoverControlsPlayback() {
        val plays = mutableListOf<Int>()
        val playback = mutableStateOf(PlayerState())
        compose.setContent { Host { DailyRecommendationHero(songs, false, null, false, playback.value,
            { plays += it }, {}, {}, {}) } }
        listOf("home_daily_previous", "home_daily_play", "home_daily_next").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        compose.onNodeWithText("1 / 3 · 左右滑动切换").assertDoesNotExist()
        browseNext()
        settle()
        compose.onNodeWithTag("home_daily_title").assertTextEquals("Second recommendation")
        assertTrue("Browsing must not autoplay", plays.isEmpty())
        browseNext()
        compose.onNodeWithTag("home_daily_title").assertTextEquals("Third recommendation")
        assertTrue(plays.isEmpty())
        compose.onNodeWithTag("home_daily_cover_33").performClick()
        assertEquals(listOf(2), plays)
        compose.runOnUiThread { playback.value = PlayerState(song = songs[2], playing = true) }
        compose.onNodeWithTag("home_daily_cover_33").performClick()
        assertEquals(listOf(2, 2), plays)
        // Vertical motion over the deck must not browse or trigger playback.
        compose.onNodeWithTag("home_daily_deck").performTouchInput { swipeUp() }
        compose.onNodeWithTag("home_daily_title").assertTextEquals("Third recommendation")
        assertEquals(listOf(2, 2), plays)
        browseNext()
        compose.onNodeWithTag("home_daily_title").assertTextEquals("First recommendation")
        assertEquals("Wrapping around also only browses", listOf(2, 2), plays)
    }

    @Test fun selectionSurvivesRefreshReorderingAndSavedStateThenClampsWhenRemoved() {
        val current = mutableStateOf(songs)
        val played = mutableListOf<Long>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Host { DailyRecommendationHero(current.value, false, null, false, PlayerState(),
            { played += current.value[it].id }, {}, {}, {}) } }
        browseNext()
        compose.runOnUiThread { current.value = songs.reversed() }
        compose.onNodeWithTag("home_daily_title").assertTextEquals("Second recommendation")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("home_daily_title").assertTextEquals("Second recommendation")
        compose.onNodeWithTag("home_daily_cover_22").performClick()
        assertEquals(listOf(22L), played)
        compose.runOnUiThread { current.value = listOf(songs[2]) }
        compose.onNodeWithTag("home_daily_title").assertTextEquals("Third recommendation")
        compose.onNodeWithTag("home_daily_cover_33").assertIsEnabled().performClick()
        assertEquals(listOf(22L, 33L), played)
    }

    @Test fun oneContinuousDragBrowsesSeveralCoversBeforeReleaseWithoutAutoplay() {
        val plays = mutableListOf<Int>()
        compose.setContent { Host { DailyRecommendationHero(songs, false, null, false, PlayerState(),
            { plays += it }, {}, {}, {}) } }
        val deck = compose.onNodeWithTag("home_daily_deck")
        deck.performTouchInput { down(center); moveBy(Offset(-width * .30f, 0f), 400) }
        compose.onNodeWithTag("home_daily_title").assertTextEquals("Second recommendation")
        deck.performTouchInput { moveBy(Offset(-width * .30f, 0f), 400) }
        compose.onNodeWithTag("home_daily_title").assertTextEquals("Third recommendation")
        assertTrue("Dragging across multiple covers must not autoplay", plays.isEmpty())
        deck.performTouchInput { cancel() }
        compose.onNodeWithTag("home_daily_title").assertTextEquals("Third recommendation")
        compose.onNodeWithTag("home_daily_cover_33").performClick()
        assertEquals(listOf(2), plays)
    }

    @Test fun loadingFailureAndAnonymousEmptyStateOfferTheCorrectAction() {
        val loading = mutableStateOf(true)
        val error = mutableStateOf<String?>(null)
        var retries = 0; var connections = 0; var opens = 0
        compose.setContent { Host { DailyRecommendationHero(emptyList(), loading.value, error.value, true, PlayerState(),
            {}, { opens++ }, { retries++ }, { connections++ }) } }
        compose.onNodeWithTag("home_loading").assertExists()
        compose.onNodeWithTag("home_daily_play").assertDoesNotExist()
        compose.runOnUiThread { loading.value = false; error.value = "暂时无法连接" }
        compose.onNodeWithTag("home_loading").assertDoesNotExist()
        compose.onNodeWithText("暂时无法连接").assertIsDisplayed()
        compose.onNodeWithTag("home_daily_retry").performClick()
        assertEquals(1, retries); assertEquals(0, connections)
        compose.runOnUiThread { error.value = null }
        compose.onNodeWithTag("home_daily_retry").performClick()
        assertEquals(1, connections)
        compose.onNodeWithTag("open_daily").performClick()
        assertEquals(1, opens)
    }

    @Test fun roomRequestsDisableDuplicateSubmissionButStillAllowBrowsing() {
        val played = mutableListOf<Int>()
        val room = RoomSongRequest({}, { it == 11L })
        compose.setContent { Host { DailyRecommendationHero(songs, false, null, false,
            PlayerState(song = songs[0], playing = true), { played += it }, {}, {}, {}, room = room) } }
        compose.onNodeWithTag("home_daily_cover_11").assertIsNotEnabled()
        browseNext()
        compose.onNodeWithTag("home_daily_cover_22").assertIsEnabled().performClick()
        assertEquals(listOf(1), played)
    }

    @Test fun playbackSlidesRecordOutAndPauseRetractsItIncludingInterruptedMotion() {
        val playback = mutableStateOf(PlayerState(song = songs[0]))
        compose.setContent { Host { DailyRecommendationHero(songs, false, null, false, playback.value,
            { playback.value = playback.value.copy(playing = !playback.value.playing) }, {}, {}, {}) } }
        val record = compose.onNodeWithTag("home_daily_record", useUnmergedTree = true)
        fun centre() = record.fetchSemanticsNode().boundsInRoot.center.x
        val closed = centre()
        compose.mainClock.autoAdvance = false
        fun advance(milliseconds: Long) {
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(milliseconds)
            compose.waitForIdle()
        }
        compose.onNodeWithTag("home_daily_cover_11").performClick()
        advance(160)
        val emerging = centre()
        assertTrue("Play must slide the record out", emerging > closed + 1f)
        advance(1_200)
        val opened = centre()
        assertTrue("The record must move continuously rather than jump to the end", opened > emerging + 1f)
        compose.onNodeWithTag("home_daily_cover_11").performClick()
        advance(160)
        val retracting = centre()
        assertTrue("Pause must slide back through an intermediate position", retracting > closed + 1f && retracting < opened - 1f)
        // Reverse before retraction finishes; the current layer must continue, without being recreated at an endpoint.
        compose.onNodeWithTag("home_daily_cover_11").performClick()
        advance(96)
        val reversed = centre()
        assertTrue("Interrupted playback must keep the record between its endpoints", reversed > closed + 1f && reversed < opened - 1f)
        advance(1_200)
        assertEquals(opened, centre(), 1f)
        compose.onNodeWithTag("home_daily_cover_11").performClick()
        advance(1_500)
        assertEquals("Pause must retract fully behind the cover", closed, centre(), 1f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun narrowLargeTextLayoutAndBothThemesKeepTheSelectedCoverInsideTheViewport() {
        val theme = mutableStateOf(ThemeMode.LIGHT)
        val context = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
        val artwork = songs.mapIndexed { index, song ->
            val file = File(context.cacheDir, "hero-cover-${song.id}.png")
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(listOf(0xFF9F5237, 0xFF3E776D, 0xFF555E98)[index].toInt())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE6D9B9.toInt(); style = Paint.Style.STROKE; strokeWidth = 8f }
            repeat(7) { canvas.drawCircle(128f, 128f, 22f + it * 17, paint) }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            song.copy(cover = file.absolutePath)
        }
        compose.setContent { Host(theme.value) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                Box(Modifier.width(292.dp).testTag("narrow_hero")) {
                    DailyRecommendationHero(artwork, false, null, false, PlayerState(song = artwork[0], playing = true), {}, {}, {}, {})
                }
            }
        } }
        fun capture(name: String) {
            compose.onNodeWithTag("home_daily_cover_11").assertIsDisplayed()
            val bounds = compose.onNodeWithTag("narrow_hero").fetchSemanticsNode().boundsInRoot
            val cover = compose.onNodeWithTag("home_daily_cover_11").fetchSemanticsNode().boundsInRoot
            assertTrue("Selected cover must fit the narrow viewport", cover.left >= bounds.left && cover.right <= bounds.right + 1)
            compose.onNodeWithTag("hero_fixture").captureToImage().asAndroidBitmap().let { image ->
                File(context.externalCacheDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        capture("daily-hero-light.png")
        compose.runOnUiThread { theme.value = ThemeMode.DARK }
        capture("daily-hero-dark.png")
        browseNext()
        compose.onNodeWithTag("home_daily_title").assertTextEquals("Second recommendation")
    }
}
