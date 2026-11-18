package io.github.currencortex.music.data.qqmusic

import android.os.Build
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
        const val ANDROID_USER_AGENT = "QQMusic 14090008(android 10)"
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
    private data class AndroidDevice(
        val openUdid: String,
        val androidId: String,
        val osRelease: String,
        val model: String,
        val sdk: Int,
        val fingerprint: String,
    )
    private data class AndroidSession(val uid: String, val sid: String)
    private val androidDevice by lazy {
        AndroidDevice(
            openUdid = DEFAULT_GUID,
            androidId = runCatching {
                Build.FINGERPRINT.hashCode().toUInt().toString(16).padStart(16, '0')
            }.getOrDefault("0000000000000000"),
            osRelease = runCatching { Build.VERSION.RELEASE }.getOrDefault(""),
            model = runCatching { Build.MODEL }.getOrDefault(""),
            sdk = runCatching { Build.VERSION.SDK_INT }.getOrDefault(0),
            fingerprint = runCatching { Build.FINGERPRINT }.getOrDefault(""),
        )
    }
    @Volatile private var androidSession: AndroidSession? = null
    @Volatile private var androidSessionAt = 0L

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
                route == "song/qualities" -> songQualities(query)
                route == "song/favorite-count" -> songFavoriteCount(query)
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
                route == "user/radio-scenes" -> radioScenes(requireNotNull(credential))
                route == "user/radio-preference" -> radioPreference(requireNotNull(credential))
                route == "user/radio-preference/save" -> saveRadioPreference(requireNotNull(credential), body ?: JsonObject(emptyMap()))
                route == "user/daily-songs" -> dailySongs(requireNotNull(credential))
                route == "user/listening-rank" -> recentListening(requireNotNull(credential))
                route == "recommend/toplists" -> topLists()
                route == "recommend/toplist" -> topList(query)
                route == "comment/new" -> comments(query, credential)
                route == "comment/list" -> comments(query, credential)
                route == "comment/replies" -> commentReplies(query, credential)
                route == "comment/send" -> addComment(body ?: buildJsonObject {}, requireNotNull(credential))
                route == "comment/delete" -> deleteComment(body ?: buildJsonObject {}, requireNotNull(credential))
                route == "comment/like" -> likeComment(body ?: buildJsonObject {}, requireNotNull(credential))
                route == "album" -> album(query)
                route == "album/songs" -> albumSongs(query)
                route == "artist" -> artist(query)
                route == "artist/songs" -> artistSongs(query)
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

    private fun songQualities(query: Map<String, String>): JsonObject {
        val mid = query["mid"].orEmpty()
        if (!mid.matches(Regex("[A-Za-z0-9]{1,64}"))) throw ApiException(ErrorKind.NotFound)
        // ct=24 is the client identity that returns the full quality size table; the
        // default ct=11 shape zeroes out 320k/FLAC even when the files exist.
        val data = cgi("music.pf_song_detail_svr", "get_song_detail_yqq",
            buildJsonObject { put("song_mid", mid) }, null,
            commOverride = buildJsonObject {
                put("ct", 24)
                put("cv", 0)
                put("format", "json")
            })
        val track = data["track_info"] as? JsonObject
        val file = track?.get("file") as? JsonObject ?: JsonObject(emptyMap())
        fun size(key: String): Long = file.firstLong(key)
        val newSizes = file.array("size_new").mapNotNull { (it as? JsonPrimitive)?.longOrNull }
        fun new(index: Int): Boolean = newSizes.getOrNull(index)?.let { it > 0 } == true
        val available = buildJsonObject {
            put("standard", size("size_128mp3") > 0 || size("size_96aac") > 0 || size("size_48aac") > 0)
            put("exhigh", size("size_320mp3") > 0 || size("size_192aac") > 0 ||
                size("size_192ogg") > 0 || new(3))
            put("lossless", size("size_flac") > 0 || new(5))
            put("hires", size("size_hires") > 0)
            put("dolby", size("size_dolby") > 0)
            put("qq_dts_x", size("size_dts") > 0 || new(9))
            put("qq_master", new(0) || size("size_ape") > 0)
            put("qq_surround", new(1))
            put("qq_studio", new(2))
            put("qq_nac", new(7))
        }
        return wrapped(buildJsonObject { put("available", available) })
    }

    private fun songFavoriteCount(query: Map<String, String>): JsonObject {
        val mid = query["mid"].orEmpty()
        if (!mid.matches(Regex("[A-Za-z0-9]{1,64}"))) throw ApiException(ErrorKind.NotFound)
        val data = cgi("music.musicasset.SongFavRead", "GetSongFansNumberByMid", buildJsonObject {
            put("v_songMid", buildJsonArray { add(JsonPrimitive(mid)) })
        }, null)
        val count = ((data["m_numbers"] as? JsonObject)?.get(mid) as? JsonPrimitive)?.longOrNull ?: 0L
        val display = ((data["m_show"] as? JsonObject)?.get(mid) as? JsonPrimitive)?.contentOrNull.orEmpty()
        return wrapped(buildJsonObject {
            put("count", count)
            put("display", display)
        })
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
            val subscribed = mutableSetOf<String>()
            var offset = 0
            var total = 0L
            var complete = false
            for (page in 0 until 20) {
                val favorites = favoriteAlbums(credential, mapOf("offset" to "$offset", "limit" to "100"))
                val data = favorites["data"] as? JsonObject ?: JsonObject(emptyMap())
                val ids = data.array("albums", "albumList", "albumlist", "list").mapNotNull { value ->
                    (value as? JsonObject)?.identifier("albummid", "albumMid", "mid", "id")
                        ?.takeIf(String::isNotBlank)
                }
                subscribed += ids
                total = maxOf(total, data.firstLong("total"))
                // A full page without explicit paging metadata is not proof that the
                // favorite list has ended. Preserve an unknown result rather than
                // reporting an album beyond the first page as not subscribed.
                val more = data.flag("hasmore") ||
                    (total > 0 && offset + ids.size < total) ||
                    (total == 0L && ids.size >= 100)
                if (!more || ids.isEmpty()) {
                    complete = true
                    break
                }
                offset += ids.size
            }
            return wrapped(buildJsonObject {
                put("items", buildJsonArray {
                    ids.forEach { id ->
                        add(buildJsonObject {
                            put("id", id)
                            put("status", when {
                                id in subscribed -> "subscribed"
                                complete -> "not-subscribed"
                                else -> "unknown"
                            })
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
            val raw = fans[mid]
            log("QQ favorite state $mid type=${raw?.let { it::class.simpleName }} value=${raw?.toString()?.take(80)}")
            fun JsonPrimitive.flag(): Boolean? =
                booleanOrNull ?: when (contentOrNull?.lowercase()) {
                    "1", "true" -> true
                    "0", "false" -> false
                    else -> null
                }
            when (val value = raw) {
                is JsonPrimitive -> value.flag()
                is JsonObject -> (value["v_fan"] as? JsonPrimitive)?.flag()
                else -> null
            }
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
        val acknowledgement = cgiAndroidWrite("music.musicasset.PlaylistDetailWrite", method, buildJsonObject {
            put("dirId", 201)
            put("v_songInfo", buildJsonArray {
                add(buildJsonObject { put("songId", id); put("songType", type) })
            })
        }, credential)
        val retCode = acknowledgement.firstLong("retCode", "code").toInt()
        log("QQ direct $method retCode=$retCode")
        if (retCode != 0 && retCode != 80092)
            throw ApiException(ErrorKind.Server, retCode, "QQ 音乐未确认收藏变更")
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
        val data = cgiAndroidWrite("music.musicasset.PlaylistFavWrite", method, buildJsonObject {
            put("uin", credential.stringMusicId.ifBlank { credential.musicId.toString() })
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
        val data = cgiAndroidWrite("music.musicasset.AlbumFavWrite", if (subscribed) "FavAlbum" else "CancelFavAlbum",
            buildJsonObject {
                put("uin", credential.stringMusicId.ifBlank { credential.musicId.toString() })
                put("v_albumMid", buildJsonArray { add(JsonPrimitive(id)) })
            }, credential)
        val failed = data.array("v_failedAlbumId").isNotEmpty() || data.array("v_failedAlbumMid").isNotEmpty()
        val result = data.firstLong("result")
        log("QQ album subscribe result=$result failed=$failed")
        if (result != 0L || failed)
            throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未确认专辑收藏变更")
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
            query["pool_id"]?.takeIf { it.isNotBlank() }?.let { put("pool_id", it) }
            query["scene_id"]?.takeIf { it.isNotBlank() }?.let { put("scene_id", it) }
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

    private fun radioScenes(credential: DirectCredential): JsonObject {
        val data = cgi("music.startup.RadioConfig", "GetRadioSceneConfigs", buildJsonObject {
            put("Ext", buildJsonObject { put("client_time", (System.currentTimeMillis() / 1000).toString()) })
        }, credential)
        return wrapped(data)
    }

    private fun radioPreference(credential: DirectCredential): JsonObject {
        val data = cgi("recommend.RecPreferenceSettingServer", "GetPreference", buildJsonObject {
            put("id", 1)
        }, credential)
        return wrapped(data)
    }

    private fun saveRadioPreference(credential: DirectCredential, body: JsonObject): JsonObject {
        val tags = body["vecTag"] as? JsonArray ?: JsonArray(emptyList())
        val data = cgi("recommend.RecPreferenceSettingServer", "ReportPreference", buildJsonObject {
            put("id", 1)
            put("vecTag", tags)
        }, credential)
        return wrapped(data)
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

    private fun comments(query: Map<String, String>, credential: DirectCredential?): JsonObject {
        val id = query["id"].orEmpty().takeIf { it.matches(Regex("[1-9][0-9]{0,14}")) }
            ?: throw ApiException(ErrorKind.NotFound)
        val page = query["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 25) ?: 20
        val seq = query["seq"].orEmpty()
        val sort = query["sort"].orEmpty().ifBlank { "latest" }
        val parameters = buildJsonObject {
            put("BizType", 1)
            put("BizSubType", 2)
            put("BizId", id)
            put("LastCommentSeqNo", seq)
            put("PageSize", limit)
            put("PageNum", page - 1)
            put("PicEnable", 1)
            when (sort) {
                "recommended" -> {
                    put("Flag", 1)
                    put("CmListUIVer", 1)
                    put("AudioEnable", 1)
                }
                "hot" -> {
                    put("HotType", 1)
                    put("WithAirborne", 0)
                }
                else -> {
                    put("HashTagID", "")
                    put("SelfSeeEnable", 1)
                    put("AudioEnable", 1)
                }
            }
        }
        val method = when (sort) {
            "recommended" -> "GetRecCommentList"
            "hot" -> "GetHotCommentList"
            else -> "GetNewCommentList"
        }
        return try {
            commentPage(cgi("music.globalComment.CommentRead", method, parameters, credential))
        } catch (failure: ApiException) {
            if (sort != "latest" || failure.status != 10000 && failure.kind != ErrorKind.Server) throw failure
            legacyComments(id, (page - 1) * limit, limit)
        }
    }

    private fun commentReplies(query: Map<String, String>, credential: DirectCredential?): JsonObject {
        val id = query["id"].orEmpty().takeIf { it.matches(Regex("[1-9][0-9]{0,14}")) }
            ?: throw ApiException(ErrorKind.NotFound)
        val parent = query["parent"].orEmpty().takeIf(String::isNotBlank)
            ?: throw ApiException(ErrorKind.NotFound)
        val page = query["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 25) ?: 20
        val data = cgi("music.globalComment.CommentReadServer", "GetReplyCommentList", buildJsonObject {
            put("BizType", 1)
            put("BizSubType", 2)
            put("BizId", id)
            put("RootCmId", parent)
            put("CmId", parent)
            put("LastCommentSeqNo", query["seq"].orEmpty())
            put("PageSize", limit)
            put("PageNum", page - 1)
            put("PicEnable", 1)
        }, credential)
        return commentPage(data)
    }

    private fun commentPage(data: JsonObject): JsonObject {
        val list = (data["CommentList"] as? JsonObject) ?: JsonObject(emptyMap())
        var lastSeq = ""
        val comments = list.array("Comments").mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            lastSeq = row.firstText("SeqNo")
            val quoted = row.array("RepliedComments").firstOrNull() as? JsonObject
            fun body(value: JsonObject, vararg keys: String): String =
                value.firstText(*keys).replace("\\r\\n", "\n").replace("\\n", "\n")
            buildJsonObject {
                put("id", row.firstText("CmId"))
                put("seqNo", row.firstText("SeqNo"))
                put("content", body(row, "Content"))
                put("nick", row.firstText("Nick"))
                put("avatar", row.firstText("Avatar"))
                put("praiseCount", row.firstLong("PraiseNum"))
                put("time", row.firstLong("PubTime"))
                put("likedByMe", row.firstLong("IsPraised") == 1L)
                put("isMine", row.firstLong("IsSelf") == 1L)
                put("replyCount", row.firstLong("ReplyCnt"))
                put("quotedNickname", quoted?.firstText("Nick").orEmpty())
                quoted?.let { body(it, "Content") }?.takeIf(String::isNotBlank)?.let { put("quotedContent", it) }
            }
        }
        val total = list.firstLong("Total").takeIf { it > 0 }
            ?: data.firstLong("TotalCmNum").takeIf { it > 0 }
            ?: comments.size.toLong()
        return wrapped(buildJsonObject {
            put("comments", JsonArray(comments))
            put("total", total)
            put("more", list.flag("HasMore") || list.firstLong("NextOffset") > 0)
            put("nextSeq", lastSeq)
        })
    }

    private fun addComment(body: JsonObject, credential: DirectCredential): JsonObject {
        val id = body.firstLong("songId")
        val content = body.firstText("content").trim()
        require(content.isNotEmpty() && content.length <= 1000)
        val data = cgi("music.globalComment.CommentWriteServer", "AddComment", buildJsonObject {
            put("Content", content)
            put("BizType", 1)
            put("BizSubType", 2)
            put("BizId", id.toString())
            body.firstText("replyTo").takeIf(String::isNotBlank)?.let { put("RepliedCmId", it) }
        }, credential)
        val subCode = data.firstLong("SubCode", "subCode").toInt()
        if (subCode != 0) throw ApiException(ErrorKind.Server, subCode, data.firstText("Msg", "msg"))
        val commentId = data.firstText("CommentId", "AddedCmId", "CmId")
        return wrapped(buildJsonObject {
            put("commentId", commentId)
            put("verified", commentId.isNotBlank())
        })
    }

    private fun deleteComment(body: JsonObject, credential: DirectCredential): JsonObject {
        val id = body.firstText("commentId").takeIf(String::isNotBlank)
            ?: throw ApiException(ErrorKind.NotFound)
        val data = cgi("music.globalComment.CommentWriteServer", "DelComment",
            buildJsonObject { put("CommentId", id) }, credential)
        val subCode = data.firstLong("SubCode", "subCode").toInt()
        if (subCode != 0) throw ApiException(ErrorKind.Server, subCode, data.firstText("Msg", "msg"))
        return wrapped(buildJsonObject { put("deleted", true) })
    }

    private fun likeComment(body: JsonObject, credential: DirectCredential): JsonObject {
        val id = body.firstText("commentId").takeIf(String::isNotBlank)
            ?: throw ApiException(ErrorKind.NotFound)
        val liked = body.flag("liked")
        webCgi("/base/fcgi-bin/fcg_global_comment_praise_h5.fcg", mapOf(
            "g_tk" to hash33(credential.musicKey),
            "loginUin" to credential.stringMusicId,
            "hostUin" to 0,
            "cmd" to if (liked) 1 else 2,
            "reqtype" to 2,
            "commentid" to id,
        ), credential)
        return wrapped(buildJsonObject { put("liked", liked) })
    }

    private fun legacyComments(id: String, offset: Int, limit: Int): JsonObject {
        val data = webCgi("/base/fcgi-bin/fcg_global_comment_h5.fcg", mapOf(
            "cid" to 205360772,
            "reqtype" to 2,
            "biztype" to 1,
            "topid" to id,
            "cmd" to 8,
            "needmusiccrit" to 0,
            "pagenum" to offset / limit,
            "pagesize" to limit,
        ))
        val list = (data["comment"] as? JsonObject) ?: data
        val comments = list.array("commentlist", "comments").mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            buildJsonObject {
                put("id", row.firstText("commentid", "commentId", "CmId"))
                put("content", row.firstText("rootcommentcontent", "content", "Content"))
                put("nick", row.firstText("nick", "Nick", "nickname"))
                put("avatar", row.firstText("avatarurl", "Avatar", "avatar"))
                put("praiseCount", row.firstLong("praisenum", "PraiseNum", "praiseCount"))
                put("time", row.firstLong("time", "PubTime"))
            }
        }
        val total = list.firstLong("total", "commenttotal", "Total").takeIf { it > 0 } ?: (offset + comments.size).toLong()
        return wrapped(buildJsonObject {
            put("comments", JsonArray(comments))
            put("total", total)
            put("more", offset + comments.size < total)
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
        val basic = (data["basicInfo"] as? JsonObject) ?: (data["album"] as? JsonObject) ?: data
        val songData = cgi("music.musichallAlbum.AlbumSongList", "GetAlbumSongList", buildJsonObject {
            put("albumMid", id)
            put("begin", 0)
            put("num", 300)
        }, null)
        val songs = songData.array("songList", "songlist", "songs", "list")
            .ifEmpty { data.array("songList", "songlist", "songs", "list") }
        val total = songData.firstLong("totalNum", "total_song_num", "total_num", "total", "song_num")
            .takeIf { it > 0 } ?: songs.size.toLong()
        val albumMid = basic.firstText("albumMid", "albumMID", "mid").ifBlank { id }
        val singerList = (data["singer"] as? JsonObject)?.array("singerList", "singers", "list")
            ?.takeIf { it.isNotEmpty() } ?: data.array("singerList", "singers")
        val company = data["company"] as? JsonObject
        val album = buildJsonObject {
            basic.forEach { (key, value) -> put(key, value) }
            put("mid", albumMid)
            put("albumMid", albumMid)
            put("name", basic.firstText("albumName", "name", "title"))
            put("albumName", basic.firstText("albumName", "name", "title"))
            put("tranName", basic.firstText("tranName", "transName"))
            put("desc", basic.firstText("desc", "description", "intro"))
            put("publishDate", basic.firstText("publishDate", "time_public", "releaseDate"))
            put("language", basic.firstText("language"))
            put("albumType", basic.firstText("albumType", "typeName"))
            put("cover", basic.firstText("cover", "pic", "picurl", "picUrl", "albumPic")
                .ifBlank { "https://y.gtimg.cn/music/photo_new/T002R500x500M000$albumMid.jpg" })
            put("singerList", JsonArray(singerList))
            company?.let { value ->
                put("company", value)
                put("companyName", value.firstText("name"))
            }
        }
        return wrapped(buildJsonObject {
            put("album", album)
            put("songs", JsonArray(songs))
            put("totalSongs", total)
            put("hasMoreSongs", songs.size < total)
        })
    }

    private fun albumSongs(query: Map<String, String>): JsonObject {
        val id = query["id"].orEmpty()
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 300) ?: 300
        val data = cgi("music.musichallAlbum.AlbumSongList", "GetAlbumSongList", buildJsonObject {
            put("albumMid", id)
            put("begin", offset)
            put("num", limit)
        }, null)
        val songs = data.array("songList", "songlist", "songs", "list")
        val total = data.firstLong("totalNum", "total_song_num", "total_num", "total", "song_num")
            .takeIf { it > 0 } ?: (offset + songs.size).toLong()
        return wrapped(buildJsonObject {
            put("songs", JsonArray(songs))
            put("total", total)
            put("more", data.flag("hasmore") || offset + songs.size < total)
        })
    }

    private fun singerPage(id: String, offset: Int, limit: Int): JsonObject =
        cgi("music.web_singer_info_svr", "get_singer_detail_info", buildJsonObject {
            put("sort", 5)
            put("singermid", id)
            put("sin", offset)
            put("num", limit)
        }, null)

    private fun artist(query: Map<String, String>): JsonObject {
        val id = query["id"].orEmpty()
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
        val data = singerPage(id, offset, limit)
        val songs = data.array("songlist", "songs", "list")
        val info = (data["singer_info"] as? JsonObject)
            ?: (data["singer"] as? JsonObject)
            ?: (data["artist"] as? JsonObject)
            ?: JsonObject(emptyMap())
        // Album failures must not hide the singer's playable songs or biography.
        val albumPage = runCatching {
            cgi("music.musichallAlbum.AlbumListServer", "GetAlbumList", buildJsonObject {
                put("singerMid", id)
                put("begin", 0)
                put("num", 20)
            }, null)
        }.getOrNull()
        val albums = albumPage?.array("albumList", "albumlist", "albums", "list").orEmpty()
        val totalSong = data.firstLong("total_song", "totalSong", "total").takeIf { it > 0 }
            ?: info.firstLong("song_total", "songNum", "songnum").takeIf { it > 0 } ?: 0L
        val totalAlbum = data.firstLong("total_album", "totalAlbum").takeIf { it > 0 }
            ?: info.firstLong("album_total", "albumNum", "albumnum").takeIf { it > 0 } ?: 0L
        val totalMv = data.firstLong("total_mv", "totalMv").takeIf { it > 0 }
            ?: info.firstLong("mv_total", "mvNum", "mvnum").takeIf { it > 0 } ?: 0L
        val mid = info.firstText("mid", "singermid").ifBlank { id }
        return wrapped(buildJsonObject {
            put("artist", buildJsonObject {
                info.forEach { (key, value) -> put(key, value) }
                put("mid", mid)
                put("name", info.firstText("name", "title", "singerName"))
                put("alias", info.firstText("other_name", "otherName", "alias"))
                put("fans", info.firstLong("fans", "fansNum", "fans_num"))
                put("cover", info.firstText("cover", "pic", "avatar", "avatarUrl")
                    .ifBlank { "https://y.gtimg.cn/music/photo_new/T001R500x500M000$mid.jpg" })
            })
            put("songs", JsonArray(songs))
            put("albums", JsonArray(albums))
            put("brief", data.firstText("singer_brief", "brief", "desc"))
            put("fans", info.firstLong("fans", "fansNum", "fans_num"))
            put("totalSong", totalSong)
            put("totalAlbum", totalAlbum)
            put("totalMv", totalMv)
            put("moreSongs", data.flag("hasmore") ||
                (totalSong > 0 && offset + songs.size < totalSong))
            put("moreAlbums", totalAlbum > 0 && albums.size < totalAlbum)
            put("albumsFailed", albumPage == null)
        })
    }

    private fun artistSongs(query: Map<String, String>): JsonObject {
        val id = query["id"].orEmpty()
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
        val data = singerPage(id, offset, limit)
        val songs = data.array("songlist", "songs", "list")
        val total = data.firstLong("total_song", "totalSong", "total").takeIf { it > 0 } ?: 0L
        return wrapped(buildJsonObject {
            put("songs", JsonArray(songs))
            put("total", total)
            put("more", data.flag("hasmore") ||
                (total > 0 && offset + songs.size < total) ||
                (total == 0L && songs.size >= limit))
        })
    }

    private fun artistAlbums(query: Map<String, String>): JsonObject {
        val id = query["id"].orEmpty()
        val offset = query["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 30
        val data = cgi("music.musichallAlbum.AlbumListServer", "GetAlbumList", buildJsonObject {
            put("singerMid", id); put("begin", offset); put("num", limit)
        }, null)
        val albums = data.array("albumList", "albumlist", "albums", "list")
        val total = data.firstLong("total", "total_num", "total_album")
            .takeIf { it > 0 } ?: (offset + albums.size).toLong()
        return wrapped(buildJsonObject {
            put("albums", JsonArray(albums))
            put("total", total)
            put("more", data.flag("hasmore") || offset + albums.size < total)
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
        // musicu AlbumFavRead returns a zero-filled struct; the legacy profile
        // asset CGI is the endpoint that actually lists favourited albums.
        val data = webCgi("/fav/fcgi-bin/fcg_get_profile_order_asset.fcg", mapOf(
            "ct" to 20,
            "cid" to 205360956,
            "userid" to credential.stringMusicId.ifBlank { credential.musicId.toString() },
            "reqtype" to 2,
            "sin" to offset,
            "ein" to (offset + limit - 1).coerceAtLeast(offset),
        ), credential)
        val albums = data.array("albumlist", "albums", "albumList", "list")
        val total = data.firstLong("totalalbum", "total").takeIf { it > 0 }
            ?: (offset + albums.size).toLong()
        return wrapped(buildJsonObject {
            put("albums", JsonArray(albums))
            put("total", total)
            put("more", data.flag("has_more", "hasmore") || offset + albums.size < total)
        })
    }

    private fun androidComm(credential: DirectCredential?, session: AndroidSession?,
        includeAuth: Boolean = credential?.valid == true): JsonObject = buildJsonObject {
        put("ct", 11)
        put("cv", 14_090_008)
        put("v", 14_090_008)
        put("chid", "10003505")
        put("tmeAppID", "qqmusic")
        put("QIMEI", "")
        put("QIMEI36", FALLBACK_QIMEI36)
        put("OpenUDID", androidDevice.openUdid)
        put("udid", androidDevice.openUdid)
        put("OpenUDID2", androidDevice.openUdid)
        put("aid", androidDevice.androidId)
        put("os_ver", androidDevice.osRelease)
        put("phonetype", androidDevice.model)
        put("devicelevel", androidDevice.sdk.toString())
        put("newdevicelevel", androidDevice.sdk.toString())
        put("rom", androidDevice.fingerprint)
        session?.uid?.takeIf(String::isNotBlank)?.let { put("uid", it) }
        session?.sid?.takeIf(String::isNotBlank)?.let { put("sid", it) }
        if (includeAuth && credential?.valid == true) {
            put("qq", credential.stringMusicId.ifBlank { credential.musicId.toString() })
            put("authst", credential.musicKey)
            put("tmeLoginType", credential.loginType)
        }
    }

    private fun musicuAndroid(module: String, method: String, param: JsonObject,
        comm: JsonObject, cookie: String?, gTk: Int = 5381, userAgent: String = ANDROID_USER_AGENT): JsonObject {
        val root = buildJsonObject {
            put("comm", comm)
            put("req_0", buildJsonObject {
                put("module", module)
                put("method", method)
                put("param", param)
            })
        }
        val url = CGI.toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("g_tk", gTk.toString())
            .build()
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "https://y.qq.com/")
            .apply { cookie?.takeIf(String::isNotBlank)?.let { header("Cookie", it) } }
            .post(root.toString().toRequestBody(JSON))
            .build()
        val response = execute(cgiHttp, request)
        val text = response.body?.string().orEmpty()
        log("QQ direct android POST $module/$method HTTP ${response.code}")
        if (response.code !in 200..299)
            throw ApiException(ErrorKind.Server, response.code, "QQ 音乐客户端请求失败（HTTP ${response.code}）")
        val json = parseJson(text)
        val rootCode = json.firstLong("code", "ret").toInt()
        if (rootCode != 0) {
            log("QQ direct android $module/$method business code $rootCode")
            throw cgiError(rootCode, json)
        }
        val node = json["req_0"] as? JsonObject
            ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐响应缺少 req_0")
        val nodeCode = node.firstLong("code", "ret").toInt()
        if (nodeCode != 0) {
            log("QQ direct android $module/$method node code $nodeCode")
            throw cgiError(nodeCode, node)
        }
        return (node["data"] as? JsonObject) ?: node
    }

    private fun androidSession(): AndroidSession? {
        val cached = androidSession
        if (cached != null && System.currentTimeMillis() - androidSessionAt < 6 * 60 * 60 * 1000L) return cached
        val comm = buildJsonObject {
            androidComm(null, null, includeAuth = false).forEach { (key, value) -> put(key, value) }
            put("uin", "0")
            put("format", "json")
            put("platform", "yqq.json")
        }
        val data = runCatching {
            musicuAndroid("music.getSession.session", "GetSession", buildJsonObject {
                put("uid", "")
                put("vkey", 0)
                put("caller", 0)
            }, comm, null)
        }.getOrElse {
            log("QQ direct android session unavailable: ${it.message}")
            return null
        }
        val session = data["session"] as? JsonObject ?: return null
        val uid = session.firstText("uid")
        val sid = session.firstText("sid")
        if (uid.isBlank() || sid.isBlank()) return null
        return AndroidSession(uid, sid).also {
            androidSession = it
            androidSessionAt = System.currentTimeMillis()
        }
    }

    private fun cgiAndroidWrite(module: String, method: String, param: JsonObject,
        credential: DirectCredential): JsonObject {
        val session = androidSession()
        val comm = androidComm(credential, session)
        val cookie = buildString {
            append("uin=").append(credential.stringMusicId.ifBlank { credential.musicId.toString() })
            append("; qqmusic_uin=").append(credential.stringMusicId.ifBlank { credential.musicId.toString() })
            append("; qm_keyst=").append(credential.musicKey)
            append("; qqmusic_key=").append(credential.musicKey)
        }
        return musicuAndroid(module, method, param, comm, cookie)
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
                put("uin", credential.stringMusicId)
                put("qq", credential.stringMusicId)
                put("authst", credential.musicKey)
                put("tmeLoginType", credential.loginType)
                put("tmeAppID", "qqmusic")
                put("QIMEI36", FALLBACK_QIMEI36)
                put("nettype", "NETWORK_WIFI")
                put("format", "json")
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
        if (rootCode != 0) {
            log("QQ direct signed $module/$method root code $rootCode")
            throw cgiError(rootCode, root)
        }
        val node = root["playlist"] as? JsonObject
            ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐签名响应缺少 playlist")
        val nodeCode = node.firstLong("code", "ret").toInt()
        if (nodeCode != 0) {
            log("QQ direct signed $module/$method node code $nodeCode")
            throw cgiError(nodeCode, node)
        }
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

    private fun webCgi(path: String, params: Map<String, Any>, credential: DirectCredential? = null): JsonObject {
        val url = ("https://c.y.qq.com" + path).toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("outCharset", "utf-8")
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value.toString()) } }
            .build()
        val response = execute(cgiHttp, Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .header("Accept", "application/json")
            .apply { credential?.cookieHeader()?.let { header("Cookie", it) } }
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
