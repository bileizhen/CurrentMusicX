package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.domain.LyricsKey
import io.github.currencortex.music.feature.lyrics.model.*

fun interface LyricsProvider { suspend fun findLyrics(song: Song): LyricsProviderResult }

sealed interface LyricsProviderResult {
    data class Ttml(val text: String, val key: LyricsKey) : LyricsProviderResult
    data class Parsed(val document: LyricsDocument) : LyricsProviderResult
    data class Failure(val code: LyricsErrorCode, val detail: String) : LyricsProviderResult
}

fun interface LyricsRemoteDataSource { suspend fun fetch(key: LyricsKey): LyricsProviderResult }
