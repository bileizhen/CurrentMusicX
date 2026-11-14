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
    private val navidrome: io.github.currencortex.music.data.navidrome.NavidromeRepository? = null,
    private val lx: io.github.currencortex.music.data.lx.LxSourceStore? = null,
    private val emby: io.github.currencortex.music.data.emby.EmbyRepository? = null) {
    val insights = SongInsightsRepository(api)
    suspend fun previewClip(song: Song): SongPreviewClip {
        val id = NeteaseSongActionsRepository.songId(song)
        val segments = if (song.navidrome == null && id != null) {
            when (val response = appResult { insights.chorus(song) }) {
                is AppResult.Success -> response.value
                is AppResult.Failure -> emptyList()
            }
        } else emptyList()
        return songPreviewClip(segments, song.durationMs)
    }
    suspend fun detail(song: Song): Song = when (song.musicSource) {
        MusicSource.EMBY -> requireNotNull(emby).detail(song)
        MusicSource.LOCAL -> song
        MusicSource.NAVIDROME -> requireNotNull(navidrome).detail(song)
        else -> if (song.neteaseCloud) song else detail(song.id)
    }
    suspend fun lyricsDocument(song: Song) = if (song.musicSource == MusicSource.EMBY) appResult {
        io.github.currencortex.music.feature.lyrics.model.LyricsDocument(metadata = io.github.currencortex.music.feature.lyrics.model.LyricsMetadata(source = "Emby"))
    } else if (song.musicSource == MusicSource.NAVIDROME) appResult { requireNotNull(navidrome).lyrics(song) } else lyricsDocument(song.id)
    suspend fun source(song: Song, quality: AudioQuality, session: RequestSession? = null, refresh: Boolean = false,
        supportsDolby: Boolean = false): AudioSource {
        if (song.musicSource == MusicSource.NAVIDROME) return requireNotNull(navidrome).source(song, quality)
        if (song.musicSource == MusicSource.EMBY) return requireNotNull(emby).source(song)
        if (song.neteaseCloud) return source(song.id, quality, session, refresh, supportsDolby, nativeCloud = true)
        val access = audioAccess()
        return if (access.provider == AudioProvider.LX) lxSource(song, quality, access)
        else source(song.id, quality, session, refresh, supportsDolby)
    }
    private suspend fun lxSource(song: Song, quality: AudioQuality, access: AudioSourceAccess): AudioSource {
        val result = try { requireNotNull(lx) { "落雪音源尚未初始化" }.source(song, quality, access.lxSourceId, access.lxSmartSwitch) }
            catch (e: io.github.currencortex.music.data.lx.LxSourceException) { throw ApiException(ErrorKind.Server, userMessage = e.message) }
        if (audioAccess().identity != access.identity) throw ApiException(ErrorKind.AudioSourceChanged)
        return result
    }
    private fun parseDocument(raw: JsonObject): io.github.currencortex.music.feature.lyrics.model.LyricsDocument {
        val code = raw["code"]?.jsonPrimitive?.intOrNull
        if (code != null && code != 200) throw ApiException(ErrorKind.Server, code)
        return io.github.currencortex.music.feature.lyrics.parser.LyricsParser.parse(raw)
    }
    suspend fun search(keyword: String, offset: Int = 0, backend: SearchBackend? = null): AppResult<SearchPage> =
        searchPage(keyword, offset, "song", backend)

    private suspend fun searchPage(keyword: String, offset: Int, type: String, backend: SearchBackend?): AppResult<SearchPage> = appResult {
        val owner = api.publicSession()
        val revision = api.netease?.sessions?.state?.value?.revision
        val query = mapOf("keywords" to keyword, "offset" to "$offset", "limit" to "30", "type" to type)
        suspend fun read(source: SearchBackend): SearchPage {
            val raw = if (source == SearchBackend.NATIVE) requireNotNull(api.netease).request("GET", "ncm/search", query, null,
                expectedRevision = revision)
            else api.request("GET", "ncm/search", query, expectedSession = owner, projectOnly = true,
                callTimeoutMillis = if (type == "lyric") 20_000 else 8_000)
            if (owner != api.publicSession()) throw ApiException(ErrorKind.Unauthorized)
            val code = (raw as? JsonObject)?.get("code")?.jsonPrimitive?.intOrNull
            if (code != null && code != 200) throw ApiException(ErrorKind.Server, code)
            val dto = api.decode<SearchDto>(raw)
            val supplements = if (type == "song" && offset == 0) dto.catalogSongs else emptyList()
            val songs = (supplements + dto.songs).map(SongDto::toDomain).distinctBy(Song::id)
            return SearchPage(songs, dto.totals.song, dto.hasMore.song,
                if (type == "lyric") dto.songs.mapNotNull { song -> searchLyricContextOf(song)?.let { song.id to it } }.toMap() else emptyMap(),
                upstreamCount = dto.songs.size, backend = source)
        }
        // The project gateway currently drops translated and alternate titles. A native
        // search keeps the project's catalog fallback and pagination, but restores names.
        suspend fun readNativeNames(value: String): List<Song> {
            if (!api.hasLocalNeteaseLogin) return emptyList()
            val native = api.netease ?: return emptyList()
            val nativeQuery = mapOf("keywords" to value, "offset" to "0", "limit" to "100", "type" to "song")
            return withTimeoutOrNull(SEARCH_NAME_ENRICHMENT_TIMEOUT_MS) {
                when (val result = appResult {
                    val raw = native.request("GET", "ncm/search", nativeQuery, null, expectedRevision = revision)
                    if (owner != api.publicSession()) throw ApiException(ErrorKind.Unauthorized)
                    val code = (raw as? JsonObject)?.get("code")?.jsonPrimitive?.intOrNull
                    if (code != null && code != 200) throw ApiException(ErrorKind.Server, code)
                    api.decode<SearchDto>(raw).songs.map(SongDto::toDomain)
                }) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> emptyList()
                }
            } ?: emptyList()
        }
        // Pin the transport after the first page: offsets from two search indexes cannot be mixed.
        val page = if (backend != null) read(backend) else when (val result = appResult { read(SearchBackend.PROJECT) }) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> if (offset == 0 && api.netease != null && result.kind in setOf(
                    ErrorKind.NotFound, ErrorKind.Network, ErrorKind.Timeout, ErrorKind.Server)) read(SearchBackend.NATIVE)
                else throw ApiException(result.kind, result.status, result.message)
        }
        val enriched = if (page.backend == SearchBackend.PROJECT && type == "song" && api.hasLocalNeteaseLogin)
            page.copy(songs = mergeNativeSearchNames(page.songs, readNativeNames(keyword))) else page
        if (owner != api.publicSession()) throw ApiException(ErrorKind.Unauthorized)
        enriched
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
    suspend fun searchLyrics(keyword: String, offset: Int = 0, backend: SearchBackend? = null): AppResult<SearchPage> =
        searchPage(keyword, offset, "lyric", backend)
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
    suspend fun source(id: Long, quality: AudioQuality, session: RequestSession? = null, refresh: Boolean = false,
        supportsDolby: Boolean = false, nativeCloud: Boolean = false): AudioSource {
        val access = audioAccess()
        if (!nativeCloud && access.provider == AudioProvider.LX) return lxSource(Song(id, ""), quality, access)
        if (!nativeCloud && access.provider == AudioProvider.LEIZ) {
            val source = leiz.source(id, quality, access.key)
            if (audioAccess().identity != access.identity) throw ApiException(ErrorKind.AudioSourceChanged)
            return source
        }
        val query = buildMap {
            put("id", "$id"); put("level", quality.value)
            if (quality == AudioQuality.AUTO && supportsDolby) put("supportsDolby", "1")
            if (refresh && access.provider == AudioProvider.CURRENT_MUSIC) put("refresh", "1")
        }
        suspend fun fetch(params: Map<String, String>): SongUrlDto = if (nativeCloud || access.provider == AudioProvider.NETEASE) {
            val gateway = api.netease ?: throw ApiException(ErrorKind.NotFound)
            ApiJson.decodeFromJsonElement<SongUrlDto>(gateway.request("GET", "ncm/song/url", params, null,
                expectedRevision = session?.neteaseRevision))
        } else if (session == null) api.get<SongUrlDto>("ncm/song/url", params, authenticated = true)
            else ApiJson.decodeFromJsonElement<SongUrlDto>(api.request("GET", "ncm/song/url", params,
                authenticated = true, expectedSession = session))
        fun SongUrlDto.isDolby() = level == AudioQuality.DOLBY.value
        fun SongUrlDto.isUsableDolby() = url?.toHttpUrlOrNull() != null &&
            (freeTrialInfo == null || freeTrialInfo == JsonNull)

        var dto = fetch(query)
        if (quality == AudioQuality.AUTO && dto.isDolby()) {
            if (!nativeCloud && access.provider == AudioProvider.CURRENT_MUSIC && supportsDolby && !dto.isUsableDolby())
                dto = fetch(query - "supportsDolby")
            if (dto.isDolby() && (!supportsDolby || !dto.isUsableDolby()))
                throw ApiException(ErrorKind.NeteaseAudioUnavailable)
        }
        val url = dto.url?.toHttpUrlOrNull() ?: throw ApiException(ErrorKind.NotFound)
        if (!nativeCloud && audioAccess().identity != access.identity) throw ApiException(ErrorKind.AudioSourceChanged)
        // Both sources report expi. Bound reuse to five minutes when a server omits it.
        return AudioSource(url.toString(), dto.level, maxOf(dto.sr, dto.sampleRate), maxOf(dto.ch, dto.channelCount), dto.type, dto.md5,
            validForSeconds = dto.expi.takeIf { it > 0 } ?: if (dto.level == "matched" || dto.source == "netease-match") 120 else 300,
            source = dto.source, requestedLevel = dto.requestedLevel, matchSource = dto.matchSource,
            bitrate = dto.br, bits = dto.bits, size = dto.size, durationMs = dto.durationMs, expectedDurationMs = dto.expectedDurationMs)
    }
}

