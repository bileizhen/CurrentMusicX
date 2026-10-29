// Lyric-source matching adapted from Folia Major, AGPL-3.0.
// chthollyphile/folia-major f96c65b27f7ca351e8db781301cedfa0a52252e2:
// utils/lyrics/{amllDbCandidates,lyricMatchSources,autoMatchBestLyric}.ts and
// services/onlineMusic/kugou/kugouLyricProvider.ts. Original contributors retain copyright.
package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.feature.lyrics.domain.LyricsMatcher
import io.github.currencortex.music.feature.lyrics.domain.LyricsTimingAligner
import io.github.currencortex.music.feature.lyrics.domain.FoliaLyricsMatchPolicy
import io.github.currencortex.music.feature.lyrics.domain.isExplicitPureMusic
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

enum class LyricsMatchSource(val label: String, val shortLabel: String) {
    NETEASE("网易云音乐", "网易云"), CURRENT_MUSIC("CurrentMusic 歌词", "CurrentMusic"),
    QQ_MUSIC("QQ 音乐", "QQ 音乐"), QISHUI("汽水音乐", "汽水"),
    AMLL("AMLL 逐字歌词", "AMLL"), KUGOU("酷狗音乐", "酷狗")
}

data class LyricsCandidate(val source: LyricsMatchSource, val id: String, val title: String,
    val artists: String = "", val album: String = "", val durationMs: Long = 0, val reference: String = "",
    val cover: String = "")

class LyricsMatchException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class LyricsMatchAdapter(val search: suspend (String) -> List<LyricsCandidate>,
    val load: suspend (LyricsCandidate) -> LyricsDocument)

