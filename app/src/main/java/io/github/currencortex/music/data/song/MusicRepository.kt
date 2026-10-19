package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import io.github.currencortex.music.data.settings.AudioProvider
import io.github.currencortex.music.data.settings.AudioSourceAccess
import io.github.currencortex.music.data.settings.LyricsSource

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
    suspend fun details(ids: List<Long>): List<Song> = ids.distinct().chunked(200).flatMap { batch ->
        api.get<SongDetailDto>("ncm/song/detail", mapOf("ids" to batch.joinToString(","))).songs.map(SongDto::toDomain)
    }
    suspend fun lyrics(id: Long): AppResult<List<LyricLine>> = appResult {
        parseDocument(api.get<JsonObject>("ncm/lyric", mapOf("id" to "$id"))).lines.map {
            LyricLine(it.startTimeMs, it.text, it.translation)
        }
    }
    suspend fun lyricsDocument(id: Long, source: LyricsSource = LyricsSource.NETEASE, expectedServer: String? = null) = appResult {
        suspend fun read(path: String): io.github.currencortex.music.feature.lyrics.model.LyricsDocument =
            parseDocument(api.decode(api.request("GET", path, mapOf("id" to "$id"),
                expectedSession = expectedServer?.let { RequestSession(it, null) },
                projectOnly = source == LyricsSource.CURRENT_MUSIC)))
        // CurrentMusic's normalized endpoint adds LDDC real timing. The parser still
        // discards wSynth estimates. Native selection never calls this server route.
        if (source == LyricsSource.CURRENT_MUSIC) {
            val normalized = appResult { read("ncm/lyric") }
            if (normalized is AppResult.Success && normalized.value.lines.isNotEmpty()) return@appResult normalized.value
        }
        val enhanced = appResult {
            read("ncm/lyric/new")
        }
        // Native /song/lyric/v1 includes LRC for songs without YRC. Keep that
        // instead of requesting a server-generated approximation of word timing.
        if (enhanced is AppResult.Success && enhanced.value.lines.isNotEmpty()) enhanced.value
        else if (source == LyricsSource.CURRENT_MUSIC) throw ApiException(ErrorKind.Server)
        else read("ncm/lyric")
    }
    suspend fun source(id: Long, quality: AudioQuality, session: RequestSession? = null): AudioSource {
        val access = audioAccess()
        if (access.provider == AudioProvider.LEIZ) {
            val source = leiz.source(id, quality, access.key)
            if (audioAccess().identity != access.identity) throw ApiException(ErrorKind.AudioSourceChanged)
            return source
        }
        val query = mapOf("id" to "$id", "level" to quality.value)
        val dto = if (access.provider == AudioProvider.NETEASE) {
            val gateway = api.netease ?: throw ApiException(ErrorKind.NotFound)
            ApiJson.decodeFromJsonElement<SongUrlDto>(gateway.request("GET", "ncm/song/url", query, null,
                expectedRevision = session?.neteaseRevision))
        } else if (session == null) api.get<SongUrlDto>("ncm/song/url", query, authenticated = true)
            else ApiJson.decodeFromJsonElement<SongUrlDto>(api.request("GET", "ncm/song/url", query,
                authenticated = true, expectedSession = session))
        val url = dto.url?.toHttpUrlOrNull() ?: throw ApiException(ErrorKind.NotFound)
        if (audioAccess().identity != access.identity) throw ApiException(ErrorKind.AudioSourceChanged)
        // Both sources report expi. Bound reuse to five minutes when a server omits it.
        return AudioSource(url.toString(), dto.level, maxOf(dto.sr, dto.sampleRate), maxOf(dto.ch, dto.channelCount), dto.type, dto.md5,
            validForSeconds = dto.expi.takeIf { it > 0 } ?: 300)
    }
}
