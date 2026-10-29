package io.github.currencortex.music

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.player.PlayerLyricsMatchPanel
import io.github.currencortex.music.ui.theme.LeiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in public network regression; never reads accounts, plays audio or saves a lyric override. */
class QqLyricsLiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun publicQqCoverAndColonTimestampNoticeCanBePreviewedOnDevice(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("qqLyricsLive") == "true")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        check(context.packageName.endsWith(".verification"))
        val source = QqLyricsMatchSource()
        val results = source.search("SUBARU AWA 486 (4) - MKMS")
        val candidate = results.first { it.id == "001bIzp24ISxYM" }
        assertEquals("https://y.gtimg.cn/music/photo_new/T002R300x300M00000017J8H3zbzxe.jpg", candidate.cover)
        val cover = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(candidate.cover).size(160).build())
        assertTrue("The app image loader must decode the public QQ album cover", cover is SuccessResult)
        val document = source.load(candidate)
        assertEquals("此歌曲为没有填词的纯音乐，请您欣赏", document.lines.single().text)
        assertEquals(0L, document.lines.single().startTimeMs)
        assertFalse(document.hasWordTiming)
        val saved = SavedLyrics(document, LyricsOrigin("QQ 音乐", "qq_music", candidate.id))
        var selected by mutableStateOf<LyricsCandidate?>(null)
        compose.setContent {
            LeiTheme(AppearanceSettings(blur = false)) {
                PlayerLyricsMatchPanel(
                    song = Song(661195419, candidate.title, candidate.artists, candidate.album, durationMs = candidate.durationMs),
                    source = LyricsMatchSource.QQ_MUSIC, query = "SUBARU AWA 486 (4) - MKMS",
                    results = listOf(candidate), selectedCandidate = selected,
                    preview = if (selected == null) null else saved, assessment = null, assessmentNote = null,
                    searching = false, previewing = false, busy = false, message = null, messageError = false,
                    hasSearched = true, onSource = {}, onQuery = {}, onSearch = {}, onAutomatic = {},
                    onCandidate = { selected = it }, onCancelLoading = {}, onBack = {}, onApply = {},
                    modifier = Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithTag("lyrics_match_candidate_cover_0", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lyrics_match_candidate_0").performClick()
        compose.onNodeWithTag("lyrics_match_preview_cover", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lyrics_match_preview_line_0").performScrollTo()
        compose.onNodeWithText("此歌曲为没有填词的纯音乐，请您欣赏").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_match_apply").assertIsEnabled()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.getExternalFilesDir(null), "qq-lyrics-fixed.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
