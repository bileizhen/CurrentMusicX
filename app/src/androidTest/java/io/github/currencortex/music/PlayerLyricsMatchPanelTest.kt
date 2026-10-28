package io.github.currencortex.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.*
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import io.github.currencortex.music.feature.lyrics.model.LyricsTimingAlignment
import io.github.currencortex.music.feature.lyrics.model.LyricsTimeAnchor
import io.github.currencortex.music.feature.lyrics.domain.LyricsReferenceScore
import io.github.currencortex.music.feature.player.PlayerLyricsMatchPanel
import io.github.currencortex.music.ui.theme.LeiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The actual compact panel with inert callbacks; no container, accounts, playback or network. */
class PlayerLyricsMatchPanelTest {
    @get:Rule val compose = createComposeRule()
    private val song = Song(708, "Panel fixture", "Fixture singer", "Fixture album", durationMs = 8_000)
    private val candidate = LyricsCandidate(LyricsMatchSource.NETEASE, "708", song.name,
        song.artists, song.album, 0, "708")
    private val saved = SavedLyrics(LyricsParser.lrc("[00:01]Panel fixture first line\n[00:04]Second line"),
        LyricsOrigin("网易云音乐", "netease", "708"))
    private val score = LyricsMatchAssessment(90, score = LyricsReferenceScore(true, 1.0, 1.0, .9, .96), metadataReliable = true)

