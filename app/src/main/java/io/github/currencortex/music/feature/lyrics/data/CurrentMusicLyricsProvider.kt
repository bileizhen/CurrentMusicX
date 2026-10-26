package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.local.*
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.domain.LyricsMatcher
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import io.github.currencortex.music.data.settings.LyricsSource
import kotlinx.serialization.*
import kotlinx.serialization.json.JsonArray

class CurrentMusicLyricsProvider(private val music: MusicRepository, private val cache: MusicDao,
    private val source: LyricsSource = LyricsSource.NETEASE,
    private val server: () -> String = { "" },
    private val log: (String) -> Unit = {}) : LyricsProvider {
    override suspend fun findLyrics(song: Song): LyricsProviderResult {
        val id = if (song.musicSource == MusicSource.NAVIDROME) song.id else LyricsMatcher.candidates(song).firstOrNull { it.source == MusicSource.NETEASE }?.songId?.toLongOrNull()
            ?: return LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "CurrentMusic requires an NCM id")
        val targetServer = server()
        val scope = when {
            song.musicSource == MusicSource.NAVIDROME -> SongLyricsStore.identity(song)
            source == LyricsSource.NETEASE -> "netease"
            else -> "currentmusic:$targetServer"
        }
        val result = if (song.musicSource == MusicSource.NAVIDROME) music.lyricsDocument(song)
            else music.lyricsDocument(id, source, targetServer.takeIf { it.isNotBlank() })
        if (result is AppResult.Success && result.value.lines.isNotEmpty()) {
            try { cache.cacheLyrics(CachedLyrics(id, ApiJson.encodeToString(result.value), scope)) }
            catch (e: java.io.IOException) { log("CurrentMusic cache write: ${e.javaClass.simpleName}") }
            return LyricsProviderResult.Parsed(result.value)
        }
        try {
            cache.lyrics(id, scope)?.payload?.let { payload ->
                val json = ApiJson.parseToJsonElement(payload)
                val saved = if (json is JsonArray) LyricsParser.parse(json) else ApiJson.decodeFromString<LyricsDocument>(payload)
                val cleaned = saved.withoutEstimatedTiming()
                if (cleaned != saved) try { cache.cacheLyrics(CachedLyrics(id, ApiJson.encodeToString(cleaned), scope)) }
                catch (e: java.io.IOException) { log("CurrentMusic cache migration: ${e.javaClass.simpleName}") }
                if (cleaned.lines.isNotEmpty()) return LyricsProviderResult.Parsed(cleaned)
            }
        } catch (e: SerializationException) { log("CurrentMusic cache parse: ${e.javaClass.simpleName}") }
        return if (result is AppResult.Failure) LyricsProviderResult.Failure(LyricsErrorCode.NETWORK_ERROR, result.kind.message)
        else LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "CurrentMusic returned no lyrics")
    }
}
