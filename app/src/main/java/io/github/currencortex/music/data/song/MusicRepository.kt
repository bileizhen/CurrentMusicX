package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import io.github.currencortex.music.data.settings.AudioProvider
import io.github.currencortex.music.data.settings.AudioSourceAccess

class MusicRepository(private val api: ApiClient,
    private val audioAccess: () -> AudioSourceAccess = { AudioSourceAccess(AudioProvider.CURRENT_MUSIC) },
    private val leiz: LeizAudioClient = LeizAudioClient(),
    private val navidrome: io.github.currencortex.music.data.navidrome.NavidromeRepository? = null) {
    suspend fun detail(song: Song): Song = if (song.musicSource == MusicSource.NAVIDROME) requireNotNull(navidrome).detail(song) else detail(song.id)
    suspend fun lyricsDocument(song: Song) = if (song.musicSource == MusicSource.NAVIDROME) appResult { requireNotNull(navidrome).lyrics(song) } else lyricsDocument(song.id)
    suspend fun source(song: Song, quality: AudioQuality, session: RequestSession? = null): AudioSource =
        if (song.musicSource == MusicSource.NAVIDROME) requireNotNull(navidrome).source(song, quality) else source(song.id, quality, session)
    private fun parseDocument(raw: JsonObject): io.github.currencortex.music.feature.lyrics.model.LyricsDocument {
        val code = raw["code"]?.jsonPrimitive?.intOrNull
        if (code != null && code != 200) throw ApiException(ErrorKind.Server, code)
        return io.github.currencortex.music.feature.lyrics.parser.LyricsParser.parse(raw)
    }
    suspend fun search(keyword: String, offset: Int = 0): AppResult<SearchPage> = appResult {
        val result = api.get<SearchDto>("ncm/search", mapOf("keywords" to keyword, "offset" to "$offset", "limit" to "30", "type" to "song"))
        SearchPage(result.songs.map(SongDto::toDomain), result.totals.song, result.hasMore.song)
    }
    suspend fun detail(id: Long): Song = api.get<SongDetailDto>("ncm/song/detail", mapOf("ids" to "$id")).songs.first().toDomain()
    suspend fun lyrics(id: Long): AppResult<List<LyricLine>> = appResult {
        parseDocument(api.get<JsonObject>("ncm/lyric", mapOf("id" to "$id"))).lines.map {
            LyricLine(it.startTimeMs, it.text, it.translation)
        }
    }
    suspend fun lyricsDocument(id: Long) = appResult {
        val enhanced = appResult {
            val raw = api.get<JsonObject>("ncm/lyric/new", mapOf("id" to "$id"))
            parseDocument(raw)
        }
        if (enhanced is AppResult.Success && enhanced.value.hasWordTiming) enhanced.value
        else {
            val normalized = appResult { parseDocument(api.get<JsonObject>("ncm/lyric", mapOf("id" to "$id"))) }
            when {
                normalized is AppResult.Success && normalized.value.lines.isNotEmpty() -> normalized.value
                enhanced is AppResult.Success && enhanced.value.lines.isNotEmpty() -> enhanced.value
                normalized is AppResult.Failure -> throw ApiException(normalized.kind, normalized.status)
                else -> (normalized as AppResult.Success).value
            }
        }
    }
    suspend fun source(id: Long, quality: AudioQuality, session: RequestSession? = null): AudioSource {
        val access = audioAccess()
        if (access.provider == AudioProvider.LEIZ) {
            val source = leiz.source(id, quality, access.key)
            if (audioAccess().identity != access.identity) throw ApiException(ErrorKind.AudioSourceChanged)
            return source
        }
        val query = mapOf("id" to "$id", "level" to quality.value)
        val dto = if (session == null) api.get<SongUrlDto>("ncm/song/url", query, authenticated = true)
            else ApiJson.decodeFromJsonElement<SongUrlDto>(api.request("GET", "ncm/song/url", query,
                authenticated = true, expectedSession = session))
        val url = dto.url?.toHttpUrlOrNull() ?: throw ApiException(ErrorKind.NotFound)
        if (audioAccess().identity != access.identity) throw ApiException(ErrorKind.AudioSourceChanged)
        // CurrentMusic documents a 20-minute CDN URL lifetime. Older servers omit expi;
        // use only five minutes in that case, rather than discarding a prefetch after 30s.
        return AudioSource(url.toString(), dto.level, maxOf(dto.sr, dto.sampleRate), maxOf(dto.ch, dto.channelCount), dto.type, dto.md5,
            validForSeconds = dto.expi.takeIf { it > 0 } ?: 300)
    }
}
