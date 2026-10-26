// QQ lyric matching informed by Folia Major, AGPL-3.0, aecd330b720eb8fc728e6b10ebcec261b144d683.
// Original Folia contributors retain attribution. Uses QQ's public web catalogue/LRC endpoints;
// the upstream anonymous Lite search was refused, so neither it nor encrypted QRC is advertised.
package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cookie-free QQ matching. Public LRC may include auxiliary tracks; it is never promoted to QRC. */
class QqLyricsMatchSource(client: OkHttpClient = OkHttpClient(), baseUrl: String = "https://c.y.qq.com/") {
    private val base = baseUrl.toHttpUrl()
    private val client = client.newBuilder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS).cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .apply { interceptors().clear(); networkInterceptors().clear() }.build()

    suspend fun search(query: String): List<LyricsCandidate> = bounded {
        val text = query.trim()
        if (text.isEmpty()) return@bounded emptyList()
        require(text.length <= OnlineLyricsMatcher.MAX_QUERY_LENGTH) { "搜索关键词过长" }
        // The album remains candidate-ranking evidence; including it in QQ's keyword can hide
        // the right recording, especially for long editions and soundtrack catalogue names.
        val keyword = text.split(Regex("\\s+-\\s+")).take(2).joinToString(" ")
        val root = request("soso/fcgi-bin/search_for_qq_cp", mapOf("w" to keyword, "format" to "json", "p" to "1",
            "n" to OnlineLyricsMatcher.MAX_RESULTS.toString(), "ct" to "24", "qqmusic_ver" to "1298"))
        checkBusiness(root, "搜索")
        val songs = root.obj("data").obj("song").array("list")
        songs.asSequence().mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            val mid = row.string("songmid").takeIf { it.matches(SONG_MID) } ?: return@mapNotNull null
            val title = cleanLabel(row.string("songname"))
            if (title.isBlank()) return@mapNotNull null
            val artists = (row["singer"] as? JsonArray).orEmpty().mapNotNull { singer ->
                (singer as? JsonObject)?.string("name")?.let(::cleanLabel)?.takeIf(String::isNotBlank)
            }.joinToString(", ").take(1024)
            val seconds = row.number("interval")?.takeIf { it in 0..604_800 } ?: 0
            val id = row.number("songid")?.takeIf { it > 0 }?.toString().orEmpty()
            LyricsCandidate(LyricsMatchSource.QQ_MUSIC, mid, title, artists, cleanLabel(row.string("albumname")),
                seconds * 1000, id.ifEmpty { mid })
        }.distinctBy { it.id }.take(OnlineLyricsMatcher.MAX_RESULTS).toList()
    }

    suspend fun load(candidate: LyricsCandidate): LyricsDocument = bounded {
        require(candidate.source == LyricsMatchSource.QQ_MUSIC && candidate.id.matches(SONG_MID)) { "QQ 音乐歌曲编号无效" }
        val root = request("lyric/fcgi-bin/fcg_query_lyric_new.fcg", mapOf("songmid" to candidate.id,
            "format" to "json", "nobase64" to "1", "g_tk" to "5381"))
        checkBusiness(root, "歌词")
        val main = decodeTrack(root.string("lyric"))
        if (main.isBlank()) throw LyricsMatchException("QQ 音乐未提供这首歌的公开歌词")
        val translation = decodeTrack(root.string("trans"))
        val romanization = decodeTrack(root.string("roma").ifBlank { root.string("romalrc") })
        val parsed = LyricsParser.parse(buildJsonObject {
            put("lrc", main); put("tlyric", translation); put("romalrc", romanization)
        }).withoutEstimatedTiming()
        if (parsed.lines.isEmpty()) throw LyricsMatchException("QQ 音乐没有可识别的歌词时间轴")
        LyricsFileCodec.validate(parsed)
        parsed.copy(metadata = parsed.metadata.copy(source = "QQ Music",
            values = parsed.metadata.values + ("qqMusicId" to listOf(candidate.id))))
    }

    private suspend fun request(path: String, params: Map<String, String>): JsonObject = suspendCancellableCoroutine { continuation ->
        val url = base.newBuilder().addPathSegments(path).apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0").header("Referer", "https://y.qq.com/").header("Origin", "https://y.qq.com").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(LyricsMatchException("QQ 音乐网络请求失败", e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val root = response.use {
                        if (!it.isSuccessful) throw LyricsMatchException("QQ 音乐请求失败（HTTP ${it.code}）")
                        val body = it.body ?: throw LyricsMatchException("QQ 音乐返回了空响应")
                        if (body.contentLength() > MAX_RESPONSE_BYTES) throw LyricsMatchException("QQ 音乐响应过大")
                        val source = body.source()
                        source.request(MAX_RESPONSE_BYTES.toLong() + 1)
                        if (source.buffer.size > MAX_RESPONSE_BYTES) throw LyricsMatchException("QQ 音乐响应过大")
                        try { Json.parseToJsonElement(source.readUtf8()) as? JsonObject ?: throw IOException("Expected an object") }
                        catch (e: Exception) { throw LyricsMatchException("QQ 音乐响应格式无效", e) }
                    }
                    if (continuation.isActive) continuation.resume(root)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(if (e is LyricsMatchException) e else LyricsMatchException("QQ 音乐响应读取失败", e))
                }
            }
        })
    }

    private suspend fun <T> bounded(block: suspend () -> T): T = try { withTimeout(7_000) { block() } }
    catch (e: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw LyricsMatchException("QQ 音乐歌词请求超时", e)
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        private val SONG_MID = Regex("[A-Za-z0-9_-]{1,128}")
        private val TIMESTAMP = Regex("\\[\\d{1,4}:\\d{1,2}(?:\\.\\d{1,3})?]")
        private val ENTITY = Regex("&(#(?:x[0-9a-fA-F]{1,6}|[0-9]{1,7})|amp|lt|gt|quot|apos|nbsp);")
        private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
        private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
        private fun JsonObject.obj(key: String) = get(key) as? JsonObject ?: throw LyricsMatchException("QQ 音乐响应缺少 $key")
        private fun JsonObject.array(key: String) = get(key) as? JsonArray ?: throw LyricsMatchException("QQ 音乐响应缺少 $key")
        private fun checkBusiness(root: JsonObject, action: String) {
            val code = root.number("code") ?: throw LyricsMatchException("QQ 音乐${action}响应缺少状态码")
            val rejected = listOf(code, root.number("retcode") ?: 0, root.number("subcode") ?: 0).firstOrNull { it != 0L }
            if (rejected != null) throw LyricsMatchException("QQ 音乐${action}请求失败（$rejected），请切换来源或稍后重试")
        }
        private fun cleanLabel(value: String) = entities(value).replace(Regex("<[^>]{1,80}>"), "").trim().take(512)
        private fun entities(value: String): String = ENTITY.replace(value) { match ->
            when (val name = match.groupValues[1]) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                else -> {
                    val code = if (name.startsWith("#x")) name.drop(2).toIntOrNull(16) else name.drop(1).toIntOrNull()
                    code?.takeIf { it in 1..0x10ffff && it !in 0xd800..0xdfff }?.let { String(Character.toChars(it)) } ?: match.value
                }
            }
        }
        private fun decodeTrack(value: String): String {
            if (value.isBlank()) return ""
            val plain = entities(value).replace("\r\n", "\n").replace('\r', '\n')
            if (plain.toByteArray(Charsets.UTF_8).size > LyricsFileCodec.MAX_BYTES) throw LyricsMatchException("QQ 音乐歌词文件过大")
            if (TIMESTAMP.containsMatchIn(plain)) return plain
            val bytes = try { Base64.getDecoder().decode(value.replace(Regex("\\s+"), "")) }
            catch (e: IllegalArgumentException) { throw LyricsMatchException("QQ 音乐返回了不支持的歌词编码", e) }
            if (bytes.size > LyricsFileCodec.MAX_BYTES) throw LyricsMatchException("QQ 音乐歌词文件过大")
            val decoded = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
            catch (e: Exception) { throw LyricsMatchException("QQ 音乐歌词文字编码无效", e) }
            val text = entities(decoded).replace("\r\n", "\n").replace('\r', '\n')
            if (!TIMESTAMP.containsMatchIn(text)) throw LyricsMatchException("QQ 音乐返回了不支持的歌词格式")
            return text
        }
    }
}
