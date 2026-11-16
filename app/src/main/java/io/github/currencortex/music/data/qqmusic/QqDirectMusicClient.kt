package io.github.currencortex.music.data.qqmusic

import io.github.currencortex.music.core.network.ApiClient
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ApiJson
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Direct client for the same TME CGI endpoints used by the QQ Music Android application.
 *
 * No CurrentMusic account token or proxy response format is involved here. Public metadata uses
 * musicu.fcg; QR authorization and playback rights use the credentials returned by Tencent.
 */
class QqDirectMusicClient(
    baseClient: OkHttpClient = ApiClient.defaultHttpClient(),
    private val log: (String) -> Unit = {},
) {
    private companion object {
        const val CGI = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        const val APP_VERSION = 7658
        const val FALLBACK_QIMEI36 = "6c9d3cd110abca9b16311cee10001e717614"
        const val DEFAULT_GUID = "abcdef1234567890abcdef1234567890"
        const val STREAM_ORIGIN = "https://isure.stream.qqmusic.qq.com/"
        const val USER_AGENT = "QQMusic 2005000982(android 15) (Phone)"
        val JSON = "application/json; charset=utf-8".toMediaType()
        val PLAIN_TEXT = "text/plain; charset=utf-8".toMediaType()
        val REQUEST_KEY = byteArrayOf(
            -67, 48, 95, 16, -48, -1, 116, -74, -17, 84, -38, -72, 53, -75, -31, -49,
        )
        val RESPONSE_XOR = byteArrayOf(
            122, 63, -116, 29, 94, -101, 47, 10, 108, 77, 126, -117, 31, 58, 92, -99, 14, 43, 111, 74, -127,
        )
        val SIGN_SCRAMBLE = intArrayOf(
            89, 39, 179, 150, 218, 82, 58, 252, 177, 52, 186, 123, 120, 64, 242, 133, 143, 161, 121, 179,
        )
        val PTUI = Regex("""ptuiCB\((.*)\)\s*;?\s*$""", RegexOption.DOT_MATCHES_ALL)
        val QUOTED = Regex("""'((?:\\.|[^'])*)'""")
        const val QQ_OAUTH_URL = "https://graph.qq.com/oauth2.0/authorize"
        const val QQ_OAUTH_REDIRECT = "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/"
        val WX_UUID = Regex("""uuid=(.+?)\"""")
        val WX_STATUS = Regex("""window\.wx_errcode=(\d+);window\.wx_code='([^']*)'""")
    }

    private data class DirectCredential(
        val musicId: Long,
        val stringMusicId: String,
        val musicKey: String,
        val encryptUin: String,
        val loginType: Int,
        val openId: String = "",
        val refreshToken: String = "",
        val accessToken: String = "",
        val expiredAt: Long = 0,
        val unionId: String = "",
        val refreshKey: String = "",
    ) {
        val valid get() = musicId > 0 && musicKey.isNotBlank()

        fun encode(): String = buildJsonObject {
            put("musicid", musicId)
            put("str_musicid", stringMusicId.ifBlank { musicId.toString() })
            put("musickey", musicKey)
            put("encryptUin", encryptUin)
            put("loginType", loginType)
            put("openid", openId)
            put("refresh_token", refreshToken)
            put("access_token", accessToken)
            put("expired_at", expiredAt)
            put("unionid", unionId)
            put("refresh_key", refreshKey)
        }.toString()

        fun profile(): JsonObject = buildJsonObject {
            put("musicid", musicId)
            put("uin", stringMusicId.ifBlank { musicId.toString() })
            put("encryptUin", encryptUin)
        }

        fun cookieHeader(): String? = qqAuthCookieHeader(
            stringMusicId.ifBlank { musicId.toString() },
            musicKey,
            loginType,
            openId,
            refreshToken,
            unionId,
            refreshKey,
        )

        companion object {
            fun parse(raw: String?): DirectCredential? {
                if (raw.isNullOrBlank()) return null
                val json = runCatching { ApiJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
                return fromJson(json)
            }

            fun fromJson(value: JsonObject): DirectCredential? {
                val data = (value["data"] as? JsonObject) ?: value
                val id = data.firstLong("musicid", "musicId", "uin", "qq")
                val key = data.firstText("musickey", "musicKey", "qqmusic_key", "authst")
                if (id <= 0 || key.isBlank()) return null
                val login = data.firstLong("loginType", "tmeLoginType").toInt().takeIf { it > 0 } ?: 2
                return DirectCredential(
                    musicId = id,
                    stringMusicId = data.firstText("str_musicid", "strMusicId", "uin", "qq")
                        .ifBlank { id.toString() },
                    musicKey = key,
                    encryptUin = data.firstText("encryptUin", "encrypt_uin"),
                    loginType = login,
                    openId = data.firstText("openid", "openId"),
                    refreshToken = data.firstText("refresh_token", "refreshToken"),
                    accessToken = data.firstText("access_token", "accessToken"),
                    expiredAt = data.firstLong("expired_at", "expiredAt", "expired_in"),
                    unionId = data.firstText("unionid", "unionId"),
                    refreshKey = data.firstText("refresh_key", "refreshKey"),
                )
            }
        }
    }

    private class QrSession(
        val channel: String,
        val secret: String,
        val image: ByteArray,
        val mime: String,
        val cookieJar: MemoryCookieJar,
        val client: OkHttpClient,
        val createdAt: Long = System.currentTimeMillis(),
    ) {
        @Volatile var subscription: QqMusicQrSubscription? = null
    }

    private data class PlaylistAttempt(
        val query: Map<String, String>,
        val aiDissInfo: Boolean,
        val mobileAi: Boolean,
        val compactAuthComm: Boolean,
        val useCredential: Boolean,
    )

    private val sessions = ConcurrentHashMap<String, QrSession>()
    private val ownedPlaylistDirectories = ConcurrentHashMap<String, String>()
    private val http = baseClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()
    private val cgiCookieJar = MemoryCookieJar()
    private val cgiHttp = http.newBuilder().cookieJar(cgiCookieJar).build()

    fun isCredential(raw: String?): Boolean = DirectCredential.parse(raw)?.valid == true

    suspend fun call(path: String, query: Map<String, String>, body: JsonObject?,
        method: String, requiresSession: Boolean, login: Boolean, qr: Boolean): JsonObject =
        withContext(Dispatchers.IO) {
            val credential = DirectCredential.parse(query["credential"])
            if (requiresSession && credential?.valid != true)
                throw ApiException(ErrorKind.Forbidden, userMessage = "请先扫码登录 QQ 音乐")
            val rawRoute = path.trim('/')
            val route = rawRoute.removePrefix("native/")
            when {
                route == "login/channels" -> channels()
                route == "login/qr/key" -> qrKey(body?.firstText("channel").orEmpty())
                route == "login/qr/create" -> qrImage(body?.firstText("key").orEmpty())
                route == "login/qr/check" -> qrCheck(body?.firstText("key").orEmpty())
                route == "login/qr/cancel" -> {
                    sessions.remove(body?.firstText("key").orEmpty())?.subscription?.close()
                    wrapped(buildJsonObject { put("cancelled", true) })
                }
                route == "login/status" -> loginStatus(requireNotNull(credential))
                route == "login/refresh" -> refresh(requireNotNull(credential))
                route == "logout" -> {
                    runCatching {
                        cgi("music.login.LoginServer", "Logout", buildJsonObject {}, requireNotNull(credential),
                            extraComm = buildJsonObject { put("tmeLoginType", credential.loginType) })
                    }
                    wrapped(buildJsonObject { put("loggedOut", true) })
                }
                route == "search" -> search(query)
                route == "search/suggest" -> suggestions(query)
                route == "search/hot" -> hotSearch()
                route == "song/detail" -> songDetail(query)
                route == "song/url" -> songUrl(query, credential)
                route == "lyric" -> lyric(query)
                route == "playlist" -> playlist(query)
                rawRoute.startsWith("native/getSongListDetail/") -> playlist(
                    mapOf("id" to rawRoute.substringAfterLast('/')), rawFull = true)
                route == "user/playlist-detail" -> {
                    val id = query["tid"].orEmpty()
                    val directory = ownedPlaylistDirectories[id]
                    playlist(buildMap {
                        put("id", id)
                        directory?.let { put("dirid", it) }
                        val userCredential = requireNotNull(credential)
                        put("encryptUin", userCredential.encryptUin)
                    }, rawFull = false, credential)
                }
                route == "user/playlist" -> userPlaylists(requireNotNull(credential))
                route == "user/liked-songs" -> likedSongs(requireNotNull(credential), query)
                route == "user/subscription-status" -> subscriptionStatus(
                    requireNotNull(credential), query)
                route == "user/song/like" -> changeSongLike(
                    requireNotNull(credential), body ?: buildJsonObject {})
                route == "user/playlist/create" -> createPlaylist(
                    requireNotNull(credential), body ?: buildJsonObject {})
                route == "user/playlist/delete" -> deletePlaylist(
                    requireNotNull(credential), body ?: buildJsonObject {})
                route == "user/playlist/rename" -> renamePlaylist(
                    requireNotNull(credential), body ?: buildJsonObject {})
                route == "user/playlist/tracks" -> editPlaylistTracks(
                    requireNotNull(credential), body ?: buildJsonObject {})
                route == "user/playlist/subscribe" -> subscribePlaylist(
                    requireNotNull(credential), body ?: buildJsonObject {})
                route == "user/album/subscribe" -> subscribeAlbum(
                    requireNotNull(credential), body ?: buildJsonObject {})
                route == "user/detail" -> userDetail(requireNotNull(credential))
                route == "user/music-gene" -> musicGene(requireNotNull(credential))
                route == "recommend/new-songs" -> newSongs()
                route == "recommend/playlists" -> recommendedPlaylists(query)
                route == "recommend/categories/all" -> musicCategories()
                route == "recommend/category-playlists/sorted" -> categoryPlaylists(query, credential)
                route == "user/guess-you-like" -> guessYouLike(requireNotNull(credential))
                route == "user/personal-fm" -> personalFm(query, requireNotNull(credential))
                route == "user/daily-songs" -> dailySongs(requireNotNull(credential))
                route == "user/listening-rank" -> recentListening(requireNotNull(credential))
                route == "recommend/toplists" -> topLists()
                route == "recommend/toplist" -> topList(query)
                route == "comment/new" -> newComments(query)
                route == "album" -> album(query)
                route == "artist" -> artist(query)
                route == "artist/albums" -> artistAlbums(query)
                route == "user/vip" -> vip(requireNotNull(credential))
                route == "user/albums" -> favoriteAlbums(requireNotNull(credential), query)
                else -> throw ApiException(
                    ErrorKind.NotFound,
                    userMessage = "QQ 音乐直连接口尚未覆盖：$route",
                )
            }
        }

    private fun channels(): JsonObject = wrapped(buildJsonObject {
        put("configured", true)
        put("sessionMode", "qq-oauth-direct")
        put("channels", buildJsonArray { add("qq"); add("qqmusic") })
        put("features", buildJsonObject {
            put("auth.getQrKey", true)
            put("auth.createQr", true)
            put("auth.checkQr", true)
        })
    })

    private fun qrKey(channel: String): JsonObject {
        val selected = channel.ifBlank { "qq" }
        val key = "q" + UUID.randomUUID().toString().replace("-", "")
        sessions.forEach { (expiredKey, session) ->
            if (System.currentTimeMillis() - session.createdAt > 180_000 && sessions.remove(expiredKey, session)) {
                session.subscription?.close()
            }
        }
        sessions[key] = when (selected) {
            "qq" -> createQqQr()
            "qqmusic" -> createMusicAppQr()
            "wechat" -> createWeChatQr()
            else -> throw ApiException(ErrorKind.Parse, userMessage = "未知的 QQ 音乐登录方式")
        }
        return wrapped(buildJsonObject { put("key", key); put("unikey", key) })
    }

    private fun qrImage(key: String): JsonObject {
        val session = sessions[key] ?: throw ApiException(ErrorKind.NotFound, userMessage = "二维码已过期，请重新生成")
        if (session.channel == "qqmusic" && session.subscription == null) {
            val subscription = QqMusicQrSubscription(session.secret)
            session.subscription = subscription
            try {
                subscription.connect()
            } catch (failure: Exception) {
                sessions.remove(key, session)
                subscription.close()
                throw failure
            }
        }
        val encoded = Base64.getEncoder().encodeToString(session.image)
        return wrapped(buildJsonObject { put("qrimg", "data:${session.mime};base64,$encoded") })
    }

    private fun qrCheck(key: String): JsonObject {
        val session = sessions[key] ?: return qrStatus(800)
        return when (session.channel) {
            "qqmusic" -> checkMusicAppQr(key, session)
            "wechat" -> checkWeChatQr(key, session)
            else -> checkQqQr(key, session)
        }
    }

    private fun createMusicAppQr(): QrSession {
        val data = cgi("music.login.LoginServer", "CreateQRCode", buildJsonObject {
            put("tmeAppID", "qqmusic")
            put("ct", 11)
            put("cv", APP_VERSION)
            put("v", APP_VERSION)
        }, null, extraComm = buildJsonObject {
            put("ct", 23)
            put("cv", 0)
        }, client = http)
        val qrId = data.firstText("qrcodeID")
        if (qrId.isBlank() || qrId.any { it in "/+#" }) {
            throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐扫码响应缺少有效二维码标识")
        }
        val image = runCatching {
            Base64.getDecoder().decode(data.firstText("qrcode").substringAfter(','))
        }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐扫码响应缺少二维码图片")
        return QrSession("qqmusic", qrId, image, "image/png", MemoryCookieJar(), http)
    }

    private fun checkMusicAppQr(key: String, session: QrSession): JsonObject {
        val subscription = session.subscription ?: return qrStatus(801)
        val event = subscription.event
        if (event == QqMusicQrSubscription.Event.Waiting) return qrStatus(801)
        if (event == QqMusicQrSubscription.Event.Scanned) return qrStatus(802)
        if (!sessions.remove(key, session)) return qrStatus(800)
        subscription.close()
        return when (event) {
            QqMusicQrSubscription.Event.Expired -> qrStatus(800)
            QqMusicQrSubscription.Event.Cancelled -> qrStatus(804)
            is QqMusicQrSubscription.Event.Failed -> throw ApiException(event.kind, userMessage = event.message)
            is QqMusicQrSubscription.Event.Authorized -> finishLogin(
                cgi("music.login.LoginServer", "Login", buildJsonObject {
                    put("musicid", event.musicId)
                    put("qrCodeID", session.secret)
                    put("token", event.token)
                }, null, extraComm = buildJsonObject { put("tmeLoginType", 6) }, client = http),
            )
            else -> error("Expected a terminal QR event")
        }
    }

    private fun createQqQr(): QrSession {
        val jar = MemoryCookieJar()
        val client = http.newBuilder().cookieJar(jar).build()
        val url = "https://ssl.ptlogin2.qq.com/ptqrshow".toHttpUrl().newBuilder()
            .addQueryParameter("appid", "716027609")
            .addQueryParameter("e", "2")
            .addQueryParameter("l", "M")
            .addQueryParameter("s", "3")
            .addQueryParameter("d", "72")
            .addQueryParameter("v", "4")
            .addQueryParameter("t", Random.nextDouble().toString())
            .addQueryParameter("daid", "383")
            .addQueryParameter("pt_3rd_aid", "100497308")
            .build()
        val response = execute(client, Request.Builder().url(url)
            .header("Referer", "https://xui.ptlogin2.qq.com/")
            .header("User-Agent", USER_AGENT)
            .get().build())
        if (response.code !in 200..299) throw ApiException(ErrorKind.Server, response.code, "获取 QQ 登录二维码失败")
        val secret = jar.valueFor("qrsig")
            ?: response.headers("Set-Cookie").joinToString(";").let { cookieHeader ->
                Regex("""(?:^|;\s*)qrsig=([^;]+)""").find(cookieHeader)?.groupValues?.get(1).orEmpty()
            }
        if (secret.isBlank()) throw ApiException(ErrorKind.Parse, userMessage = "QQ 登录二维码缺少 qrsig")
        val bytes = response.body?.bytes() ?: throw ApiException(ErrorKind.Parse)
        return QrSession("qq", secret, bytes, "image/png", jar, client)
    }

    private fun createWeChatQr(): QrSession {
        val jar = MemoryCookieJar()
        val client = http.newBuilder().cookieJar(jar).build()
        val connect = "https://open.weixin.qq.com/connect/qrconnect".toHttpUrl().newBuilder()
            .addQueryParameter("appid", "wx48db31d50e334801")
            .addQueryParameter("redirect_uri",
                "https://y.qq.com/portal/wx_redirect.html?login_type=2&surl=https://y.qq.com/")
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", "snsapi_login")
            .addQueryParameter("state", "STATE")
            .addQueryParameter("href",
                "https://y.qq.com/mediastyle/music_v17/src/css/popup_wechat.css#wechat_redirect")
            .build()
        val page = execute(client, Request.Builder().url(connect)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", USER_AGENT)
            .get().build())
        val text = page.body?.string().orEmpty()
        val uuid = WX_UUID.find(text)?.groupValues?.get(1)
            ?: throw ApiException(ErrorKind.Parse, userMessage = "微信登录二维码缺少 uuid")
        val image = execute(client, Request.Builder()
            .url("https://open.weixin.qq.com/connect/qrcode/$uuid")
            .header("Referer", "https://open.weixin.qq.com/connect/qrconnect")
            .header("User-Agent", USER_AGENT)
            .get().build())
        val bytes = image.body?.bytes() ?: throw ApiException(ErrorKind.Parse)
        return QrSession("wechat", uuid, bytes, "image/jpeg", jar, client)
    }

    private fun checkQqQr(key: String, session: QrSession): JsonObject {
        val url = "https://ssl.ptlogin2.qq.com/ptqrlogin".toHttpUrl().newBuilder()
            .addQueryParameter("u1", "https://graph.qq.com/oauth2.0/login_jump")
            .addQueryParameter("ptqrtoken", hash33(session.secret).toString())
            .addQueryParameter("ptredirect", "0")
            .addQueryParameter("h", "1")
            .addQueryParameter("t", "1")
            .addQueryParameter("g", "1")
            .addQueryParameter("from_ui", "1")
            .addQueryParameter("ptlang", "2052")
            .addQueryParameter("action", "0-0-${System.currentTimeMillis()}")
            .addQueryParameter("js_ver", "20102616")
            .addQueryParameter("js_type", "1")
            .addQueryParameter("pt_uistyle", "40")
            .addQueryParameter("aid", "716027609")
            .addQueryParameter("daid", "383")
            .addQueryParameter("pt_3rd_aid", "100497308")
            .addQueryParameter("has_onekey", "1")
            .build()
        val response = execute(http, Request.Builder().url(url)
            .header("Referer", "https://xui.ptlogin2.qq.com/")
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "qrsig=${session.secret}")
            .get().build())
        val text = response.use {
            if (!it.isSuccessful) throw ApiException(ErrorKind.Server, it.code, "查询 QQ 扫码状态失败")
            it.body?.string().orEmpty()
        }
        val match = PTUI.find(text) ?: throw ApiException(ErrorKind.Parse, userMessage = "无法解析 QQ 扫码状态")
        val args = QUOTED.findAll(match.groupValues[1]).map { it.groupValues[1] }.toList()
        val qqCode = args.firstOrNull()?.toIntOrNull() ?: throw ApiException(ErrorKind.Parse)
        when (qqCode) {
            66 -> return qrStatus(801)
            67 -> return qrStatus(802)
            65, 68 -> {
                sessions.remove(key, session)
                return qrStatus(if (qqCode == 65) 800 else 804)
            }
            0 -> if (!sessions.remove(key, session)) return qrStatus(800)
            else -> throw ApiException(ErrorKind.Unauthorized, qqCode, "QQ 扫码授权失败（$qqCode）")
        }
        val callback = args.getOrNull(2)?.toHttpUrlOrNull()
            ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 扫码结果缺少授权回调")
        val uin = callback.queryParameter("uin")?.takeIf { it.isNotBlank() }
            ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 扫码结果缺少 uin")
        val sigx = callback.queryParameter("ptsigx")?.takeIf { it.isNotBlank() }
            ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 扫码结果缺少 ptsigx")
        return authorizeQq(session, uin, sigx)
    }

    private fun authorizeQq(session: QrSession, uin: String, sigx: String): JsonObject {
        val check = "https://ssl.ptlogin2.graph.qq.com/check_sig".toHttpUrl().newBuilder()
            .addQueryParameter("uin", uin)
            .addQueryParameter("pttype", "1")
            .addQueryParameter("service", "ptqrlogin")
            .addQueryParameter("nodirect", "0")
            .addQueryParameter("ptsigx", sigx)
            .addQueryParameter("s_url", "https://graph.qq.com/oauth2.0/login_jump")
            .addQueryParameter("ptlang", "2052")
            .addQueryParameter("ptredirect", "100")
            .addQueryParameter("aid", "716027609")
            .addQueryParameter("daid", "383")
            .addQueryParameter("j_later", "0")
            .addQueryParameter("low_login_hour", "0")
            .addQueryParameter("regmaster", "0")
            .addQueryParameter("pt_login_type", "3")
            .addQueryParameter("pt_aid", "0")
            .addQueryParameter("pt_aaid", "16")
            .addQueryParameter("pt_light", "0")
            .addQueryParameter("pt_3rd_aid", "100497308")
            .build()
        execute(session.client, Request.Builder().url(check)
            .header("Referer", "https://xui.ptlogin2.qq.com/")
            .header("User-Agent", USER_AGENT)
            .get().build()).use {
                if (!it.isSuccessful && !it.isRedirect)
                    throw ApiException(ErrorKind.Unauthorized, it.code, "QQ 登录授权校验失败")
            }
        val authorizeUrl = QQ_OAUTH_URL.toHttpUrl()
        val pSkey = session.cookieJar.loadForRequest(authorizeUrl).firstOrNull { it.name == "p_skey" }?.value
            ?: throw ApiException(ErrorKind.Unauthorized, userMessage = "QQ 授权缺少 p_skey")
        val state = UUID.randomUUID().toString()
        val form = FormBody.Builder()
            .add("response_type", "code")
            .add("client_id", "100497308")
            .add("redirect_uri", QQ_OAUTH_REDIRECT)
            .add("scope", "get_user_info,get_app_friends")
            .add("state", state)
            .add("switch", "")
            .add("from_ptlogin", "1")
            .add("src", "1")
            .add("update_auth", "1")
            .add("openapi", "1010_1030")
            .add("g_tk", hash33(pSkey, 5381).toString())
            .add("auth_time", System.currentTimeMillis().toString())
            .add("ui", UUID.randomUUID().toString())
            .build()
        val redirect = execute(session.client, Request.Builder().url(authorizeUrl)
            .header("Origin", "https://graph.qq.com")
            .header("Referer", "https://graph.qq.com/")
            .header("User-Agent", USER_AGENT)
            .post(form)
            .build()).use { response ->
                response.header("Location")?.takeIf { response.isRedirect }?.let(authorizeUrl::resolve)
            }
        val code = redirect?.takeIf {
            it.isHttps && it.host == "y.qq.com" && it.encodedPath == "/portal/wx_redirect.html" &&
                it.queryParameter("state") == state
        }?.queryParameter("code")?.takeIf { it.isNotBlank() }
            ?: throw ApiException(ErrorKind.Unauthorized, userMessage = "QQ OAuth 未返回授权码")
        val data = cgi(
            "QQConnectLogin.LoginServer",
            "QQLogin",
            buildJsonObject { put("code", code) },
            null,
            extraComm = buildJsonObject { put("tmeLoginType", 2) },
            client = http,
        )
        return finishLogin(data)
    }

    private fun checkWeChatQr(key: String, session: QrSession): JsonObject {
        val url = "https://lp.open.weixin.qq.com/connect/l/qrconnect".toHttpUrl().newBuilder()
            .addQueryParameter("uuid", session.secret)
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        val response = execute(session.client, Request.Builder().url(url)
            .header("Referer", "https://open.weixin.qq.com/")
            .header("User-Agent", USER_AGENT)
            .get().build())
        val text = response.body?.string().orEmpty()
        val match = WX_STATUS.find(text) ?: return qrStatus(801)
        val wxCode = match.groupValues[1].toIntOrNull() ?: 0
        if (wxCode != 405) return qrStatus(when (wxCode) {
            408 -> 800
            404 -> 802
            else -> 801
        })
        val code = match.groupValues[2]
        sessions.remove(key)
        val data = cgi(
            "music.login.LoginServer",
            "Login",
            buildJsonObject {
                put("code", code)
                put("strAppid", "wx48db31d50e334801")
            },
            null,
            extraComm = buildJsonObject { put("tmeLoginType", 1) },
        )
        return finishLogin(data)
    }

    private fun finishLogin(data: JsonObject): JsonObject {
        val credential = DirectCredential.fromJson(data)
            ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐登录结果缺少账号凭证")
        val profile = (data["profile"] as? JsonObject)?.let(::normalizeProfile)
            ?: credential.profile()
        return buildJsonObject {
            put("code", 803)
            put("data", buildJsonObject {
                put("code", 803)
                put("session", credential.encode())
                put("profile", profile)
            })
        }
    }

    private fun loginStatus(credential: DirectCredential): JsonObject {
        val data = runCatching {
            cgi("music.UserInfo.userInfoServer", "GetLoginUserInfo", buildJsonObject {}, credential)
        }.getOrElse {
            if (it is ApiException && it.kind == ErrorKind.Unauthorized) throw it
            JsonObject(emptyMap())
        }
        val profile = normalizeProfile((data["profile"] as? JsonObject) ?: data)
        return wrapped(buildJsonObject { put("profile", normalizeProfile(profile + credential.profile())) })
    }

    private fun userDetail(credential: DirectCredential): JsonObject {
        val data = cgi("music.UserInfo.userInfoServer", "GetLoginUserInfo", buildJsonObject {}, credential)
        return wrapped(normalizeProfile((data["profile"] as? JsonObject) ?: data) + credential.profile())
    }

    private fun refresh(credential: DirectCredential): JsonObject {
        val param = buildJsonObject {
            put("openid", credential.openId)
            put("access_token", credential.accessToken)
            put("refresh_token", credential.refreshToken)
            put("expired_in", credential.expiredAt)
            put("musicid", credential.musicId)
            put("musickey", credential.musicKey)
            put("refresh_key", credential.refreshKey)
            put("unionid", credential.unionId)
            put("str_musicid", credential.stringMusicId)
            put("loginMode", 2)
        }
        val data = cgi("music.login.LoginServer", "Login", param, credential,
            extraComm = buildJsonObject { put("tmeLoginType", credential.loginType) })
        val refreshed = DirectCredential.fromJson(data) ?: credential
        return wrapped(buildJsonObject {
            put("token", refreshed.encode())
            put("session", refreshed.encode())
        })
    }

    private suspend fun search(query: Map<String, String>): JsonObject {
        val keyword = query["keywords"].orEmpty().trim()
        if (keyword.isBlank()) throw IllegalArgumentException("QQ 音乐搜索词不能为空")
        val type = query["type"] ?: "song"
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 60) ?: 30
        val param = buildJsonObject {
            put("searchid", searchId())
            put("search_type", 100)
            put("page_num", limit)
            put("query", keyword)
            put("page_id", offset / limit + 1)
            put("highlight", true)
            put("grp", true)
        }
        var data: JsonObject? = null
        var attempt = 0
        while (data == null) {
            try {
                data = cgi("music.adaptor.SearchAdaptor", "do_search_v2", param, null)
            } catch (failure: ApiException) {
                if (failure.status == 1) {
                    data = liteSearch(keyword, type, offset, limit)
                    break
                }
                if (attempt >= 4 || failure.status !in setOf(2001, 500001, 502, 503, 504)) throw failure
                delay(if (attempt == 0) 300 else 600)
                attempt++
            }
        }
        val responseData = requireNotNull(data)
        val body = responseData["body"] as? JsonObject ?: JsonObject(emptyMap())
        val songs = body["item_song"].groupItems("items")
        val artists = body["singer"].groupItems("items")
        val albums = body["item_album"].groupItems("items")
        val playlists = body["item_songlist"].groupItems("items")
        val totalSources = mapOf(
            "song" to body["item_song"],
            "artist" to body["singer"],
            "album" to body["item_album"],
            "playlist" to body["item_songlist"],
        )
        fun total(key: String): Long = (totalSources[key] as? JsonObject)
            ?.firstLong("total_num", "estimate_sum") ?: 0
        val selected = when (type) { "song" -> songs; "artist" -> artists; "album" -> albums; else -> playlists }
        return wrapped(buildJsonObject {
            put("songs", JsonArray(if (type == "song") selected else emptyList()))
            put("artists", JsonArray(if (type == "artist") selected else emptyList()))
            put("albums", JsonArray(if (type == "album") selected else emptyList()))
            put("playlists", JsonArray(if (type == "playlist") selected else emptyList()))
            put("totals", buildJsonObject {
                put("song", total("song")); put("artist", total("artist"))
                put("album", total("album")); put("playlist", total("playlist"))
            })
            put("hasMore", buildJsonObject {
                put("song", offset + songs.size < total("song"))
                put("artist", offset + artists.size < total("artist"))
                put("album", offset + albums.size < total("album"))
                put("playlist", offset + playlists.size < total("playlist"))
            })
        })
    }

    private fun liteSearch(keyword: String, type: String, offset: Int, limit: Int): JsonObject {
        val searchType = when (type) {
            "artist" -> 1
            "album" -> 2
            "playlist" -> 3
            else -> 7
        }
        val response = cgiLite("music.search.SearchCgiService", "DoSearchForQQMusicLite", buildJsonObject {
            put("search_id", searchId())
            put("remoteplace", "search.android.keyboard")
            put("query", keyword)
            put("search_type", searchType)
            put("num_per_page", limit)
            put("page_num", offset / limit + 1)
            put("highlight", 0)
            put("nqc_flag", 0)
            put("page_id", offset / limit + 1)
            put("grp", 1)
        })
        val responseBody = response["body"] as? JsonObject ?: JsonObject(emptyMap())
        val sourceKey = when (type) { "song" -> "item_song"; "artist" -> "singer"; "album" -> "item_album"; else -> "item_songlist" }
        val rows = responseBody.array(sourceKey)
        val total = (response["meta"] as? JsonObject)?.firstLong("sum") ?: rows.size.toLong()
        val group = buildJsonObject {
            put("items", JsonArray(rows))
            put("total_num", total)
        }
        return buildJsonObject {
            put("body", buildJsonObject {
                put("item_song", if (type == "song") group else JsonObject(emptyMap()))
                put("singer", if (type == "artist") group else JsonObject(emptyMap()))
                put("item_album", if (type == "album") group else JsonObject(emptyMap()))
                put("item_songlist", if (type == "playlist") group else JsonObject(emptyMap()))
            })
            put("meta", response["meta"] ?: JsonObject(emptyMap()))
        }
    }

    private fun suggestions(query: Map<String, String>): JsonObject {
        val keyword = query["keyword"].orEmpty().trim()
        val data = cgi("music.smartboxCgi.SmartBoxCgi", "GetSmartBoxResult", buildJsonObject {
            put("search_id", searchId())
            put("query", keyword)
            put("num_per_page", 0)
            put("page_idx", 0)
        }, null)
        val items = data.array("items").mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            if (row.firstLong("type") != 0L) return@mapNotNull null
            row.firstText("hint", "query", "title").takeIf { it.isNotBlank() }
        }.distinct()
        return wrapped(buildJsonObject { put("suggestions", JsonArray(items.map(::JsonPrimitive))) })
    }

    private fun hotSearch(): JsonObject {
        val data = cgi("music.musicsearch.HotkeyService", "GetHotkeyForQQMusicMobile",
            buildJsonObject { put("search_id", searchId()) }, null)
        val rows = data.array("vec_hotkey").mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            val keyword = row.firstText("query", "title")
            keyword.takeIf(String::isNotBlank)?.let { buildJsonObject { put("keyword", it) } }
        }
        return wrapped(buildJsonObject { put("hotkeys", JsonArray(rows)) })
    }

    private fun songDetail(query: Map<String, String>): JsonObject {
        val ids = query["ids"].orEmpty().split(',').map(String::trim).filter { it.matches(Regex("[A-Za-z0-9]{1,64}")) }
        if (ids.isEmpty()) throw ApiException(ErrorKind.NotFound)
        val data = cgi("music.trackInfo.UniformRuleCtrl", "CgiGetTrackInfo", buildJsonObject {
            put("ctx", 0)
            put("client", 1)
            put("mids", JsonArray(ids.map(::JsonPrimitive)))
            put("types", JsonArray(ids.map { JsonPrimitive(0) }))
            put("modify_stamp", JsonArray(ids.map { JsonPrimitive(0) }))
        }, null)
        val tracks = data.array("tracks", "track_info", "songs", "songlist", "list")
        return wrapped(buildJsonObject { put("songs", JsonArray(tracks)) })
    }

    private fun songUrl(query: Map<String, String>, credential: DirectCredential?): JsonObject {
        val mid = query["id"].orEmpty()
        if (!mid.matches(Regex("[A-Za-z0-9]{1,64}"))) throw ApiException(ErrorKind.NotFound)
        val mediaMid = query["mediaMid"].orEmpty().takeIf { it.matches(Regex("[A-Za-z0-9]{1,64}")) } ?: mid
        val requested = query["level"].orEmpty().ifBlank { "standard" }
        val candidates = QqMusicStreamQuality.candidates(requested, supportsDolby = true, supportsDtsX = true)
        var lastResult = 0L
        for (candidate in candidates) {
            val filename = candidate.filename(mid, mediaMid)
            val data = cgi("music.vkey.GetVkey", "UrlGetVkey", buildJsonObject {
                put("uin", credential?.stringMusicId ?: "0")
                put("filename", buildJsonArray { add(JsonPrimitive(filename)) })
                put("guid", randomGuid())
                put("songmid", buildJsonArray { add(JsonPrimitive(mid)) })
                put("songtype", buildJsonArray { add(JsonPrimitive(0)) })
                put("ctx", 0)
            }, credential, extraComm = buildJsonObject {
                put("cv", 14_090_008)
                put("v", 14_090_008)
                put("chid", "10003505")
                put("QIMEI", "")
                put("OpenUDID", DEFAULT_GUID)
                put("udid", DEFAULT_GUID)
                put("OpenUDID2", DEFAULT_GUID)
                put("aid", DEFAULT_GUID.substring(0, 16))
                put("os_ver", "15")
                put("phonetype", "MI 6")
                put("devicelevel", "29")
                put("newdevicelevel", "29")
                put("rom", "xiaomi/iarim/sagit:10/eomam.200122.001/7654321:user/release-keys")
            }, userAgent = "QQMusic 14090008(android 15)")
            val row = data.array("midurlinfo").firstOrNull() as? JsonObject ?: continue
            lastResult = row.firstLong("result")
            val purl = row.firstText("purl", "wifiurl", "flowurl")
            if (purl.isBlank()) continue
            val url = if (purl.startsWith("http")) purl else STREAM_ORIGIN + purl.trimStart('/')
            return wrapped(buildJsonObject {
                put("url", url)
                put("level", candidate.level)
                put("requestedLevel", requested)
                put("type", candidate.format)
                put("expi", (data.firstLong("expiration").takeIf { it > 0 } ?: 120) / 2)
                put("size", payloadSize(candidate.prefix))
                put("br", candidate.nominalBitrate.takeIf { it > 0 } ?: payloadBitrate(candidate.prefix))
            })
        }
        throw ApiException(
            ErrorKind.AudioUnavailable,
            lastResult.toInt(),
            "QQ 音乐未提供可播放音源，请检查登录状态、会员权限或地区限制",
        )
    }

    private fun lyric(query: Map<String, String>): JsonObject {
        val mid = query["id"].orEmpty()
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg".toHttpUrl().newBuilder()
            .addQueryParameter("songmid", mid)
            .addQueryParameter("g_tk", "5381")
            .addQueryParameter("loginUin", "0")
            .addQueryParameter("hostUin", "0")
            .addQueryParameter("format", "json")
            .addQueryParameter("inCharset", "utf8")
            .addQueryParameter("outCharset", "utf-8")
            .addQueryParameter("platform", "yqq")
            .build()
        val response = execute(http, Request.Builder().url(url)
            .header("Referer", "https://y.qq.com/portal/player.html")
            .header("User-Agent", USER_AGENT)
            .get().build())
        val root = parseJson(response.body?.string().orEmpty())
        fun decoded(key: String): String {
            val text = root.firstText(key)
            if (text.isBlank()) return ""
            return runCatching { String(Base64.getDecoder().decode(text), Charsets.UTF_8) }.getOrDefault(text)
        }
        return buildJsonObject {
            put("lrc", decoded("lyric"))
            put("tlyric", decoded("trans"))
            put("romalrc", decoded("roma"))
            put("source", "QQ 音乐直连")
        }
    }

    private fun playlist(query: Map<String, String>, rawFull: Boolean = false,
        credential: DirectCredential? = null, aiDissInfo: Boolean = false,
        mobileAi: Boolean = false, optType: Int = 1,
        compactAuthComm: Boolean = true): JsonObject {
        val id = query["id"].orEmpty()
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) throw ApiException(ErrorKind.NotFound)
        val numeric = id.toLongOrNull()
        val requestedDirectory = ownedPlaylistDirectories[id]?.toLongOrNull()
        val authComm = credential?.takeIf { compactAuthComm }?.let {
            buildJsonObject {
                put("uin", it.stringMusicId.ifBlank { it.musicId.toString() })
                put("ct", 19)
                put("cv", 0)
                put("format", "json")
                put("authst", it.musicKey)
            }
        } ?: JsonObject(emptyMap())
        val dailyRequest = query["daily"] == "1"
        val playlistNumeric = query["dirid"]?.toLongOrNull() ?: requestedDirectory ?: numeric
        val rows = mutableListOf<JsonElement>()
        var info: JsonObject? = null
        var total = 0L
        for (currentPage in 1..100) {
            val data = cgi(
                if (aiDissInfo || mobileAi) "music.srfDissInfo.aiDissInfo" else "music.srfDissInfo.DissInfo",
                if (mobileAi) "mobile_get_aiDissinfo" else if (aiDissInfo) "get_aiDissinfo" else "CgiGetDiss",
                buildJsonObject {
                    if (credential != null || aiDissInfo || mobileAi || dailyRequest) put("new_format", 1)
                    if (playlistNumeric != null) put("disstid", JsonPrimitive(playlistNumeric))
                    else put("disstid", id)
                    put("dirid", JsonPrimitive(query["dirid"]?.toLongOrNull() ?: requestedDirectory ?: 0L))
                    put("tag", true)
                    put("song_begin", (currentPage - 1) * 100)
                    put("song_num", 100)
                    put("userinfo", true)
                    put("orderlist", true)
                    put("onlysonglist", false)
                    (query["encryptUin"] ?: credential?.encryptUin?.takeIf(String::isNotBlank)
                        ?: credential?.stringMusicId)?.takeIf(String::isNotBlank)
                        ?.let { put("enc_host_uin", it) }
                    if (credential != null || aiDissInfo || mobileAi || dailyRequest) {
                        put("optype", optType)
                        put("need_game_ad", if (playlistNumeric == 201L) 0 else 1)
                        put("guid", randomGuid())
                        put("update_rtime", 0)
                        put("is_mobile", 1)
                        put("censor_status", 1)
                        put("local_time", System.currentTimeMillis() / 1_000)
                        put("rec_flag", 1)
                        put("dsb", 1)
                    }
                },
                credential,
                commOverride = authComm.takeIf { credential != null },
            )
            info = info ?: (data["dirinfo"] as? JsonObject)
            val batch = data.array("songlist")
            rows.addAll(batch)
            total = data.firstLong("total_song_num", "songlist_size").takeIf { it > 0 } ?: rows.size.toLong()
            if (!data.flag("hasmore") || batch.isEmpty() || rows.size >= total) break
        }
        val dirinfo = info ?: buildJsonObject { put("id", id) }
        val playlist = buildJsonObject {
            dirinfo.forEach { (key, value) -> put(key, value) }
            put("id", dirinfo.firstText("id").ifBlank { id })
            put("tid", dirinfo.firstText("id").ifBlank { id })
            put("dissid", dirinfo.firstText("id").ifBlank { id })
            put("name", dirinfo.firstText("title", "name"))
            put("cover", dirinfo.firstText("picurl"))
            put("count", total)
            put("songnum", total)
            put("songs", JsonArray(rows))
            put("songlist", JsonArray(rows))
            put("more", rows.size < total)
            put("hasmore", rows.size < total)
        }
        return if (rawFull) wrapped(buildJsonObject {
            put("response", buildJsonObject {
                put("code", 0)
                put("cdlist", buildJsonArray { add(playlist) })
            })
        }) else wrapped(playlist)
    }

    private fun userPlaylists(credential: DirectCredential): JsonObject {
        if (credential.encryptUin.isBlank())
            throw ApiException(ErrorKind.Unauthorized, userMessage = "QQ 音乐凭证缺少加密账号标识，请重新登录")
        val created = cgi("music.musicasset.PlaylistBaseRead", "GetPlaylistByUin",
            buildJsonObject { put("uin", credential.stringMusicId) }, credential)
        val favorites = cgi("music.musicasset.PlaylistFavRead", "CgiGetPlaylistFavInfo",
            buildJsonObject { put("uin", credential.encryptUin); put("offset", 0); put("size", 200) }, credential)
        val rows = mutableListOf<JsonElement>()
        created.array("v_playlist", "playlist", "playlists").forEach { value ->
            val row = value as? JsonObject ?: return@forEach
            val tid = row.identifier("tid", "dirId", "id")
            val directory = row.identifier("dirId", "dirid")
            if (tid.isNotBlank() && directory.isNotBlank()) ownedPlaylistDirectories[tid] = directory
            rows += buildJsonObject {
                row.forEach { (key, child) -> put(key, child) }
                put("id", tid)
                put("tid", tid)
                put("dirId", directory)
                put("name", row.firstText("dirName", "title", "name"))
                put("cover", row.firstText("picUrl", "cover", "logo"))
                put("songnum", row.firstLong("songNum", "songnum", "count"))
                put("owned", true)
            }
        }
        favorites.array("playlists", "playlist", "v_playlist").forEach { value ->
            val row = value as? JsonObject ?: return@forEach
            rows += buildJsonObject {
                row.forEach { (key, child) -> put(key, child) }
                put("id", row.identifier("dissid", "tid", "id"))
                put("tid", row.identifier("dissid", "tid", "id"))
                put("name", row.firstText("dissname", "title", "name"))
                put("cover", row.firstText("logo", "picurl", "cover"))
                put("songnum", row.firstLong("songnum", "songNum", "count"))
                put("owned", false)
            }
        }
        return wrapped(buildJsonObject {
            put("playlist", JsonArray(rows.distinctBy { (it as JsonObject).firstText("id") }))
            put("more", false)
        })
    }

    private fun likedSongs(credential: DirectCredential, query: Map<String, String>): JsonObject {
        if (credential.encryptUin.isBlank())
            throw ApiException(ErrorKind.Unauthorized, userMessage = "QQ 音乐凭证缺少加密账号标识，请重新登录")
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 100
        val data = cgi("music.srfDissInfo.DissInfo", "CgiGetDiss", buildJsonObject {
            put("disstid", 0)
            put("dirid", 201)
            put("tag", true)
            put("song_begin", offset)
            put("song_num", limit)
            put("userinfo", true)
            put("orderlist", true)
            put("onlysonglist", true)
            put("enc_host_uin", credential.encryptUin)
        }, credential)
        val songs = data.array("songlist")
        val total = data.firstLong("total_song_num", "songlist_size")
        return wrapped(buildJsonObject {
            put("songs", JsonArray(songs))
            put("total", total)
            put("more", data.flag("hasmore") || offset + songs.size < total)
        })
    }

    private fun subscriptionStatus(credential: DirectCredential, query: Map<String, String>): JsonObject {
        val type = query["type"].orEmpty()
        val ids = query["ids"].orEmpty().split(',').filter(String::isNotBlank)
        if (type == "song") {
            val liked = songFavoriteStatuses(credential, ids)
            val items = ids.map { id ->
                buildJsonObject {
                    put("id", id)
                    val state = liked[id]
                    put("status", when (state) {
                        true -> "subscribed"
                        false -> "not-subscribed"
                        null -> "unknown"
                    })
                }
            }
            return wrapped(buildJsonObject { put("items", JsonArray(items)) })
        }
        if (type == "album") {
            val favorites = favoriteAlbums(credential, mapOf("offset" to "0", "limit" to "500"))
            val data = favorites["data"] as? JsonObject ?: JsonObject(emptyMap())
            val subscribed = data.array("albums").mapNotNull { value ->
                val row = value as? JsonObject ?: return@mapNotNull null
                row.identifier("albummid", "albumMid", "mid", "id")
            }.toSet()
            return wrapped(buildJsonObject {
                put("items", buildJsonArray {
                    ids.forEach { id ->
                        add(buildJsonObject {
                            put("id", id)
                            put("status", if (id in subscribed) "subscribed" else "not-subscribed")
                        })
                    }
                })
            })
        }
        if (type == "playlist") {
            val owned = userPlaylists(credential)
            val data = owned["data"] as? JsonObject ?: JsonObject(emptyMap())
            val subscribed = data.array("playlist").mapNotNull { value ->
                (value as? JsonObject)?.identifier("tid", "dissid", "id")
            }.toSet()
            return wrapped(buildJsonObject {
                put("items", buildJsonArray {
                    ids.forEach { id ->
                        add(buildJsonObject {
                            put("id", id)
                            put("status", if (id in subscribed) "subscribed" else "not-subscribed")
                        })
                    }
                })
            })
        }
        val items = ids.map { id ->
            buildJsonObject {
                put("id", id)
                put("status", "unknown")
            }
        }
        return wrapped(buildJsonObject { put("items", JsonArray(items)) })
    }

    private fun songFavoriteStatuses(credential: DirectCredential, mids: List<String>): Map<String, Boolean?> {
        val unique = mids.filter { it.matches(Regex("[A-Za-z0-9]{1,64}")) }.distinct()
        if (unique.isEmpty()) return emptyMap()
        val data = cgi("music.musicasset.SongFavRead", "IsSongFanByMid", buildJsonObject {
            put("v_songMid", JsonArray(unique.map(::JsonPrimitive)))
        }, credential)
        val fans = data["m_fan"] as? JsonObject ?: return emptyMap()
        return unique.associateWith { mid ->
            (fans[mid] as? JsonPrimitive)?.booleanOrNull
        }
    }

    private fun allLikedMids(credential: DirectCredential): Set<String> {
        val rows = mutableListOf<JsonElement>()
        var offset = 0
        while (offset < 100_000) {
            val data = cgi("music.srfDissInfo.DissInfo", "CgiGetDiss", buildJsonObject {
                put("disstid", 0); put("dirid", 201); put("tag", true)
                put("song_begin", offset); put("song_num", 100)
                put("userinfo", true); put("orderlist", true); put("onlysonglist", true)
                put("enc_host_uin", credential.encryptUin)
            }, credential)
            val batch = data.array("songlist")
            rows.addAll(batch)
            val total = data.firstLong("total_song_num", "songlist_size")
            if (!data.flag("hasmore") || batch.isEmpty() || rows.size >= total) break
            offset += batch.size
        }
        return rows.mapNotNull { (it as? JsonObject)?.firstText("mid")?.takeIf(String::isNotBlank) }.toSet()
    }

    private fun changeSongLike(credential: DirectCredential, body: JsonObject): JsonObject {
        val song = body["song"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        val id = song.firstLong("id")
        val type = song.firstLong("type").toInt()
        val liked = body.flag("liked")
        val mid = song.firstText("mid")
        val current = songFavoriteStatuses(credential, listOf(mid))[mid]
        if (current == null)
            throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未返回完整收藏状态，请重试")
        if (current == liked) return wrapped(buildJsonObject { put("liked", liked) })
        val method = if (liked) "AddSonglist" else "DelSonglist"
        val data = cgiSigned("music.musicasset.PlaylistDetailWrite", method, buildJsonObject {
            put("dirId", 201)
            put("tid", 0)
            put("bFmtUtf8", true)
            put("v_songInfo", buildJsonArray {
                add(buildJsonObject { put("songId", id); put("songType", type) })
            })
        }, credential)
        val success = data.firstLong("retCode").toInt().let { it == 0 || it == 80092 }
        if (!success) throw ApiException(ErrorKind.Server, data.firstLong("retCode").toInt(), "QQ 音乐未确认收藏变更")
        val stillLiked = songFavoriteStatuses(credential, listOf(mid))[mid]
            ?: throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未返回收藏变更结果")
        if (stillLiked != liked) throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐收藏状态未同步，请刷新后重试")
        return wrapped(buildJsonObject { put("liked", liked) })
    }

    private fun createPlaylist(credential: DirectCredential, body: JsonObject): JsonObject {
        val name = body.firstText("name").trim()
        if (name.isBlank()) throw IllegalArgumentException("歌单名称不能为空")
        val data = cgi("music.musicasset.PlaylistBaseWrite", "AddPlaylist",
            buildJsonObject { put("dirName", name) }, credential)
        val id = data.identifier("tid", "dirId", "dirid", "id")
        return wrapped(buildJsonObject { put("tid", id); put("verified", id.isNotBlank()) })
    }

    private fun deletePlaylist(credential: DirectCredential, body: JsonObject): JsonObject {
        val id = body.firstLong("tid")
        val data = cgi("music.musicasset.PlaylistBaseWrite", "DelPlaylist",
            buildJsonObject { put("dirId", id) }, credential)
        return wrapped(buildJsonObject { put("verified", data.firstLong("code", "retCode") == 0L) })
    }

    private fun renamePlaylist(credential: DirectCredential, body: JsonObject): JsonObject {
        val tid = body.firstLong("tid").takeIf { it > 0 } ?: throw ApiException(ErrorKind.NotFound)
        val name = body.firstText("name").trim()
        if (name.isBlank() || name.codePointCount(0, name.length) > 20) throw IllegalArgumentException("歌单名称无效")
        var directory = ownedPlaylistDirectories[tid.toString()]
        if (directory.isNullOrBlank()) {
            userPlaylists(credential)
            directory = ownedPlaylistDirectories[tid.toString()]
        }
        val dirId = directory?.toLongOrNull()?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.NotFound,
            userMessage = "找不到 QQ 音乐歌单目录，请刷新后重试")
        fun read(): JsonObject {
            val data = cgi("music.srfDissInfo.aiDissInfo", "uniform_get_Dissinfo", buildJsonObject {
                put("dirid", dirId)
                put("optype", 3)
                put("tag", 1)
                put("userinfo", 0)
                put("pic_dpi", 300)
            }, credential)
            return (data["dirinfo"] as? JsonObject) ?: data
        }
        val before = read()
        val tagIds = before.array("tag").mapNotNull {
            (it as? JsonObject)?.identifier("id")?.takeIf(String::isNotBlank)
        }
        val dirShow = before.firstLong("dir_show").toInt().takeIf { it in 1..2 } ?: 1
        cgi("music.musicasset.PlaylistBaseWrite", "EditPlaylist", buildJsonObject {
            put("dirId", dirId)
            put("mask", 95)
            put("dirNewName", name)
            put("dirNewDesc", before.firstText("desc"))
            put("dirNewPicUrl", before.firstText("picurl"))
            put("dirNewPicUrl2", before.firstText("picurl2"))
            put("dirNewtaglist", tagIds.joinToString(","))
            put("dirShow", dirShow)
        }, credential)
        val after = read()
        val afterTagIds = after.array("tag").mapNotNull {
            (it as? JsonObject)?.identifier("id")?.takeIf(String::isNotBlank)
        }
        val verified = after.firstText("title", "name") == name &&
            after.firstText("desc") == before.firstText("desc") &&
            after.firstText("picurl") == before.firstText("picurl") &&
            after.firstText("picurl2") == before.firstText("picurl2") &&
            afterTagIds == tagIds &&
            after.firstLong("dir_show").toInt() == dirShow
        if (!verified) throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未确认歌单重命名结果，请刷新后重试")
        return wrapped(buildJsonObject { put("tid", tid); put("name", name); put("verified", true) })
    }

    private fun editPlaylistTracks(credential: DirectCredential, body: JsonObject): JsonObject {
        val tid = body.firstLong("tid")
        val operation = body.firstText("op")
        val method = if (operation == "add") "AddSonglist" else "DelSonglist"
        val songs = body.array("songs").mapNotNull { it as? JsonObject }
        val data = cgi("music.musicasset.PlaylistDetailWrite", method, buildJsonObject {
            put("dirId", tid)
            put("tid", tid)
            put("bFmtUtf8", true)
            put("v_songInfo", buildJsonArray {
                songs.forEach { song ->
                    add(buildJsonObject {
                        put("songId", song.firstLong("id"))
                        put("songType", song.firstLong("type").toInt())
                    })
                }
            })
        }, credential)
        val result = data.firstLong("retCode").toInt()
        return wrapped(buildJsonObject {
            put("changed", result == 0 || result == 80092)
            put("verification", buildJsonObject {
                put("status", if (result == 0 || result == 80092) {
                    if (operation == "add") "present" else "absent"
                } else "unknown")
            })
        })
    }

    private fun subscribePlaylist(credential: DirectCredential, body: JsonObject): JsonObject {
        val id = body.firstLong("tid")
        val subscribed = body.flag("subscribed")
        val method = if (subscribed) "FavPlaylist" else "CancelFavPlaylist"
        val data = cgi("music.musicasset.PlaylistFavWrite", method, buildJsonObject {
            put("uin", credential.encryptUin)
            put("v_playlistId", buildJsonArray { add(JsonPrimitive(id)) })
        }, credential)
        val failed = data.array("v_failedPlaylistId").any {
            (it as? JsonPrimitive)?.longOrNull == id
        }
        val success = data.firstLong("result").toInt() == 0 && !failed
        if (!success) throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未确认歌单收藏变更")
        return wrapped(buildJsonObject { put("subscribed", subscribed) })
    }

    private fun subscribeAlbum(credential: DirectCredential, body: JsonObject): JsonObject {
        val id = body.firstText("id")
        if (!id.matches(Regex("[A-Za-z0-9]{1,64}"))) throw ApiException(ErrorKind.NotFound)
        val subscribed = body.flag("subscribed")
        cgi("music.musicasset.AlbumFavWrite", if (subscribed) "FavAlbum" else "CancelFavAlbum",
            buildJsonObject {
                put("euin", credential.encryptUin)
                put("v_albumMid", buildJsonArray { add(JsonPrimitive(id)) })
            }, credential)
        return wrapped(buildJsonObject { put("subscribed", subscribed) })
    }

    private fun musicGene(credential: DirectCredential): JsonObject {
        if (credential.encryptUin.isBlank())
            throw ApiException(ErrorKind.Unauthorized, userMessage = "QQ 音乐凭证缺少加密账号标识，请重新登录")
        val data = cgi("music.recommend.UserProfileSettingSvr", "GetProfileReport",
            buildJsonObject { put("VisitAccount", credential.encryptUin) }, credential)
        return wrapped(data)
    }

    private fun newSongs(): JsonObject {
        val data = cgi("newsong.NewSongServer", "get_new_song_info",
            buildJsonObject { put("type", 5) }, null)
        val rows = data.array("songlist", "song_list", "songs", "list")
        return wrapped(buildJsonObject { put("songs", JsonArray(rows)) })
    }

    private fun recommendedPlaylists(query: Map<String, String>): JsonObject {
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val data = cgi("music.playlist.PlaylistSquare", "GetRecommendFeed",
            buildJsonObject { put("From", 0); put("Size", limit) }, null)
        val playlists = data.array("List", "songlists", "v_playlist", "playlists").map { value ->
            normalizeFeedPlaylist(value)
        }
        return wrapped(buildJsonObject { put("playlists", JsonArray(playlists)) })
    }

    private fun musicCategories(): JsonObject {
        val data = webCgi("/splcloud/fcgi-bin/fcg_get_diss_tag_conf.fcg", emptyMap())
        val rawGroups = data.array("categories")
        val groups = rawGroups.mapIndexedNotNull { index, groupValue ->
            val group = groupValue as? JsonObject ?: return@mapIndexedNotNull null
            val items = group.array("items").mapNotNull { itemValue ->
                val item = itemValue as? JsonObject ?: return@mapNotNull null
                val id = item.identifier("categoryId", "id")
                val name = item.text("categoryName", "name").decodeHtmlEntities()
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                val sorts = item.array("allsorts").mapNotNull { sortValue ->
                    val sort = sortValue as? JsonObject ?: return@mapNotNull null
                    val sortId = sort.identifier("sortId", "id").toIntOrNull()?.takeIf { it > 0 }
                        ?: return@mapNotNull null
                    val sortName = sort.text("sortName", "name").decodeHtmlEntities()
                        .takeIf(String::isNotBlank) ?: return@mapNotNull null
                    buildJsonObject {
                        put("id", sortId)
                        put("name", sortName)
                    }
                }.distinctBy { (it["id"] as? JsonPrimitive)?.content }
                buildJsonObject {
                    put("id", id)
                    put("name", name)
                    put("sorts", JsonArray(sorts))
                }
            }
            if (items.isEmpty()) return@mapIndexedNotNull null
            buildJsonObject {
                put("id", index + 1)
                put("name", group.text("categoryGroupName", "name").decodeHtmlEntities())
                put("categories", JsonArray(items))
            }
        }
        if (groups.isEmpty()) {
            throw if (rawGroups.isEmpty()) ApiException(ErrorKind.NotFound,
                userMessage = "QQ 音乐暂未提供曲风分类")
            else ApiException(ErrorKind.Parse, userMessage = "QQ 音乐曲风分类响应异常")
        }
        return wrapped(buildJsonObject { put("groups", JsonArray(groups)) })
    }

    private fun categoryPlaylists(query: Map<String, String>, _credential: DirectCredential?): JsonObject {
        val categoryId = query["id"].orEmpty()
        if (!categoryId.matches(Regex("[1-9][0-9]{0,15}"))) throw ApiException(ErrorKind.NotFound)
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 60) ?: 30
        val sortId = query["sortId"]?.toIntOrNull()?.takeIf { it > 0 } ?: 3
        val end = offset + limit - 1
        val data = webCgi("/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg", mapOf(
            "categoryId" to categoryId,
            "sortId" to sortId,
            "sin" to offset,
            "ein" to end,
            "picmid" to 1,
        ))
        val rows = data.array("list")
        val playlists = rows.mapNotNull { rowValue ->
            val row = rowValue as? JsonObject ?: return@mapNotNull null
            val id = row.identifier("dissid", "id")
            val name = row.text("dissname", "name").decodeHtmlEntities()
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            buildJsonObject {
                put("id", id)
                put("name", name)
                put("description", row.text("introduction", "description"))
                put("cover", row.text("imgurl", "cover"))
                put("listennum", row.number("listennum", "play_cnt"))
            }
        }
        if (playlists.isEmpty() && rows.isNotEmpty()) {
            throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐分类歌单响应异常")
        }
        val total = data.firstLong("sum").takeIf { it > 0 } ?: (offset + playlists.size).toLong()
        if (rows.isEmpty() && offset == 0) {
            throw ApiException(ErrorKind.NotFound, userMessage = "这个曲风暂时没有歌单")
        }
        return wrapped(buildJsonObject {
            put("playlists", JsonArray(playlists))
            put("total", total)
            put("more", playlists.isNotEmpty() && offset + rows.size < total)
        })
    }

    private fun guessYouLike(credential: DirectCredential): JsonObject {
        val data = cgi("music.radioProxy.MbTrackRadioSvr", "get_radio_track", buildJsonObject {
            put("id", 99); put("num", 20); put("from", 0); put("scene", 0)
            put("song_ids", JsonArray(emptyList()))
        }, credential)
        val rows = data.array("tracks", "songlist", "songs", "list")
        return wrapped(buildJsonObject { put("songs", JsonArray(rows)) })
    }

    private fun personalFm(query: Map<String, String>, credential: DirectCredential): JsonObject {
        val page = query["page"]?.toIntOrNull()?.coerceIn(1, 100) ?: 1
        val limit = 30
        val data = cgi("music.radioProxy.MbTrackRadioSvr", "get_radio_track", buildJsonObject {
            put("id", 99)
            put("num", limit)
            put("from", (page - 1) * limit)
            put("scene", 0)
            put("song_ids", JsonArray(emptyList()))
        }, credential)
        val rows = data.array("tracks", "songlist", "songs", "list")
        val more = rows.size >= limit && page < 100
        return wrapped(buildJsonObject {
            put("songs", JsonArray(rows))
            put("more", more)
            if (more) put("nextPage", page + 1)
        })
    }

    private fun dailySongs(credential: DirectCredential): JsonObject {
        val feed = cgi("music.recommend.RecommendFeed", "get_recommend_feed", buildJsonObject {
            put("direction", 0)
            put("page", 1)
            put("v_cache", JsonArray(emptyList()))
            put("v_uniq", JsonArray(emptyList()))
            put("s_num", 0)
        }, credential)
        val daily = findDailyCard(feed)
        val playlistId = daily?.identifier("id", "tid", "dissid")
            ?.takeIf { it.matches(Regex("[1-9][0-9]{0,19}")) }
            ?: throw ApiException(ErrorKind.NotFound, userMessage = "QQ 音乐今日推荐尚未生成或已过期")
        val query = mapOf(
            "id" to playlistId,
            "encryptUin" to credential.encryptUin,
        )
        val publicQuery = mapOf("id" to playlistId, "daily" to "1")
        val attempts = listOf(
            PlaylistAttempt(publicQuery + ("dirid" to playlistId), false, false, compactAuthComm = true, useCredential = false),
            PlaylistAttempt(query + ("dirid" to playlistId), false, false, compactAuthComm = true, useCredential = true),
            PlaylistAttempt(query + ("dirid" to playlistId), true, false, compactAuthComm = false, useCredential = true),
            PlaylistAttempt(query + ("dirid" to playlistId), true, true, compactAuthComm = false, useCredential = true),
        )
        var songs: JsonArray? = null
        var lastFailure: ApiException? = null
        for (attempt in attempts) {
            try {
                val candidate = playlist(
                    attempt.query,
                    credential = credential.takeIf { attempt.useCredential },
                    aiDissInfo = attempt.aiDissInfo,
                    mobileAi = attempt.mobileAi,
                    compactAuthComm = attempt.compactAuthComm,
                )
                val data = candidate["data"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
                val candidateSongs = data.array("songs", "songlist")
                if (candidateSongs.isNotEmpty()) {
                    songs = candidateSongs
                    break
                }
            } catch (failure: ApiException) {
                if (failure.kind !in setOf(ErrorKind.Unauthorized, ErrorKind.Server, ErrorKind.NotFound)) throw failure
                lastFailure = failure
            }
        }
        val resolvedSongs = songs ?: throw lastFailure
            ?: ApiException(ErrorKind.NotFound, userMessage = "QQ 音乐今日推荐暂时为空")
        return wrapped(buildJsonObject {
            put("songs", resolvedSongs)
            put("playlistId", playlistId)
        })
    }

    private fun recentListening(credential: DirectCredential): JsonObject {
        val data = cgi("music.musicasset.PlayRecentlyRead", "GetPlayRecentlyInfo", buildJsonObject {
            put("requestCnt", 500)
            put("type", 2)
            put("updateTime", 0)
            put("lastTime", 0)
        }, credential)
        val nested = (data["data"] as? JsonObject) ?: data
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val allRows = nested.array("songList").mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            val track = row["track"] as? JsonObject ?: return@mapNotNull null
            val raw = row.firstLong("lastTime")
            val millis = if (raw > 10_000_000_000L) raw else raw * 1000L
            Triple(track, millis, row.firstLong("listenCnt", "playCount"))
        }
        val weekly = allRows.filter { it.second >= cutoff }
        val selected = (weekly.ifEmpty { allRows }).take(100)
        val items = selected.map { (track, _, count) ->
            buildJsonObject {
                put("track", track)
                put("listenCount", count.coerceAtLeast(1))
            }
        }
        return wrapped(buildJsonObject {
            put("items", JsonArray(items))
            put("from", "")
            put("token", "")
        })
    }

    private fun newComments(query: Map<String, String>): JsonObject {
        val id = query["id"].orEmpty().takeIf { it.matches(Regex("[1-9][0-9]{0,14}")) }
            ?: throw ApiException(ErrorKind.NotFound)
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 30
        val data = cgi("music.globalComment.CommentRead", "GetNewCommentList", buildJsonObject {
            put("BizType", 1)
            put("BizId", id)
            put("LastCommentSeqNo", "")
            put("PageSize", limit)
            put("PageNum", offset / limit)
            put("FromCommentId", "")
            put("WithHot", 0)
            put("PicEnable", 1)
        }, null)
        val list = (data["CommentList"] as? JsonObject) ?: JsonObject(emptyMap())
        val comments = list.array("Comments").mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            buildJsonObject {
                put("id", row.firstText("CmId"))
                put("content", row.firstText("Content"))
                put("nick", row.firstText("Nick"))
                put("avatar", row.firstText("Avatar"))
                put("praiseCount", row.firstLong("PraiseNum"))
                put("time", row.firstLong("PubTime"))
            }
        }
        return wrapped(buildJsonObject {
            put("comments", JsonArray(comments))
            put("total", list.firstLong("Total"))
            put("more", list.flag("HasMore"))
        })
    }

    private fun topLists(): JsonObject {
        val data = cgi("music.musicToplist.Toplist", "GetAll", buildJsonObject {}, null)
        val groups = data.array("groups", "v_group", "group").mapNotNull { value ->
            val group = value as? JsonObject ?: return@mapNotNull null
            buildJsonObject {
                put("name", group.firstText("name", "title"))
                put("toplists", JsonArray(
                    group.array("toplists", "toplist", "list").mapNotNull { item ->
                        val row = item as? JsonObject ?: return@mapNotNull null
                        normalizeToplist(row)
                    }
                ))
            }
        }
        return wrapped(buildJsonObject { put("groups", JsonArray(groups)) })
    }

    private fun topList(query: Map<String, String>): JsonObject {
        val id = query["id"].orEmpty()
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 300) ?: 100
        val data = cgi("music.musicToplist.Toplist", "GetDetail", buildJsonObject {
            val numericId = id.toIntOrNull()
            if (numericId != null) put("topId", numericId) else put("topId", id)
            put("offset", offset)
            put("num", limit)
        }, null)
        val info = (data["toplist"] as? JsonObject) ?: normalizeToplist(data)
        val songs = data.array("songlist", "songs", "list")
        val total = data.firstLong("total", "total_num").takeIf { it > 0 }
            ?: info.firstLong("total", "song_num").takeIf { it > 0 }
            ?: (offset + songs.size).toLong()
        return wrapped(buildJsonObject {
            put("toplist", info)
            put("songs", JsonArray(songs))
            put("total", total)
            put("more", data.flag("hasmore") || (offset + songs.size).toLong() < total)
        })
    }

    private fun normalizeToplist(value: JsonObject): JsonObject {
        val nested = (value["Toplist"] as? JsonObject)
            ?: (value["basic"] as? JsonObject)
            ?: value
        return buildJsonObject {
            nested.forEach { (key, child) -> put(key, child) }
            put("id", nested.identifier("topId", "id", "tid", "topID"))
            put("title", nested.firstText("title", "name", "topName"))
            put("pic", nested.firstText("pic", "cover", "headPic"))
            put("songnum", nested.firstLong("song_num", "songNum", "total"))
        }
    }

    private fun normalizeFeedPlaylist(value: JsonElement): JsonObject {
        val outer = value as? JsonObject ?: return JsonObject(emptyMap())
        val playlist = (outer["Playlist"] as? JsonObject) ?: outer
        val basic = (playlist["basic"] as? JsonObject) ?: playlist
        val cover = basic["cover"]
        val coverUrl = when (cover) {
            is JsonObject -> cover.firstText("medium_url", "large_url", "small_url", "url")
            is JsonPrimitive -> cover.contentOrNull.orEmpty()
            else -> ""
        }
        val creator = basic["creator"] as? JsonObject
        return buildJsonObject {
            put("id", basic.identifier("tid", "id", "dissid"))
            put("tid", basic.identifier("tid", "id", "dissid"))
            put("name", basic.firstText("title", "name"))
            put("cover", coverUrl)
            put("description", basic.firstText("subtitle", "description"))
            put("nickname", creator?.firstText("nick", "name").orEmpty())
            put("songnum", basic.firstLong("song_cnt", "songNum", "songnum"))
            put("listennum", basic.firstLong("play_cnt", "listenNum", "listennum"))
        }
    }

    private fun findDailyCard(feed: JsonObject): JsonObject? {
        val cards = feed.array("v_shelf").flatMap { shelf ->
            (shelf as? JsonObject)?.array("v_niche").orEmpty().flatMap { niche ->
                (niche as? JsonObject)?.array("v_card").orEmpty()
            }
        }
        return cards.mapNotNull { it as? JsonObject }
            .filter { it.firstLong("subtype") == 510L }
            .singleOrNull()
    }

    private fun album(query: Map<String, String>): JsonObject {
        val id = query["id"].orEmpty()
        val data = cgi("music.musichallAlbum.AlbumInfoServer", "GetAlbumDetail",
            buildJsonObject { put("albumMid", id) }, null)
        val album = (data["album"] as? JsonObject) ?: data
        val songData = cgi("music.musichallAlbum.AlbumSongList", "GetAlbumSongList", buildJsonObject {
            put("albumMid", id)
            put("begin", 0)
            put("num", 300)
        }, null)
        val songs = songData.array("songList", "songlist", "songs", "list")
            .ifEmpty { data.array("songList", "songlist", "songs", "list") }
        return wrapped(buildJsonObject {
            put("album", album)
            album.forEach { (key, value) -> if (key !in setOf("album")) put(key, value) }
            put("songs", JsonArray(songs))
        })
    }

    private fun artist(query: Map<String, String>): JsonObject {
        val id = query["id"].orEmpty()
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
        val singer = cgi("music.web_singer_info_svr", "get_singer_detail_info",
            buildJsonObject { put("singerMid", id) }, null)
        val songs = cgi("musichall.song_list_server", "GetSingerSongList", buildJsonObject {
            put("singerMid", id); put("begin", offset); put("num", limit)
        }, null)
        val albums = cgi("music.musichallAlbum.AlbumListServer", "GetAlbumList", buildJsonObject {
            put("singerMid", id); put("begin", 0); put("num", 20)
        }, null)
        return wrapped(buildJsonObject {
            put("artist", (singer["singer"] as? JsonObject) ?: singer)
            put("songs", JsonArray(songs.array("songlist", "songs", "list")))
            put("albums", JsonArray(albums.array("albumlist", "albums", "list")))
            put("more", songs.flag("hasmore"))
        })
    }

    private fun artistAlbums(query: Map<String, String>): JsonObject {
        val id = query["id"].orEmpty()
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
        val data = cgi("music.musichallAlbum.AlbumListServer", "GetAlbumList", buildJsonObject {
            put("singerMid", id); put("begin", offset); put("num", limit)
        }, null)
        val albums = data.array("albumlist", "albums", "list")
        return wrapped(buildJsonObject {
            put("albums", JsonArray(albums))
            put("total", data.firstLong("total", "total_num").takeIf { it > 0 } ?: (offset + albums.size))
            put("more", data.flag("hasmore"))
        })
    }

    private fun vip(credential: DirectCredential): JsonObject {
        val data = cgi("VipLogin.VipLoginInter", "vip_login_base", buildJsonObject {}, credential)
        val normalized = buildJsonObject {
            data.forEach { (key, value) -> put(key, value) }
            val level = data.firstText("vipLevel", "level")
            put("vipLevel", level.ifBlank {
                if (data.firstLong("superVip", "svip") > 0) "super"
                else if (data.firstLong("greenVip", "vip") > 0) "green"
                else "free"
            })
        }
        return wrapped(normalized)
    }

    private fun favoriteAlbums(credential: DirectCredential, query: Map<String, String>): JsonObject {
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
        val data = cgi("music.musicasset.AlbumFavRead", "CgiGetAlbumFavInfo", buildJsonObject {
            put("euin", credential.encryptUin); put("offset", offset); put("size", limit)
        }, credential)
        val albums = data.array("albums", "albumlist", "list")
        return wrapped(buildJsonObject {
            put("albums", JsonArray(albums))
            put("total", data.firstLong("total").takeIf { it > 0 } ?: (offset + albums.size))
            put("more", data.flag("hasmore"))
        })
    }

    private fun cgiSigned(module: String, method: String, param: JsonObject,
        credential: DirectCredential): JsonObject {
        val token = hash33(credential.musicKey, 5381)
        val payload = buildJsonObject {
            put("comm", buildJsonObject {
                put("ct", 24)
                put("cv", 4_747_474)
                put("platform", "yqq.json")
                put("g_tk", token)
                put("g_tk_new_20200303", token)
                put("uci", credential.stringMusicId)
                put("qq", credential.stringMusicId)
                put("authst", credential.musicKey)
                put("tmeLoginType", credential.loginType)
                put("inCharset", "utf-8")
                put("outCharset", "utf-8")
                put("notice", 0)
                put("needNewCode", 1)
            })
            put("playlist", buildJsonObject {
                put("module", module)
                put("method", method)
                put("param", param)
            })
        }
        val json = payload.toString()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(REQUEST_KEY, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val encodedBody = Base64.getEncoder().encodeToString(iv + encrypted)
        val url = "https://u6.y.qq.com/cgi-bin/musics.fcg".toHttpUrl().newBuilder()
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .addQueryParameter("encoding", "ag-1")
            .addQueryParameter("sign", webSign(json))
            .build()
        val response = execute(cgiHttp, Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .header("Accept", "application/octet-stream")
            .apply { credential.cookieHeader()?.let { header("Cookie", it) } }
            .post(encodedBody.toRequestBody(PLAIN_TEXT))
            .build())
        log("QQ direct signed POST $module/$method HTTP ${response.code}")
        if (response.code !in 200..299)
            throw ApiException(ErrorKind.Server, response.code, "QQ 音乐签名请求失败（HTTP ${response.code}）")
        val bytes = response.body?.bytes() ?: throw ApiException(ErrorKind.Parse)
        val decoded = ByteArray(bytes.size) { index ->
            (bytes[index].toInt() xor RESPONSE_XOR[index % RESPONSE_XOR.size].toInt()).toByte()
        }
        val decodedText = String(decoded, Charsets.UTF_8)
        val root = parseJson(decodedText)
        val rootCode = root.firstLong("code", "ret").toInt()
        if (rootCode != 0) throw cgiError(rootCode, root)
        val node = root["playlist"] as? JsonObject
            ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐签名响应缺少 playlist")
        val nodeCode = node.firstLong("code", "ret").toInt()
        if (nodeCode != 0) throw cgiError(nodeCode, node)
        return (node["data"] as? JsonObject) ?: node
    }

    private fun cgiLite(module: String, method: String, param: JsonObject): JsonObject {
        val key = "$module.$method"
        val root = buildJsonObject {
            put("comm", buildJsonObject {
                put("ct", 11)
                put("cv", "1003006")
                put("v", "1003006")
                put("tmeAppID", "qqmusiclight")
                put("nettype", "NETWORK_WIFI")
                put("udid", "0")
                put("uid", "0")
            })
            put(key, buildJsonObject {
                put("module", module)
                put("method", method)
                put("param", param)
            })
        }
        val response = execute(cgiHttp, Request.Builder().url(CGI)
            .header("User-Agent", "okhttp/3.14.9")
            .header("Cookie", "tmeLoginType=-1")
            .post(root.toString().toRequestBody(JSON))
            .build())
        val text = response.body?.string().orEmpty()
        log("QQ direct lite POST $module/$method HTTP ${response.code}")
        if (response.code !in 200..299)
            throw ApiException(ErrorKind.Server, response.code, "QQ 音乐轻量搜索失败（HTTP ${response.code}）")
        val json = parseJson(text)
        val rootCode = json.firstLong("code", "ret").toInt()
        if (rootCode != 0) {
            log("QQ direct lite $module/$method business code $rootCode")
            throw cgiError(rootCode, json)
        }
        val node = json[key] as? JsonObject
            ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐轻量搜索响应不完整")
        val nodeCode = node.firstLong("code", "ret").toInt()
        if (nodeCode != 0) {
            log("QQ direct lite $module/$method node code $nodeCode")
            throw cgiError(nodeCode, node)
        }
        return (node["data"] as? JsonObject) ?: node
    }

    private fun webCgi(path: String, params: Map<String, Any>): JsonObject {
        val url = ("https://c.y.qq.com" + path).toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("outCharset", "utf-8")
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value.toString()) } }
            .build()
        val response = execute(cgiHttp, Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .header("Accept", "application/json")
            .get()
            .build())
        log("QQ direct GET ${url.encodedPath} HTTP ${response.code}")
        if (response.code !in 200..299) {
            val code = response.code
            response.close()
            throw ApiException(if (code == 401) ErrorKind.Unauthorized else ErrorKind.Server,
                code, "QQ 音乐直连 HTTP $code")
        }
        val bytes = response.body?.bytes() ?: throw ApiException(ErrorKind.Parse)
        val root = runCatching { parseWebJson(bytes, "UTF-8") }
            .recoverCatching { parseWebJson(bytes, "GB18030") }
            .getOrElse {
                throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐响应格式异常")
            }
        val rootCode = root.webCode("code", "ret")
        if (rootCode != 0) throw cgiError(rootCode, root)
        val subcode = if (root.containsKey("subcode")) root.webCode("subcode") else 0
        if (subcode != 0) throw cgiError(subcode, root)
        return (root["data"] as? JsonObject) ?: root
    }

    private fun parseWebJson(bytes: ByteArray, charsetName: String): JsonObject {
        val decoder = Charset.forName(charsetName).newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        var text = decoder.decode(ByteBuffer.wrap(bytes)).toString().trim().removePrefix("\uFEFF")
        Regex("""^[\w.$]+\((.*)\);?\s*$""", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.let { text = it.groupValues[1] }
        return ApiJson.parseToJsonElement(text) as? JsonObject ?: throw ApiException(ErrorKind.Parse)
    }

    private fun JsonObject.webCode(vararg keys: String): Int = keys.firstNotNullOfOrNull { key ->
        (get(key) as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
    }?.toInt() ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐响应缺少业务状态码")

    private fun cgi(module: String, method: String, param: JsonObject,
        credential: DirectCredential?, extraComm: JsonObject = JsonObject(emptyMap()),
        client: OkHttpClient = cgiHttp, commOverride: JsonObject? = null,
        userAgent: String = USER_AGENT): JsonObject {
        val key = "$module.$method"
        val root = buildJsonObject {
            put("comm", commOverride ?: common(credential, extraComm))
            put(key, buildJsonObject {
                put("module", module)
                put("method", method)
                put("param", param)
            })
        }
        val request = Request.Builder().url(CGI)
            .header("User-Agent", userAgent)
            .header("Referer", "https://y.qq.com/")
            .post(root.toString().toRequestBody(JSON))
            .apply { credential?.cookieHeader()?.let { header("Cookie", it) } }
            .build()
        val response = execute(client, request)
        val text = response.body?.string().orEmpty()
        log("QQ direct POST $module/$method HTTP ${response.code}")
        if (response.code !in 200..299)
            throw ApiException(if (response.code == 401) ErrorKind.Unauthorized else ErrorKind.Server,
                response.code, "QQ 音乐直连 HTTP ${response.code}")
        val json = parseJson(text)
        val rootCode = json.firstLong("code", "ret").toInt()
        if (rootCode != 0) {
            log("QQ direct $module/$method business code $rootCode")
            throw cgiError(rootCode, json)
        }
        val node = json[key] as? JsonObject
            ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐响应缺少 $key")
        val nodeCode = node.firstLong("code", "ret").toInt()
        if (nodeCode != 0) {
            log("QQ direct $module/$method node code $nodeCode")
            throw cgiError(nodeCode, node)
        }
        return (node["data"] as? JsonObject) ?: node
    }

    private fun common(credential: DirectCredential?, extra: JsonObject): JsonObject = buildJsonObject {
        put("ct", 11)
        put("cv", APP_VERSION)
        put("v", APP_VERSION)
        put("QIMEI36", FALLBACK_QIMEI36)
        put("tmeAppID", "qqmusic")
        put("format", "json")
        put("inCharset", "utf-8")
        put("outCharset", "utf-8")
        put("uid", credential?.stringMusicId?.ifBlank { "0" } ?: "0")
        if (credential?.valid == true) {
            put("qq", credential.stringMusicId.ifBlank { credential.musicId.toString() })
            put("authst", credential.musicKey)
            put("tmeLoginType", credential.loginType)
        }
        extra.forEach { (key, value) -> put(key, value) }
    }

    private fun execute(client: OkHttpClient, request: Request): okhttp3.Response {
        val response = try {
            client.newCall(request).execute()
        } catch (failure: IOException) {
            throw ApiException(ErrorKind.Network, userMessage = "QQ 音乐直连网络异常：${failure.message.orEmpty()}")
        }
        if (response.code in 500..599) {
            response.close()
            throw ApiException(ErrorKind.Server, response.code, "QQ 音乐服务器暂时不可用")
        }
        return response
    }

    private fun parseJson(text: String): JsonObject = try {
        ApiJson.parseToJsonElement(text) as? JsonObject
            ?: throw ApiException(ErrorKind.Parse)
    } catch (failure: ApiException) {
        throw failure
    } catch (failure: Exception) {
        throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐响应格式异常")
    }

    private fun cgiError(code: Int, data: JsonObject): ApiException = ApiException(
        when (code) {
            401, 1000, 104400, 104401 -> ErrorKind.Unauthorized
            403, 20277, 20278, 20450 -> ErrorKind.Forbidden
            429, 104604 -> ErrorKind.RateLimited
            404, 10007, 104003 -> ErrorKind.NotFound
            else -> ErrorKind.Server
        },
        code,
        data.firstText("msg", "message", "failureHint").take(180).ifBlank { "QQ 音乐请求失败（$code）" },
    )

    private fun wrapped(data: JsonObject): JsonObject = buildJsonObject {
        put("code", 0)
        put("data", data)
    }

    private fun qrStatus(code: Int): JsonObject = buildJsonObject {
        put("code", code)
        put("data", buildJsonObject { put("code", code) })
    }

    private fun normalizeProfile(raw: JsonObject): JsonObject {
        val nested = (raw["profile"] as? JsonObject)
            ?: (raw["user"] as? JsonObject)
            ?: (raw["creator"] as? JsonObject)
            ?: raw
        val result = buildJsonObject { nested.forEach { (key, value) -> put(key, value) } }
        fun value(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
            (nested[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        }.orEmpty()
        fun firstUrl(depth: Int = 0, value: JsonElement = nested): String {
            if (depth > 5) return ""
            return when (value) {
                is JsonObject -> value.entries.firstNotNullOfOrNull { (key, child) ->
                    val text = (child as? JsonPrimitive)?.contentOrNull.orEmpty()
                    text.takeIf {
                        key.lowercase() in setOf("avatar", "avatarurl", "headurl", "headpic",
                            "figureurl", "logo", "pic") && it.startsWith("http")
                    }
                } ?: value.values.firstNotNullOfOrNull { child ->
                    firstUrl(depth + 1, child).takeIf(String::isNotBlank)
                }.orEmpty()
                is JsonArray -> value.firstNotNullOfOrNull { child ->
                    firstUrl(depth + 1, child).takeIf(String::isNotBlank)
                }.orEmpty()
                else -> ""
            }
        }
        return buildJsonObject {
            result.forEach { (key, child) -> put(key, child) }
            put("nickname", value("nickname", "nick", "name", "title"))
            put("uin", value("uin", "musicid", "musicId", "str_musicid"))
            put("avatar", value("avatar", "avatarurl", "headurl", "headpic", "figureurl")
                .ifBlank { firstUrl() }.replace("http://", "https://"))
        }
    }

    private fun hash33(value: String, seed: Int = 0): Int {
        var hash = seed
        value.forEach { character -> hash = (hash shl 5) + hash + character.code }
        return hash and Int.MAX_VALUE
    }

    private fun searchId(): String {
        val high = Random.nextLong(1, 21) * 18_014_398_509_481_984L
        val middle = Random.nextLong(0, 4_194_305) * 4_294_967_296L
        val tail = System.currentTimeMillis() % 86_400_000L
        return (high + middle + tail).toString()
    }

    private fun randomGuid(): String = buildString(32) {
        repeat(32) { append("abcdef0123456789"[Random.nextInt(16)]) }
    }

    private fun webSign(body: String): String {
        val hash = MessageDigest.getInstance("SHA-1").digest(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }
        val first = listOf(23, 14, 6, 36, 16, 7, 19).joinToString("") { hash[it].toString() }
        val second = listOf(16, 1, 32, 12, 19, 27, 8, 5).joinToString("") { hash[it].toString() }
        val middleBytes = ByteArray(SIGN_SCRAMBLE.size) { index ->
            (SIGN_SCRAMBLE[index] xor hash.substring(index * 2, index * 2 + 2).toInt(16)).toByte()
        }
        val middle = Base64.getEncoder().encodeToString(middleBytes)
            .replace(Regex("""[\\/+=]"""), "")
        return "zzc$first$middle$second".lowercase()
    }

    private fun payloadSize(prefix: String): Long = when (prefix) {
        "F000", "O801" -> 0
        "O800", "M800" -> 0
        else -> 0
    }

    private fun payloadBitrate(prefix: String): Long = when (prefix) {
        "AI00", "Q000", "Q001", "Q003", "Q005", "D004", "D008", "DT03", "TL01", "RS01" -> 0
        "F000" -> 1_411_200
        "O600", "C600" -> 192_000
        "O801" -> 640_000
        "O800" -> 320_000
        "M800" -> 320_000
        "C400" -> 96_000
        else -> 128_000
    }

    private class MemoryCookieJar : CookieJar {
        private val cookies = ConcurrentHashMap<String, Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { cookie ->
                this.cookies["${cookie.name.lowercase()}|${cookie.domain}|${cookie.path}"] = cookie
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.values.filter {
            it.matches(url)
        }

        fun valueFor(name: String): String? = cookies.values.firstOrNull {
            it.name.equals(name, ignoreCase = true) && it.expiresAt > System.currentTimeMillis()
        }?.value
    }
}

internal fun qqAuthCookieHeader(
    uin: String,
    musicKey: String,
    loginType: Int = 2,
    openId: String = "",
    refreshToken: String = "",
    unionId: String = "",
    refreshKey: String = "",
): String? {
    val safeUin = uin.trim().takeIf { value ->
        value.isNotBlank() && value.none { it <= ' ' || it == ';' }
    } ?: return null
    val safeKey = musicKey.trim().takeIf { value ->
        value.isNotBlank() && value.none { it <= ' ' || it == ';' }
    } ?: return null
    fun safe(value: String) = value.trim().takeIf { text ->
        text.isNotBlank() && text.none { it <= ' ' || it == ';' }
    }
    val cookies = linkedMapOf(
        "uin" to safeUin,
        "qqmusic_uin" to safeUin,
        "qm_keyst" to safeKey,
        "qqmusic_key" to safeKey,
    )
    if (loginType == 1) {
        cookies["login_type"] = "2"
        cookies["wxuin"] = "o" + safeUin.padStart(10, '0')
        safe(openId)?.let { cookies["wxopenid"] = it }
        safe(unionId)?.let { cookies["wxunionid"] = it }
        safe(refreshToken)?.let { cookies["wxrefresh_token"] = it }
    } else {
        cookies["login_type"] = "1"
        safe(openId)?.let { cookies["psrf_qqopenid"] = it }
        safe(refreshToken)?.let { cookies["psrf_qqrefresh_token"] = it }
    }
    if (refreshKey.isNotBlank()) cookies["psrf_musickey_createtime"] = (System.currentTimeMillis() / 1_000).toString()
    return cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
}

private fun JsonObject.firstText(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}.orEmpty()

private fun JsonObject.firstLong(vararg keys: String): Long = keys.firstNotNullOfOrNull { key ->
    (get(key) as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
} ?: 0L

private fun JsonElement?.groupItems(key: String): List<JsonElement> =
    ((this as? JsonObject)?.get(key) as? JsonArray).orEmpty()

private operator fun JsonObject.plus(other: JsonObject): JsonObject =
    JsonObject(toMutableMap().apply { putAll(other) })
