package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.local.CachedLyrics
import io.github.currencortex.music.data.local.MusicDao
import io.github.currencortex.music.data.song.MusicRepository
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray

data class LyricsResult(val document: LyricsDocument = LyricsDocument(), val error: String? = null)

class LyricsRepository(private val music: MusicRepository, private val cache: MusicDao) {
    suspend fun load(id: Long): LyricsResult = withContext(Dispatchers.IO) {
        when (val result = music.lyricsDocument(id)) {
            is AppResult.Success -> {
                cache.cacheLyrics(CachedLyrics(id, ApiJson.encodeToString(result.value)))
                LyricsResult(result.value)
            }
            is AppResult.Failure -> {
                val saved = cache.lyrics(id)?.payload?.let { payload -> runCatching {
                    val json = ApiJson.parseToJsonElement(payload)
                    if (json is JsonArray) LyricsParser.parse(json) else ApiJson.decodeFromString<LyricsDocument>(payload)
                }.getOrNull() }
                LyricsResult(saved ?: LyricsDocument(), if (saved?.lines.isNullOrEmpty()) result.kind.message else null)
            }
        }
    }
}
