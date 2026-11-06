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

/** Only disposable CurrentMusic results use this envelope; user choices live in SongLyricsStore. */
@Serializable private data class CurrentMusicLyricsCache(val document: LyricsDocument, val fetchedAtMillis: Long) {
    val expiresAtMillis: Long get() = fetchedAtMillis + when {
        document.metadata.instrumental == true || document.metadata.source == "ncm:yrc" || document.metadata.source == "ncm:inst" ||
            document.metadata.source.startsWith("lddc:") -> 7 * 24 * 60 * 60 * 1000L
        else -> 2 * 60 * 60 * 1000L
    }
    fun fresh(now: Long) = now >= fetchedAtMillis && now < expiresAtMillis
}

class CurrentMusicLyricsProvider(private val music: MusicRepository, private val cache: MusicDao,
    private val source: LyricsSource = LyricsSource.NETEASE,
    private val server: () -> String = { "" },
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {}) : LyricsProvider {
    override suspend fun findLyrics(song: Song): LyricsProviderResult {
        val id = if (song.privateLibrary) song.id else LyricsMatcher.candidates(song).firstOrNull { it.source == MusicSource.NETEASE }?.songId?.toLongOrNull()
            ?: return LyricsProviderResult.Failure(LyricsErrorCode.NO_LYRICS, "CurrentMusic requires an NCM id")
        val targetServer = server()
        val projectCache = !song.privateLibrary && source == LyricsSource.CURRENT_MUSIC
        val scope = when {
            song.privateLibrary -> SongLyricsStore.identity(song)
            source == LyricsSource.NETEASE -> "netease"
            // Do not migrate pre-validation results that may contain another recording's lyrics.
            else -> "currentmusic:v2:$targetServer"
        }
        val result = if (song.privateLibrary) music.lyricsDocument(song)
            else music.lyricsDocument(id, source, targetServer.takeIf { it.isNotBlank() })
        if (result is AppResult.Success && result.value.lines.isNotEmpty()) {
            val entry = if (projectCache) CurrentMusicLyricsCache(result.value, now()) else null
            val payload = if (entry != null) ApiJson.encodeToString(entry) else ApiJson.encodeToString(result.value)
            try { cache.cacheLyrics(CachedLyrics(id, payload, scope)) }
            catch (e: java.io.IOException) { log("CurrentMusic cache write: ${e.javaClass.simpleName}") }
            return LyricsProviderResult.Parsed(result.value, entry?.expiresAtMillis)
        }
        try {
            cache.lyrics(id, scope)?.payload?.let { payload ->
                if (projectCache) {
                    val entry = ApiJson.decodeFromString<CurrentMusicLyricsCache>(payload)
                    if (entry.fresh(now())) {
                        val cleaned = entry.document.withoutEstimatedTiming()
                        if (cleaned.lines.isNotEmpty()) return LyricsProviderResult.Parsed(cleaned, entry.expiresAtMillis)
                    }
                    return@let
                }
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
