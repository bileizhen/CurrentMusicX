package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.local.*
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.domain.LyricsMatcher
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonArray

class CurrentMusicLyricsProvider(private val music: MusicRepository, private val cache: MusicDao,
    private val log: (String) -> Unit = {}) : LyricsProvider {
    override suspend fun findLyrics(song: Song): LyricsProviderResult {
        val id = LyricsMatcher.candidates(song).firstOrNull { it.source == MusicSource.NETEASE }?.songId?.toLongOrNull()
            ?: return LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "CurrentMusic requires an NCM id")
        val result = music.lyricsDocument(id)
        if (result is AppResult.Success && result.value.lines.isNotEmpty()) {
            try { cache.cacheLyrics(CachedLyrics(id, ApiJson.encodeToString(result.value))) }
            catch (e: java.io.IOException) { log("CurrentMusic cache write: ${e.javaClass.simpleName}") }
            return LyricsProviderResult.Parsed(result.value)
        }
        try {
            cache.lyrics(id)?.payload?.let { payload ->
                val json = ApiJson.parseToJsonElement(payload)
                val saved = if (json is JsonArray) LyricsParser.parse(json) else ApiJson.decodeFromString<LyricsDocument>(payload)
                if (saved.lines.isNotEmpty()) return LyricsProviderResult.Parsed(saved)
            }
        } catch (e: SerializationException) { log("CurrentMusic cache parse: ${e.javaClass.simpleName}") }
        return if (result is AppResult.Failure) LyricsProviderResult.Failure(LyricsErrorCode.NETWORK_ERROR, result.kind.message)
        else LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "CurrentMusic returned no lyrics")
    }
}
