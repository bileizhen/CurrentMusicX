// Public car catalogue protocol adapted from GSQZ/qishui-music-api@0.2.2, MIT.
// Copyright (c) 2026 sayqz. https://github.com/GSQZ/qishui-music-api
// npm gitHead: 924add376cadf8a2268f11999051eb976e3b8e0d. Full MIT notice: legal/QISHUI-MIT.txt.
// Lyric normalization follows Folia Major aecd330b720eb8fc728e6b10ebcec261b144d683,
// src/services/onlineMusic/qishui/qishuiLyrics.ts and qishuiNormalize.ts, AGPL-3.0.
package io.github.currencortex.music.feature.lyrics.data

import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Search and lyric metadata only. No account sessions, media URLs, audio models or native modules. */
class QishuiLyricsMatchSource(
    client: OkHttpClient = OkHttpClient(),
    tokenBaseUrl: String = "https://open.qishui.com/",
    platformBaseUrl: String = "https://open.douyin.com/",
    private val deviceId: String = createDeviceId(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val client = client.newBuilder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS)
        .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .apply { interceptors().clear(); networkInterceptors().clear() }.build()
    private val tokenBase = tokenBaseUrl.toHttpUrl()
    private val platformBase = platformBaseUrl.toHttpUrl()
    private val tokenLock = Mutex()
    private class ClientToken(val value: String) { var replacementFailure: Exception? = null }
    private var currentToken: ClientToken? = null
    private class Rejected(val code: String) : IOException()

    init { require(deviceId.matches(Regex("[0-9]{19}"))) { "汽水读取设备标识无效" } }

    suspend fun search(query: String): List<LyricsCandidate> = bounded {
        val text = query.trim()
        if (text.isBlank()) return@bounded emptyList()
        require(text.length <= OnlineLyricsMatcher.MAX_QUERY_LENGTH) { "搜索关键词过长" }
        val keyword = text.split(Regex("\\s+-\\s+")).take(2).joinToString(" ").trim()
        val searchId = UUID.randomUUID().toString()
        val data = platform("search/", buildJsonObject {
            put("q", keyword); put("cursor", "0"); put("search_id", searchId); put("from_search_id", searchId)
            put("search_method", "input"); put("search_type", "track"); put("search_scene", "normal")
            put("scene_name", "search"); put("ab_param", ""); put("last_viewed_media", buildJsonObject {})
        })
        val groups = data["result_groups"] as? JsonArray ?: throw LyricsMatchException("汽水搜索响应缺少候选分组")
        val group = groups.mapNotNull { it as? JsonObject }.firstOrNull { it.string("id") == "tracks" }
            ?: return@bounded emptyList()
        val values = group["data"] as? JsonArray ?: throw LyricsMatchException("汽水搜索响应缺少候选列表")
        values.asSequence().mapNotNull { value ->
            val track = ((value as? JsonObject)?.get("entity") as? JsonObject)?.get("track") as? JsonObject
                ?: return@mapNotNull null
            val base = track["base_info"] as? JsonObject ?: return@mapNotNull null
            val id = base.string("id").takeIf { it.matches(MEDIA_ID) } ?: return@mapNotNull null
            val title = base.string("name").take(512).takeIf(String::isNotBlank) ?: return@mapNotNull null
            val related = track["related_info"] as? JsonObject
            val artists = related?.get("artist_links") as? JsonArray
                ?: (base["song_maker_team"] as? JsonObject)?.get("composers") as? JsonArray
            val artist = artists.orEmpty().mapNotNull { (it as? JsonObject)?.string("name")?.takeIf(String::isNotBlank) }
                .joinToString(", ").take(1024)
            val album = (related?.get("album_link") as? JsonObject)?.string("name").orEmpty().take(512)
            val duration = base["duration_ms"]?.jsonPrimitive?.longOrNull?.takeIf { it in 0..604_800_000 } ?: 0L
            LyricsCandidate(LyricsMatchSource.QISHUI, id, title, artist, album, duration, id,
                cover((track["display_info"] as? JsonObject)?.get("cover_url")))
        }.distinctBy { it.id }.take(OnlineLyricsMatcher.MAX_RESULTS).toList()
    }

    private fun cover(raw: JsonElement?): String {
        val direct = (raw as? JsonPrimitive)?.contentOrNull
        if (direct != null) return direct.takeIf { it.startsWith("https://") && it.length <= 4096 }.orEmpty()
        val value = raw as? JsonObject ?: return ""
        val url = (value["urls"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.firstOrNull { it.startsWith("https://") && it.length <= 4096 } ?: return ""
        val uri = value.string("uri")
        val result = if (uri.isNotEmpty() && url.endsWith('/')) {
            val prefix = value.string("template_prefix")
            url + uri + if (prefix.isNotEmpty()) "~$prefix-image.image" else ""
        } else url
        return result.takeIf { it.length <= 4096 }.orEmpty()
    }

    suspend fun load(candidate: LyricsCandidate): LyricsDocument = bounded {
        require(candidate.source == LyricsMatchSource.QISHUI && candidate.id.matches(MEDIA_ID)) { "汽水歌词候选编号无效" }
        val data = platform("media/mget/", buildJsonObject {
            put("media_list", buildJsonArray { add(buildJsonObject { put("id", candidate.id); put("type", "track") }) })
            put("card_context", buildJsonObject {})
        })
        val media = data["media_list"] as? JsonArray ?: throw LyricsMatchException("汽水歌词响应缺少歌曲列表")
        val item = media.mapNotNull { it as? JsonObject }.firstOrNull { value ->
            val track = value["track_entity"] as? JsonObject
            value.string("media_id").ifEmpty { (track?.get("base_info") as? JsonObject)?.string("id").orEmpty() } == candidate.id
        } ?: throw LyricsMatchException("汽水没有返回所选歌曲的歌词")
        val track = item["track_entity"] as? JsonObject ?: throw LyricsMatchException("汽水歌词响应缺少歌曲信息")
        val returnedId = (track["base_info"] as? JsonObject)?.string("id").orEmpty()
        if (returnedId.isNotBlank() && returnedId != candidate.id) throw LyricsMatchException("汽水返回了其他歌曲的歌词")
        val lyricInfo = (track["display_info"] as? JsonObject)?.get("lyric_info") as? JsonObject
            ?: throw LyricsMatchException("汽水尚未提供这首歌的歌词")
        val entity = lyricInfo["lyric_entity"] as? JsonObject ?: throw LyricsMatchException("汽水返回了空歌词")
        val document = decodeEntity(entity)
        val translations = lyricInfo["lang_translations"] as? JsonObject
        val translated = (translations?.entries?.firstOrNull {
            it.key.equals("zh-CN", true) || it.key.equals("ZH-HANS-CN", true)
        }?.value ?: translations?.values?.firstOrNull()) as? JsonObject
        if (translated == null || translated.string("content").isBlank()) return@bounded document
        val byTime = decodeEntity(translated).lines.associateBy { it.startTimeMs }
        document.copy(lines = document.lines.map { line -> line.copy(
            translation = byTime[line.startTimeMs]?.text?.takeIf(String::isNotBlank) ?: line.translation) })
    }

    private fun decodeEntity(entity: JsonObject): LyricsDocument {
        val text = entity.string("content").replace("\u0000", "")
        if (text.isBlank()) throw LyricsMatchException("汽水尚未提供这首歌的歌词")
        val type = entity.string("type").lowercase(Locale.ROOT)
        if (type !in setOf("", "lrc", "krc")) throw LyricsMatchException("暂不支持汽水返回的歌词格式")
        return LyricsFileCodec.decode(text.toByteArray(Charsets.UTF_8), if (type == "krc") "lyrics.krc" else "lyrics.lrc").document
    }

    /** Only an explicit expired public token permits one replay of these two metadata reads. */
    private suspend fun platform(path: String, content: JsonObject): JsonObject {
        var token = token()
        val body = JsonObject(content + ("common_params" to buildJsonObject {}))
        repeat(2) { attempt ->
            try {
                val root = request(url(platformBase, path), body, token.value)
                return root["data"] as? JsonObject ?: throw LyricsMatchException("汽水返回了无效的数据对象")
            } catch (rejected: Rejected) {
                if (attempt != 0 || rejected.code != EXPIRED_TOKEN) throw rejection(rejected.code)
                token = token(token)
            }
        }
        throw LyricsMatchException("汽水歌词请求失败")
    }

    private suspend fun token(expired: ClientToken? = null): ClientToken = tokenLock.withLock {
        currentToken?.takeIf { it !== expired }?.let { return@withLock it }
        expired?.replacementFailure?.let { throw it }
        if (currentToken === expired) currentToken = null
        try {
            val root = try { request(url(tokenBase, "client/token/get/", tokenRequest = true)) }
            catch (rejected: Rejected) { throw rejection(rejected.code) }
            val value = root.string("client_token").takeIf { it.isNotBlank() && it.length <= 4096 }
                ?: throw LyricsMatchException("汽水未返回公共读取令牌")
            ClientToken(value).also { currentToken = it }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            expired?.replacementFailure = failure
            throw failure
        }
    }

    private fun url(base: HttpUrl, path: String, tokenRequest: Boolean = false): HttpUrl = base.newBuilder()
        .addPathSegments("api/luna/v1/platform/$path")
        .apply {
            COMMON.forEach { (key, value) -> addQueryParameter(key, value) }
            addQueryParameter("device_id", deviceId); addQueryParameter("did", deviceId); addQueryParameter("iid", deviceId)
            addQueryParameter("vehicle_id", "qishui-$deviceId")
            addQueryParameter("_timestamp", (now() / 1000).toString())
            addQueryParameter("_nonce", random.nextInt(65536).toString())
            if (tokenRequest) addQueryParameter("trigger_type", "cold_start")
        }.build()

    private suspend fun request(url: HttpUrl, body: JsonObject? = null, token: String? = null): JsonObject =
        suspendCancellableCoroutine { continuation ->
            val builder = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "LunaOpenSDK/1.0.10")
            if (token != null) builder.header("access-token", token)
            if (body != null) builder.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            val call = client.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(LyricsMatchException("汽水网络请求失败", e))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val root = response.use {
                            if (!it.isSuccessful) throw LyricsMatchException("汽水请求失败（HTTP ${it.code}）")
                            val responseBody = it.body ?: throw LyricsMatchException("汽水返回了空响应")
                            if (responseBody.contentLength() > OnlineLyricsMatcher.MAX_RESPONSE_BYTES) throw LyricsMatchException("汽水响应过大")
                            val source = responseBody.source()
                            source.request(OnlineLyricsMatcher.MAX_RESPONSE_BYTES.toLong() + 1)
                            if (source.buffer.size > OnlineLyricsMatcher.MAX_RESPONSE_BYTES) throw LyricsMatchException("汽水响应过大")
                            val parsed = Json.parseToJsonElement(source.readUtf8()) as? JsonObject
                                ?: throw LyricsMatchException("汽水响应格式无效")
                            val nested = parsed["data"] as? JsonObject
                            listOfNotNull(parsed["err_no"], nested?.get("error_code")).forEach { raw ->
                                val code = (raw as? JsonPrimitive)?.longOrNull ?: throw LyricsMatchException("汽水响应错误码无效")
                                if (code != 0L) throw Rejected(code.toString())
                            }
                            parsed
                        }
                        if (continuation.isActive) continuation.resume(root)
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(when (failure) {
                            is LyricsMatchException, is Rejected -> failure
                            else -> LyricsMatchException("汽水响应读取失败", failure)
                        })
                    }
                }
            })
        }

    private suspend fun <T> bounded(block: suspend () -> T): T = try {
        withTimeout(10_000) { block() }
    } catch (timeout: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw LyricsMatchException("汽水歌词请求超时", timeout)
    }

    companion object {
        private const val EXPIRED_TOKEN = "28001008"
        private val MEDIA_ID = Regex("[0-9]{1,30}")
        private val random = SecureRandom()
        private val COMMON = mapOf("aid" to "765213", "app_id" to "765213", "app_name" to "byte_autoservice_lite",
            "version_code" to "100010", "version_name" to "1.0.10", "channel" to "bydSong", "sdk_aid" to "753999",
            "caller_name" to "luna_open_sdk", "sdk_version_code" to "100000100", "sdk_version_name" to "0.0.1",
            "sdk_channel" to "Test", "vehicle_type" to "BYD", "device_brand" to "BYD", "device_type" to "BYD",
            "device_platform" to "android", "resolution" to "1920x1080", "os_version" to "13", "ac" to "wifi")
        private fun createDeviceId() = BigInteger(64, random).mod(BigInteger("8000000000000000000"))
            .add(BigInteger("1000000000000000000")).toString()
        private fun rejection(code: String) = LyricsMatchException("汽水拒绝了歌词读取请求（$code）")
        private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}
