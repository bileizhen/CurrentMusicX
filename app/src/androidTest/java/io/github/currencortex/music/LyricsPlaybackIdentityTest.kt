package io.github.currencortex.music

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.NavidromeTrackRef
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.SongExternalIds
import io.github.currencortex.music.feature.lyrics.data.SongLyricsStore
import io.github.currencortex.music.feature.lyrics.desktop.DesktopIslandCoverTransition
import io.github.currencortex.music.feature.lyrics.desktop.DesktopIslandCueHost
import io.github.currencortex.music.feature.lyrics.desktop.rememberIslandCoverTransition
import io.github.currencortex.music.feature.lyrics.desktop.rememberIslandPresentation
import io.github.currencortex.music.feature.lyrics.model.LyricLine
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Track identity changes reset animation resources even when cue text and numeric ids coincide. */
class LyricsPlaybackIdentityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun platformExternalIdAndLibraryChangesResetIntroCoverAndPreparedCue() {
        val current = mutableStateOf(Song(42, "Song", externalIds = SongExternalIds(neteaseId = "42")))
        val cue = LyricLine(0, 5000, "Same cue in different songs")
        lateinit var reveal: State<Float>
        lateinit var cover: DesktopIslandCoverTransition
        var renderersCreated = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val song = current.value
            val identity = SongLyricsStore.identity(song)
            reveal = rememberIslandPresentation(identity, ready = true, motion = true)
            cover = rememberIslandCoverTransition(song, motion = true)
            DesktopIslandCueHost(identity, 0, cue, -1, null, Unit, Modifier.width(240.dp)) { line ->
                DisposableEffect(Unit) { renderersCreated++; onDispose { } }
                BasicText(line.text)
            }
        }
        compose.mainClock.advanceTimeBy(3000)
        compose.runOnIdle {
            assertEquals(1f, reveal.value, .001f)
            assertEquals(1, renderersCreated)
        }
        val destinations = listOf(
            Song(42, "QQ A", musicSource = MusicSource.QQ_MUSIC, externalIds = SongExternalIds(qqMusicId = "a")),
            Song(42, "QQ B", musicSource = MusicSource.QQ_MUSIC, externalIds = SongExternalIds(qqMusicId = "b")),
            Song(42, "Library A", musicSource = MusicSource.NAVIDROME, navidrome = NavidromeTrackRef("track", "server-a")),
            Song(42, "Library B", musicSource = MusicSource.NAVIDROME, navidrome = NavidromeTrackRef("track", "server-b")),
        )
        destinations.forEachIndexed { index, song ->
            compose.runOnIdle { current.value = song }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                assertEquals("A different complete identity starts a fresh intro", 0f, reveal.value, .001f)
                assertTrue("A same-id song change must still hand over its cover", cover.progress.value < 1f)
                assertEquals("Prepared cue slots cannot outlive their song", index + 2, renderersCreated)
            }
            compose.mainClock.advanceTimeBy(3000)
            compose.runOnIdle { assertEquals(1f, reveal.value, .001f) }
        }
        val settledCover = cover
        compose.runOnIdle { current.value = current.value.copy(name = "Metadata correction", cover = "new-cover") }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertSame("Metadata edits do not replay a song handover", settledCover, cover)
            assertEquals(1f, reveal.value, .001f)
            assertEquals(destinations.size + 1, renderersCreated)
        }
    }
}
