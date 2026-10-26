// Lyric-source matching adapted from Folia Major, AGPL-3.0.
// chthollyphile/folia-major aecd330b720eb8fc728e6b10ebcec261b144d683:
// utils/lyrics/{amllDbCandidates,lyricMatchSources,autoMatchBestLyric}.ts and
// services/onlineMusic/kugou/kugouLyricProvider.ts. Original contributors retain copyright.
package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.feature.lyrics.domain.LyricsMatcher
import io.github.currencortex.music.feature.lyrics.domain.LyricsReferenceMatcher
import io.github.currencortex.music.feature.lyrics.domain.LyricsReferenceScore
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer
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

    /** Compatibility entry point for playback callers that only need the winning document. */
    suspend fun bestMatch(song: Song, preferred: LyricsMatchSource = LyricsMatchSource.NETEASE): SavedLyrics? =
        bestMatchDetailed(song, preferred)?.saved

    /** NetEase supplies the reference text, rather than winning because of source order. */
    @Suppress("UNUSED_PARAMETER") // Kept for existing manual-dialog callers; quality determines the winner.
    suspend fun bestMatchDetailed(song: Song, preferred: LyricsMatchSource = LyricsMatchSource.NETEASE): LyricsMatchResult? {
        if (song.video || song.name.isBlank()) return null
        var best: ScoredLyrics? = null
        return try {
            withTimeout(30_000) {
                val query = query(song)
                val reference = try {
                    withTimeout(10_000) { reference(song, query) }
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    throw missingReference(e)
                } catch (e: Exception) { throw missingReference(e) }
                val referenceScore = compare(reference.document, reference.document)
                if (!referenceScore.accepted || !referenceScore.total.isFinite()) throw missingReference()
                best = ScoredLyrics(reference, referenceScore, true)
                val lock = Mutex()
                val permits = Semaphore(5)
                // Every configured source starts now. A slow earlier source cannot consume
                // the budget before later platforms have had a chance to return candidates.
                supervisorScope { automaticSources().map { source -> async {
                    try {
                        val candidates = permits.withPermit { withTimeout(3_500) { search(source, query) } }
                        for (candidate in rankCandidates(song, candidates).filter { isReliableMatch(song, it) }.take(3)) {
                            try {
                                val saved = permits.withPermit { withTimeout(5_000) { load(candidate) } }
                                val score = compare(reference.document, saved.document)
                                currentCoroutineContext().ensureActive()
                                if (score.accepted && score.total.isFinite()) lock.withLock {
                                    val contender = ScoredLyrics(saved, score, false)
                                    if (best == null || SCORE_ORDER.compare(contender, best!!) > 0) best = contender
                                }
                            } catch (e: TimeoutCancellationException) {
                                currentCoroutineContext().ensureActive()
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { currentCoroutineContext().ensureActive() }
                        }
                    } catch (e: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { currentCoroutineContext().ensureActive() }
                } }.awaitAll() }
                best?.result()
            }
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            best?.result() ?: throw missingReference(e)
        }
    }

    /** Preview assessment never changes source selection or saves a manual override. */
    suspend fun assess(song: Song, saved: SavedLyrics, reference: LyricsDocument? = null): LyricsMatchAssessment {
        if (reference == null && (song.video || song.name.isBlank()))
            return LyricsMatchAssessment(unavailableReason = "当前歌曲信息不足，无法取得网易云基准歌词")
        return try {
            val baseline = reference ?: withTimeout(10_000) { this@OnlineLyricsMatcher.reference(song, query(song)).document }
            val baselineScore = compare(baseline, baseline)
            if (!baselineScore.accepted || !baselineScore.total.isFinite())
                LyricsMatchAssessment(unavailableReason = "网易云基准歌词不足以进行可靠比较")
            else LyricsMatchAssessment(score = compare(baseline, saved.document))
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            LyricsMatchAssessment(unavailableReason = "网易云基准歌词读取超时，暂无法计算内容匹配度")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            LyricsMatchAssessment(unavailableReason = "无法取得网易云基准歌词，暂无法计算内容匹配度")
        }
    }

    private data class ScoredLyrics(val saved: SavedLyrics, val score: LyricsReferenceScore, val reference: Boolean) {
        fun result() = LyricsMatchResult(saved, LyricsMatchAssessment(score = score))
    }

    private fun query(song: Song) = listOf(song.name, song.artists, song.album).filter(String::isNotBlank)
        .joinToString(" - ").take(MAX_QUERY_LENGTH)

    private suspend fun compare(reference: LyricsDocument, candidate: LyricsDocument): LyricsReferenceScore =
        withContext(scoreDispatcher) {
            currentCoroutineContext().ensureActive()
            LyricsReferenceMatcher.compare(reference, candidate).also { currentCoroutineContext().ensureActive() }
        }

    private suspend fun reference(song: Song, query: String): SavedLyrics {
        val id = LyricsMatcher.candidates(song).firstOrNull { it.source == MusicSource.NETEASE }
            ?.songId?.toLongOrNull()?.takeIf { it > 0 }
        if (id != null) return withTimeout(5_000) {
            load(LyricsCandidate(LyricsMatchSource.NETEASE, id.toString(), song.name, song.artists,
                song.album, song.durationMs, id.toString()))
        }
        val candidates = withTimeout(3_500) { search(LyricsMatchSource.NETEASE, query) }
        var failure: Exception? = null
        for (candidate in rankCandidates(song, candidates).filter { isReliableMatch(song, it) }.take(3)) {
            try { return withTimeout(5_000) { load(candidate) } }
            catch (e: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); failure = e }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { currentCoroutineContext().ensureActive(); failure = e }
        }
        throw missingReference(failure)
    }

    private fun automaticSources() = LyricsMatchSource.entries.filter { source -> when (source) {
        LyricsMatchSource.NETEASE -> false
        LyricsMatchSource.CURRENT_MUSIC -> source in additionalSources || (searchCurrentMusic != null && loadCurrentMusic != null)
        LyricsMatchSource.QQ_MUSIC, LyricsMatchSource.QISHUI -> source in additionalSources
        LyricsMatchSource.AMLL, LyricsMatchSource.KUGOU -> true
    } }

    private fun missingReference(cause: Throwable? = null) = LyricsMatchException(
        "无法取得网易云基准歌词，暂不能自动匹配；可手动选择来源", cause)

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
        private val SCORE_ORDER = compareBy<ScoredLyrics> { it.score.total }
            .thenBy { it.score.similarity }.thenBy { it.score.timingSimilarity }.thenBy { it.score.quality }
            .thenBy { it.reference } // An exact tie keeps the known reference, without source preference.
            .thenByDescending { it.saved.origin.reference }
            .thenByDescending { it.saved.origin.provider + ":" + it.saved.origin.reference }
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
        private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
        private fun artistMatch(target: String, candidate: String): Boolean {
            val split = Regex("[,，、/&]|\\s+(?:feat\\.?|ft\\.?)\\s+", RegexOption.IGNORE_CASE)
            val names = candidate.split(split).map(::normalized).filter(String::isNotBlank).toSet()
            return target.split(split).map(::normalized).firstOrNull(String::isNotBlank)?.let { it in names } == true
        }
        fun isReliableMatch(song: Song, candidate: LyricsCandidate): Boolean {
            val title = normalized(song.name)
            if (title.isEmpty() || title != normalized(candidate.title)) return false
            val identity = if (song.artists.isNotBlank()) artistMatch(song.artists, candidate.artists)
                else song.album.isNotBlank() && normalized(song.album) == normalized(candidate.album)
            return identity && (song.durationMs <= 0 || candidate.durationMs <= 0 || abs(song.durationMs - candidate.durationMs) <= 3_000)
        }
        /** Search metadata only: this percentage makes no claim about the fetched lyric content. */
        fun metadataMatchPercent(song: Song, candidate: LyricsCandidate): Int = (
            (if (normalized(song.name) == normalized(candidate.title)) 55 else 0) +
                (if (artistMatch(song.artists, candidate.artists)) 25 else 0) +
                (if (song.album.isNotBlank() && normalized(song.album) == normalized(candidate.album)) 10 else 0) +
                (if (song.durationMs > 0 && candidate.durationMs > 0 && abs(song.durationMs - candidate.durationMs) <= 3_000) 10 else 0)
            ).coerceIn(0, 100)
        fun rankCandidates(song: Song, candidates: List<LyricsCandidate>): List<LyricsCandidate> =
            candidates.sortedByDescending { metadataMatchPercent(song, it) }
    }
}
