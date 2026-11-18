package io.github.currencortex.music.data.qqmusic

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.library.Daily
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.model.LyricsDocument
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.security.MessageDigest

/**
 * QQ desktop/mobile uses the official Tencent HTTP CGI directly. The legacy ApiClient path is
 * retained only for focused repository tests and older injected fakes.
 */
class QqMusicRepository(private val api: ApiClient, private val owner: () -> RequestSession,
    private val tokenStore: TokenStore? = null, private val direct: QqDirectMusicClient? = null) {
    private data class AudioSnapshot(val identity: String, val generation: Long)
    private val mutableState = MutableStateFlow(QqAccountState())
    val state: StateFlow<QqAccountState> = mutableState
    private val likes = MutableStateFlow<Set<Long>>(emptySet())
    val likedState: StateFlow<Set<Long>> = likes
    private val knownLikes = mutableSetOf<Long>()
    private val mutableKnownLikeState = MutableStateFlow<Set<Long>>(emptySet())
    /** IDs whose current liked/unliked state has been confirmed by QQ. */
    val knownLikeState: StateFlow<Set<Long>> = mutableKnownLikeState
    private var selectedOwner: RequestSession? = null
    private var credential: String? = null
    private var generation = 0L
    private var persisted = mutableMapOf<String, JsonObject>()
    private val playlistMetadata = mutableMapOf<String, Playlist>()
    private val playlistSubscriptions = mutableMapOf<String, Boolean?>()
    private val qrOwners = mutableMapOf<String, RequestSession>()
    private val writeMutex = Mutex()
    private val likesRefreshMutex = Mutex()
    private val likedReads = SessionReadCache(::session, { state.value.revision })
    private var radioPoolId: String = ""
    private var radioSceneId: String = ""
    private val persistenceMutex = Mutex()
    private var homeSnapshot = QqHomeSnapshot()

    fun cachedHome(): QqHomeSnapshot = synchronized(this) { homeSnapshot }
    fun cacheHome(snapshot: QqHomeSnapshot) = synchronized(this) { homeSnapshot = snapshot }

    init {
        runCatching { tokenStore?.read()?.let { raw -> ApiJson.parseToJsonElement(raw) as? JsonObject } }
            .getOrNull()?.forEach { (key, value) -> (value as? JsonObject)?.let { persisted[key] = it } }
        selectOwner()
    }

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun ownerKey(value: RequestSession) = digest(
        if (direct != null) "qq-direct\n" + value.server.trimEnd('/')
        else value.server.trimEnd('/') + "\n" + value.token.orEmpty(),
    )
    private fun resetKnownLikes() {
        likedReads.clear()
        knownLikes.clear()
        mutableKnownLikeState.value = emptySet()
    }
    private fun identityLocked() = "qqmusic:" + ownerKey(owner()) + ":" + digest(credential.orEmpty())
    private fun currentSnapshotLocked(): AudioSnapshot {
        selectOwner()
        return AudioSnapshot(identityLocked(), generation)
    }
    private fun audioSnapshot() = synchronized(this) { currentSnapshotLocked() }
    private fun requireCurrentLocked(expected: AudioSnapshot) {
        if (currentSnapshotLocked() != expected)
            throw ApiException(ErrorKind.AudioSourceChanged, userMessage = "音乐账号已切换，请重试")
    }
    private fun publishResolvedLikes(expected: AudioSnapshot, resolved: List<Pair<Song, Boolean>>) =
        synchronized(this) {
            requireCurrentLocked(expected)
            val nextKnown = knownLikes.toMutableSet()
            val nextLiked = likes.value.toMutableSet()
            resolved.forEach { (song, liked) ->
                nextKnown += song.id
                if (liked) nextLiked += song.id else nextLiked -= song.id
            }
            knownLikes.clear(); knownLikes += nextKnown
            likes.value = nextLiked
            mutableKnownLikeState.value = nextKnown
        }
    private fun publishLikedSongs(expected: AudioSnapshot, songs: List<Song>) = synchronized(this) {
        requireCurrentLocked(expected)
        val liked = songs.map { it.id }.toSet()
        knownLikes.clear(); knownLikes += liked
        likes.value = liked
        mutableKnownLikeState.value = liked
    }
    private fun publishSingleLike(expected: AudioSnapshot, song: Song, liked: Boolean) = synchronized(this) {
        requireCurrentLocked(expected)
        likedReads.clear()
        knownLikes += song.id
        likes.value = if (liked) likes.value + song.id else likes.value - song.id
        mutableKnownLikeState.value = knownLikes.toSet()
    }
    @Synchronized fun selectOwner() {
        val next = owner()
        if (selectedOwner == next) return
        selectedOwner = next; generation++; qrOwners.clear(); playlistMetadata.clear(); playlistSubscriptions.clear()
        homeSnapshot = QqHomeSnapshot()
        resetKnownLikes(); likes.value = emptySet()
        val saved = persisted[ownerKey(next)]
        val stored = saved?.text("session")?.takeIf(String::isNotBlank)
        credential = stored?.takeIf { direct == null || direct.isCredential(it) }
        val activeProfile = saved?.takeIf { credential != null }
        mutableState.value = QqAccountState(credential != null, activeProfile?.text("nickname").orEmpty(),
            activeProfile?.text("avatar").orEmpty(), activeProfile?.text("uin").orEmpty(), generation)
    }
    fun session(): RequestSession { selectOwner(); return owner() }
    val audioIdentity: String get() = audioSnapshot().identity
    val directMode: Boolean get() = direct != null
    fun isLiked(song: Song) = song.musicSource == MusicSource.QQ_MUSIC && song.id in likes.value

    private suspend fun persist() = persistenceMutex.withLock {
        val snapshot = synchronized(this) {
            val key = selectedOwner?.let(::ownerKey) ?: return@withLock
            if (credential == null) persisted.remove(key) else persisted[key] = buildJsonObject {
                put("session", credential); put("nickname", state.value.nickname); put("avatar", state.value.avatar); put("uin", state.value.uin)
            }
            while (persisted.size > 8) persisted.remove(persisted.keys.first())
            JsonObject(persisted.toMap()).toString()
        }
        withContext(Dispatchers.IO) { tokenStore?.write(snapshot) }
    }

    private fun ensureOwner(expected: RequestSession, revision: Long) {
        selectOwner()
        if (owner() != expected || generation != revision)
            throw ApiException(ErrorKind.AudioSourceChanged, userMessage = "音乐账号已切换，请重试")
    }
    private suspend fun clearLocalQqSession(expected: RequestSession? = null, revision: Long? = null) {
        selectOwner()
        val changed = synchronized(this) {
            if (expected != null && (owner() != expected || generation != revision)) return@synchronized false
            val hadSession = credential != null || state.value.loggedIn || state.value.nickname.isNotBlank() ||
                state.value.avatar.isNotBlank() || state.value.uin.isNotBlank()
            credential = null
            generation++
            homeSnapshot = QqHomeSnapshot()
            resetKnownLikes(); likes.value = emptySet()
            mutableState.value = QqAccountState(revision = generation)
            qrOwners.clear()
            playlistMetadata.clear()
            playlistSubscriptions.clear()
            hadSession
        }
        if (changed) persist()
    }

    private suspend fun call(path: String, query: Map<String, String> = emptyMap(), requiresSession: Boolean = false,
        method: String = "GET", body: JsonObject? = null, login: Boolean = false, qr: Boolean = false,
        retryTransientRead: Boolean = false, timeoutMillis: Long? = null,
        expectedAudio: AudioSnapshot? = null): JsonObject {
        val requestOwner = synchronized(this) {
            selectOwner()
            if (expectedAudio != null && currentSnapshotLocked() != expectedAudio)
                throw ApiException(ErrorKind.AudioSourceChanged, userMessage = "音乐账号已切换，请重试")
            Triple(owner(), generation, credential)
        }
        val expected = requestOwner.first; val revision = requestOwner.second; val qq = requestOwner.third
        if (requiresSession && qq.isNullOrBlank())
            throw ApiException(ErrorKind.Forbidden, userMessage = "请先扫码登录 QQ 音乐")
        if (direct != null) {
            val raw = try {
                val directQuery = if (!qq.isNullOrBlank()) query + ("credential" to qq) else query
                direct.call(path, directQuery, body, method, requiresSession, login, qr).also {
                    ensureOwner(expected, revision)
                }
            } catch (failure: ApiException) {
                if (failure.kind == ErrorKind.Unauthorized && !qq.isNullOrBlank() && !login)
                    clearLocalQqSession(expected, revision)
                throw failure
            }
            val code = (raw["code"] as? JsonPrimitive)?.intOrNull
            if (code == 401 && requiresSession) {
                clearLocalQqSession(expected, revision)
                throw ApiException(ErrorKind.Unauthorized, 401, "QQ 音乐登录已失效，请重新扫码")
            }
            return raw
        }
        if ((requiresSession || login) && expected.token.isNullOrBlank())
            throw ApiException(ErrorKind.Forbidden, userMessage = "请先登录 CurrentMusic 账号，再连接 QQ 音乐")
        val authenticated = requiresSession || login
        val raw = try {
            suspend fun requestOnce() = api.request(method, "qqmusic/$path", query, body, authenticated,
                expectedSession = if (authenticated) expected else RequestSession(expected.server, null),
                retryConnection = method == "GET",
                callTimeoutMillis = timeoutMillis ?: if (path == "login/qr/check") 45_000 else 30_000,
                projectOnly = true, extraHeaders = if (authenticated && !qq.isNullOrBlank() && !login) mapOf("X-QQ-Session" to qq) else emptyMap())
            if (!retryTransientRead || method != "GET") requestOnce() else {
                var response: JsonElement? = null
                var attempt = 0
                while (response == null) {
                    try {
                        response = requestOnce()
                    } catch (failure: ApiException) {
                        ensureOwner(expected, revision)
                        if (attempt >= 2 || failure.kind != ErrorKind.Server || failure.status !in 502..504) throw failure
                        delay(if (attempt == 0) 350 else 1_000)
                        ensureOwner(expected, revision)
                        attempt++
                    }
                }
                response
            }
        } catch (e: ApiException) {
            if (e.kind == ErrorKind.Unauthorized && !qq.isNullOrBlank() && !login)
                clearLocalQqSession(expected, revision)
            throw e
        }
        ensureOwner(expected, revision)
        val obj = raw as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        val code = (obj["code"] as? JsonPrimitive)?.intOrNull
        if (code == 401 && requiresSession) {
            clearLocalQqSession(expected, revision)
            throw ApiException(ErrorKind.Unauthorized, 401, "QQ 音乐登录已失效，请重新扫码")
        }
        if (code != null && code !in setOf(0, 200) && !(qr && code in 800..804))
            throw ApiException(if (code == 429) ErrorKind.RateLimited else ErrorKind.Server, code,
                obj.text("failureHint", "message", "error").take(240).ifBlank { "QQ 音乐请求失败（$code）" })
        // Some legacy routes wrap a separate business response inside data.
        val nested = obj["data"] as? JsonObject
        val nestedCode = (nested?.get("code") as? JsonPrimitive)?.intOrNull
        if (nestedCode == 401 && requiresSession) {
            clearLocalQqSession(expected, revision)
            throw ApiException(ErrorKind.Unauthorized, 401, "QQ 音乐登录已失效，请重新扫码")
        }
        if (nestedCode != null && nestedCode !in setOf(0, 200) && !(qr && nestedCode in 800..804))
            throw ApiException(ErrorKind.Server, nestedCode, "QQ 音乐上游返回异常（$nestedCode）")
        return obj
    }
    private fun payload(raw: JsonObject): JsonObject = (raw["data"] as? JsonObject) ?: raw
    private suspend fun native(path: String, query: Map<String, String> = emptyMap(), requiresSession: Boolean = false,
        expectedAudio: AudioSnapshot? = null) =
        payload(call("native/$path", query, requiresSession, expectedAudio = expectedAudio))
    private suspend fun write(path: String, body: JsonObject, expectedAudio: AudioSnapshot? = null): JsonObject =
        payload(call("native/$path", requiresSession = true, method = "POST",
            body = JsonObject(body + ("confirm" to JsonPrimitive(1))), expectedAudio = expectedAudio))
    private suspend fun <T> withWriteOwner(expected: AudioSnapshot = audioSnapshot(), block: suspend () -> T): T {
        return writeMutex.withLock {
            if (audioSnapshot() != expected) throw ApiException(ErrorKind.AudioSourceChanged)
            block()
        }
    }
    private fun mid(song: Song): String = song.externalIds.qqMusicId?.takeIf { song.musicSource == MusicSource.QQ_MUSIC }
        ?: song.qqMusic?.mid?.takeIf { song.musicSource == MusicSource.QQ_MUSIC }
        ?: throw ApiException(ErrorKind.Parse, userMessage = "不是 QQ 音乐歌曲")
    private fun remoteId(id: String): String = id.takeIf { it.isNotBlank() && it.length <= 100 && it.matches(Regex("[a-zA-Z0-9_-]+")) }
        ?: throw ApiException(ErrorKind.Parse, userMessage = "QQ 音乐资源标识无效")

    suspend fun channels(): QqLoginCapabilities =
        QqMusicParsing.loginCapabilities(payload(call("login/channels")))
    suspend fun qrKey(channel: String = "qqmusic"): String {
        require(channel in setOf("qq", "qqmusic", "wechat"))
        val expected = owner()
        val data = payload(call("login/qr/key", method = "POST", body = buildJsonObject { put("channel", channel) }, login = true))
        val key = data.text("unikey", "key").ifBlank { throw ApiException(ErrorKind.Parse) }
        if (expected != owner()) throw ApiException(ErrorKind.AudioSourceChanged)
        synchronized(this) { qrOwners[key] = expected }
        return key
    }
    @Synchronized private fun checkQr(key: String) {
        selectOwner()
        if (qrOwners[key] != selectedOwner) throw ApiException(ErrorKind.AudioSourceChanged, userMessage = "二维码已失效，请重新生成")
    }
    suspend fun qrImage(key: String): String {
        checkQr(key)
        return payload(call("login/qr/create", method = "POST", body = buildJsonObject { put("key", key) }, login = true)).text("qrimg")
            .takeIf { it.startsWith("data:image/") } ?: throw ApiException(ErrorKind.Parse)
    }
    suspend fun qrCheck(key: String): Int {
        val expected = synchronized(this) {
            checkQr(key)
            currentSnapshotLocked()
        }
        val raw = call("login/qr/check", method = "POST", body = buildJsonObject { put("key", key) },
            login = true, qr = true, expectedAudio = expected)
        val data = payload(raw)
        val code = data.number("code").toInt().takeIf { it in 800..804 } ?: raw.number("code").toInt()
        if (code == 803) {
            val token = data.text("session", "token").takeIf(String::isNotBlank) ?: throw ApiException(ErrorKind.Parse)
            if (direct != null && !direct.isCredential(token)) throw ApiException(ErrorKind.Parse)
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                synchronized(this@QqMusicRepository) {
                    requireCurrentLocked(expected)
                    checkQr(key)
                    qrOwners.remove(key)
                    credential = token
                    generation++
                    homeSnapshot = QqHomeSnapshot()
                    resetKnownLikes()
                    likes.value = emptySet()
                    playlistMetadata.clear()
                    playlistSubscriptions.clear()
                    mutableState.value = profileState(data, true).copy(revision = generation)
                }
                persist()
            }
        }
        return code
    }
    suspend fun qrCancel(key: String) {
        val previousOwner = synchronized(this) { qrOwners.remove(key) }
        val body = buildJsonObject { put("key", key) }
        if (direct != null) direct.call("login/qr/cancel", emptyMap(), body, "POST", false, true, false)
        else if (previousOwner != null && previousOwner == owner())
            call("login/qr/cancel", method = "POST", body = body, login = true)
    }
    private fun profileState(raw: JsonObject, loggedIn: Boolean): QqAccountState {
        val p = (raw["profile"] as? JsonObject) ?: raw
        return QqAccountState(loggedIn, p.text("nickname", "nick", "name").ifBlank { state.value.nickname },
            profileAvatar(p).ifBlank { state.value.avatar },
            p.text("uin", "uid", "musicid").ifBlank { state.value.uin }, generation)
    }
    private fun profileAvatar(raw: JsonObject): String {
        val keys = setOf("avatar", "avatarurl", "headpic", "headurl", "figureurl", "figure_url", "logo", "pic")
        fun find(value: kotlinx.serialization.json.JsonElement, depth: Int = 0): String {
            if (depth > 5) return ""
            return when (value) {
                is JsonObject -> value.entries.firstOrNull { (key, child) ->
                    key.lowercase() in keys && (child as? JsonPrimitive)?.contentOrNull?.startsWith("http") == true
                }?.let { (it.value as JsonPrimitive).content } ?: value.values.firstNotNullOfOrNull { find(it, depth + 1).takeIf(String::isNotBlank) }.orEmpty()
                is JsonArray -> value.firstNotNullOfOrNull { find(it, depth + 1).takeIf(String::isNotBlank) }.orEmpty()
                else -> ""
            }
        }
        return find(raw).https().replace("\\/", "/")
    }
    suspend fun status(): QqAccountState {
        val data = payload(call("login/status", requiresSession = true))
        val profile = data["profile"] as? JsonObject
        if (profile == null) {
            clearLocalQqSession()
            throw ApiException(ErrorKind.Forbidden, userMessage = "QQ 音乐登录已失效，请重新扫码")
        }
        val resolved = if (profileAvatar(data).isBlank() && profileAvatar(profile).isBlank())
            appResult { native("user/detail", requiresSession = true) }.let { detail ->
                if (detail is AppResult.Success) profileState(detail.value, true)
                else profileState(data, true)
            }
        else profileState(data, true)
        mutableState.value = resolved.copy(revision = state.value.revision); persist(); return state.value
    }
    suspend fun profile(): QqAccountState {
        val data = native("user/detail", requiresSession = true)
        val current = profileState(data, true)
        if (current.avatar.isBlank() && current.nickname.isBlank() && current.uin.isBlank()) return state.value
        mutableState.value = current.copy(revision = state.value.revision); persist()
        return state.value
    }
    suspend fun refresh() {
        val previousCredential = credential
        val data = payload(call("login/refresh", requiresSession = true, method = "POST", body = buildJsonObject {}))
        data.text("token", "session").takeIf(String::isNotBlank)?.let { credential = it; generation++ }
        if (credential != previousCredential) homeSnapshot = QqHomeSnapshot()
        mutableState.value = state.value.copy(revision = generation); persist()
    }
    suspend fun logout() {
        try { call("logout", requiresSession = true, method = "POST", body = buildJsonObject { put("confirm", 1) }) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { }
        finally { clearLocalQqSession() }
    }

    suspend fun search(query: String, offset: Int = 0, type: String = "song"): QqSearchPage {
        val keywords = query.trim()
        require(keywords.isNotEmpty() && keywords.codePointCount(0, keywords.length) <= 60)
        require(type in setOf("song", "artist", "album", "playlist")); require(offset in 0..1000)
        val raw = payload(call("search", mapOf("keywords" to keywords, "type" to type, "offset" to "$offset", "limit" to "30"),
            retryTransientRead = true))
        val total = (raw["totals"] as? JsonObject)?.number(type)?.toInt() ?: 0
        val more = (raw["hasMore"] as? JsonObject)?.flag(type) ?: false
        val songs = if (type == "song") QqMusicParsing.songs(raw) else emptyList()
        val entries = if (type == "song") emptyList() else raw.array(type + "s", type).mapNotNull { QqMusicParsing.entry(it, type) }
        return QqSearchPage(songs, entries, total, more && offset + 30 <= 1000)
    }
    suspend fun suggestions(query: String): List<String> = native("search/suggest", mapOf("keyword" to query.trim().take(60)))
        .array("suggestions").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    suspend fun hotSearch(): List<String> = native("search/hot").array("hotkeys").mapNotNull { (it as? JsonObject)?.text("keyword", "title")?.takeIf(String::isNotBlank) }
    suspend fun detail(song: Song): Song = detail(mid(song))
    suspend fun detail(id: String): Song = QqMusicParsing.songs(payload(call("song/detail", mapOf("ids" to remoteId(id))))).firstOrNull()
        ?: throw ApiException(ErrorKind.NotFound)
    suspend fun source(song: Song, quality: AudioQuality): AudioSource {
        val requestedLevel = when (quality) {
            AudioQuality.STANDARD -> "standard"
            AudioQuality.HIGHER -> "higher"
            AudioQuality.EXHIGH -> "exhigh"
            AudioQuality.LOSSLESS -> "lossless"
            AudioQuality.HIRES -> "hires"
            AudioQuality.QQ_MASTER, AudioQuality.QQ_STUDIO, AudioQuality.QQ_SURROUND,
            AudioQuality.QQ_DTS_X, AudioQuality.QQ_NAC, AudioQuality.DOLBY -> quality.value
            else -> "auto"
        }
        val requestedLevels = when (quality) {
            AudioQuality.DOLBY -> listOf("dolby", "auto")
            AudioQuality.QQ_SURROUND -> listOf("qq_surround", "auto")
            AudioQuality.QQ_STUDIO -> listOf("qq_studio", "lossless")
            AudioQuality.QQ_MASTER -> listOf("qq_master", "hires", "lossless")
            AudioQuality.QQ_DTS_X, AudioQuality.QQ_NAC -> listOf(requestedLevel)
            else -> listOf(requestedLevel)
        }
        var lastFailure: ApiException? = null
        for (level in requestedLevels) {
            try {
                return resolveSource(song, level)
            } catch (failure: ApiException) {
                if (failure.kind != ErrorKind.AudioUnavailable || requestedLevels.last() == level) throw failure
                lastFailure = failure
            }
        }
        throw lastFailure ?: ApiException(ErrorKind.AudioUnavailable)
    }

    private suspend fun resolveSource(song: Song, level: String): AudioSource {
        val raw = payload(call("song/url", buildMap {
            put("id", mid(song))
            put("level", level)
            song.qqMusic?.mediaMid?.takeIf(String::isNotBlank)?.let { put("mediaMid", it) }
        }, requiresSession = true))
        val url = raw.text("url").takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: throw ApiException(ErrorKind.NotFound, userMessage = "QQ 音乐未提供完整播放地址，请检查歌曲和账号权限")
        return AudioSource(url, raw.text("level").ifBlank { level }, raw.number("sampleRate", "sr").toInt(), raw.number("channelCount", "ch").toInt(),
            format = raw.text("type", "format"), md5 = raw.text("md5"), validForSeconds = raw.number("expi").takeIf { it > 0 } ?: 30,
            source = "qqmusic", requestedLevel = level, bitrate = raw.number("br", "bitrate"), bits = raw.number("bits").toInt(), size = raw.number("size"),
            durationMs = raw.number("durationMs"), expectedDurationMs = song.durationMs)
    }
    suspend fun lyrics(song: Song): LyricsDocument = LyricsParser.parse(call("lyric", mapOf("id" to mid(song))))

    suspend fun daily(): Daily {
        selectOwner()
        val songs = if (state.value.loggedIn) QqMusicParsing.songs(native("user/daily-songs", requiresSession = true))
            else QqMusicParsing.songs(native("recommend/new-songs", mapOf("area" to "1")))
        return Daily(songs, songs.take(12), emptyList())
    }
    suspend fun recommendedPlaylists(): List<Playlist> = native("recommend/playlists", mapOf("limit" to "20"))
        .array("playlists").mapNotNull { QqMusicParsing.playlist(it) }
    suspend fun guessYouLike(): List<Song> = QqMusicParsing.songs(
        native("user/guess-you-like", requiresSession = true))
    suspend fun listeningRankPage(range: String = "week"): QqListeningRankPage {
        require(range in setOf("day", "week", "month", "year"))
        return QqMusicParsing.listeningRankPage(
            native("user/listening-rank", mapOf("range" to range), requiresSession = true))
    }
    suspend fun listeningRank(range: String = "week"): List<Song> =
        listeningRankPage(range).entries.map(QqListeningRankEntry::song)
    suspend fun categories(): List<QqMusicCategory> = QqMusicParsing.categories(
        payload(call("native/recommend/categories/all", retryTransientRead = true)))
    suspend fun categoryPlaylists(categoryId: String, offset: Int = 0, limit: Int = 30,
        sortId: Int = 3): QqCategoryPlaylistPage {
        val id = categoryId.trim().toIntOrNull()?.takeIf { it > 0 }?.toString()
            ?: throw IllegalArgumentException("QQ 音乐分类标识无效")
        require(offset in 0..10_000) { "QQ 音乐分类歌单偏移量无效" }
        require(limit in 1..100) { "QQ 音乐分类歌单数量无效" }
        require(sortId > 0) { "QQ 音乐分类排序标识无效" }
        val raw = payload(call("native/recommend/category-playlists/sorted",
            mapOf("id" to id, "offset" to "$offset", "limit" to "$limit", "sortId" to "$sortId"),
            retryTransientRead = true))
        return QqCategoryPlaylistPage(
            playlists = raw.array("playlists").mapNotNull { QqMusicParsing.playlist(it) },
            total = raw.number("total").coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            hasMore = raw.flag("more", "hasMore"),
        )
    }
    suspend fun charts(): List<QqCatalogEntry> = native("recommend/toplists").array("groups").flatMap {
        (it as? JsonObject)?.array("toplists").orEmpty().mapNotNull { value -> QqMusicParsing.entry(value, "chart") }
    }
    suspend fun chart(id: String): Playlist {
        val songs = mutableListOf<Song>()
        var base: Playlist? = null
        var total = 0
        var offset = 0
        var pages = 0
        while (pages++ < 100) {
            val raw = native("recommend/toplist", mapOf("id" to remoteId(id), "offset" to "$offset", "limit" to "100"))
            val info = raw["toplist"] as? JsonObject ?: buildJsonObject { put("id", id); put("title", "排行榜") }
            if (base == null) base = QqMusicParsing.playlist(info)
            val batch = QqMusicParsing.songs(raw)
            total = raw.number("total").toInt().takeIf { it > 0 } ?: total
            songs += batch
            val pageSize = raw.array("songs", "songlist", "songList", "tracks", "list").size
            if (!raw.flag("more") || batch.isEmpty() || pageSize <= 0) break
            offset += pageSize
        }
        return requireNotNull(base).copy(songs = songs.distinctBy { it.id }, count = total.takeIf { it > 0 } ?: songs.size)
    }
    suspend fun radioScenes(): List<QqRadioScene> {
        val raw = native("user/radio-scenes", requiresSession = true)
        fun parse(key: String): List<QqRadioScene> = raw.array(key).mapNotNull { value ->
            val row = value as? JsonObject ?: return@mapNotNull null
            val id = row.text("Id", "id")
            val name = row.text("Name", "name")
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            val ext = row["Ext"] as? JsonObject
            QqRadioScene(id, name, ext?.text("pool_id").orEmpty(), row.text("Pic", "pic"),
                row.number("Sort", "sort").toInt(), row.number("Status", "status").toInt())
        }
        val modes = parse("TopConfigList")
        val scenes = parse("ConfigList")
        return modes + scenes
    }
    suspend fun radioPreferences(): List<QqPreferenceGroup> {
        val raw = native("user/radio-preference", requiresSession = true)
        return raw.array("vecTagGroup").mapNotNull { value ->
            val group = value as? JsonObject ?: return@mapNotNull null
            val options = group.array("vecTag").mapNotNull { item ->
                val row = item as? JsonObject ?: return@mapNotNull null
                val id = row.text("id")
                if (id.isBlank()) null else QqPreferenceOption(id, row.text("name"),
                    row.number("status").toInt(), row.number("type").toInt())
            }
            if (options.isEmpty()) null else QqPreferenceGroup(group.text("name"), group.number("type").toInt(), options)
        }
    }
    suspend fun saveRadioPreferences(groups: List<QqPreferenceGroup>) {
        val vecTag = buildJsonArray {
            groups.forEach { group ->
                group.options.forEach { option ->
                    add(buildJsonObject {
                        put("id", option.id); put("name", option.name)
                        put("status", option.status); put("type", option.type)
                    })
                }
            }
        }
        payload(call("native/user/radio-preference/save", requiresSession = true, method = "POST",
            body = buildJsonObject { put("vecTag", vecTag) }))
    }
    suspend fun radio(poolId: String = "", sceneId: String = ""): List<Song> {
        radioPoolId = poolId
        radioSceneId = sceneId
        return try { radioPage().songs } finally { radioPoolId = ""; radioSceneId = "" }
    }
    suspend fun radioPage(page: Int = 1): QqSongPage {
        require(page in 1..100)
        val raw = native("user/personal-fm", buildMap {
            put("page", "$page")
            radioPoolId.takeIf(String::isNotBlank)?.let { put("pool_id", it) }
            radioSceneId.takeIf(String::isNotBlank)?.let { put("scene_id", it) }
        }, requiresSession = true)
        val next = (raw["nextPage"] as? JsonPrimitive)?.intOrNull?.takeIf { it > page && it <= 100 }
        return QqSongPage(QqMusicParsing.songs(raw), raw.flag("more"), next)
    }
    suspend fun playlist(id: String): Playlist {
        selectOwner()
        val initialIdentity = audioIdentity; val initialGeneration = generation
        val known = playlistMetadata[id]
        if (known?.nativeOwned == true) {
            val songs = mutableListOf<Song>(); var offset = 0
            repeat(100) {
                val raw = native("user/playlist-detail", mapOf("tid" to remoteId(id), "offset" to "$offset", "limit" to "100"), requiresSession = true)
                if (initialIdentity != audioIdentity || initialGeneration != generation) throw ApiException(ErrorKind.AudioSourceChanged)
                val batch = QqMusicParsing.songs(raw); songs += batch
                if (!raw.flag("more") || batch.isEmpty()) return known.copy(songs = songs.distinctBy { it.id }, count = raw.number("total").toInt())
                offset += batch.size
            }
            return known.copy(songs = songs.distinctBy { it.id })
        }
        // The compatibility endpoint intentionally caps songs at 100. Prefer the
        // legacy detail response so large public playlists are complete on first load.
        val completed = try {
            fullPlaylist(id)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val value = payload(call("playlist", mapOf("id" to remoteId(id))))
            val fallback = QqMusicParsing.playlist(value) ?: throw ApiException(ErrorKind.Parse)
            // The compatibility route intentionally caps public playlists at 100
            // tracks. Treating a partial response as success silently drops songs
            // from "Play all" and the queue, so preserve the retryable detail error.
            if (fallback.songs.size < fallback.count) throw failure
            fallback
        }
        val subscription = when {
            playlistSubscriptions.containsKey(id) -> playlistSubscriptions[id]
            known?.nativeSubscriptionKnown == true -> known.nativeSubscribed
            state.value.loggedIn -> try {
                refreshPlaylistSubscription(id).also { playlistSubscriptions[id] = it }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            else -> null
        }
        if (initialIdentity != audioIdentity || initialGeneration != generation) throw ApiException(ErrorKind.AudioSourceChanged)
        return completed.copy(nativeSubscribed = subscription == true, nativeSubscriptionKnown = subscription != null)
    }
    private suspend fun fullPlaylist(id: String): Playlist {
        val raw = call("native/getSongListDetail/${remoteId(id)}", timeoutMillis = 75_000)
        val response = (raw["response"] as? JsonObject) ?: payload(raw)
        val code = (response["code"] as? JsonPrimitive)?.intOrNull
        if (code != null && code !in setOf(0, 200))
            throw ApiException(ErrorKind.Server, code, "QQ 音乐完整歌单读取失败（$code）")
        val value = response.array("cdlist").firstOrNull() as? JsonObject
            ?: throw ApiException(ErrorKind.Parse)
        val parsed = QqMusicParsing.playlist(value) ?: throw ApiException(ErrorKind.Parse)
        return parsed.copy(count = maxOf(parsed.count, parsed.songs.size))
    }
    suspend fun playlistPage(id: String, offset: Int = 0): List<Song> {
        require(offset in 0..10000)
        return QqMusicParsing.songs(native("user/playlist-detail", mapOf("tid" to remoteId(id), "offset" to "$offset", "limit" to "100"), requiresSession = true))
    }
    suspend fun playlists(): List<Playlist> {
        val initialIdentity = audioIdentity; val initialGeneration = generation
        val result = mutableListOf<Playlist>(); val seen = mutableSetOf<Int>(); var offset = 0
        repeat(20) {
            val raw = native("user/playlist", mapOf("favoriteOffset" to "$offset"), requiresSession = true)
            if (initialIdentity != audioIdentity || initialGeneration != generation) throw ApiException(ErrorKind.AudioSourceChanged)
            result += raw.array("playlist", "playlists").mapNotNull { value ->
                val item = value as? JsonObject ?: return@mapNotNull null
                // Created and favorite rows are heterogeneous. Only created rows expose dirName.
                val owned = item.text("dirName").isNotBlank() && item.number("dirId") in 1..199
                QqMusicParsing.playlist(item, owned)?.copy(nativeSubscribed = !owned && item.text("dirName").isBlank())
            }
            val next = (raw["nextFavoriteOffset"] as? JsonPrimitive)?.intOrNull
            result.forEach { playlistMetadata[it.remoteId] = it }
            if (!raw.flag("more") || next == null || next <= offset || !seen.add(next)) return result.distinctBy { it.remoteId }
            offset = next
        }
        return result.distinctBy { it.remoteId }
    }
    private suspend fun refreshPlaylistSubscription(id: String): Boolean? {
        val raw = native("user/subscription-status", mapOf("type" to "playlist", "ids" to remoteId(id)), requiresSession = true)
        return subscriptionStatus(raw, id)
    }
    suspend fun albumSubscription(id: String): Boolean? {
        val raw = native("user/subscription-status", mapOf("type" to "album", "ids" to remoteId(id)), requiresSession = true)
        return subscriptionStatus(raw, id)
    }
    private fun subscriptionStatus(raw: JsonObject, id: String): Boolean? {
        val status = raw.array("items").mapNotNull { it as? JsonObject }
            .firstOrNull { it.identifier("id", "mid", "albumMID", "albumMid") == id }
            ?.text("status")
        return when (status) {
            "subscribed" -> true
            "not-subscribed" -> false
            else -> null
        }
    }
    suspend fun likedSongs(fresh: Boolean = false): List<Song> = withContext(Dispatchers.Default) {
        val expected = audioSnapshot()
        likesRefreshMutex.withLock {
            synchronized(this@QqMusicRepository) { requireCurrentLocked(expected) }
            likedReads.read("likes", fresh) {
                val songs = mutableListOf<Song>(); var offset = 0
                repeat(100) {
                    val raw = native("user/liked-songs", mapOf("offset" to "$offset", "limit" to "100"),
                        requiresSession = true, expectedAudio = expected)
                    songs += QqMusicParsing.songs(raw)
                    if (!raw.flag("more")) {
                        val result = songs.distinctBy(Song::id)
                        publishLikedSongs(expected, result)
                        return@read result
                    }
                    val pageSize = raw.array("songs", "songlist", "songList", "tracks", "list").size
                    if (pageSize == 0) throw ApiException(ErrorKind.Parse)
                    offset += pageSize
                }
                throw ApiException(ErrorKind.Parse)
            }.also {
                synchronized(this@QqMusicRepository) { requireCurrentLocked(expected) }
            }
        }
    }
    suspend fun likedSongCount(): Int {
        val expected = audioSnapshot()
        val raw = native("user/liked-songs", mapOf("offset" to "0", "limit" to "1"), requiresSession = true)
        if (audioSnapshot() != expected) throw ApiException(ErrorKind.AudioSourceChanged)
        return raw.number("total").coerceAtLeast(0).toInt()
    }
    private suspend fun refreshLikedLocked(targets: List<Song>, expected: AudioSnapshot) {
        if (audioSnapshot() != expected) throw ApiException(ErrorKind.AudioSourceChanged)
        val unknown = synchronized(this) { targets.filter { it.id !in knownLikes } }
        if (unknown.isEmpty()) return
        unknown.chunked(50).forEach { batch ->
            val mids = batch.associate { it.id to mid(it) }
            val raw = native("user/subscription-status",
                mapOf("type" to "song", "ids" to mids.values.joinToString(",")), requiresSession = true)
            if (audioSnapshot() != expected) throw ApiException(ErrorKind.AudioSourceChanged)
            val statuses = raw.array("items").mapNotNull { item ->
                (item as? JsonObject)?.let { row -> row.text("id").takeIf(String::isNotBlank)?.let { it to row.text("status") } }
            }.toMap()
            val resolved = batch.mapNotNull { song ->
                val status = statuses[mids[song.id]] ?: return@mapNotNull null
                when (status) {
                    "subscribed" -> song to true
                    "not-subscribed" -> song to false
                    else -> null
                }
            }
            publishResolvedLikes(expected, resolved)
        }
    }
    suspend fun refreshLiked(songs: List<Song>) {
        val targets = songs.filter { it.musicSource == MusicSource.QQ_MUSIC }.distinctBy(Song::id)
        val expected = audioSnapshot()
        likesRefreshMutex.withLock { refreshLikedLocked(targets, expected) }
    }
    suspend fun refreshLiked(song: Song): Boolean {
        val expected = audioSnapshot()
        return likesRefreshMutex.withLock {
            refreshLikedLocked(listOf(song), expected)
            synchronized(this) {
                if (song.id !in knownLikes)
                    throw ApiException(ErrorKind.Server, userMessage = "无法确认 QQ 音乐收藏状态，请重试")
                song.id in likes.value
            }
        }
    }
    private suspend fun numericSong(song: Song): Song = song.takeIf { (it.qqMusic?.songId ?: 0) > 0 } ?: detail(song)
    private suspend fun setLikedLocked(song: Song, liked: Boolean, expected: AudioSnapshot) = withWriteOwner(expected) {
        val full = numericSong(song); val id = full.qqMusic?.songId?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.Parse)
        if (audioSnapshot() != expected) throw ApiException(ErrorKind.AudioSourceChanged)
        val raw = write("user/song/like", buildJsonObject {
            put("song", buildJsonObject { put("id", id); put("mid", mid(song)); put("type", 0) }); put("liked", liked)
        }, expectedAudio = expected)
        if ((raw["liked"] as? JsonPrimitive)?.booleanOrNull != liked)
            throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未确认收藏结果，请刷新后重试")
        publishSingleLike(expected, song, liked)
        liked
    }
    suspend fun toggleLiked(song: Song, desiredLiked: Boolean? = null): Boolean {
        val expected = audioSnapshot()
        return likesRefreshMutex.withLock {
            if (audioSnapshot() != expected) throw ApiException(ErrorKind.AudioSourceChanged)
            val target = desiredLiked ?: synchronized(this) {
                if (song.id in knownLikes) song.id !in likes.value else true
            }
            setLikedLocked(song, target, expected)
        }
    }
    suspend fun setLiked(song: Song, liked: Boolean): Boolean {
        val expected = audioSnapshot()
        return likesRefreshMutex.withLock {
            if (audioSnapshot() != expected) throw ApiException(ErrorKind.AudioSourceChanged)
            setLikedLocked(song, liked, expected)
        }
    }
    suspend fun favoriteCount(song: Song): QqFavoriteCount {
        val raw = native("song/favorite-count", mapOf("mid" to mid(song)))
        return QqFavoriteCount(raw.number("count").coerceAtLeast(0), raw.text("display"))
    }
    suspend fun qualities(song: Song): Set<AudioQuality> {
        val available = native("song/qualities", mapOf("mid" to mid(song)))["available"] as? JsonObject
            ?: return emptySet()
        return AudioQuality.entries.filter { quality ->
            quality == AudioQuality.AUTO || available.flag(quality.value)
        }.toSet()
    }
    private fun playlistName(name: String): String = name.trim().also { require(it.isNotEmpty() && it.codePointCount(0, it.length) <= 20) }
    suspend fun createPlaylist(name: String): String = withWriteOwner {
        val raw = write("user/playlist/create", buildJsonObject { put("name", playlistName(name)) })
        if (!raw.flag("verified")) throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未确认歌单创建结果，请刷新后重试")
        raw.identifier("tid", "id").takeIf(String::isNotBlank) ?: throw ApiException(ErrorKind.Parse)
    }
    suspend fun deletePlaylist(id: String) = withWriteOwner {
        val raw = write("user/playlist/delete", buildJsonObject { put("tid", remoteId(id)) })
        if (!raw.flag("verified")) throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未确认歌单删除结果，请刷新后重试")
        playlistMetadata.remove(id); playlistSubscriptions.remove(id); Unit
    }
    suspend fun renamePlaylist(id: String, name: String) = withWriteOwner {
        val raw = write("user/playlist/rename", buildJsonObject { put("tid", remoteId(id)); put("name", playlistName(name)) })
        if (!raw.flag("verified")) throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未确认歌单重命名结果，请刷新后重试")
        playlistSubscriptions.remove(id)
        playlistMetadata[id]?.let { playlistMetadata[id] = it.copy(name = name.trim()) }
        Unit
    }
    suspend fun subscribePlaylist(id: String, subscribe: Boolean) = withWriteOwner {
        val raw = write("user/playlist/subscribe", buildJsonObject { put("tid", remoteId(id)); put("subscribed", subscribe) })
        if ((raw["subscribed"] as? JsonPrimitive)?.booleanOrNull != subscribe)
            throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未确认歌单收藏结果，请刷新后重试")
        playlistSubscriptions[id] = subscribe
        playlistMetadata[id]?.let {
            playlistMetadata[id] = it.copy(nativeSubscribed = subscribe, nativeSubscriptionKnown = true)
        }
    }
    suspend fun addToPlaylist(id: String, songs: List<Song>) = editTracks(id, songs, "add")
    suspend fun add(id: String, songs: List<Song>) = addToPlaylist(id, songs)
    suspend fun removeFromPlaylist(id: String, songs: List<Song>) = editTracks(id, songs, "del")
    private suspend fun editTracks(id: String, songs: List<Song>, op: String) = withWriteOwner {
        val initialIdentity = audioIdentity; val initialGeneration = generation
        require(songs.isNotEmpty() && songs.size <= 100)
        val full = songs.distinctBy(::mid).map { numericSong(it) }
        if (initialIdentity != audioIdentity || initialGeneration != generation) throw ApiException(ErrorKind.AudioSourceChanged)
        val raw = write("user/playlist/tracks", buildJsonObject {
            put("tid", remoteId(id)); put("op", op); put("songs", buildJsonArray { full.forEach { song ->
                val numeric = song.qqMusic?.songId?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.Parse)
                add(buildJsonObject { put("id", numeric); put("type", 0) })
            } })
        })
        val expectedStatus = if (op == "add") "present" else "absent"
        val verifiedStatus = (raw["verification"] as? JsonObject)?.text("status")
        if (!raw.flag("changed") && verifiedStatus != expectedStatus)
            throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未确认歌单变更，请刷新歌单查看")
    }
    suspend fun albumDetail(id: String): QqAlbumDetail {
        val raw = payload(call("album", mapOf("id" to remoteId(id))))
        return QqMusicParsing.albumDetail(raw)
    }
    suspend fun album(id: String): QqCatalogDetail {
        val detail = albumDetail(id)
        return QqCatalogDetail(
            QqCatalogEntry(detail.mid, detail.title, detail.cover,
                listOf(detail.singerNames, detail.publishDate).filter(String::isNotBlank).joinToString(" · "),
                "album"),
            detail.songs,
        )
    }
    suspend fun albumSongs(id: String, offset: Int = 0, limit: Int = 300): QqSongPage {
        require(offset in 0..10_000)
        require(limit in 1..300)
        val raw = payload(call("album/songs", mapOf(
            "id" to remoteId(id),
            "offset" to "$offset",
            "limit" to "$limit",
        )))
        return QqMusicParsing.albumSongPage(raw)
    }
    suspend fun artistDetail(id: String, offset: Int = 0, limit: Int = 30): QqArtistDetail {
        require(offset in 0..10_000)
        val raw = payload(call("artist", mapOf(
            "id" to remoteId(id),
            "offset" to "$offset",
            "limit" to "${limit.coerceIn(1, 100)}",
        )))
        return QqMusicParsing.artistDetail(raw)
    }
    suspend fun artist(id: String, offset: Int = 0): QqCatalogDetail {
        require(offset in 0..10000)
        val detail = artistDetail(id, offset, 30)
        val entry = QqCatalogEntry(detail.mid, detail.name, detail.cover, detail.alias, "artist")
        return QqCatalogDetail(entry, detail.songs, detail.albums, detail.hasMoreSongs)
    }
    suspend fun artistSongs(id: String, offset: Int = 0, limit: Int = 30): QqSongPage {
        require(offset in 0..10_000)
        val raw = payload(call("artist/songs", mapOf(
            "id" to remoteId(id),
            "offset" to "$offset",
            "limit" to "${limit.coerceIn(1, 100)}",
        )))
        return QqMusicParsing.artistSongPage(raw)
    }
    suspend fun artistAlbums(id: String, offset: Int = 0): QqCatalogPage {
        val raw = payload(call("artist/albums",
            mapOf("id" to remoteId(id), "offset" to "$offset", "limit" to "30")))
        val entries = raw.array("albums").mapNotNull { QqMusicParsing.entry(it, "album") }
        val total = raw.number("albumTotal", "total").takeIf { it > 0 }?.toInt()
            ?: (offset + entries.size)
        return QqCatalogPage(entries, total, raw.flag("more", "hasMore") || offset + entries.size < total)
    }
    suspend fun comments(song: Song, sort: String = "latest", page: Int = 1, seq: String = "", limit: Int = 20): QqCommentsPage {
        val id = numericSong(song).qqMusic?.songId?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.Parse)
        val raw = native("comment/list", mapOf("id" to "$id", "sort" to sort, "page" to "$page",
            "seq" to seq, "limit" to "${limit.coerceIn(1, 25)}"))
        return QqCommentsPage(raw.array("comments").mapNotNull { value -> (value as? JsonObject)?.let {
            QqComment(it.text("id"), it.text("content", "text"), it.text("nick", "nickname"), it.text("avatar"),
                it.number("praiseCount", "likes"), it.number("time") * 1000,
                it.flag("likedByMe"), it.flag("isMine"), it.number("replyCount").toInt(), it.text("seqNo"),
                it.text("quotedNickname"), it.text("quotedContent").takeIf(String::isNotBlank))
        } }, raw.number("total").toInt(), raw.flag("more"), raw.text("nextSeq"))
    }
    suspend fun commentReplies(song: Song, parentId: String, page: Int = 1, seq: String = "", limit: Int = 20): QqCommentsPage {
        val id = numericSong(song).qqMusic?.songId?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.Parse)
        val raw = native("comment/replies", mapOf("id" to "$id", "parent" to parentId, "page" to "$page",
            "seq" to seq, "limit" to "${limit.coerceIn(1, 25)}"))
        return QqCommentsPage(raw.array("comments").mapNotNull { value -> (value as? JsonObject)?.let {
            QqComment(it.text("id"), it.text("content", "text"), it.text("nick", "nickname"), it.text("avatar"),
                it.number("praiseCount", "likes"), it.number("time") * 1000,
                it.flag("likedByMe"), it.flag("isMine"), it.number("replyCount").toInt(), it.text("seqNo"),
                it.text("quotedNickname"), it.text("quotedContent").takeIf(String::isNotBlank))
        } }, raw.number("total").toInt(), raw.flag("more"), raw.text("nextSeq"))
    }
    suspend fun commentCount(song: Song): Int {
        return comments(song, page = 1, limit = 1).total
    }
    suspend fun sendComment(song: Song, content: String, replyTo: String? = null): String {
        val id = numericSong(song).qqMusic?.songId?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.Parse)
        val raw = write("comment/send", buildJsonObject {
            put("songId", id)
            put("content", content.trim())
            replyTo?.takeIf(String::isNotBlank)?.let { put("replyTo", it) }
        }, audioSnapshot())
        if (!raw.flag("verified")) throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未确认评论发送结果")
        return raw.text("commentId")
    }
    suspend fun deleteComment(song: Song, commentId: String) {
        require(commentId.isNotBlank())
        write("comment/delete", buildJsonObject { put("commentId", commentId) }, audioSnapshot())
    }
    suspend fun setCommentLiked(song: Song, commentId: String, liked: Boolean): Boolean {
        require(commentId.isNotBlank())
        val raw = write("comment/like", buildJsonObject {
            put("commentId", commentId)
            put("liked", liked)
        }, audioSnapshot())
        return raw.flag("liked")
    }
    suspend fun songMvs(song: Song): List<QqCatalogEntry> {
        val id = numericSong(song).qqMusic?.songId?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.Parse)
        return native("song/mvs", mapOf("id" to "$id")).array("mvs", "videos").mapNotNull { QqMusicParsing.entry(it, "mv") }
    }
    suspend fun mvUrl(id: String): String {
        val raw = native("mv/url", mapOf("vid" to remoteId(id)), requiresSession = true)
        return raw.array("files").mapNotNull { it as? JsonObject }.flatMap { it.array("urls") }
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
            ?: throw ApiException(ErrorKind.NotFound, userMessage = "QQ 音乐未提供 MV 播放地址")
    }
    suspend fun favoriteAlbums(offset: Int = 0): QqCatalogPage {
        require(offset in 0..10_000) { "QQ 音乐收藏专辑偏移量无效" }
        val raw = native("user/albums", mapOf("offset" to "$offset", "limit" to "30"), requiresSession = true)
        val entries = raw.array("albums").mapNotNull { QqMusicParsing.entry(it, "album") }
        val total = raw.number("total", "albumTotal", "count").takeIf { it > 0 } ?: (offset + entries.size).toLong()
        return QqCatalogPage(
            entries = entries,
            total = total.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            hasMore = raw.flag("more", "hasMore"),
        )
    }
    suspend fun subscribeAlbum(id: String, subscribe: Boolean) = withWriteOwner {
        val raw = write("user/album/subscribe", buildJsonObject { put("id", remoteId(id)); put("subscribed", subscribe) })
        if ((raw["subscribed"] as? JsonPrimitive)?.booleanOrNull != subscribe)
            throw ApiException(ErrorKind.Server, userMessage = "QQ 音乐未确认专辑收藏结果，请刷新后重试")
    }
    suspend fun vip(): JsonObject = native("user/vip", requiresSession = true)
    suspend fun vipLevel(): String {
        fun find(value: kotlinx.serialization.json.JsonElement): String = when (value) {
            is JsonObject -> value.entries.firstOrNull { it.key.equals("vipLevel", ignoreCase = true) }
                ?.value?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                .ifBlank { value.values.firstNotNullOfOrNull { find(it).takeIf(String::isNotBlank) }.orEmpty() }
            is JsonArray -> value.firstNotNullOfOrNull { find(it).takeIf(String::isNotBlank) }.orEmpty()
            else -> ""
        }
        return find(vip()).lowercase().ifBlank { "unknown" }
    }
    suspend fun musicGene(): JsonObject = native("user/music-gene", requiresSession = true)
}
