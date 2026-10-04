package io.github.currencortex.music.feature.lyrics.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.currencortex.music.data.settings.*
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument

/** Compatibility entry; the source-independent renderer is LyricsScreen. */
@Composable fun AppleLyrics(document: LyricsDocument, position: State<Long>, onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier, canSeek: Boolean = true, translation: Boolean = true,
    romanization: Boolean = false, wordAnimation: Boolean = true, effects: Boolean = true,
    fontSize: Float = LyricsTypography.DEFAULT_SIZE, weightMode: LyricsWeight = LyricsWeight.CURRENT,
    lyricsOffsetMs: Long = 0) = LyricsScreen(document, position, onSeek, modifier, canSeek, translation,
        romanization, wordAnimation, effects, fontSize, weightMode, lyricsOffsetMs)