private const val SEARCH_NAME_ENRICHMENT_TIMEOUT_MS = 1_000L
private const val LYRICS_MATCH_SEARCH_TIMEOUT_MS = 3_500L
private const val LYRICS_MATCH_LOAD_TIMEOUT_MS = 5_000L

/** Upstream highlights are display text, never rendered as markup. */
internal fun cleanSearchLyric(value: String): String = value.replace(Regex("<[^>]*>"), "")
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
    .replace(Regex("\\s+"), " ").trim().take(240)

/** Gateway joins the hit with one neighbour line on each side; markers survive until this split. */
internal fun parseSearchLyricContext(value: String): SearchLyricContext? {
    val parts = value.split('\n').mapNotNull { raw ->
        val hit = raw.contains("<b>", true) || raw.contains("<em>", true)
        cleanSearchLyric(raw).takeIf(String::isNotBlank)?.let { it to hit }
    }
    if (parts.isEmpty()) return null
    val hitIndex = parts.indexOfLast { it.second }
    if (hitIndex < 0) return SearchLyricContext(null, parts.joinToString(" · ") { it.first }, null)
    return SearchLyricContext(parts.take(hitIndex).lastOrNull()?.first,
        parts.filter { it.second }.joinToString(" · ") { it.first },
        parts.drop(hitIndex + 1).firstOrNull()?.first)
}

/** CurrentMusic servers now ship structured context rows; older deployments only carry the joined string. */
internal fun searchLyricContextOf(dto: SongDto): SearchLyricContext? {
    val context = dto.lyricContext
    if (dto.lyricContextStatus != null && context.isEmpty()) return SearchLyricContext(null,
        cleanSearchLyric(dto.lyricMatch), null, dto.lyricContextStatus, dto.lyricContextRetryable)
    if (context.isEmpty()) return parseSearchLyricContext(dto.lyric)
    val firstHit = context.indexOfFirst { it.matched }
    val lastHit = context.indexOfLast { it.matched }
    val matched = (if (firstHit >= 0) context.filter { it.matched }.joinToString(" · ") { it.txt }
        else context.joinToString(" · ") { it.txt }).let(::cleanSearchLyric)
    if (matched.isBlank()) return null
    val before = if (firstHit > 0) cleanSearchLyric(context[firstHit - 1].txt).takeIf(String::isNotBlank) else null
    val after = if (lastHit >= 0 && lastHit < context.lastIndex) cleanSearchLyric(context[lastHit + 1].txt).takeIf(String::isNotBlank) else null
    return SearchLyricContext(before, matched, after, dto.lyricContextStatus, dto.lyricContextRetryable)
}
