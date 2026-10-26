package io.github.currencortex.music

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.currencortex.music.core.media.PlayerState
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.LyricsSource
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import io.github.currencortex.music.feature.player.PlayerLyricsManagementDialog
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Real manager UI, isolated storage and fixed lyric provider; no audio, accounts or live network. */
class PlayerLyricsManagementTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<CurrentMusicApplication>()
    private val song = Song(708, "Manager fixture", "Fixture singer", durationMs = 8_000)
    private val original = LyricsParser.lrc("[00:01.00]Original fixture\n[00:04.00]Second fixture")
    private val imported = SavedLyrics(LyricsParser.lrc("[00:01.00]Imported fixture"), LyricsOrigin("chosen.lrc", "imported", "chosen.lrc"))
    private val matched = SavedLyrics(LyricsParser.lrc("[00:01.00]Matched fixture"), LyricsOrigin("AMLL fixture", "amll", "ncm:708"))

    private suspend fun container(calls: AtomicInteger = AtomicInteger()): AppContainer {
        check(context.packageName.endsWith(".verification"))
        return AppContainer(context, "lyrics-manager-${UUID.randomUUID()}", ttmlProvider = LyricsProvider {
            calls.incrementAndGet()
            LyricsProviderResult.Parsed(original)
        }).also {
            it.ready.await(); it.sessionRestored.await()
            it.updateSettings.setAutoCheck(false)
        }
    }

    private suspend fun playSilently(container: AppContainer, value: Song = song) = withContext(Dispatchers.Main) {
        container.playbackQueue.replace(listOf(value), 0)
        container.playerController.state.value = PlayerState(song = value, positionMs = 2_000, durationMs = value.durationMs)
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    @Test fun offsetButtonsTypedValuesAndResetStayPerSongAndDoNotReloadLyrics() = runBlocking<Unit> {
        val calls = AtomicInteger()
        val container = container(calls)
        try {
            playSilently(container)
            compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
                PlayerLyricsManagementDialog(container, song) {}
            } }
            compose.waitUntil(5_000) { container.playbackLyrics.state.value.document == original }
            val loads = calls.get()
            click("lyrics_offset_plus")
            compose.waitUntil(5_000) { container.songLyrics.settings(song).offsetMs == 250L }
            compose.waitUntil(5_000) { container.playbackLyrics.state.value.songOffsetMs == 250L }
            click("lyrics_offset_minus")
            compose.waitUntil(5_000) { container.songLyrics.settings(song).offsetMs == 0L }
            compose.onNodeWithTag("lyrics_offset_input").performScrollTo().performTextReplacement("-750")
            click("lyrics_offset_apply")
            compose.waitUntil(5_000) { container.songLyrics.settings(song).offsetMs == -750L }
            click("lyrics_offset_reset")
            compose.waitUntil(5_000) { container.songLyrics.settings(song).offsetMs == 0L }
            assertEquals(loads, calls.get())
            assertEquals(original, container.playbackLyrics.state.value.document)
            assertEquals(listOf(song), container.playbackQueue.state.value.songs)
            assertFalse(container.playerController.state.value.playing)
        } finally { container.close() }
    }

    @Test fun importedAndMatchedChoicesStayIndependentAndRestoringDefaultRetainsOffset() = runBlocking<Unit> {
        val container = container()
        try {
            assertEquals(LyricsSource.AUTO, container.musicSettings.snapshot().lyricsSource)
            container.songLyrics.saveImported(song, imported)
            container.songLyrics.saveMatched(song, matched)
            container.songLyrics.setOffset(song, 500)
            playSilently(container)
            compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
                PlayerLyricsManagementDialog(container, song) {}
            } }
            compose.waitUntil(5_000) { container.playbackLyrics.state.value.origin == matched.origin }
            compose.onNodeWithTag("lyrics_source_current").assertTextContains(matched.origin.label)
            compose.onNodeWithTag("lyrics_source_default").assertTextContains("自动择优")
                .assertTextContains("以网易云歌词为基准匹配")
            click("lyrics_source_imported")
            compose.waitUntil(5_000) { container.playbackLyrics.state.value.origin == imported.origin }
            assertNotNull(container.songLyrics.settings(song).matchedOrigin)
            click("lyrics_source_matched")
            compose.waitUntil(5_000) { container.playbackLyrics.state.value.origin == matched.origin }
            assertNotNull(container.songLyrics.settings(song).importedOrigin)
            click("lyrics_source_default")
            compose.waitUntil(5_000) { container.playbackLyrics.state.value.document == original }
            compose.onNodeWithTag("lyrics_source_default").assertIsSelected().assertTextContains("自动择优")
                .assertTextContains("以网易云歌词为基准匹配")
            assertNull(container.songLyrics.settings(song).matchedOrigin)
            assertNull(container.songLyrics.settings(song).importedOrigin)
            assertEquals(500L, container.songLyrics.settings(song).offsetMs)
            assertEquals(listOf(song), container.playbackQueue.state.value.songs)
            assertFalse(container.playerController.state.value.playing)
        } finally { container.close() }
    }

    @Test fun navidromeManagerShowsAutomaticPolicyUntilAnOriginalSourceIsExplicitlySelected() = runBlocking<Unit> {
        val calls = AtomicInteger()
        val container = container(calls)
        val navidromeSong = song.copy(musicSource = MusicSource.NAVIDROME)
        try {
            assertEquals(LyricsSource.AUTO, container.musicSettings.snapshot().lyricsSource)
            compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
                PlayerLyricsManagementDialog(container, navidromeSong) {}
            } }
            compose.onNodeWithTag("lyrics_source_default").assertTextContains("自动择优")
                .assertTextContains("以网易云歌词为基准匹配")
            compose.onNodeWithTag("lyrics_source_current").assertTextContains("自动择优")
            container.musicSettings.setLyricsSource(LyricsSource.NETEASE)
            compose.waitUntil(5000) { container.musicSettings.state.value.lyricsSource == LyricsSource.NETEASE }
            compose.onNodeWithTag("lyrics_source_default").assertTextContains("Navidrome")
            container.musicSettings.setLyricsSource(LyricsSource.AUTO)
            compose.waitUntil(5000) { container.musicSettings.state.value.lyricsSource == LyricsSource.AUTO }
            compose.onNodeWithTag("lyrics_source_default").assertTextContains("自动择优")
                .assertTextContains("以网易云歌词为基准匹配")
            assertEquals(0, calls.get())
            assertTrue(container.playbackQueue.state.value.songs.isEmpty())
        } finally { container.close() }
    }

    @Test fun sixMatchSourcesUseHorizontalTabsAndChoosingATabDoesNotSearchOrReplaceLyrics() = runBlocking<Unit> {
        val calls = AtomicInteger()
        val container = container(calls)
        try {
            playSilently(container)
            compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
                PlayerLyricsManagementDialog(container, song) {}
            } }
            compose.waitUntil(5_000) { container.playbackLyrics.state.value.document == original }
            val loads = calls.get()
            click("lyrics_match_open")
            compose.onNodeWithTag("lyrics_match_source_netease").performScrollTo()
                .assertIsSelected().assertTextContains(LyricsMatchSource.NETEASE.shortLabel)
            assertEquals(6, LyricsMatchSource.entries.size)
            LyricsMatchSource.entries.forEach { source ->
                compose.onNodeWithTag("lyrics_match_source_${source.name.lowercase()}").assertExists()
                    .performScrollTo().assertIsDisplayed()
            }
            val firstTab = compose.onNodeWithTag("lyrics_match_source_netease").performScrollTo()
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val laterTab = compose.onNodeWithTag("lyrics_match_source_qishui").performScrollTo()
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("All source tabs remain on one horizontal row", firstTab.top, laterTab.top, 2f)
            listOf(LyricsMatchSource.CURRENT_MUSIC, LyricsMatchSource.QQ_MUSIC, LyricsMatchSource.QISHUI).forEach { source ->
                val tag = "lyrics_match_source_${source.name.lowercase()}"
                click(tag)
                compose.onNodeWithTag(tag).assertIsSelected().assertTextContains(source.shortLabel)
                compose.onNodeWithTag("lyrics_match_source_netease").assertIsNotSelected()
                compose.onNodeWithTag("lyrics_match_preview_line_0").assertDoesNotExist()
                compose.runOnIdle {
                    assertEquals(loads, calls.get())
                    assertEquals(original, container.playbackLyrics.state.value.document)
                    assertNull(container.songLyrics.settings(song).matchedOrigin)
                    assertNull(container.songLyrics.settings(song).importedOrigin)
                    assertEquals(listOf(song), container.playbackQueue.state.value.songs)
                    assertFalse(container.playerController.state.value.playing)
                }
            }
            compose.onNodeWithTag("lyrics_match_back").performClick()
            compose.onNodeWithTag("lyrics_source_current").assertExists()
            assertEquals(original, container.playbackLyrics.state.value.document)
            assertNull(container.songLyrics.settings(song).matchedOrigin)
            // Search and automatic selection remain explicit actions; neither is clicked here.
        } finally { container.close() }
    }

    @Test fun replacingTheDialogSongUsesANewOffsetTargetAndLeavesThePlayingQueueAlone() = runBlocking<Unit> {
        val container = container()
        val other = Song(709, "Second manager fixture", "Other singer", durationMs = 8_000)
        var shownSong by mutableStateOf(song)
        var open by mutableStateOf(true)
        try {
            container.songLyrics.setOffset(song, 750)
            playSilently(container)
            compose.setContent { LeiTheme(AppearanceSettings(blur = false)) {
                if (open) PlayerLyricsManagementDialog(container, shownSong) { open = false }
            } }
            compose.onNodeWithTag("lyrics_management").assertExists()
            compose.runOnIdle { shownSong = other }
            compose.onNodeWithText(other.name).assertExists()
            click("lyrics_offset_plus")
            compose.waitUntil(5_000) { container.songLyrics.settings(other).offsetMs == 250L }
            assertEquals(750L, container.songLyrics.settings(song).offsetMs)
            assertEquals(listOf(song), container.playbackQueue.state.value.songs)
            compose.onNodeWithTag("lyrics_management_close").performClick()
            compose.onNodeWithTag("lyrics_management").assertDoesNotExist()
        } finally { container.close() }
    }
}