/** Anonymous public lyric matching; playback metadata and the selected audio never change. */
class OnlineLyricsMatcher(
    private val searchNetease: suspend (String) -> List<Song>,
    private val loadNetease: suspend (Long) -> LyricsDocument,
    client: OkHttpClient = OkHttpClient(),
    amllBaseUrl: String = "https://api.amll.dev/",
    kugouSearchBaseUrl: String = "https://mobiles.kugou.com/",
    kugouLyricsBaseUrl: String = "https://lyrics.kugou.com/",
    kugouCatalogBaseUrl: String = "https://complexsearch.kugou.com/",
    private val searchCurrentMusic: (suspend (String) -> List<Song>)? = null,
    private val loadCurrentMusic: (suspend (Long) -> LyricsDocument)? = null,
    private val additionalSources: Map<LyricsMatchSource, LyricsMatchAdapter> = emptyMap(),
    private val scoreDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val client = client.newBuilder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS)
        .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false)
        .apply { interceptors().clear(); networkInterceptors().clear() }.build()
    private val amllBase = amllBaseUrl.toHttpUrl()
    private val kugouSearchBase = kugouSearchBaseUrl.toHttpUrl()
    private val kugouLyricsBase = kugouLyricsBaseUrl.toHttpUrl()
    private val kugouCatalogBase = kugouCatalogBaseUrl.toHttpUrl()

    suspend fun search(source: LyricsMatchSource, query: String): List<LyricsCandidate> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        require(text.length <= MAX_QUERY_LENGTH) { "搜索关键词过长" }
        return bounded(source, 8_000) {
            (if (source != LyricsMatchSource.NETEASE && additionalSources.containsKey(source))
                additionalSources.getValue(source).search(text) else when (source) {
                LyricsMatchSource.AMLL -> searchAmll(text)
                LyricsMatchSource.NETEASE -> searchNetease(text).asSequence()
                    .filter { it.id > 0 && !it.video && it.name.isNotBlank() }.take(MAX_RESULTS)
                    .map { LyricsCandidate(source, it.id.toString(), it.name, it.artists, it.album,
                        it.durationMs, it.id.toString(), it.cover) }.toList()
                LyricsMatchSource.CURRENT_MUSIC -> (searchCurrentMusic
                    ?: throw LyricsMatchException("CurrentMusic 歌词搜索尚未配置"))(text).asSequence()
                    .filter { it.id > 0 && !it.video && it.name.isNotBlank() }.take(MAX_RESULTS)
                    .map { LyricsCandidate(source, it.id.toString(), it.name, it.artists, it.album,
                        it.durationMs, it.id.toString(), it.cover) }.toList()
                LyricsMatchSource.QQ_MUSIC, LyricsMatchSource.QISHUI -> (additionalSources[source]
                    ?: throw LyricsMatchException("${source.label}歌词匹配尚未配置")).search(text)
                LyricsMatchSource.KUGOU -> searchKugou(text)
            }).onEach { require(it.source == source) { "歌词搜索来源不一致" } }
                .distinctBy { it.reference.ifEmpty { it.id } }.take(MAX_RESULTS)
        }
    }

    suspend fun load(candidate: LyricsCandidate): SavedLyrics = bounded(candidate.source, 7_000) {
        require(candidate.id.isNotBlank() && candidate.id.length <= 128 && candidate.reference.length <= 256) { "歌词候选无效" }
        val document = (if (candidate.source != LyricsMatchSource.NETEASE && additionalSources.containsKey(candidate.source))
            additionalSources.getValue(candidate.source).load(candidate) else when (candidate.source) {
            LyricsMatchSource.AMLL -> loadAmll(candidate)
            LyricsMatchSource.NETEASE -> {
                val id = candidate.id.toLongOrNull()?.takeIf { it > 0 } ?: throw LyricsMatchException("网易云歌曲编号无效")
                loadNetease(id)
            }
            LyricsMatchSource.CURRENT_MUSIC -> {
                val id = candidate.id.toLongOrNull()?.takeIf { it > 0 } ?: throw LyricsMatchException("CurrentMusic 歌曲编号无效")
                (loadCurrentMusic ?: throw LyricsMatchException("CurrentMusic 歌词读取尚未配置"))(id)
            }
            LyricsMatchSource.QQ_MUSIC, LyricsMatchSource.QISHUI -> (additionalSources[candidate.source]
                ?: throw LyricsMatchException("${candidate.source.label}歌词匹配尚未配置")).load(candidate)
            LyricsMatchSource.KUGOU -> loadKugou(candidate)
        }).withoutEstimatedTiming()
        if (document.lines.none { it.text.isNotBlank() || it.backgroundVocals.isNotEmpty() })
            throw LyricsMatchException("${candidate.source.label}没有可用歌词")
        LyricsFileCodec.validate(document)
        SavedLyrics(document, LyricsOrigin(candidate.source.label, candidate.source.name.lowercase(Locale.ROOT),
            candidate.reference.ifEmpty { candidate.id }))
    }

    suspend fun bestMatch(song: Song, preferred: LyricsMatchSource = LyricsMatchSource.QQ_MUSIC): SavedLyrics? =
        bestMatchDetailed(song, preferred)?.saved

    /** Folia: source order, one reliable song per platform, first word timing or first line fallback. */
    suspend fun bestMatchDetailed(song: Song, preferred: LyricsMatchSource = LyricsMatchSource.QQ_MUSIC): LyricsMatchResult? {
        if (song.video || song.name.isBlank()) return null
        val candidates = mutableMapOf<LyricsMatchSource, LyricsCandidate?>()
        var fallback: LyricsMatchResult? = null
        suspend fun candidate(source: LyricsMatchSource): LyricsCandidate? {
            if (candidates.containsKey(source)) return candidates[source]
            val found = withTimeoutOrNull(3_500) {
                // The active NetEase id is already an exact identity, like Folia's providerCandidate.
                val known = if (source == LyricsMatchSource.NETEASE) automaticCandidates(song, source, query(song)) else search(source, query(song))
                if (source == LyricsMatchSource.NETEASE && LyricsMatcher.candidates(song).any { it.source == MusicSource.NETEASE }) known.firstOrNull()
                else withContext(scoreDispatcher) {
                    rankCandidates(song, known.take(10)).firstOrNull { isReliableMatch(song, it) }
                }
            }
            candidates[source] = found
            return found
        }
        for (source in automaticSources(preferred)) {
            try {
                val chosen = if (source == LyricsMatchSource.AMLL) {
                    // Automatic AMLL lookup uses the reliable NCM identity; no global title search.
                    val netease = candidate(LyricsMatchSource.NETEASE) ?: continue
                    netease.copy(source = LyricsMatchSource.AMLL, reference = "ncm:${netease.id}")
                } else candidate(source) ?: continue
                val saved = withTimeoutOrNull(5_000) { load(chosen) } ?: continue
                val assessment = assess(song, chosen, saved)
                val result = LyricsMatchResult(saved, assessment, chosen)
                if (hasRealWordTiming(saved.document) || isPureMusicResult(source, saved.document)) return result
                if (fallback == null) fallback = result
            } catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { currentCoroutineContext().ensureActive() }
        }
        return fallback
    }

    /** Preview uses the same Folia metadata gate. It never fetches a comparison lyric. */
    @Suppress("UNUSED_PARAMETER") // Preserve the preview API for callers with a document in hand.
    suspend fun assess(song: Song, candidate: LyricsCandidate, saved: SavedLyrics,
        reference: LyricsDocument? = null): LyricsMatchAssessment = withContext(scoreDispatcher) {
        val known = candidate.source == LyricsMatchSource.NETEASE && LyricsMatcher.candidates(song).any {
            it.source == MusicSource.NETEASE && it.songId == candidate.id
        }
        val details = FoliaLyricsMatchPolicy.details(song, candidate)
        LyricsMatchAssessment(details.score, hasRealWordTiming(saved.document),
            metadataReliable = known || details.accepted,
            hasRenderableLyrics = !song.video && song.name.isNotBlank() && saved.document.lines.any {
                it.text.isNotBlank() || it.translation.isNotBlank()
            })
    }

    private fun isPureMusicResult(source: LyricsMatchSource, document: LyricsDocument): Boolean =
        source != LyricsMatchSource.AMLL && source != LyricsMatchSource.QQ_MUSIC &&
            document.isExplicitPureMusic()

    /** Optional manual-preview alignment. A missing baseline never blocks the selected lyrics. */
    suspend fun align(song: Song, saved: SavedLyrics): SavedLyrics {
        if (saved.timingAlignment != null || saved.origin.provider == "netease" || song.video) return saved
        val baseline = try {
            withTimeoutOrNull(4_000) {
                automaticCandidates(song, LyricsMatchSource.NETEASE, query(song))
                    .firstOrNull { isReliableMatch(song, it) }?.let { load(it).document }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { currentCoroutineContext().ensureActive(); null }
        return alignSaved(saved, baseline)
    }

    private suspend fun alignSaved(saved: SavedLyrics, baseline: LyricsDocument?): SavedLyrics {
        if (baseline == null || saved.timingAlignment != null || saved.origin.provider == "netease") return saved
        return withContext(Dispatchers.Default) {
            ensureActive()
            val timing = LyricsTimingAligner.align(baseline, saved.document)
            ensureActive()
            if (timing == null) saved else saved.copy(timingAlignment = timing)
        }
    }

    private fun query(song: Song) = FoliaLyricsMatchPolicy.query(song)

    private suspend fun automaticCandidates(song: Song, source: LyricsMatchSource, query: String): List<LyricsCandidate> {
        if (source != LyricsMatchSource.NETEASE) return search(source, query)
        val id = LyricsMatcher.candidates(song).firstOrNull { it.source == MusicSource.NETEASE }
            ?.songId?.toLongOrNull()?.takeIf { it > 0 }
        return if (id != null) listOf(LyricsCandidate(source, id.toString(), song.name, song.artists,
            song.album, song.durationMs, id.toString())) else search(source, query)
    }

    private fun automaticSources(preferred: LyricsMatchSource): List<LyricsMatchSource> {
        val order = listOf(preferred, LyricsMatchSource.NETEASE, LyricsMatchSource.AMLL,
            LyricsMatchSource.QQ_MUSIC, LyricsMatchSource.KUGOU, LyricsMatchSource.QISHUI,
            LyricsMatchSource.CURRENT_MUSIC).distinct()
        return order.filter { source -> when (source) {
            LyricsMatchSource.CURRENT_MUSIC -> source in additionalSources || (searchCurrentMusic != null && loadCurrentMusic != null)
            LyricsMatchSource.QQ_MUSIC, LyricsMatchSource.QISHUI -> source in additionalSources
            else -> true
        } }
    }

    private suspend fun searchAmll(query: String): List<LyricsCandidate> {
        // Structured queries avoid matching a short title against arbitrary lyric body text.
        val parts = query.split(Regex("\\s+-\\s+")).map(String::trim).filter(String::isNotEmpty)
        val title = parts.firstOrNull() ?: return emptyList()
        val baseTitle = title.replace(Regex("(?:\\s*[(\\[（【][^)\\]）】]*[)\\]）】])+\\s*$"), "").trim().ifEmpty { title }
        val artist = parts.getOrNull(1)?.split(Regex("\\s*[,，、&]\\s*|\\s+(?:feat\\.?|ft\\.?)\\s+", RegexOption.IGNORE_CASE))?.firstOrNull()
        val queries = if (parts.size == 1) listOf(mapOf("musicName" to title), mapOf("q" to query))
            else buildList {
                if (!artist.isNullOrBlank()) add(mapOf("musicName" to title, "artistName" to artist))
                if (!artist.isNullOrBlank() && baseTitle != title) add(mapOf("musicName" to baseTitle, "artistName" to artist))
                add(mapOf("musicName" to baseTitle))
            }.distinct()
        for (params in queries) {
            val root = request(amllBase.path("v1/lyrics/search", params + ("pageSize" to MAX_RESULTS.toString())), "AMLL")
            if (root.number("status") != 200L) throw LyricsMatchException("AMLL 搜索失败（${root.string("status").ifEmpty { "响应无效" }}）")
            val items = root.obj("data").array("items")
            if (items.isEmpty()) continue
            return items.asSequence().mapNotNull { value ->
                val item = value as? JsonObject ?: return@mapNotNull null
                val ncm = item.strings("ncmMusicIds").firstOrNull { it.matches(PLATFORM_ID) }
                val qq = item.strings("qqMusicIds").firstOrNull { it.matches(PLATFORM_ID) }
                val id = ncm ?: qq ?: return@mapNotNull null
                LyricsCandidate(LyricsMatchSource.AMLL, id, item.strings("musicNames").firstOrNull().orEmpty().take(512),
                    item.strings("artistNames").joinToString(", ").take(1024), item.strings("albumNames").firstOrNull().orEmpty().take(512),
                    reference = "${if (ncm != null) "ncm" else "qq"}:$id")
            }.filter { it.title.isNotBlank() }.take(MAX_RESULTS).toList()
        }
        return emptyList()
    }

    private suspend fun loadAmll(candidate: LyricsCandidate): LyricsDocument {
        val parts = candidate.reference.split(':', limit = 2)
        if (parts.size != 2 || parts[0] !in setOf("ncm", "qq") || !parts[1].matches(PLATFORM_ID))
            throw LyricsMatchException("AMLL 歌曲关联编号无效")
        val field = if (parts[0] == "ncm") "ncmMusicId" else "qqMusicId"
        val root = request(amllBase.path("v1/lyrics/get", mapOf(field to parts[1])), "AMLL")
        if (root.number("status") != 200L) throw LyricsMatchException("AMLL 歌词获取失败（${root.string("status")}）")
        val data = root.obj("data")
        val ids = data.strings(if (parts[0] == "ncm") "ncmMusicIds" else "qqMusicIds")
        if (ids.isNotEmpty() && parts[1] !in ids) throw LyricsMatchException("AMLL 返回了其他歌曲的歌词")
        val raw = data.string("lyrics").takeIf { it.isNotBlank() } ?: throw LyricsMatchException("AMLL 尚未收录这首歌的歌词")
        val document = LyricsFileCodec.decode(raw.toByteArray(Charsets.UTF_8), "lyrics.ttml").document
        val declaredIds = document.metadata.values[field].orEmpty()
        if (declaredIds.isNotEmpty() && parts[1] !in declaredIds) throw LyricsMatchException("AMLL 歌词元数据与歌曲不符")
        return document
    }

    private suspend fun searchKugou(query: String): List<LyricsCandidate> {
        val keyword = query.split(Regex("\\s+-\\s+")).first().trim()
        try {
            val params = linkedMapOf("sorttype" to "0", "keyword" to keyword, "pagesize" to MAX_RESULTS.toString(), "page" to "1")
            val timestamp = System.currentTimeMillis()
            params += mapOf("userid" to "0", "appid" to "3116", "token" to "", "clienttime" to (timestamp / 1000).toString(),
                "iscorrection" to "1", "uuid" to "-", "mid" to md5(timestamp.toString()), "dfid" to "-", "clientver" to "11070", "platform" to "AndroidFilter")
            val root = request(kugouCatalogBase.path("v2/search/song", signed(params)), "酷狗", "SearchSong")
            kugouStatus(root)
            val values = root.obj("data").array("lists")
            val candidates = values.asSequence().mapNotNull { value ->
                val item = value as? JsonObject ?: return@mapNotNull null
                val hash = item.string("FileHash").uppercase(Locale.ROOT).takeIf { it.matches(KUGOU_HASH) } ?: return@mapNotNull null
                LyricsCandidate(LyricsMatchSource.KUGOU, hash, item.string("SongName").cleanTitle(),
                    item.arrayOrEmpty("Singers").mapNotNull { (it as? JsonObject)?.string("name") }.joinToString(", ").take(1024),
                    item.string("AlbumName").take(512), duration(item.number("Duration"), 1000), hash, kugouCover(item))
            }.filter { it.title.isNotBlank() }.take(MAX_RESULTS).toList()
            return candidates
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { currentCoroutineContext().ensureActive() }
        // The mobile search is independent of the newer signed catalogue's availability.
        val root = request(kugouSearchBase.path("api/v3/search/song", mapOf("keyword" to keyword, "page" to "1",
            "pagesize" to MAX_RESULTS.toString(), "format" to "json", "showtype" to "14", "plat" to "0", "version" to "9108")), "酷狗")
        kugouStatus(root)
        if (root.number("status") != 1L) throw LyricsMatchException("酷狗搜索失败（${root.string("status")}）")
        return root.obj("data").array("info").asSequence().mapNotNull { value ->
            val item = value as? JsonObject ?: return@mapNotNull null
            val hash = item.string("hash").uppercase(Locale.ROOT).takeIf { it.matches(KUGOU_HASH) } ?: return@mapNotNull null
            LyricsCandidate(LyricsMatchSource.KUGOU, hash, item.string("songname").cleanTitle(), item.string("singername").take(1024),
                item.string("album_name").take(512), duration(item.number("duration"), 1000), hash, kugouCover(item))
        }.filter { it.title.isNotBlank() }.take(MAX_RESULTS).toList()
    }

    private suspend fun loadKugou(candidate: LyricsCandidate): LyricsDocument {
        val hash = candidate.id.uppercase(Locale.ROOT)
        if (!hash.matches(KUGOU_HASH)) throw LyricsMatchException("酷狗歌曲编号无效")
        val params = mapOf("duration" to candidate.durationMs.coerceAtLeast(0).toString(), "hash" to hash,
            "keyword" to "${candidate.artists} - ${candidate.title}".take(MAX_QUERY_LENGTH), "lrctxt" to "1", "man" to "no")
        val root = request(kugouLyricsBase.path("v1/search", signed(params + LYRIC_PARAMS)), "酷狗", "Lyric")
        kugouStatus(root)
        val best = root.array("candidates").firstOrNull() as? JsonObject ?: throw LyricsMatchException("酷狗没有可用歌词")
        val id = best.string("id").takeIf { it.matches(Regex("[0-9]{1,30}")) } ?: throw LyricsMatchException("酷狗歌词编号无效")
        val access = best.string("accesskey").takeIf { it.length in 1..256 } ?: throw LyricsMatchException("酷狗歌词下载参数无效")
        val download = request(kugouLyricsBase.path("download", signed(LYRIC_PARAMS + mapOf("accesskey" to access,
            "charset" to "utf8", "client" to "mobi", "fmt" to "krc", "id" to id, "ver" to "1"))), "酷狗", "Lyric")
        kugouStatus(download)
        val raw = download.string("content").takeIf { it.isNotBlank() } ?: throw LyricsMatchException("酷狗返回了空歌词")
        val bytes = try { Base64.getDecoder().decode(raw) } catch (e: IllegalArgumentException) { throw LyricsMatchException("酷狗歌词编码无效", e) }
        if (bytes.size > LyricsFileCodec.MAX_BYTES) throw LyricsMatchException("酷狗歌词文件过大")
        val name = if (download.number("contenttype") == 2L) "lyrics.lrc" else "lyrics.krc"
        return LyricsFileCodec.decode(bytes, name).document
    }

    private suspend fun request(url: HttpUrl, provider: String, module: String? = null): JsonObject = suspendCancellableCoroutine { continuation ->
        val builder = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", if (module != null) "Android14-1070-11070-201-0-$module-wifi" else "CurrentMusicX/1.2 (bileizhen/CurrentMusicX)")
        if (module != null) builder.header("KG-Rec", "1").header("KG-RC", "1").header("KG-CLIENTTIMEMS", System.currentTimeMillis().toString())
        val call = client.newCall(builder.build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(LyricsMatchException("${provider}网络请求失败", e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val root = response.use {
                        if (!it.isSuccessful) throw LyricsMatchException("${provider}请求失败（HTTP ${it.code}）")
                        val body = it.body ?: throw LyricsMatchException("${provider}返回了空响应")
                        if (body.contentLength() > MAX_RESPONSE_BYTES) throw LyricsMatchException("${provider}响应过大")
                        val source = body.source()
                        source.request(MAX_RESPONSE_BYTES.toLong() + 1)
                        if (source.buffer.size > MAX_RESPONSE_BYTES) throw LyricsMatchException("${provider}响应过大")
                        try { Json.parseToJsonElement(source.readUtf8()) as? JsonObject ?: throw IOException("Expected an object") }
                        catch (e: Exception) { throw LyricsMatchException("${provider}响应格式无效", e) }
                    }
                    if (continuation.isActive) continuation.resume(root)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(if (e is LyricsMatchException) e else LyricsMatchException("${provider}响应读取失败", e))
                }
            }
        })
    }

    private suspend fun <T> bounded(source: LyricsMatchSource, timeoutMs: Long, block: suspend () -> T): T = try {
        withTimeout(timeoutMs) { block() }
    } catch (e: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw LyricsMatchException("${source.label}歌词请求超时", e)
    }

    companion object {
        private fun hasRealWordTiming(document: LyricsDocument): Boolean {
            fun real(line: io.github.currencortex.music.feature.lyrics.model.LyricLine): Boolean =
                (!line.wordsEstimated && line.words.any { it.text.isNotBlank() && it.endTimeMs > it.startTimeMs && it.endOffset > it.startOffset }) ||
                    line.backgroundVocals.any(::real)
            return document.lines.any(::real)
        }
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        const val MAX_RESULTS = 30
        const val MAX_QUERY_LENGTH = 240
        private val PLATFORM_ID = Regex("[A-Za-z0-9_-]{1,128}")
        private val KUGOU_HASH = Regex("[0-9A-F]{32}")
        private val LYRIC_PARAMS = mapOf("appid" to "3116", "clientver" to "11070")
        private const val KUGOU_SIGN_SEED = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        private fun md5(value: String) = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun signed(params: Map<String, String>): Map<String, String> = params + ("signature" to md5(
            KUGOU_SIGN_SEED + params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" } + KUGOU_SIGN_SEED))
        private fun duration(value: Long?, scale: Long): Long = value?.takeIf { it in 0..(604_800_000 / scale) }?.times(scale) ?: 0
        private fun String.cleanTitle() = replace(Regex("<[^>]{1,80}>"), "").take(512)
        private fun kugouCover(item: JsonObject): String {
            val values = listOf((item["trans_param"] as? JsonObject)?.string("union_cover").orEmpty(),
                item.string("sizable_cover"), item.string("Image"), item.string("imgurl"))
            return values.firstOrNull { it.length <= 4096 && (it.startsWith("https://") || it.startsWith("http://")) }
                ?.replace("{size}", "480").orEmpty()
        }
        private fun HttpUrl.path(path: String, params: Map<String, String>) = newBuilder().addPathSegments(path)
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
        private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
        private fun JsonObject.obj(key: String) = get(key) as? JsonObject ?: throw LyricsMatchException("歌词服务响应缺少 $key")
        private fun JsonObject.array(key: String) = get(key) as? JsonArray ?: throw LyricsMatchException("歌词服务响应缺少 $key")
        private fun JsonObject.arrayOrEmpty(key: String) = get(key) as? JsonArray ?: JsonArray(emptyList())
        private fun JsonObject.strings(key: String) = arrayOrEmpty(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        private fun kugouStatus(root: JsonObject) {
            val code = root.number("error_code") ?: root.number("errcode")
            if (code != null && code !in setOf(0L, 200L)) throw LyricsMatchException("酷狗服务拒绝请求（$code）")
            root.number("status")?.let { if (it !in setOf(1L, 200L)) throw LyricsMatchException("酷狗服务拒绝请求（$it）") }
        }
        fun isReliableMatch(song: Song, candidate: LyricsCandidate): Boolean =
            FoliaLyricsMatchPolicy.details(song, candidate).accepted
        fun metadataMatchPercent(song: Song, candidate: LyricsCandidate): Int =
            FoliaLyricsMatchPolicy.details(song, candidate).score
        fun rankCandidates(song: Song, candidates: List<LyricsCandidate>): List<LyricsCandidate> =
            candidates.sortedByDescending { metadataMatchPercent(song, it) }
    }
}
