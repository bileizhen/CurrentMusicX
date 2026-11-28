package io.github.currencortex.music.data.kugou

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.kugou.KugouParsing.array
import io.github.currencortex.music.data.kugou.KugouParsing.long
import io.github.currencortex.music.data.kugou.KugouParsing.obj
import io.github.currencortex.music.data.kugou.KugouParsing.text
import io.github.currencortex.music.data.library.Daily
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.roaming.RoamingCatalog
import io.github.currencortex.music.data.song.AudioSource
import io.github.currencortex.music.data.song.KugouTrackRef
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.feature.lyrics.data.LyricsFileCodec
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import java.util.UUID

/** One resolvable file of a song: its hash, the `quality` the tracker expects and the app-level quality label. */
internal data class KugouVariant(val hash: String, val quality: String, val level: String)

/** Best-first ladder limited by what the user asked for; lower tiers stay as fallbacks. */
internal fun kugouVariants(ref: KugouTrackRef, requested: AudioQuality): List<KugouVariant> {
    val viperMaster = KugouVariant(ref.viperMasterHash, "viper_tape", AudioQuality.KUGOU_VIPER_MASTER.value)
    val viperClear = KugouVariant(ref.viperClearHash, "viper_clear", AudioQuality.KUGOU_VIPER_CLEAR.value)
    val viperAtmos = KugouVariant(ref.viperAtmosHash, "viper_atmos", AudioQuality.KUGOU_VIPER_ATMOS.value)
    val dolby = KugouVariant(ref.dolbyHash, "dolby2_448k", AudioQuality.DOLBY.value)
    val superVar = KugouVariant(ref.superHash, "super", AudioQuality.HIRES.value)
    val res = KugouVariant(ref.resHash, "high", AudioQuality.HIRES.value)
    val flac = KugouVariant(ref.sqHash, "flac", AudioQuality.LOSSLESS.value)
    val hq = KugouVariant(ref.hqHash, "320", AudioQuality.EXHIGH.value)
    val std = KugouVariant(ref.hash, "128", AudioQuality.STANDARD.value)
    val candidates = when (requested) {
        AudioQuality.STANDARD -> listOf(std)
        AudioQuality.HIGHER, AudioQuality.EXHIGH -> listOf(hq, std)
        AudioQuality.LOSSLESS -> listOf(flac, hq, std)
        AudioQuality.HIRES -> listOf(superVar, res, flac, hq, std)
        AudioQuality.DOLBY -> listOf(dolby, superVar, res, flac, hq, std)
        AudioQuality.KUGOU_VIPER_ATMOS -> listOf(viperAtmos, dolby, superVar, res, flac, hq, std)
        AudioQuality.KUGOU_VIPER_CLEAR -> listOf(viperClear, superVar, res, flac, hq, std)
        AudioQuality.KUGOU_VIPER_MASTER -> listOf(viperMaster, viperClear, superVar, res, flac, hq, std)
        AudioQuality.AUTO -> listOf(viperClear, dolby, superVar, res, flac, hq, std)
        else -> listOf(superVar, res, flac, hq, std)
    }
    return candidates.filter { it.hash.isNotBlank() }.distinctBy { it.hash to it.quality }
}

/**
 * KuGou direct client built from the 20.9.2 Android protocol (docs/KUGOU_PROTOCOL_ANALYSIS.md). Catalog,
 * search, rankings, comments, MV, lyrics and free-tier playback work anonymously; QR login adds the account's
 * rights, VIP info, cloud playlists and "我喜欢" sync.
 */
