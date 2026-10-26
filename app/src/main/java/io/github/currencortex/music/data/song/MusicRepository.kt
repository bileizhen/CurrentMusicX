package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import io.github.currencortex.music.data.settings.AudioProvider
import io.github.currencortex.music.data.settings.AudioSourceAccess
import io.github.currencortex.music.data.settings.LyricsSource
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.withTimeoutOrNull

class MusicRepository(private val api: ApiClient,
    private val audioAccess: () -> AudioSourceAccess = { AudioSourceAccess(AudioProvider.CURRENT_MUSIC) },
    private val leiz: LeizAudioClient = LeizAudioClient(),
    private val navidrome: io.github.currencortex.music.data.navidrome.NavidromeRepository? = null) {
    suspend fun previewClip(song: Song): SongPreviewClip {
        val id = NeteaseSongActionsRepository.songId(song)
        val raw = if (song.navidrome == null && id != null) {
            when (val response = appResult { api.get<JsonObject>("ncm/song/chorus", mapOf("id" to "$id")) }) {
                is AppResult.Success -> response.value
                is AppResult.Failure -> null
            }
        } else null
        return songPreviewClip(raw, id ?: song.id, song.durationMs)
    }
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
    /** Matching sources are explicit: the media-source setting never reroutes this search. */
    suspend fun searchForLyricsMatching(keyword: String, source: LyricsSource): AppResult<SearchPage> = appResult {
        require(source != LyricsSource.AUTO) { "自动择优需要选择器处理，请指定具体歌词来源" }
        val query = keyword.trim().split(Regex("\\s+-\\s+"), limit = 3).take(2).joinToString(" ")
            .replace(Regex("\\s+"), " ").trim().take(200)
        if (query.isBlank()) throw ApiException(ErrorKind.Parse, userMessage = "请输入歌名或歌手")
        val gateway = if (source == LyricsSource.NETEASE) api.netease
            ?: throw ApiException(ErrorKind.NotFound, userMessage = "网易云原生歌词接口未启用") else null
        val nativeRevision = gateway?.sessions?.state?.value?.revision
        withTimeoutOrNull(LYRICS_MATCH_SEARCH_TIMEOUT_MS) {
            val parameters = mapOf("keywords" to query, "offset" to "0", "limit" to "30", "type" to "song")
            val raw = if (gateway != null) gateway.request("GET", "ncm/search", parameters, null,
                expectedRevision = nativeRevision)
            else api.request("GET", "ncm/search", parameters, projectOnly = true,
                callTimeoutMillis = LYRICS_MATCH_SEARCH_TIMEOUT_MS)
            val code = (raw as? JsonObject)?.get("code")?.jsonPrimitive?.intOrNull
            if (code != null && code != 200) throw ApiException(ErrorKind.Server, code)
            val result = api.decode<SearchDto>(raw)
            SearchPage(result.songs.map(SongDto::toDomain), result.totals.song, result.hasMore.song)
        } ?: throw ApiException(ErrorKind.Timeout)
    }

    /** Read the selected source directly, without allowing an implicit project/native fallback. */
    suspend fun lyricsForMatching(id: Long, source: LyricsSource): AppResult<LyricsDocument> = appResult {
        require(source != LyricsSource.AUTO) { "自动择优需要选择器处理，请指定具体歌词来源" }
        if (id <= 0) throw ApiException(ErrorKind.NotFound)
        val gateway = if (source == LyricsSource.NETEASE) api.netease
            ?: throw ApiException(ErrorKind.NotFound, userMessage = "网易云原生歌词接口未启用") else null
        val nativeRevision = gateway?.sessions?.state?.value?.revision
        withTimeoutOrNull(LYRICS_MATCH_LOAD_TIMEOUT_MS) {
            suspend fun read(path: String): LyricsDocument {
                val parameters = mapOf("id" to "$id")
                val raw = if (gateway != null) gateway.request("GET", path, parameters, null,
                    expectedRevision = nativeRevision)
                else api.request("GET", path, parameters, projectOnly = true,
                    callTimeoutMillis = LYRICS_MATCH_LOAD_TIMEOUT_MS)
                return parseDocument(raw as? JsonObject ?: throw ApiException(ErrorKind.Parse))
            }
            // The project can provide normalized real LDDC timing. Native NetEase reads only
            // its official YRC/LRC endpoints and never asks the project for an estimated axis.
            if (source == LyricsSource.CURRENT_MUSIC) {
                val normalized = appResult { read("ncm/lyric") }
                if (normalized is AppResult.Success && normalized.value.lines.isNotEmpty())
                    return@withTimeoutOrNull normalized.value
            }
            val enhanced = appResult { read("ncm/lyric/new") }
            if (enhanced is AppResult.Success && enhanced.value.lines.isNotEmpty()) enhanced.value
            else if (source == LyricsSource.NETEASE) read("ncm/lyric")
            else when (enhanced) {
                is AppResult.Failure -> throw ApiException(enhanced.kind, enhanced.status, enhanced.message)
                is AppResult.Success -> throw ApiException(ErrorKind.NotFound, userMessage = "这个来源没有提供歌词")
            }
        } ?: throw ApiException(ErrorKind.Timeout)
    }
    suspend fun searchLyrics(keyword: String, offset: Int = 0): AppResult<SearchPage> = appResult {
        val result = api.get<SearchDto>("ncm/search", mapOf("keywords" to keyword, "offset" to "$offset", "limit" to "30", "type" to "lyric"))
        SearchPage(result.songs.map(SongDto::toDomain), result.totals.song, result.hasMore.song,
            result.songs.mapNotNull { song -> cleanSearchLyric(song.lyric).takeIf(String::isNotBlank)?.let { song.id to it } }.toMap())
    }
    suspend fun searchSuggestions(keyword: String): AppResult<List<String>> = appResult {
        val raw = api.request("GET", "ncm/search/suggest", mapOf("keywords" to keyword, "type" to "mobile"),
            callTimeoutMillis = 5000).jsonObject
        if (raw["code"]?.jsonPrimitive?.intOrNull?.let { it != 200 } == true) throw ApiException(ErrorKind.Server)
        val result = raw["result"] as? JsonObject ?: raw
        (result["allMatch"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonObject)?.get("keyword")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        }.distinct().take(8)
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
        require(source != LyricsSource.AUTO) { "自动择优需要选择器处理，请指定具体歌词来源" }
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

private const val LYRICS_MATCH_SEARCH_TIMEOUT_MS = 3_500L
private const val LYRICS_MATCH_LOAD_TIMEOUT_MS = 5_000L

/** Upstream highlights are display text, never rendered as markup. */
internal fun cleanSearchLyric(value: String): String = value.replace(Regex("<[^>]*>"), "")
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
    .replace(Regex("\\s+"), " ").trim().take(240)