    @Test fun previewShowsCorrectedTimesAndAnAlignmentLabelWhileKeepingTheSavedSourceRaw() {
        val state = PanelState().also {
            it.preview = saved.copy(timingAlignment = LyricsTimingAlignment(listOf(LyricsTimeAnchor(1000, 0)), 6, 1.0))
            it.assessment = score
        }
        show(state)
        compose.onNodeWithTag("lyrics_match_alignment").assertTextContains("提前 1.00 秒", substring = true)
        compose.onNodeWithTag("lyrics_match_preview_line_0").performScrollTo()
        compose.onNodeWithText("0:00").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1000L, state.preview!!.document.lines.first().startTimeMs) }
    }

    @Test fun highInformationPercentageCannotOverrideFailedTimingAndEligibleAmllIsLabelled() {
        val state = PanelState().also {
            it.preview = saved
            it.assessment = score.copy(hasWordTiming = true, score = score.score!!.copy(accepted = false, timingSimilarity = 0.0))
        }
        show(state)
        compose.onNodeWithTag("lyrics_match_content_score").assertTextContains("90%", substring = true)
        compose.onNodeWithTag("lyrics_match_policy").assertTextContains("内容或时间轴未通过", substring = true)
        compose.onNodeWithTag("lyrics_match_timing_score").assertTextContains("未通过校验", substring = true)
        compose.runOnIdle {
            state.preview = saved.copy(origin = LyricsOrigin("AMLL 逐字歌词", "amll", "ncm:708"))
            state.assessment = score.copy(hasWordTiming = true, score = score.score!!.copy(similarity = .75, hasWordTiming = true))
        }
        compose.onNodeWithTag("lyrics_match_reference_score").assertTextContains("75%", substring = true)
        compose.onNodeWithTag("lyrics_match_policy").assertTextContains("AMLL 逐字优先", substring = true)
    }

    private class PanelState {
        var source by mutableStateOf(LyricsMatchSource.NETEASE)
        var query by mutableStateOf("Panel fixture - Fixture singer - Fixture album")
        var selected by mutableStateOf<LyricsCandidate?>(null)
        var preview by mutableStateOf<SavedLyrics?>(null)
        var assessment by mutableStateOf<LyricsMatchAssessment?>(null)
        var assessmentNote by mutableStateOf<String?>(null)
        var searches = 0
        var automaticMatches = 0
        var loads = 0
        var cancellations = 0
        var returnsToManager = 0
        var applications = 0
        var applied: SavedLyrics? = null
    }

    private fun show(state: PanelState) {
        compose.setContent {
            LeiTheme(AppearanceSettings(blur = false)) {
                Box(Modifier.fillMaxSize()) {
                    PlayerLyricsMatchPanel(song = song, source = state.source, query = state.query,
                        results = listOf(candidate), selectedCandidate = state.selected,
                        preview = state.preview, assessment = state.assessment,
                        assessmentNote = state.assessmentNote, searching = false, previewing = false,
                        busy = false, message = null, messageError = false, hasSearched = true,
                        onSource = { state.source = it }, onQuery = { state.query = it },
                        onSearch = { state.searches++ }, onAutomatic = { state.automaticMatches++ },
                        onCandidate = {
                            state.loads++
                            state.selected = it
                            state.preview = saved
                            state.assessment = score
                        },
                        onCancelLoading = { state.cancellations++ },
                        onBack = { state.returnsToManager++ },
                        onApply = { state.applications++; state.applied = state.preview },
                        modifier = Modifier.width(380.dp).fillMaxHeight())
                }
            }
        }
    }

    @Test fun sourceTabsAndQueryEditsNeverStartARequestOrApplyLyrics() {
        val state = PanelState()
        show(state)
        compose.onNodeWithTag("lyrics_match_source_netease").assertIsSelected()
        for (source in LyricsMatchSource.entries) {
            val tab = compose.onNodeWithTag("lyrics_match_source_${source.name.lowercase()}")
            tab.performScrollTo().performClick()
            tab.assertIsSelected().assertTextContains(source.shortLabel, substring = true)
        }
        compose.onNodeWithTag("lyrics_match_query").performTextReplacement("Another manual query")
        compose.runOnIdle {
            assertEquals("Another manual query", state.query)
            assertEquals(0, state.searches)
            assertEquals(0, state.automaticMatches)
            assertEquals(0, state.loads)
            assertEquals(0, state.applications)
            assertNull(state.preview)
        }
        compose.onNodeWithTag("lyrics_match_apply").assertIsNotEnabled()
    }

    @Test fun returningFromPreviewToResultsOrManagementNeverAppliesThePreview() {
        val state = PanelState()
        show(state)
        compose.onNodeWithTag("lyrics_match_candidate_0").performClick()
        compose.onNodeWithTag("lyrics_match_preview").assertIsDisplayed()
        compose.onNodeWithTag("lyrics_match_back_results").performClick()
        compose.onNodeWithTag("lyrics_match_candidate_0").assertIsDisplayed().assertIsSelected()
        compose.runOnIdle {
            assertEquals(1, state.loads)
            assertEquals(0, state.applications)
            assertEquals(0, state.returnsToManager)
        }
        compose.onNodeWithTag("lyrics_match_back").performClick()
        compose.runOnIdle {
            assertEquals(1, state.returnsToManager)
            assertEquals(0, state.applications)
            assertNull(state.applied)
        }
    }

    @Test fun listAndPreviewShowTheSamePercentageAndOnlySaveApplies() {
        val state = PanelState()
        show(state)
        compose.onNodeWithTag("lyrics_match_metadata_0", useUnmergedTree = true).assertTextContains("信息匹配", substring = true)
            .assertTextContains("90%", substring = true)
        compose.onNodeWithTag("lyrics_match_candidate_0").performClick()
        compose.onNodeWithTag("lyrics_match_content_score").assertTextContains("歌曲信息", substring = true).assertTextContains("90%", substring = true)
        compose.onNodeWithTag("lyrics_match_timing_score").assertTextContains("通过", substring = true)
        compose.onNodeWithTag("lyrics_match_quality_score").assertDoesNotExist()
        compose.onNodeWithTag("lyrics_match_policy").assertTextContains("符合自动条件", substring = true)
        compose.runOnIdle { assertEquals(0, state.applications) }
        compose.onNodeWithTag("lyrics_match_apply").assertIsEnabled().assertTextContains("保存歌词", substring = true)
            .performClick()
        compose.runOnIdle {
            assertEquals(1, state.applications)
            assertEquals(saved, state.applied)
            assertEquals(0, state.searches)
            assertEquals(0, state.automaticMatches)
        }
    }

    @Test fun unavailableReferenceIsUnverifiedRatherThanZeroPercentAndStillNeedsExplicitSave() {
        val state = PanelState().apply {
            selected = candidate
            preview = saved
            assessmentNote = "候选信息尚未读取"
        }
        show(state)
        compose.onNodeWithTag("lyrics_match_content_score").assertTextContains("未校验", substring = true)
        compose.onNodeWithTag("lyrics_match_timing_score").assertDoesNotExist()
        compose.onNodeWithTag("lyrics_match_quality_score").assertDoesNotExist()
        compose.onNodeWithTag("lyrics_match_policy").assertTextContains("未校验 · 可手动保存", substring = true)
        compose.onNodeWithTag("lyrics_match_assessment_note").assertTextContains("候选信息尚未读取", substring = true)
        compose.onNodeWithTag("lyrics_match_apply").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(0, state.applications)
            assertNull(state.applied)
            assertEquals(0, state.searches)
            assertEquals(0, state.automaticMatches)
        }
    }
}