class KugouMusicRepository(
    private val tokenStore: TokenStore? = null,
    private val client: KugouClient = KugouClient(),
    private val newMid: () -> String = { KugouProtocol.md5(UUID.randomUUID().toString()) },
    private val gatewayBase: String = KugouProtocol.GATEWAY,
    private val mobileBase: String = KugouProtocol.MOBILE,
) {
    private val mutableState = MutableStateFlow(KugouAccountState())
    val state: StateFlow<KugouAccountState> = mutableState
    private val mutableLiked = MutableStateFlow<Set<Long>>(emptySet())
    val likedState: StateFlow<Set<Long>> = mutableLiked.asStateFlow()
    private val mutableKnownLikes = MutableStateFlow<Set<Long>>(emptySet())
    val knownLikeState: StateFlow<Set<Long>> = mutableKnownLikes.asStateFlow()
    private val mutablePlaylistRevision = MutableStateFlow(0L)
    val playlistRevision: StateFlow<Long> = mutablePlaylistRevision.asStateFlow()

    private val likedLock = Mutex()
    private var device = KugouDevice(newMid())
    private var credentials: KugouCredentials? = null
    private var vipSyncedRevision: Long = -1L
    private var homeSnapshot = KugouHomeSnapshot()
    private var likedCache: List<Song>? = null
    private val likedFileIds = mutableMapOf<Long, Int>()
    private val playlistGlobalIds = mutableMapOf<Int, String>()
    private val playlistVersions = mutableMapOf<Int, Int>()
    private val userPlaylistMeta = mutableMapOf<Int, Playlist>()
    private val collectedKeyToListId = mutableMapOf<String, Int>()
    private val listIdToCollectedKeys = mutableMapOf<Int, MutableSet<String>>()
    private val specialCollectMeta = mutableMapOf<String, SpecialCollectMeta>()
    private var playlistsSynced = false
    private val detailCache = mutableMapOf<String, Pair<Song, Set<AudioQuality>>>()
    private var personalFmMarkList = ""
    private var personalFmLastPoolId = 0
    private var personalFmInitialized = false

    init {
        runCatching {
            val root = tokenStore?.read()?.let { Json.parseToJsonElement(it).jsonObject }
            root?.text("mid")?.takeIf { it.length == 32 }?.let { device = KugouDevice(it, root.text("dfid").ifBlank { "-" }) }
            val userId = root?.long("userId") ?: 0
            val token = root?.text("token").orEmpty()
            val savedAppId = root?.text("appId").orEmpty()
            if (userId > 0 && token.isNotBlank()) {
                if (savedAppId == KugouProtocol.APP_ID) {
                    credentials = KugouCredentials(
                        userId = userId,
                        token = token,
                        nickname = root?.text("nickname").orEmpty(),
                        avatar = root?.text("avatar").orEmpty(),
                        vipType = root?.long("vipType")?.toInt() ?: 0,
                        appId = savedAppId,
                    )
                } else {
                    client.logEvent("KuGou cleared legacy QR session (appId=${savedAppId.ifBlank { "1001" }}); re-login required for appId=${KugouProtocol.APP_ID}")
                }
            }
        }
        publish()
        persist()
    }

    /** Changes whenever the device or account does, so queued audio requests never cross accounts. */
    val audioIdentity: String get() = synchronized(this) {
        "kugou:" + KugouProtocol.md5("${device.mid}:${credentials?.userId ?: 0}:${credentials?.token.orEmpty()}").take(16)
    }
    fun session(): RequestSession = RequestSession("kugou://account", null, state.value.revision)
    fun accepts(song: Song) = song.musicSource == MusicSource.KUGOU && song.kugou != null
    fun cachedHome(): KugouHomeSnapshot = synchronized(this) { homeSnapshot }
    fun cacheHome(snapshot: KugouHomeSnapshot) = synchronized(this) { homeSnapshot = snapshot }

    private fun snapshot() = synchronized(this) { device to credentials }
    private fun requireCredentials(): Pair<KugouDevice, KugouCredentials> {
        val (dev, creds) = snapshot()
        if (creds == null) throw ApiException(ErrorKind.Unauthorized, userMessage = "请先登录酷狗音乐账号")
        return dev to creds
    }

    private suspend fun gateway(
        path: String, params: Map<String, String> = emptyMap(), router: String? = null,
        check: Boolean = true, headers: Map<String, String> = emptyMap(), clientTimeSeconds: Long? = null,
    ): JsonObject {
        val (dev, creds) = snapshot()
        return client.call(gatewayBase, path, params, dev, creds, router, check = check,
            headers = headers, clientTimeSeconds = clientTimeSeconds)
    }

    private suspend fun gatewayPostJson(
        path: String, body: JsonObject, params: Map<String, String> = emptyMap(), router: String? = null,
        check: Boolean = true, headers: Map<String, String> = emptyMap(), clientTimeSeconds: Long? = null,
        includeAuth: Boolean = true, maxSignatureBodyBytes: Int = Int.MAX_VALUE,
    ): JsonObject {
        val (dev, creds) = snapshot()
        return client.postJson(gatewayBase, path, body.toString(), params, dev, creds, router,
            check = check, headers = headers, clientTimeSeconds = clientTimeSeconds,
            includeAuth = includeAuth, maxSignatureBodyBytes = maxSignatureBodyBytes)
    }

    private suspend fun signedAt(base: String, path: String, params: Map<String, String>): JsonObject {
        val (dev, creds) = snapshot()
        return client.call(base, path, params, dev, creds)
    }

    private suspend fun mobile(path: String, params: Map<String, String>): JsonObject =
        client.call(mobileBase, path, params)

    // ---- account ----

    private fun clearAccountCaches() {
        synchronized(this) {
            homeSnapshot = KugouHomeSnapshot()
            likedCache = null
            likedFileIds.clear()
            playlistGlobalIds.clear()
            playlistVersions.clear()
            userPlaylistMeta.clear()
            collectedKeyToListId.clear()
            listIdToCollectedKeys.clear()
            specialCollectMeta.clear()
            playlistsSynced = false
            detailCache.clear()
            personalFmMarkList = ""
            personalFmLastPoolId = 0
            personalFmInitialized = false
        }
        mutableLiked.value = emptySet()
        mutableKnownLikes.value = emptySet()
        mutablePlaylistRevision.value++
    }

    private fun publish() {
        val creds = synchronized(this) { credentials }
        mutableState.value = KugouAccountState(creds != null, creds?.nickname.orEmpty(), creds?.avatar.orEmpty(),
            creds?.userId ?: 0, mutableState.value.revision + 1)
    }

    private fun persist() {
        val (dev, creds) = snapshot()
        tokenStore?.write(buildJsonObject {
            put("mid", dev.mid); put("dfid", dev.dfid)
            creds?.let {
                put("userId", it.userId); put("token", it.token)
                put("nickname", it.nickname); put("avatar", it.avatar)
                put("vipType", it.vipType); put("appId", it.appId)
            }
        }.toString())
    }

    private fun saveCredentials(creds: KugouCredentials) {
        synchronized(this) {
            credentials = creds
            vipSyncedRevision = -1L
        }
        clearAccountCaches()
        persist()
        publish()
    }

    /** Native Android QR login (`KGQRCodeApi`): uses `appid=1005`, `plat=1`, Android `SIGN_KEY`. */
    suspend fun startQrLogin(): KugouQrSession {
        val (dev, _) = snapshot()
        val root = client.call(
            base = KugouProtocol.LOGIN,
            path = "v2/qrcode",
            params = mapOf("type" to "1", "plat" to "1"),
            device = dev,
            key = KugouProtocol.SIGN_KEY,
            appId = KugouProtocol.APP_ID,
            includeAuth = false,
        )
        val key = root.obj("data").text("qrcode").ifBlank { throw ApiException(ErrorKind.Parse) }
        return KugouQrSession(key, KugouProtocol.qrContent(key))
    }

    suspend fun pollQrLogin(key: String): KugouQrResult {
        val (dev, _) = snapshot()
        val data = client.call(
            base = KugouProtocol.LOGIN,
            path = "v2/get_userinfo_qrcode",
            params = mapOf("plat" to "1", "qrcode" to key),
            device = dev,
            key = KugouProtocol.SIGN_KEY,
            appId = KugouProtocol.APP_ID,
            includeAuth = false,
        ).obj("data")
        return when (data.text("status")) {
            "0" -> KugouQrResult(KugouQrStatus.EXPIRED)
            "2" -> KugouQrResult(KugouQrStatus.SCANNED)
            "4" -> {
                val userId = data.long("userid")
                val token = data.text("token")
                if (userId <= 0 || token.isBlank()) throw ApiException(ErrorKind.Parse)
                val creds = KugouCredentials(
                    userId = userId,
                    token = token,
                    nickname = data.text("nickname"),
                    avatar = data.text("pic").replace("http://", "https://"),
                    vipType = data.long("vip_type", "m_type").toInt(),
                    appId = KugouProtocol.APP_ID,
                )
                saveCredentials(creds)
                KugouQrResult(KugouQrStatus.CONFIRMED, creds)
            }
            else -> KugouQrResult(KugouQrStatus.WAITING)
        }
    }

    /** Sends an SMS login verification code via `SendMobileCodeProtocolV7` (`/v8/send_mobile_code`). */
    suspend fun sendSmsCode(mobile: String, countryCode: String = ""): Int {
        val cleanMobile = mobile.trim().filter { it.isDigit() }
        if (cleanMobile.length < 5) throw ApiException(ErrorKind.Server, userMessage = "请输入有效的手机号")
        val cleanCountry = countryCode.trim().filter { it.isDigit() }.takeIf { it.isNotBlank() && it != "86" }.orEmpty()
        val (dev, _) = snapshot()
        val tsSec = client.nowSeconds()
        val aesKey = KugouProtocol.generateLoginAesKey()
        val pkJson = buildJsonObject {
            put("clienttime_ms", tsSec)
            put("key", aesKey)
        }.toString()
        val innerJson = buildJsonObject {
            put("mobile", cleanMobile)
            if (cleanCountry.isNotBlank()) put("country_code", cleanCountry)
        }.toString()
        val body = buildJsonObject {
            put("plat", "1")
            put("businessid", KugouProtocol.SMS_LOGIN_BUSINESS_ID)
            put("clienttime_ms", tsSec)
            put("pk", KugouProtocol.rsaEncryptLogin(pkJson))
            put("mobile", KugouProtocol.maskMobile(cleanMobile))
            put("params", KugouProtocol.aesEncryptLogin(innerJson, aesKey))
        }.toString()
        val root = client.postJson(
            base = gatewayBase,
            path = "v8/send_mobile_code",
            body = body,
            device = dev,
            router = KugouProtocol.LOGIN_ROUTER,
            check = false,
            headers = mapOf("SUPPORT-CALM" to "1"),
            clientTimeSeconds = tsSec,
            includeAuth = false,
        )
        if (root.long("status") == 1L) {
            return root.obj("data").long("count").toInt()
        }
        val errCode = root.long("error_code", "err_code", "errcode").toInt()
        val message = root.text("data", "msg", "error", "errmsg", "info")
            .ifBlank { "发送验证码失败（错误码 $errCode）" }
        throw ApiException(ErrorKind.Server, errCode, message)
    }

    /** Logs in with a phone number and SMS verification code via `UserLogin` (`/v7/login_by_verifycode`). */
    suspend fun loginBySmsCode(
        mobile: String, code: String, countryCode: String = "", selectedUserId: Long? = null,
    ): KugouLoginOutcome {
        val cleanMobile = mobile.trim().filter { it.isDigit() }
        val cleanCode = code.trim()
        if (cleanMobile.length < 5) throw ApiException(ErrorKind.Server, userMessage = "请输入有效的手机号")
        if (cleanCode.isBlank()) throw ApiException(ErrorKind.Server, userMessage = "请输入短信验证码")
        val cleanCountry = countryCode.trim().filter { it.isDigit() }.takeIf { it.isNotBlank() && it != "86" }.orEmpty()
        val (dev, _) = snapshot()
        val nowMs = client.nowMillis()
        val tsSec = nowMs / 1000
        val aesKey = KugouProtocol.generateLoginAesKey()
        val pkJson = buildJsonObject {
            put("clienttime_ms", nowMs)
            put("key", aesKey)
        }.toString()
        val innerJson = buildJsonObject {
            put("mobile", cleanMobile)
            put("code", cleanCode)
            if (cleanCountry.isNotBlank()) put("country_code", cleanCountry)
        }.toString()
        val body = buildJsonObject {
            put("t1", 0)
            put("t2", 0)
            put("clienttime_ms", nowMs)
            put("dfid", dev.dfid.ifBlank { "-" })
            put("plat", 1)
            put("pk", KugouProtocol.rsaEncryptLogin(pkJson))
            put("mobile", KugouProtocol.maskMobile(cleanMobile))
            selectedUserId?.takeIf { it > 0 }?.let { put("userid", it.toString()) }
            put("support_multi", 1)
            put("key", KugouProtocol.loginKey(nowMs))
            put("params", KugouProtocol.aesEncryptLogin(innerJson, aesKey))
            put("need_toneinfo", 1)
        }.toString()
        val root = client.postJson(
            base = gatewayBase,
            path = "v7/login_by_verifycode",
            body = body,
            device = dev,
            router = KugouProtocol.LOGIN_ROUTER,
            check = false,
            headers = mapOf("SUPPORT-CALM" to "1"),
            clientTimeSeconds = tsSec,
            includeAuth = false,
        )
        return parseLoginResponse(root, aesKey)
    }

    /** Logs in with a KuGou account/phone and password via `UserLogin` (`/v9/login_by_pwd`). */
    suspend fun loginByPassword(account: String, password: String): KugouLoginOutcome {
        val cleanAccount = account.trim()
        if (cleanAccount.isBlank()) throw ApiException(ErrorKind.Server, userMessage = "请输入手机号或酷狗账号")
        if (password.isEmpty()) throw ApiException(ErrorKind.Server, userMessage = "请输入密码")
        val (dev, _) = snapshot()
        val nowMs = client.nowMillis()
        val tsSec = nowMs / 1000
        val aesKey = KugouProtocol.generateLoginAesKey()
        val isMobile = KugouProtocol.isChinaMobile(cleanAccount)
        val pkJson = buildJsonObject {
            put("clienttime_ms", nowMs)
            put("key", aesKey)
        }.toString()
        val innerJson = buildJsonObject {
            put("clienttime_ms", nowMs)
            put("pwd", password)
            if (isMobile) put("username", cleanAccount)
        }.toString()
        val body = buildJsonObject {
            put("t1", 0)
            put("t2", 0)
            put("clienttime_ms", nowMs)
            put("dfid", dev.dfid.ifBlank { "-" })
            put("plat", 1)
            put("pk", KugouProtocol.rsaEncryptLogin(pkJson))
            put("username", if (isMobile) KugouProtocol.maskMobile(cleanAccount) else cleanAccount)
            put("support_third", "3")
            put("support_multi", 1)
            put("support_verify", 1)
            put("key", KugouProtocol.loginKey(nowMs))
            put("params", KugouProtocol.aesEncryptLogin(innerJson, aesKey))
            put("need_toneinfo", 1)
        }.toString()
        val root = client.postJson(
            base = gatewayBase,
            path = "v9/login_by_pwd",
            body = body,
            device = dev,
            router = KugouProtocol.LOGIN_ROUTER,
            check = false,
            headers = mapOf("SUPPORT-CALM" to "1"),
            clientTimeSeconds = tsSec,
            includeAuth = false,
        )
        return parseLoginResponse(root, aesKey)
    }

    /** Verifies and saves a user-provided KuGou `userid` + `token` pair. */
    suspend fun loginByToken(userId: Long, token: String): KugouCredentials {
        val cleanToken = token.trim()
        if (userId <= 0 || cleanToken.isBlank()) throw ApiException(ErrorKind.Server, userMessage = "请输入有效的酷狗 UID 和 Token")
        val (dev, _) = snapshot()
        val candidate = KugouCredentials(userId = userId, token = cleanToken)
        // Verify credential validity against cloudlist before persisting.
        val verifyBody = buildJsonObject {
            put("userid", userId)
            put("token", cleanToken)
            put("total_ver", 0)
            put("type", 1)
            put("page", 1)
            put("pagesize", 1)
        }
        client.postJson(
            base = gatewayBase,
            path = "cloudlist.service/v8/get_all_list",
            body = verifyBody.toString(),
            params = mapOf("plat" to "0"),
            device = dev,
            credentials = candidate,
            headers = mapOf("KG-TID" to KugouProtocol.CLOUDLIST_TID),
        )
        val vipRoot = runCatching {
            client.call(
                gatewayBase, "vipos/v1/vip_info/detail/query", mapOf(
                    "clientappid" to KugouProtocol.APP_ID,
                    "clienttoken" to cleanToken,
                    "kugouid" to userId.toString(),
                    "infotype" to "1",
                    "level_flag" to "1",
                    "tone_flag" to "1",
                    "tone_packet" to "1",
                    "nameplate_flag" to "1",
                    "fancy_flag" to "1",
                ), dev, candidate, check = false,
                headers = mapOf("KG-TID" to KugouProtocol.VIP_INFO_TID),
            )
        }.getOrNull()
        val vipData = vipRoot.obj("data")
        val creds = candidate.copy(
            nickname = vipData.text("nickname", "username"),
            avatar = vipData.text("pic", "user_pic").replace("http://", "https://"),
            vipType = vipData.long("vip_type", "m_type").toInt(),
            appId = KugouProtocol.APP_ID,
        )
        saveCredentials(creds)
        return creds
    }

    private fun parseLoginResponse(root: JsonObject, aesKey: String): KugouLoginOutcome {
        if (root.long("status") == 1L) {
            val dataObj = when (val raw = root["data"]) {
                is JsonObject -> raw
                is JsonPrimitive -> runCatching {
                    raw.contentOrNull?.takeIf(String::isNotBlank)?.let { Json.parseToJsonElement(it) as? JsonObject }
                }.getOrNull()
                else -> null
            } ?: throw ApiException(ErrorKind.Parse)

            val tList = dataObj.array("t_list").mapNotNull { entry ->
                val item = entry as? JsonObject ?: return@mapNotNull null
                val uid = item.long("userid")
                if (uid <= 0) null
                else KugouMultiAccountOption(
                    userId = uid,
                    nickname = item.text("nickname", "username").ifBlank { "酷狗用户 $uid" },
                    avatar = item.text("pic").replace("http://", "https://"),
                )
            }
            val userId = dataObj.long("userid")
            val secuParams = dataObj.text("secu_params")
            val token = if (secuParams.isNotBlank()) {
                val decrypted = runCatching { KugouProtocol.aesDecryptLogin(secuParams, aesKey) }.getOrNull()
                    ?: throw ApiException(ErrorKind.Parse, userMessage = "登录凭据解密失败")
                val secuObj = runCatching { Json.parseToJsonElement(decrypted) as? JsonObject }.getOrNull()
                secuObj.text("token")
            } else {
                dataObj.text("token")
            }
            if (userId > 0 && token.isNotBlank()) {
                val creds = KugouCredentials(
                    userId = userId,
                    token = token,
                    nickname = dataObj.text("nickname", "username"),
                    avatar = dataObj.text("pic").replace("http://", "https://"),
                    vipType = dataObj.long("vip_type", "m_type").toInt(),
                    appId = KugouProtocol.APP_ID,
                )
                saveCredentials(creds)
                return KugouLoginOutcome.Success(creds)
            }
            if (tList.isNotEmpty()) return KugouLoginOutcome.MultiAccount(tList)
            throw ApiException(ErrorKind.Parse)
        }
        val errCode = root.long("error_code", "err_code", "errcode").toInt()
        val rawMsg = root.text("data", "error", "msg", "errmsg", "info")
        val message = when (errCode) {
            20028 -> "酷狗要求安全验证，请改用手机验证码或酷狗 App 扫码登录"
            else -> rawMsg.ifBlank { "登录失败（错误码 $errCode）" }
        }
        throw ApiException(ErrorKind.Server, errCode, message)
    }

    suspend fun logout() {
        synchronized(this) { credentials = null }
        clearAccountCaches()
        persist(); publish()
    }

    suspend fun vipInfo(): KugouVipInfo {
        val (_, creds) = requireCredentials()
        val data = gateway("vipos/v1/vip_info/detail/query", mapOf(
            "clientappid" to KugouProtocol.APP_ID,
            "clienttoken" to creds.token,
            "kugouid" to creds.userId.toString(),
            "infotype" to "1",
            "level_flag" to "1",
            "tone_flag" to "1",
            "tone_packet" to "1",
            "nameplate_flag" to "1",
            "fancy_flag" to "1",
        ), headers = mapOf("KG-TID" to KugouProtocol.VIP_INFO_TID)).obj("data")
        val busiVips = data.array("busi_vip").mapNotNull { it as? JsonObject }
        val activeBusi = busiVips.filter { it.long("is_vip") == 1L }
        val hasSvipBusi = activeBusi.any {
            it.text("product_type").equals("svip", ignoreCase = true) ||
                it.text("busi_type").equals("svip", ignoreCase = true) ||
                it.long("vip_type") == 6L
        }
        val isVip = data.long("is_vip") == 1L || data.long("m_is_vip") == 1L || activeBusi.isNotEmpty()
        val rawVipType = data.long("vip_type", "m_type").toInt()
        val effectiveVipType = when {
            hasSvipBusi || data.long("su_vip_y_endtime") > 0L || data.text("su_vip_end_time").isNotBlank() || rawVipType == 6 -> 6
            rawVipType > 0 -> rawVipType
            isVip -> 1
            else -> 0
        }
        val expire = data.text("su_vip_end_time", "vip_end_time", "m_end_time").ifBlank {
            activeBusi.firstNotNullOfOrNull { it.text("vip_end_time", "end_time").takeIf(String::isNotBlank) }.orEmpty()
        }
        val label = when {
            effectiveVipType == 6 -> "超级 VIP"
            isVip -> "酷狗 VIP"
            else -> "普通用户"
        }
        val changed = synchronized(this) {
            vipSyncedRevision = mutableState.value.revision
            val cur = credentials
            if (cur != null && cur.userId == creds.userId && cur.vipType != effectiveVipType) {
                credentials = cur.copy(vipType = effectiveVipType)
                true
            } else false
        }
        if (changed) persist()
        return KugouVipInfo(isVip = isVip, vipType = effectiveVipType, label = label, expireTime = expire)
    }

    // ---- search ----

    suspend fun search(query: String, offset: Int = 0, type: String = "song"): KugouSearchPage {
        val page = (offset / PAGE_SIZE + 1).toString()
        val keyword = query.trim().take(60)
        if (type == "song") {
            val data = gateway("complexsearch/v3/search/song", mapOf("keyword" to keyword, "page" to page, "pagesize" to PAGE_SIZE.toString(),
                "bitrate" to "0", "isfuzzy" to "0", "inputtype" to "0", "platform" to "AndroidFilter", "iscorrection" to "1",
                "privilege_filter" to "0", "filter" to "10")).obj("data")
            val total = data.long("total").toInt()
            return KugouSearchPage(songs = KugouParsing.songs(data.array("lists")), total = total, hasMore = offset + PAGE_SIZE < total)
        }
        val route = when (type) { "playlist" -> "special"; "album" -> "album"; "artist" -> "author"; else -> throw ApiException(ErrorKind.NotFound) }
        val data = signedAt(KugouProtocol.COMPLEX_SEARCH, "v1/search/$route", mapOf("keyword" to keyword, "page" to page,
            "pagesize" to PAGE_SIZE.toString(), "platform" to "AndroidFilter")).obj("data")
        val entries = data.array("lists").mapNotNull { (it as? JsonObject)?.let { item ->
            when (route) { "special" -> KugouParsing.playlist(item); "album" -> KugouParsing.album(item); else -> KugouParsing.artist(item) }
        } }
        val total = data.long("total").toInt()
        return KugouSearchPage(entries = entries, total = total, hasMore = offset + PAGE_SIZE < total)
    }

    suspend fun suggestions(query: String): List<String> {
        val root = client.call(KugouProtocol.SEARCH_TIP, "getSearchTip", mapOf("MusicTipCount" to "8", "MVTipCount" to "0",
            "albumcount" to "0", "keyword" to query.trim().take(60)))
        return root.array("data").flatMap { (it as? JsonObject).array("RecordDatas") }
            .mapNotNull { (it as? JsonObject)?.text("HintInfo")?.takeIf(String::isNotBlank) }.distinct()
    }

    suspend fun hotSearch(): List<String> = mobile("api/v3/search/hot", mapOf("plat" to "0", "count" to "20", "format" to "json"))
        .obj("data").array("info").mapNotNull { (it as? JsonObject)?.text("keyword")?.takeIf(String::isNotBlank) }.distinct()

    // ---- song detail, qualities, playback, lyrics, MV ----

    private suspend fun fetchKmrDetail(song: Song): Pair<Song, Set<AudioQuality>> {
        val ref = song.kugou ?: return song to emptySet()
        val cacheKey = ref.albumAudioId.takeIf { it > 0 }?.toString() ?: ref.hash
        synchronized(this) { detailCache[cacheKey] }?.let { return it }
        if (ref.albumAudioId <= 0 && ref.hash.isBlank()) return song to emptySet()
        val (dev, creds) = snapshot()
        val ts = client.nowSeconds()
        val kmrSong = runCatching {
            val body = buildJsonObject {
                put("appid", KugouProtocol.APP_ID)
                put("clientver", KugouProtocol.CLIENT_VERSION)
                put("mid", dev.mid)
                put("clienttime", ts)
                put("key", "")
                put("dfid", dev.dfid.ifBlank { "-" })
                put("area_code", "1")
                put("show_privilege", "1")
                put("is_publish", "1")
                put("show_album_info", "1")
                put("data", buildJsonArray {
                    add(buildJsonObject {
                        if (ref.albumAudioId > 0) put("album_audio_id", ref.albumAudioId.toString())
                        else put("hash", ref.hash.lowercase())
                    })
                })
            }
            val root = gatewayPostJson("v3/album_audio/audio", body, router = "kmr.service.kugou.com", clientTimeSeconds = ts)
            val firstRow = root.array("data").firstOrNull()
            val item = when (firstRow) {
                is JsonArray -> firstRow.firstOrNull() as? JsonObject
                is JsonObject -> firstRow
                else -> null
            }
            if (item != null) KugouParsing.kmrSongDetail(song, item).first else song
        }.getOrDefault(song)

        val kmrRef = kmrSong.kugou ?: ref
        val result = if (kmrRef.hash.isNotBlank() || kmrRef.albumAudioId > 0) {
            runCatching {
                val privBody = buildJsonObject {
                    put("appid", KugouProtocol.APP_ID.toInt())
                    put("area_code", "1")
                    put("behavior", "play")
                    put("clientver", KugouProtocol.CLIENT_VERSION.toInt())
                    put("need_hash_offset", 1)
                    put("relate", 1)
                    put("qualities", buildJsonArray {
                        PRIVILEGE_QUALITIES.forEach { add(JsonPrimitive(it)) }
                    })
                    put("resource", buildJsonArray {
                        add(buildJsonObject {
                            put("album_audio_id", kmrRef.albumAudioId)
                            put("album_id", kmrRef.albumId.ifBlank { "0" })
                            put("hash", kmrRef.hash.lowercase())
                            put("id", 0)
                            put("name", listOf(kmrSong.artists, kmrSong.name).filter(String::isNotBlank).joinToString(" - "))
                            put("type", "audio")
                        })
                    })
                    put("token", creds?.token.orEmpty())
                    put("userid", creds?.userId ?: 0L)
                    put("vip", if ((creds?.vipType ?: 0) > 0) 1 else 0)
                }
                val privRoot = gatewayPostJson(
                    "v2/get_res_privilege/lite",
                    privBody,
                    router = KugouProtocol.MEDIASTORE_ROUTER,
                    check = false,
                )
                KugouParsing.applyPrivilege(kmrSong, privRoot.array("data"))
            }.getOrElse { kmrSong to KugouParsing.availableQualities(kmrRef) }
        } else {
            kmrSong to KugouParsing.availableQualities(kmrRef)
        }

        synchronized(this) {
            if (detailCache.size > 128) detailCache.clear()
            detailCache[cacheKey] = result
        }
        return result
    }

    private fun defaultQualities(ref: KugouTrackRef): Set<AudioQuality> = KugouParsing.availableQualities(ref)

    suspend fun detail(song: Song): Song = runCatching { fetchKmrDetail(song).first }.getOrDefault(song)

    suspend fun detailById(albumAudioId: Long): Song {
        val placeholder = Song(
            id = albumAudioId, name = "", musicSource = MusicSource.KUGOU,
            kugou = KugouTrackRef(hash = "", albumAudioId = albumAudioId),
        )
        return fetchKmrDetail(placeholder).first
    }

    suspend fun qualities(song: Song): Set<AudioQuality> {
        val ref = song.kugou ?: return emptySet()
        return runCatching { fetchKmrDetail(song).second }.getOrDefault(defaultQualities(ref))
    }

    suspend fun source(song: Song, quality: AudioQuality): AudioSource {
        val rawRef = song.kugou ?: throw ApiException(ErrorKind.NotFound)
        val missingViper = rawRef.viperMasterHash.isBlank() && rawRef.viperClearHash.isBlank() && rawRef.viperAtmosHash.isBlank()
        val needsEnrichment = (rawRef.albumAudioId > 0 || rawRef.hash.isNotBlank()) && (
            (rawRef.hqHash.isBlank() && rawRef.sqHash.isBlank()) ||
                (missingViper && quality in setOf(
                    AudioQuality.AUTO,
                    AudioQuality.KUGOU_VIPER_MASTER,
                    AudioQuality.KUGOU_VIPER_CLEAR,
                    AudioQuality.KUGOU_VIPER_ATMOS,
                    AudioQuality.DOLBY,
                    AudioQuality.HIRES,
                ))
            )
        val enriched = if (needsEnrichment) detail(song) else song
        val ref = enriched.kugou ?: throw ApiException(ErrorKind.NotFound)
        var failure: ApiException? = null
        for (variant in kugouVariants(ref, quality)) {
            try { return resolve(enriched, ref, variant, quality) } catch (e: ApiException) {
                if (e.kind != ErrorKind.AudioUnavailable) throw e
                failure = e
            }
        }
        throw failure ?: ApiException(ErrorKind.AudioUnavailable, userMessage = UNAVAILABLE)
    }

    private suspend fun resolve(song: Song, ref: KugouTrackRef, variant: KugouVariant, requested: AudioQuality): AudioSource {
        val currentRev = state.value.revision
        val needsVipSync = synchronized(this) { credentials != null && vipSyncedRevision != currentRev }
        if (needsVipSync) {
            runCatching { vipInfo() }.onFailure {
                synchronized(this) { vipSyncedRevision = currentRev }
            }
        }
        val (dev, creds) = snapshot()
        val userId = (creds?.userId ?: 0).toString()
        val vipType = creds?.vipType ?: 0
        val params = mapOf(
            "album_id" to ref.albumId.ifBlank { "0" },
            "area_code" to "1",
            "hash" to variant.hash.lowercase(),
            "ssa_flag" to "is_fromtrack",
            "version" to KugouProtocol.CLIENT_VERSION,
            "page_id" to "967177867",
            "quality" to variant.quality,
            "album_audio_id" to ref.albumAudioId.toString(),
            "behavior" to "play",
            "pid" to "2",
            "cmd" to "26",
            "pidversion" to "3001",
            "vipType" to vipType.toString(),
            "ptype" to "0",
            "ppage_id" to "356753938,823673182,967485191",
            "cdnBackup" to "1",
            "kcard" to "0",
            "module" to "",
            "key" to KugouProtocol.songUrlKey(variant.hash, dev.mid, userId),
            "token" to creds?.token.orEmpty(),
        )
        val root = gateway("v5/url", params, router = "trackercdn.kugou.com", check = false)
        val url = playableUrl(root["url"]) ?: playableUrl(root["backupUrl"]) ?: playableUrl(root["backup_url"])
        val status = root.text("status")
        if (status != "1" || url == null) {
            val privStatus = root.text("priv_status")
            val failProcess = root.text("fail_process")
            val errCode = root.long("error_code", "err_code", "errcode").toInt()
            val errMsg = root.text("error", "error_msg", "errmsg", "info")
            client.logEvent("KuGou v5/url denied quality=${variant.quality} status=$status priv=$privStatus fail=$failProcess err=$errCode loggedIn=${creds != null} vipType=$vipType")
            if (creds != null && (errCode in setOf(20018, 20019, 10001) || errMsg.contains("token", ignoreCase = true) || errMsg.contains("登录"))) {
                throw ApiException(ErrorKind.Unauthorized, errCode, "酷狗登录已失效，请前往“我的 → 酷狗音乐账号”重新登录")
            }
            val msg = if (creds != null) "酷狗未授权当前账号播放此歌曲（可能需要酷狗 VIP 或该曲目暂无播放版权）" else UNAVAILABLE
            throw ApiException(ErrorKind.AudioUnavailable, userMessage = msg)
        }
        val channels = if (variant.level == AudioQuality.KUGOU_VIPER_ATMOS.value) 6 else 2
        return AudioSource(url, variant.level, sampleRate = 0, channelCount = channels, format = root.text("extName").ifBlank { "mp3" },
            validForSeconds = 3600, source = "kugou", requestedLevel = requested.value, bitrate = root.long("bitRate"),
            size = root.long("fileSize"), durationMs = root.long("timeLength") * 1000, expectedDurationMs = song.durationMs)
    }

    suspend fun lyrics(song: Song): LyricsDocument {
        val ref = song.kugou ?: throw ApiException(ErrorKind.NotFound)
        val search = signedAt(KugouProtocol.LYRICS, "v1/search", mapOf("hash" to ref.hash.lowercase(), "keyword" to "${song.artists} - ${song.name}".take(240),
            "duration" to song.durationMs.coerceAtLeast(0).toString(), "client" to "android", "ver" to "1", "man" to "yes",
            "album_audio_id" to ref.albumAudioId.toString(), "lrctxt" to "1"))
        val best = search.array("candidates").firstOrNull() as? JsonObject ?: throw ApiException(ErrorKind.NotFound)
        val id = best.text("id").takeIf { it.isNotBlank() } ?: throw ApiException(ErrorKind.NotFound)
        val access = best.text("accesskey").takeIf { it.isNotBlank() } ?: throw ApiException(ErrorKind.NotFound)
        val download = signedAt(KugouProtocol.LYRICS, "download", mapOf("accesskey" to access, "charset" to "utf8", "client" to "mobi",
            "fmt" to "krc", "id" to id, "ver" to "1"))
        val raw = download.text("content").takeIf { it.isNotBlank() } ?: throw ApiException(ErrorKind.NotFound)
        val bytes = try { Base64.getDecoder().decode(raw) } catch (_: IllegalArgumentException) { throw ApiException(ErrorKind.Parse) }
        if (bytes.size > LyricsFileCodec.MAX_BYTES) throw ApiException(ErrorKind.Parse)
        val name = if (download.long("contenttype") == 2L) "lyrics.lrc" else "lyrics.krc"
        return LyricsFileCodec.decode(bytes, name).document
    }

    suspend fun mvUrl(mvHash: String): String {
        val clean = mvHash.trim()
        if (clean.isBlank()) throw ApiException(ErrorKind.NotFound)
        val root = gateway("interface/index", mapOf(
            "cmd" to "123",
            "pid" to "2",
            "ext" to "mp4",
            "backupdomain" to "1",
            "hash" to clean.lowercase(),
            "key" to KugouProtocol.mvKey(clean.lowercase()),
        ), router = "trackermv.kugou.com", check = false)
        val data = root.obj("data") ?: throw ApiException(ErrorKind.NotFound)
        val entry = data.obj(clean.lowercase()) ?: data.obj(clean.uppercase())
            ?: data.values.firstNotNullOfOrNull { it as? JsonObject }
            ?: throw ApiException(ErrorKind.NotFound)
        val url = entry.text("downurl").takeIf { it.startsWith("http") }
            ?: playableUrl(entry["backupdownurl"])
            ?: throw ApiException(ErrorKind.NotFound, userMessage = "未获取到酷狗 MV 播放地址")
        return url
    }

    // ---- comments & favorite count ----

    private suspend fun resolveCommentTarget(song: Song): KugouTrackRef {
        val ref = song.kugou ?: throw ApiException(ErrorKind.NotFound)
        if (ref.audioId > 0) return ref
        val enriched = detail(song).kugou ?: ref
        if (enriched.audioId <= 0) throw ApiException(ErrorKind.NotFound, userMessage = "该歌曲暂无评论信息")
        return enriched
    }

    suspend fun favoriteCount(song: Song): KugouFavoriteCount {
        val ref = song.kugou ?: return KugouFavoriteCount()
        val mixId = ref.albumAudioId.takeIf { it > 0 } ?: detail(song).kugou?.albumAudioId?.takeIf { it > 0 } ?: return KugouFavoriteCount()
        val root = gateway("count/v1/audio/mget_collect", mapOf("mixsongids" to mixId.toString()))
        val first = root.obj("data").array("list").firstOrNull() as? JsonObject ?: return KugouFavoriteCount()
        return KugouFavoriteCount(count = first.long("count"), display = first.text("count_text"))
    }

    suspend fun commentCount(song: Song): Long {
        val ref = resolveCommentTarget(song)
        val (_, creds) = snapshot()
        val ts = client.nowSeconds()
        val root = gateway("commentmain/v1/cmtlist", mapOf(
            "kugouid" to (creds?.userId ?: 0L).toString(),
            "clienttoken" to creds?.token.orEmpty(),
            "key" to KugouProtocol.commentKey(ts),
            "code" to KugouProtocol.COMMENT_SONG_CODE,
            "extdata" to ref.hash.lowercase(),
            "childrenid" to ref.audioId.toString(),
            "mixsongid" to ref.albumAudioId.toString(),
            "p" to "1",
            "pagesize" to "1",
        ), clientTimeSeconds = ts)
        return root.long("combine_count", "count").takeIf { it > 0 }
            ?: root.obj("data").long("combine_count", "count")
    }

    suspend fun comments(song: Song, sort: String = "hot", page: Int = 1, limit: Int = 20): KugouCommentsPage {
        val ref = resolveCommentTarget(song)
        val (_, creds) = snapshot()
        val ts = client.nowSeconds()
        val path = when (sort) {
            "latest", "newest", "time" -> "mcomment/r/v1/rank/newest"
            else -> "mcomment/r/v1/rank/topliked"
        }
        val params = mapOf(
            "kugouid" to (creds?.userId ?: 0L).toString(),
            "clienttoken" to creds?.token.orEmpty(),
            "key" to KugouProtocol.commentKey(ts),
            "code" to KugouProtocol.COMMENT_SONG_CODE,
            "extdata" to ref.hash.lowercase(),
            "childrenid" to ref.audioId.toString(),
            "mixsongid" to ref.albumAudioId.toString(),
            "p" to page.coerceAtLeast(1).toString(),
            "pagesize" to limit.coerceIn(1, 50).toString(),
        )
        val root = gateway(path, params, clientTimeSeconds = ts)
        val listNode = root.array("list").takeIf { it.isNotEmpty() } ?: root.obj("data").array("list")
        val items = listNode.mapNotNull { (it as? JsonObject)?.let(KugouParsing::comment) }
        val total = root.long("combine_count", "count").takeIf { it > 0 }
            ?: root.obj("data").long("combine_count", "count").takeIf { it > 0 }
            ?: items.size.toLong()
        return KugouCommentsPage(comments = items, total = total, hasMore = items.size >= limit)
    }

    suspend fun commentReplies(song: Song, parentId: String, page: Int = 1, limit: Int = 20): KugouCommentsPage {
        val ref = resolveCommentTarget(song)
        val (_, creds) = snapshot()
        val ts = client.nowSeconds()
        val root = gateway("mcomment/v1/hot_replylist", mapOf(
            "kugouid" to (creds?.userId ?: 0L).toString(),
            "clienttoken" to creds?.token.orEmpty(),
            "key" to KugouProtocol.commentKey(ts),
            "code" to KugouProtocol.COMMENT_SONG_CODE,
            "childrenid" to ref.audioId.toString(),
            "tid" to parentId,
            "ver" to "1",
            "p" to page.coerceAtLeast(1).toString(),
            "pagesize" to limit.coerceIn(1, 50).toString(),
        ), clientTimeSeconds = ts)
        val listNode = root.array("list").takeIf { it.isNotEmpty() } ?: root.obj("data").array("list")
        val items = listNode.mapNotNull { (it as? JsonObject)?.let(KugouParsing::comment) }
        val total = root.long("count").takeIf { it > 0 } ?: root.obj("data").long("count").takeIf { it > 0 } ?: items.size.toLong()
        return KugouCommentsPage(comments = items, total = total, hasMore = items.size >= limit)
    }

    // ---- cloud playlists & "我喜欢" (JSON cloudlist.service) ----

    private suspend fun cloudListPost(path: String, body: JsonObject): JsonObject =
        gatewayPostJson("cloudlist.service/$path", body, params = mapOf("plat" to "0"),
            headers = mapOf("KG-TID" to KugouProtocol.CLOUDLIST_TID))

    suspend fun playlists(): List<Playlist> {
        val (_, creds) = requireCredentials()
        val pageSize = 100
        val rawItems = mutableListOf<JsonObject>()
        val result = mutableListOf<Playlist>()
        val seenIds = mutableSetOf<Long>()
        var page = 1
        while (page <= 10) {
            val body = buildJsonObject {
                put("userid", creds.userId)
                put("token", creds.token)
                put("total_ver", 0)
                put("support_per", 1)
                put("support_theme", 1)
                put("type", 2)
                put("page", page)
                put("pagesize", pageSize)
                put("support_pub", 1)
                put("support_set", 1)
                put("support_player", 1)
            }
            val root = cloudListPost("v8/get_all_list", body)
            val data = root.obj("data")
            val items = data.array("info").mapNotNull { it as? JsonObject }
            val pagePlaylists = items.mapNotNull { item -> KugouParsing.cloudPlaylist(item, creds.userId) }
            val totalCount = data.long("list_count").toInt()
            client.logEvent("KuGou v8/get_all_list page=$page raw=${items.size} parsed=${pagePlaylists.size} total=$totalCount")
            rawItems += items
            pagePlaylists.forEach { playlist ->
                if (seenIds.add(playlist.id)) result += playlist
            }
            if (items.isEmpty() || page * pageSize >= totalCount) break
            page++
        }
        synchronized(this) {
            userPlaylistMeta.clear()
            collectedKeyToListId.clear()
            listIdToCollectedKeys.clear()
            rawItems.forEach { item ->
                val listId = item.long("listid").toInt()
                val gid = item.text("global_collection_id")
                val listVer = item.long("list_ver").toInt()
                if (listId > 0) {
                    if (gid.isNotBlank()) playlistGlobalIds[listId] = gid
                    if (listVer > 0) playlistVersions[listId] = listVer
                }
            }
            val parsedById = result.associateBy { it.id.toInt() }
            rawItems.forEach { item ->
                val listId = item.long("listid").toInt()
                val playlist = parsedById[listId] ?: return@forEach
                if (!playlist.nativeOwned && listId > 0) {
                    val keys = collectKeysFor(
                        specialId = "",
                        creatorUid = item.long("list_create_userid"),
                        createListId = item.long("list_create_listid"),
                        globalId = item.text("list_create_gid", "global_collection_id"),
                        extraGlobalId = item.text("global_collection_id"),
                    )
                    indexCollectedListLocked(listId, keys)
                }
            }
            result.forEach { userPlaylistMeta[it.id.toInt()] = it }
            playlistsSynced = true
        }
        return result
    }

    private fun collectKeysFor(
        specialId: String,
        creatorUid: Long = 0L,
        createListId: Long = 0L,
        globalId: String = "",
        extraGlobalId: String = "",
    ): List<String> = buildList {
        if (specialId.isNotBlank()) add("special:$specialId")
        if (globalId.isNotBlank()) add("gid:$globalId")
        if (extraGlobalId.isNotBlank()) add("gid:$extraGlobalId")
        if (creatorUid > 0L && createListId > 0L) add("src:$creatorUid:$createListId")
        if (createListId > 0L) add("list:$createListId")
    }

    private fun indexCollectedListLocked(listId: Int, keys: List<String>) {
        if (listId <= 0 || keys.isEmpty()) return
        val bucket = listIdToCollectedKeys.getOrPut(listId) { mutableSetOf() }
        keys.forEach { key ->
            collectedKeyToListId[key] = listId
            bucket += key
        }
    }

    private fun removeListIndexesLocked(listId: Int) {
        userPlaylistMeta.remove(listId)
        playlistGlobalIds.remove(listId)
        playlistVersions.remove(listId)
        listIdToCollectedKeys.remove(listId)?.forEach { collectedKeyToListId.remove(it, listId) }
        collectedKeyToListId.entries.removeAll { it.value == listId }
    }

    private suspend fun ensurePlaylistsSynced() {
        if (state.value.loggedIn && !synchronized(this) { playlistsSynced }) {
            runCatching { playlists() }
        }
    }

    private fun findCollectedListIdLocked(playlist: Playlist): Int? {
        val remote = playlist.remoteId.ifBlank { playlist.id.toString() }
        collectedKeyToListId["special:$remote"]?.let { return it }
        specialCollectMeta[remote]?.let { meta ->
            collectKeysFor(meta.specialId, meta.creatorUid, meta.createListId, meta.globalSpecialId)
                .firstNotNullOfOrNull { collectedKeyToListId[it] }
                ?.let { return it }
        }
        val numericId = remote.toIntOrNull() ?: playlist.id.toInt()
        if (numericId > 0) {
            val cached = userPlaylistMeta[numericId]
            if (cached != null && !cached.nativeOwned) return numericId
            if (playlist.nativeSubscribed && numericId in listIdToCollectedKeys) return numericId
        }
        return null
    }

    suspend fun userPlaylistPage(
        listId: Int,
        name: String = "酷狗歌单",
        page: Int = 1,
        pageSize: Int = 300,
    ): KugouUserPlaylistPage {
        val (_, creds) = requireCredentials()
        var meta = synchronized(this) { userPlaylistMeta[listId] }
        if (meta == null && name == "酷狗歌单") {
            runCatching { playlists() }
            meta = synchronized(this) { userPlaylistMeta[listId] }
        }
        val safePage = page.coerceAtLeast(1)
        val cappedPageSize = pageSize.coerceIn(1, 300)
        val globalCollectionId = synchronized(this) { playlistGlobalIds[listId] }
            ?: "collection_3_${creds.userId}_${listId}_0"
        val root = gateway(
            "pubsongs/v2/get_other_list_file_nofilt",
            mapOf(
                "global_collection_id" to globalCollectionId,
                "page" to safePage.toString(),
                "pagesize" to cappedPageSize.toString(),
                "area_code" to "1",
            ),
        )
        val data = root.obj("data")
        val rawItems = data.array("songs").takeIf { it.isNotEmpty() } ?: data.array("info")
        val rawCount = rawItems.size
        val songs = KugouParsing.songs(rawItems)
        val serverCount = data.long("count").toInt()
        val total = serverCount.coerceAtLeast(songs.size)
        val fetchedEnd = (safePage - 1) * cappedPageSize + rawCount
        val hasMore = rawCount > 0 && ((serverCount > 0 && fetchedEnd < serverCount) || (serverCount <= 0 && rawCount >= cappedPageSize))
        val resolvedName = meta?.name?.takeIf(String::isNotBlank) ?: name
        val resolvedCover = meta?.cover?.takeIf(String::isNotBlank)
            ?: songs.firstOrNull { it.cover.isNotBlank() }?.cover.orEmpty()
        val playlist = Playlist(
            id = listId.toLong(),
            name = resolvedName,
            description = meta?.description.orEmpty(),
            cover = resolvedCover,
            source = "kugou",
            ownerId = meta?.ownerId ?: creds.userId,
            count = total,
            songs = songs,
            nativeOwned = meta?.nativeOwned ?: true,
            nativeLiked = meta?.nativeLiked ?: (listId == KugouProtocol.LIKED_LIST_ID),
            nativeSubscribed = meta?.nativeSubscribed ?: false,
            remoteId = listId.toString(),
        )
        return KugouUserPlaylistPage(playlist = playlist, hasMore = hasMore)
    }

    suspend fun userPlaylist(listId: Int, name: String = "酷狗歌单", page: Int = 1, pageSize: Int = 300): Playlist =
        userPlaylistPage(listId, name, page, pageSize).playlist

    suspend fun likedSongs(fresh: Boolean = false): List<Song> = likedLock.withLock {
        if (!fresh) synchronized(this) { likedCache }?.let { return@withLock it }
        val collected = mutableListOf<Song>()
        var page = 1
        while (page <= 50) {
            val pageResult = userPlaylistPage(KugouProtocol.LIKED_LIST_ID, "我喜欢", page = page, pageSize = 300)
            collected += pageResult.playlist.songs
            if (!pageResult.hasMore) break
            page++
        }
        val songs = collected.distinctBy(Song::id)
        synchronized(this) {
            likedCache = songs
            likedFileIds.clear()
            songs.forEach { song -> song.kugou?.fileId?.takeIf { it > 0 }?.let { likedFileIds[song.id] = it } }
        }
        val ids = songs.map(Song::id).toSet()
        mutableLiked.value = ids
        mutableKnownLikes.value = ids
        songs
    }

    suspend fun likedSongCount(): Int {
        val (_, creds) = snapshot()
        if (creds == null) throw ApiException(ErrorKind.Unauthorized, userMessage = "请先登录酷狗音乐账号")
        return likedSongs().size
    }

    suspend fun refreshLiked(song: Song): Boolean {
        if (!state.value.loggedIn) return false
        val songs = likedSongs(fresh = false)
        val liked = songs.any { it.id == song.id || (song.kugou?.hash?.isNotBlank() == true && it.kugou?.hash.equals(song.kugou.hash, true)) }
        mutableKnownLikes.value = mutableKnownLikes.value + song.id
        if (liked) mutableLiked.value = mutableLiked.value + song.id
        return liked
    }

    suspend fun refreshLiked(songs: List<Song>) {
        if (!state.value.loggedIn || songs.isEmpty()) return
        val liked = likedSongs(fresh = false)
        val likedIds = liked.map(Song::id).toSet()
        val likedHashes = liked.mapNotNull { it.kugou?.hash?.uppercase()?.takeIf(String::isNotBlank) }.toSet()
        val matched = songs.filter { it.id in likedIds || it.kugou?.hash?.uppercase() in likedHashes }.map(Song::id).toSet()
        mutableKnownLikes.value = mutableKnownLikes.value + songs.map(Song::id)
        mutableLiked.value = (mutableLiked.value - songs.map(Song::id).toSet()) + matched
    }

    suspend fun setLiked(song: Song, liked: Boolean) {
        if (liked) addToPlaylist(KugouProtocol.LIKED_LIST_ID, listOf(song))
        else {
            val fileId = synchronized(this) { likedFileIds[song.id] }
                ?: likedSongs(fresh = true).firstOrNull {
                    it.id == song.id || (song.kugou?.hash?.isNotBlank() == true && it.kugou?.hash.equals(song.kugou.hash, true))
                }?.kugou?.fileId
            if (fileId != null && fileId > 0) {
                removeFromPlaylist(KugouProtocol.LIKED_LIST_ID, listOf(fileId))
            }
            synchronized(this) {
                likedFileIds.remove(song.id)
                likedCache = likedCache?.filterNot {
                    it.id == song.id || (song.kugou?.hash?.isNotBlank() == true && it.kugou?.hash.equals(song.kugou.hash, true))
                }
            }
        }
        mutableKnownLikes.value = mutableKnownLikes.value + song.id
        mutableLiked.value = if (liked) mutableLiked.value + song.id else mutableLiked.value - song.id
        mutablePlaylistRevision.value++
    }

    suspend fun toggleLiked(song: Song, desiredLiked: Boolean? = null): Boolean {
        val current = desiredLiked?.not() ?: (song.id in mutableLiked.value || refreshLiked(song))
        val target = desiredLiked ?: !current
        setLiked(song, target)
        return target
    }

    suspend fun createPlaylist(name: String): Int {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "歌单名称不能为空" }
        val (_, creds) = requireCredentials()
        val body = buildJsonObject {
            put("userid", creds.userId)
            put("token", creds.token)
            put("total_ver", 0)
            put("source", 1)
            put("name", clean)
            put("type", 0)
        }
        val root = cloudListPost("v5/add_list", body)
        mutablePlaylistRevision.value++
        return root.obj("data").long("listid").toInt()
    }

    suspend fun renamePlaylist(listId: Int, name: String) {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "歌单名称不能为空" }
        val (_, creds) = requireCredentials()
        val body = buildJsonObject {
            put("userid", creds.userId)
            put("token", creds.token)
            put("listid", listId)
            put("name", clean)
        }
        val root = cloudListPost("v4/modify_list", body)
        synchronized(this) {
            userPlaylistMeta[listId]?.let { userPlaylistMeta[listId] = it.copy(name = clean) }
        }
        mutablePlaylistRevision.value++
    }

    suspend fun isPlaylistSubscribed(playlist: Playlist): Boolean {
        if (playlist.nativeOwned || playlist.nativeLiked) return false
        ensurePlaylistsSynced()
        return playlist.nativeSubscribed || synchronized(this) { findCollectedListIdLocked(playlist) != null }
    }

    suspend fun subscribePlaylist(playlist: Playlist): Int {
        val (_, creds) = requireCredentials()
        if (playlist.nativeOwned || playlist.nativeLiked) throw ApiException(ErrorKind.Forbidden)
        val specialKey = playlist.remoteId.ifBlank { playlist.id.toString() }
        ensurePlaylistsSynced()
        synchronized(this) { findCollectedListIdLocked(playlist) }?.let { return it }
        var meta = synchronized(this) { specialCollectMeta[specialKey] }
        if (meta == null && specialKey.toLongOrNull() != null) {
            val info = mobile("api/v3/special/info", mapOf("specialid" to specialKey)).obj("data")
            meta = SpecialCollectMeta(
                specialId = specialKey,
                creatorUid = info.long("suid", "userid"),
                createListId = info.long("slid").takeIf { it > 0 } ?: (specialKey.toLongOrNull() ?: 0L),
                globalSpecialId = info.text("global_specialid", "global_collection_id", "gid"),
            )
            synchronized(this) { specialCollectMeta[specialKey] = meta }
        }
        val creatorUid = meta?.creatorUid ?: playlist.ownerId
        val createListId = meta?.createListId ?: specialKey.toLongOrNull() ?: playlist.id
        val globalId = meta?.globalSpecialId.orEmpty()
        val body = buildJsonObject {
            put("userid", creds.userId)
            put("token", creds.token)
            put("total_ver", 0)
            put("name", playlist.name)
            put("type", 1)
            put("source", 1)
            put("list_create_userid", creatorUid)
            if (globalId.isNotBlank()) {
                put("list_create_gid", globalId)
                put("list_create_listid", 0)
            } else {
                put("list_create_listid", createListId)
            }
        }
        val root = cloudListPost("v5/add_list", body)
        val data = root.obj("data")
        val newListId = data.long("listid").toInt()
        val newGlobalId = data.text("global_collection_id")
        synchronized(this) {
            if (newListId > 0) {
                if (newGlobalId.isNotBlank()) playlistGlobalIds[newListId] = newGlobalId
                indexCollectedListLocked(
                    newListId,
                    collectKeysFor(specialKey, creatorUid, createListId, globalId, newGlobalId),
                )
                userPlaylistMeta[newListId] = playlist.copy(
                    id = newListId.toLong(),
                    ownerId = creatorUid,
                    songs = emptyList(),
                    nativeOwned = false,
                    nativeLiked = false,
                    nativeSubscribed = true,
                    remoteId = newListId.toString(),
                )
            }
            playlistsSynced = false
        }
        mutablePlaylistRevision.value++
        return newListId
    }

    suspend fun unsubscribePlaylist(playlist: Playlist) {
        if (playlist.nativeLiked || playlist.nativeOwned) throw ApiException(ErrorKind.Forbidden)
        requireCredentials()
        ensurePlaylistsSynced()
        val listId = synchronized(this) { findCollectedListIdLocked(playlist) }
            ?: playlist.remoteId.toIntOrNull()?.takeIf { it > 0 && playlist.nativeSubscribed }
            ?: throw ApiException(ErrorKind.NotFound, userMessage = "未找到已收藏的酷狗歌单")
        deletePlaylist(listId, listType = 1)
    }

    suspend fun deletePlaylist(listId: Int, listType: Int? = null) {
        val (_, creds) = requireCredentials()
        if (listId <= 0 || listId == KugouProtocol.LIKED_LIST_ID) throw ApiException(ErrorKind.Forbidden)
        val type = listType ?: if (synchronized(this) { userPlaylistMeta[listId]?.nativeOwned } == false) 1 else 0
        val body = buildJsonObject {
            put("userid", creds.userId)
            put("token", creds.token)
            put("listid", listId)
            put("total_ver", 0)
            put("type", type)
        }
        cloudListPost("v2/delete_list", body)
        synchronized(this) {
            removeListIndexesLocked(listId)
        }
        mutablePlaylistRevision.value++
    }

    suspend fun addToPlaylist(listId: Int, songs: List<Song>) {
        if (songs.isEmpty()) return
        val (_, creds) = requireCredentials()
        val entries = songs.mapNotNull { song ->
            val ref = song.kugou ?: return@mapNotNull null
            if (ref.hash.isBlank()) return@mapNotNull null
            song to ref
        }
        if (entries.isEmpty()) throw ApiException(ErrorKind.NotFound, userMessage = "缺少酷狗歌曲信息，无法添加到歌单")
        val (listVer, baseSort) = synchronized(this) {
            (playlistVersions[listId] ?: 0) to (userPlaylistMeta[listId]?.count ?: 0).coerceAtLeast(0)
        }
        val body = buildJsonObject {
            put("userid", creds.userId.toString())
            put("token", creds.token)
            put("listid", listId)
            put("list_ver", listVer)
            put("type", 0)
            put("slow_upload", 0)
            put("scene", "false;")
            put("data", buildJsonArray {
                entries.forEachIndexed { index, (song, ref) ->
                    val displayName = listOf(song.artists, song.name)
                        .filter(String::isNotBlank)
                        .joinToString(" - ")
                        .ifBlank { song.name }
                    add(buildJsonObject {
                        put("number", 1)
                        put("name", displayName)
                        put("hash", ref.hash.lowercase())
                        put("size", 0)
                        put("sort", baseSort + index + 1)
                        put("timelen", song.durationMs.coerceAtLeast(0L))
                        put("bitrate", 128)
                        put("album_id", ref.albumId.ifBlank { "0" })
                        put("mixsongid", ref.albumAudioId)
                    })
                }
            })
        }
        val data = cloudListPost("v6/add_song", body).obj("data")
        val newListVer = data.long("list_ver").toInt()
        val infoItems = data.array("info").mapNotNull { it as? JsonObject }
        val firstInfo = infoItems.firstOrNull()
        val firstFileId = firstInfo?.long("fileid", "file_id")?.toInt() ?: 0
        val firstCode = firstInfo?.takeIf { "code" in it }?.long("code")
        val firstMsg = firstInfo?.text("error", "error_msg", "errmsg", "msg").orEmpty()
        client.logEvent(
            "KuGou v6/add_song result listId=$listId ver=$newListVer info=${infoItems.size} fileId=$firstFileId code=${firstCode ?: 1}" +
                if (firstMsg.isNotBlank()) " msg=$firstMsg" else "",
        )
        synchronized(this) {
            if (newListVer > 0) playlistVersions[listId] = newListVer
            if (listId == KugouProtocol.LIKED_LIST_ID) {
                infoItems.forEach { item ->
                    val fileId = item.long("fileid", "file_id").toInt()
                    if (fileId <= 0) return@forEach
                    val respHash = item.text("hash")
                    val respMixId = item.long("mixsongid")
                    val matched = entries.firstOrNull { (_, ref) ->
                        (respMixId > 0 && ref.albumAudioId == respMixId) ||
                            (respHash.isNotBlank() && ref.hash.equals(respHash, ignoreCase = true))
                    }?.first
                    if (matched != null) likedFileIds[matched.id] = fileId
                }
                val addedSongs = entries.map { (song, ref) ->
                    val fid = likedFileIds[song.id] ?: 0
                    if (fid > 0) song.copy(kugou = ref.copy(fileId = fid)) else song
                }
                likedCache = likedCache?.let { existing ->
                    val addedIds = addedSongs.map(Song::id).toSet()
                    val addedHashes = addedSongs.mapNotNull { it.kugou?.hash?.lowercase()?.takeIf(String::isNotBlank) }.toSet()
                    addedSongs + existing.filterNot {
                        it.id in addedIds || it.kugou?.hash?.lowercase() in addedHashes
                    }
                }
                val addedIds = addedSongs.map(Song::id).toSet()
                mutableLiked.value = mutableLiked.value + addedIds
                mutableKnownLikes.value = mutableKnownLikes.value + addedIds
            }
        }
        mutablePlaylistRevision.value++
    }

    suspend fun removeSongFromPlaylist(listId: Int, song: Song) {
        val fileId = song.kugou?.fileId?.takeIf { it > 0 }
            ?: userPlaylist(listId).songs.firstOrNull {
                it.id == song.id || (song.kugou?.hash?.isNotBlank() == true && it.kugou?.hash.equals(song.kugou.hash, true))
            }?.kugou?.fileId?.takeIf { it > 0 }
            ?: throw ApiException(ErrorKind.NotFound)
        removeFromPlaylist(listId, listOf(fileId))
    }

    suspend fun removeFromPlaylist(listId: Int, fileIds: List<Int>) {
        val valid = fileIds.filter { it > 0 }
        if (valid.isEmpty()) return
        val (_, creds) = requireCredentials()
        val listVer = synchronized(this) { playlistVersions[listId] ?: 0 }
        val body = buildJsonObject {
            put("userid", creds.userId)
            put("token", creds.token)
            put("listid", listId)
            put("list_ver", listVer)
            put("type", 0)
            put("data", buildJsonArray {
                valid.forEach { id -> add(buildJsonObject { put("fileid", id) }) }
            })
        }
        val data = cloudListPost("v4/delete_songs", body).obj("data")
        val newListVer = data.long("list_ver").toInt()
        val validSet = valid.toSet()
        synchronized(this) {
            if (newListVer > 0) playlistVersions[listId] = newListVer
            if (listId == KugouProtocol.LIKED_LIST_ID) {
                likedCache = likedCache?.filterNot { it.kugou?.fileId in validSet }
                likedFileIds.entries.removeAll { it.value in validSet }
            }
        }
        mutablePlaylistRevision.value++
    }

    // ---- discovery & catalog ----

    suspend fun daily(): Daily {
        val data = gateway("everydayrec.service/v2/everyday_song_recommend", mapOf("platform" to "android")).obj("data")
        val songs = KugouParsing.songs(data.array("song_list"))
        return Daily(songs = songs, forYou = songs.drop(10), artists = songs.map { it.artists }.filter(String::isNotBlank).distinct().take(12))
    }

    /** Fetch KuGou "猜你喜欢" modes & scene pools (`SceneModeListProtocol`, with built-in defaults fallback). */
    suspend fun personalFmCatalog(): RoamingCatalog {
        val (dev, creds) = snapshot()
        val ts = client.nowSeconds()
        val body = buildJsonObject {
            put("userid", creds?.userId ?: 0L)
            put("area_code", "1")
            put("mid", dev.mid)
            put("platform", "android")
            put("clientver", KugouProtocol.CLIENT_VERSION.toInt())
            put("uuid", dev.mid)
            put("clienttime", ts.toString())
            put("recall_type", "scene_id")
            put("pagesize", 50)
            put("page", 1)
            put("appid", KugouProtocol.APP_ID.toLong())
            put("module_id", 15)
            put("ky_extra", buildJsonArray {
                add(buildJsonObject { put("key", "show_type"); put("val", "1") })
            })
        }
        val queryParams = mapOf(
            "area_code" to "1",
            "debug_mode" to "2",
            "module_id" to "15",
            "module_key" to "pfm_scene_list",
            "platform" to "android",
            "serverid" to "2006",
            "servertime" to ts.toString(),
        )
        val root = gatewayPostJson("kyproxyrec/v2/client/pfm_scene_list_new", body, queryParams,
            clientTimeSeconds = ts)
        return KugouParsing.parseRoamingCatalog(root)
    }

    /** Fetch recommended tracks for the given KuGou `song_pool_id` (`persnfm.service/personal_recommend`). */
    suspend fun personalFmSongs(
        songPoolId: Int = 0,
        excludedHashes: List<String> = emptyList(),
        excludedIds: List<Long> = emptyList(),
    ): List<Song> {
        val (dev, creds) = snapshot()
        val ts = client.nowSeconds()
        val (action, markList) = synchronized(this) {
            val nextAction = when {
                !personalFmInitialized -> "login"
                songPoolId != personalFmLastPoolId -> "change_song_pool"
                else -> "next"
            }
            nextAction to personalFmMarkList
        }
        val hashList = excludedHashes.asSequence().filter(String::isNotBlank).distinct().toList().takeLast(30)
        val body = buildJsonObject {
            put("appid", KugouProtocol.APP_ID)
            put("clientver", KugouProtocol.CLIENT_VERSION)
            put("platform", "android")
            put("mid", dev.mid)
            put("clienttime", ts.toString())
            put("key", KugouProtocol.commentKey(ts))
            put("userid", creds?.userId ?: 0L)
            put("area_code", "1")
            put("action", action)
            put("module_id", 1)
            put("playlist_ver", 2)
            put("mark_list", markList)
            put("hash_like_list", hashList.joinToString("-"))
            put("callerid", "0")
            put("song_pool_id", songPoolId)
            put("peak_mode", "0")
            put("mode", "normal")
            put("active_swtich", "off")
            put("item_is_long_audio", 0)
            put("fresh_mode", 0)
            put("recommend_source_locked", "0")
            put("rec_song_version", 1)
        }
        val root = gatewayPostJson(
            path = "persnfm.service/personal_recommend",
            body = body,
            params = mapOf("fakem" to ""),
            clientTimeSeconds = ts,
            includeAuth = false,
            maxSignatureBodyBytes = 256,
        )
        val data = root.obj("data")
        val nextMark = data.text("mark_list")
        synchronized(this) {
            personalFmInitialized = true
            personalFmLastPoolId = songPoolId
            if (nextMark.isNotBlank()) personalFmMarkList = nextMark
        }
        val songs = KugouParsing.songs(data.array("song_list")).filterNot { it.id in excludedIds }
        if (songs.isNotEmpty() || songPoolId != 0) return songs
        return fallbackRadio(excludedIds)
    }

    suspend fun radio(excluded: List<Long> = emptyList()): List<Song> =
        runCatching { personalFmSongs(songPoolId = 0, excludedIds = excluded) }
            .getOrElse { fallbackRadio(excluded) }

    private suspend fun fallbackRadio(excluded: List<Long>): List<Song> {
        val dailySongs = runCatching { daily().songs }.getOrDefault(emptyList())
        val fresh = dailySongs.filterNot { it.id in excluded }
        if (fresh.isNotEmpty()) return fresh.shuffled()
        return newSongs().filterNot { it.id in excluded }.shuffled()
    }

    suspend fun categories(): List<KugouCategory> =
        mobile("api/v3/tag/recommend", mapOf("showtype" to "3", "apiver" to "2", "plat" to "0"))
            .obj("data").array("info").mapNotNull { (it as? JsonObject)?.let(KugouParsing::category) }

    suspend fun categoryPlaylists(tagId: String = "0", offset: Int = 0, limit: Int = PAGE_SIZE): KugouPlaylistPage {
        val page = (offset / limit.coerceAtLeast(1) + 1).toString()
        val data = mobile("api/v3/tag/specialList", mapOf(
            "tagid" to tagId.ifBlank { "0" },
            "page" to page,
            "pagesize" to limit.coerceIn(1, 50).toString(),
            "sort" to "5",
            "plat" to "0",
            "ugc" to "1",
        )).obj("data")
        val playlists = data.array("info").mapNotNull { (it as? JsonObject)?.let(KugouParsing::tagPlaylist) }
        val total = data.long("total").toInt().coerceAtLeast(playlists.size)
        return KugouPlaylistPage(playlists = playlists, total = total, hasMore = offset + playlists.size < total && playlists.isNotEmpty())
    }

    suspend fun recommendedPlaylists(limit: Int = 12): List<Playlist> =
        categoryPlaylists(tagId = "0", offset = 0, limit = limit).playlists

    suspend fun ranks(): List<KugouRank> = mobile("api/v3/rank/list", mapOf("withsong" to "0", "plat" to "2"))
        .obj("data").array("info").mapNotNull { (it as? JsonObject)?.let(KugouParsing::rank) }

    suspend fun rankSongs(id: String, offset: Int = 0): KugouSongPage {
        val data = mobile("api/v3/rank/song", mapOf("rankid" to id, "page" to (offset / PAGE_SIZE + 1).toString(),
            "pagesize" to PAGE_SIZE.toString(), "volid" to "", "json" to "true")).obj("data")
        val total = data.long("total").toInt()
        return KugouSongPage(KugouParsing.songs(data.array("info")), total, offset + PAGE_SIZE < total)
    }

    suspend fun newSongs(): List<Song> = KugouParsing.songs(mobile("api/v3/rank/newsong", mapOf("page" to "1", "pagesize" to "30"))
        .obj("data").array("info"))

    suspend fun playlist(id: String, offset: Int = 0): Playlist {
        val info = mobile("api/v3/special/info", mapOf("specialid" to id)).obj("data")
        val songs = mobile("api/v3/special/song", mapOf("specialid" to id, "page" to (offset / PAGE_SIZE + 1).toString(),
            "pagesize" to PAGE_SIZE.toString(), "plat" to "0", "version" to "8000")).obj("data")
        val suid = info.long("suid", "userid")
        val slid = info.long("slid").takeIf { it > 0 } ?: (id.toLongOrNull() ?: 0L)
        val globalSpecialId = info.text("global_specialid", "global_collection_id", "gid")
        synchronized(this) {
            specialCollectMeta[id] = SpecialCollectMeta(id, suid, slid, globalSpecialId)
        }
        val loggedIn = state.value.loggedIn
        if (loggedIn) ensurePlaylistsSynced()
        val matchedListId = if (loggedIn) synchronized(this) {
            collectKeysFor(id, suid, slid, globalSpecialId).firstNotNullOfOrNull { collectedKeyToListId[it] }
        } else null
        val owned = loggedIn && suid > 0L && suid == state.value.userId
        val entry = KugouCatalogEntry(id, info.text("specialname").ifBlank { "酷狗歌单" }, KugouParsing.cover(info.text("imgurl")),
            info.text("nickname"), "playlist", info.long("songcount").toInt())
        return KugouParsing.playlistModel(entry.copy(count = maxOf(entry.count, songs.long("total").toInt())),
            KugouParsing.songs(songs.array("info")), info.text("intro")).copy(
            ownerId = suid,
            nativeOwned = owned,
            nativeSubscribed = matchedListId != null,
            nativeSubscriptionKnown = loggedIn,
        )
    }

    suspend fun playlistSongs(id: String, offset: Int): KugouSongPage {
        val data = mobile("api/v3/special/song", mapOf("specialid" to id, "page" to (offset / PAGE_SIZE + 1).toString(),
            "pagesize" to PAGE_SIZE.toString(), "plat" to "0", "version" to "8000")).obj("data")
        val total = data.long("total").toInt()
        return KugouSongPage(KugouParsing.songs(data.array("info")), total, offset + PAGE_SIZE < total)
    }

    suspend fun album(id: String): KugouAlbumDetail {
        val info = mobile("api/v3/album/info", mapOf("albumid" to id)).obj("data")
        val name = info.text("albumname")
        val songs = KugouParsing.songs(mobile("api/v3/album/song", mapOf("albumid" to id, "page" to "1", "pagesize" to "200")).obj("data").array("info"))
            .map { if (it.album.isBlank()) it.copy(album = name, albumId = id.toLongOrNull() ?: it.albumId) else it }
        val entry = KugouCatalogEntry(id, name, KugouParsing.cover(info.text("imgurl")), info.text("singername"), "album", songs.size)
        return KugouAlbumDetail(entry, info.text("intro"), songs)
    }

    suspend fun artist(id: String, offset: Int = 0): KugouArtistDetail {
        val info = mobile("api/v3/singer/info", mapOf("singerid" to id)).obj("data")
        val page = mobile("api/v3/singer/song", mapOf("singerid" to id, "page" to (offset / PAGE_SIZE + 1).toString(),
            "pagesize" to PAGE_SIZE.toString(), "sorttype" to "0")).obj("data")
        val total = page.long("total").toInt()
        return KugouArtistDetail(id, info.text("singername"), KugouParsing.cover(info.text("imgurl")), info.text("intro"),
            info.long("songcount").toInt(), info.long("albumcount").toInt(), KugouParsing.songs(page.array("info")), offset + PAGE_SIZE < total)
    }

    suspend fun artistAlbums(id: String, offset: Int = 0): List<KugouCatalogEntry> =
        mobile("api/v3/singer/album", mapOf("singerid" to id, "page" to (offset / PAGE_SIZE + 1).toString(), "pagesize" to PAGE_SIZE.toString()))
            .obj("data").array("info").mapNotNull { (it as? JsonObject)?.let(KugouParsing::album) }

    private fun playableUrl(value: JsonElement?): String? = when (value) {
        is JsonArray -> value.firstNotNullOfOrNull { playableUrl(it) }
        is JsonPrimitive -> value.contentOrNull?.takeIf { it.startsWith("http") }
        else -> null
    }

    private data class SpecialCollectMeta(
        val specialId: String,
        val creatorUid: Long,
        val createListId: Long,
        val globalSpecialId: String,
    )

    private companion object {
        const val PAGE_SIZE = 30
        const val UNAVAILABLE = "酷狗未提供完整播放地址，VIP 歌曲请先在“我的”页面登录酷狗账号"
        val PRIVILEGE_QUALITIES = listOf(
            "128", "320", "flac", "high", "dolby", "dolby2_448k", "dolby2_1500k",
            "viper_atmos", "viper_tape", "viper_clear",
        )
    }
}
