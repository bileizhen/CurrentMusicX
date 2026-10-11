package io.github.currencortex.music.core.netease

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.binding.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Adapts official NetEase responses to the app's existing models, without a proxy server. */
class NeteaseGateway(val transport: NeteaseTransport) {
    private val listeningMutex = Mutex()
    private var listeningOwner: Pair<Long, Long>? = null
    private var listeningMs = 0L
    private var listeningReported = false
    val sessions get() = transport.sessions
    fun handles(path: String) = (path.startsWith("ncm/") && path != "ncm/song/url") || path == "ncmbind" || path.startsWith("ncmbind/")
    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull ?: 0L
    private fun JsonObject.obj(key: String) = get(key) as? JsonObject ?: buildJsonObject {}
    private fun JsonObject.array(key: String) = get(key) as? JsonArray ?: JsonArray(emptyList())
    private fun song(raw: JsonObject): JsonObject = buildJsonObject {
        if (raw["ncm_id"] != null) { raw.forEach { (key, value) -> put(key, value) }; return@buildJsonObject }
        val artists = ((raw["ar"] ?: raw["artists"]) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val album = (raw["al"] ?: raw["album"]) as? JsonObject ?: buildJsonObject {}
        put("ncm_id", raw.long("id")); put("name", raw.text("name"))
        put("artists", artists.joinToString(" / ") { it.text("name") }); put("artist_ids", JsonArray(artists.mapNotNull { it["id"] }))
        put("album", album.text("name")); put("pic", album.text("picUrl").ifBlank { raw.text("picUrl") }.replace("http:", "https:"))
        put("duration", raw["dt"] ?: raw["duration"] ?: JsonPrimitive(0)); put("mv", raw["mv"] ?: raw["mvid"] ?: JsonPrimitive(0))
    }
    private fun songs(raw: JsonArray) = JsonArray(raw.mapNotNull { (it as? JsonObject)?.let(::song) }.filter { it.long("ncm_id") > 0 })
    private fun artist(raw: JsonObject) = buildJsonObject {
        put("id", raw.long("id")); put("name", raw.text("name")); put("pic", raw.text("picUrl").ifBlank { raw.text("img1v1Url") })
        put("alias", raw.array("alias").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(" / "))
        put("total", raw.long("musicSize")); put("albumTotal", raw.long("albumSize"))
    }
    private fun album(raw: JsonObject, tracks: JsonArray = raw.array("songs")) = buildJsonObject {
        put("id", raw.long("id")); put("name", raw.text("name")); put("pic", raw.text("picUrl"))
        put("artist", raw.obj("artist").text("name")); put("description", raw.text("description"))
        put("publishTime", raw.long("publishTime")); put("company", raw.text("company")); put("size", raw.long("size"))
        put("songs", songs(tracks))
    }
    private fun params(vararg values: Pair<String, JsonElement>) = JsonObject(values.toMap())
    private fun data(vararg values: Pair<String, String>) = JsonObject(values.associate { it.first to JsonPrimitive(it.second) })
    private suspend fun official(path: String, body: JsonObject = buildJsonObject {}, web: Boolean = false,
        expected: NeteaseSession? = null, authenticated: Boolean = false, clientLog: Boolean = false): JsonObject {
        val owner = expected ?: sessions.snapshot()
        if (authenticated && (owner.credential.cookies["MUSIC_U"].isNullOrBlank() || owner.credential.stale))
            throw ApiException(ErrorKind.NeteaseBindingRequired)
        val raw = transport.request(path, body, when {
            clientLog -> NeteaseEncoding.LOG; web -> NeteaseEncoding.WEB; else -> NeteaseEncoding.DESKTOP
        }, session = owner).body
        val code = (raw["code"] as? JsonPrimitive)?.intOrNull ?: 200
        if (code != 200) {
            if (code in setOf(301, 401) && authenticated) sessions.markStale(owner)
            throw ApiException(when (code) { 301, 401 -> ErrorKind.NeteaseBindingRequired; 403 -> ErrorKind.Forbidden
                404 -> ErrorKind.NotFound; 406, 429 -> ErrorKind.RateLimited; else -> ErrorKind.Server }, code)
        }
        return raw
    }
    private fun profile(raw: JsonObject): BoundProfile? {
        val info = (raw["profile"] ?: raw.obj("data")["profile"]) as? JsonObject ?: return null
        return BoundProfile(info.long("userId"), info.text("nickname"), info.text("avatarUrl")).takeIf { it.uid > 0 }
    }
    private suspend fun acceptLogin(response: NeteaseResponse, expected: NeteaseSession): BoundProfile {
        if (response.cookies["MUSIC_U"].isNullOrBlank()) throw ApiException(ErrorKind.Parse)
        val user = profile(response.body) ?: profile(transport.request("/api/w/nuser/account/get", encoding = NeteaseEncoding.WEB,
            session = expected, cookieOverride = response.cookies, saveCookies = false).body) ?: throw ApiException(ErrorKind.Parse)
        currentCoroutineContext().ensureActive()
        sessions.login(response.cookies, user, expected)
        return user
    }
    private suspend fun binding(method: String, path: String, query: Map<String, String>, body: JsonObject,
        expected: NeteaseSession): JsonElement {
        val current = expected.credential
        when (path) {
            "ncmbind" -> {
                if (method == "DELETE") sessions.clear()
                val state = sessions.state.value
                return ApiJson.encodeToJsonElement(BindingState.serializer(), BindingState(state.loggedIn, state.stale, state.profile))
            }
            "ncmbind/live", "ncmbind/refresh" -> {
                if (current.cookies["MUSIC_U"].isNullOrBlank()) return ApiJson.encodeToJsonElement(LiveBinding.serializer(), LiveBinding())
                if (path.endsWith("refresh")) {
                    val refreshed = transport.request("/api/login/token/refresh", session = expected, saveCookies = false)
                    if (refreshed.body.long("code") != 200L) throw ApiException(ErrorKind.NeteaseBindingRequired)
                    val account = transport.request("/api/w/nuser/account/get", encoding = NeteaseEncoding.WEB,
                        session = expected, cookieOverride = refreshed.cookies, saveCookies = false)
                    val user = profile(account.body) ?: throw ApiException(ErrorKind.NeteaseBindingRequired)
                    sessions.login(account.cookies, user, expected)
                    return buildJsonObject { put("ok", true) }
                }
                val raw = transport.request("/api/w/nuser/account/get", encoding = NeteaseEncoding.WEB, session = expected).body
                val user = profile(raw)
                if (user == null) sessions.markStale(expected)
                return ApiJson.encodeToJsonElement(LiveBinding.serializer(), LiveBinding(true, user != null, user ?: current.profile))
            }
            "ncmbind/qr/key" -> {
                val raw = official("/api/login/qrcode/unikey", params("type" to JsonPrimitive(3)), expected = expected)
                val key = raw.text("unikey").ifBlank { raw.obj("data").text("unikey") }
                if (key.isBlank()) throw ApiException(ErrorKind.Parse)
                return buildJsonObject { put("key", key) }
            }
            "ncmbind/qr/check" -> {
                val result = transport.request("/api/login/qrcode/client/login", data("key" to query.getValue("key"), "type" to "3"),
                    session = expected, saveCookies = false)
                val code = result.body.long("code").toInt()
                val user = if (code == 803) acceptLogin(result, expected) else null
                return ApiJson.encodeToJsonElement(QrStatus.serializer(), QrStatus(code, user))
            }
            "ncmbind/phone/code" -> return official("/api/sms/captcha/sent", data("cellphone" to body.text("phone"),
                "ctcode" to body.text("ctcode").ifBlank { "86" }, "secrete" to "music_middleuser_pclogin"), web = true, expected = expected)
            "ncmbind/phone/login" -> {
                val result = transport.request("/api/w/login/cellphone", data("phone" to body.text("phone"), "captcha" to body.text("captcha"),
                    "countrycode" to body.text("ctcode").ifBlank { "86" }, "type" to "1", "https" to "true", "remember" to "true"),
                    NeteaseEncoding.WEB, session = expected, saveCookies = false)
                if (result.body.long("code") != 200L) throw ApiException(ErrorKind.Server, result.body.long("code").toInt())
                acceptLogin(result, expected)
                return buildJsonObject { put("code", 200); put("ok", true) }
            }
            "ncmbind/likelist" -> return official("/api/song/like/get", data("uid" to "${current.profile?.uid ?: 0}"), expected = expected, authenticated = true)
        }
        if (path.startsWith("ncmbind/like/")) {
            val id = path.substringAfterLast('/').toLongOrNull()?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.NotFound)
            val liked = body["like"]?.jsonPrimitive?.booleanOrNull ?: throw ApiException(ErrorKind.Parse)
            return official("/api/radio/like", params("trackId" to JsonPrimitive(id), "like" to JsonPrimitive(liked),
                "alg" to JsonPrimitive("itembased"), "time" to JsonPrimitive(3)), web = true, expected = expected, authenticated = true)
        }
        throw ApiException(ErrorKind.NotFound)
    }
    suspend fun request(method: String, path: String, query: Map<String, String>, body: JsonElement?, expectedRevision: Long? = null): JsonElement {
        val expected = sessions.snapshot()
        if (expectedRevision != null && expectedRevision != expected.revision) throw ApiException(ErrorKind.NeteaseSessionChanged)
        if (path.startsWith("ncmbind")) return binding(method, path, query, body as? JsonObject ?: buildJsonObject {}, expected)
        fun arg(name: String, fallback: String = "") = query[name] ?: fallback
        fun page(name: String, fallback: Int, min: Int = 0, max: Int = 1000) = arg(name).toIntOrNull()?.coerceIn(min, max) ?: fallback
        val id = arg("id")
        when (path.removePrefix("ncm/")) {
            "search" -> {
                val type = when (arg("type", "song")) { "song", "1" -> 1; "album", "10" -> 10; "artist", "100" -> 100; else -> throw ApiException(ErrorKind.NotFound) }
                val offset = page("offset", 0, max = Int.MAX_VALUE)
                val raw = official("/api/cloudsearch/pc", params("s" to JsonPrimitive(arg("keywords")), "type" to JsonPrimitive(type),
                    "limit" to JsonPrimitive(page("limit", 30, min = 1)), "offset" to JsonPrimitive(offset)), expected = expected).obj("result")
                val field = if (type == 1) "songs" else if (type == 10) "albums" else "artists"
                val records = raw.array(field)
                val count = raw.long(if (type == 1) "songCount" else if (type == 10) "albumCount" else "artistCount").toInt()
                val key = if (type == 1) "song" else if (type == 10) "album" else "artist"
                return buildJsonObject {
                    put(field, if (type == 1) songs(records) else JsonArray(records.mapNotNull { it as? JsonObject }.map { if (type == 10) album(it) else artist(it) }))
                    put("totals", buildJsonObject { put(key, count) }); put("hasMore", buildJsonObject { put(key, records.isNotEmpty() && offset + records.size < count) })
                }
            }
            "song/detail" -> {
                val ids = arg("ids").split(',').mapNotNull { it.trim().toLongOrNull()?.takeIf { it > 0 } }.take(1000)
                require(ids.isNotEmpty())
                val c = JsonArray(ids.map { buildJsonObject { put("id", it) } }).toString()
                val raw = official("/api/v3/song/detail", data("c" to c), web = true, expected = expected)
                return buildJsonObject { put("code", 200); put("songs", songs(raw.array("songs"))) }
            }
            "lyric", "lyric/new" -> return official(if (path.endsWith("/new")) "/api/song/lyric/v1" else "/api/song/lyric",
                params("id" to JsonPrimitive(id), "cp" to JsonPrimitive(false), "tv" to JsonPrimitive(0), "lv" to JsonPrimitive(0),
                    "rv" to JsonPrimitive(0), "kv" to JsonPrimitive(0), "yv" to JsonPrimitive(0), "ytv" to JsonPrimitive(0), "yrv" to JsonPrimitive(0)), expected = expected)
            "user/playlist" -> return official("/api/user/playlist", data("uid" to arg("uid"), "limit" to arg("limit", "100"),
                "offset" to arg("offset", "0"), "includeVideo" to "true"), web = true, expected = expected, authenticated = true)
            "playlist/detail" -> return official("/api/v6/playlist/detail", data("id" to id, "n" to "100000", "s" to arg("s", "0")), expected = expected)
            "playlist/tracks" -> return official("/api/playlist/manipulate/tracks", data("op" to arg("op"), "pid" to arg("pid"),
                "trackIds" to JsonArray(arg("tracks").split(',').map(::JsonPrimitive)).toString(), "imme" to "true"), expected = expected, authenticated = true)
            "likelist" -> return official("/api/song/like/get", data("uid" to arg("uid")), expected = expected, authenticated = true)
            "like" -> return official("/api/radio/like", params("trackId" to JsonPrimitive(id), "like" to JsonPrimitive(arg("like") != "false"),
                "alg" to JsonPrimitive("itembased"), "time" to JsonPrimitive(3)), web = true, expected = expected, authenticated = true)
            "song/red/count" -> return official("/api/song/red/count", data("songId" to id), expected = expected)
            "comment/music" -> return official("/api/v1/resource/comments/R_SO_4_$id", params("rid" to JsonPrimitive("R_SO_4_$id"),
                "limit" to JsonPrimitive(page("limit", 20, min = 1)), "offset" to JsonPrimitive(page("offset", 0, max = Int.MAX_VALUE)), "beforeTime" to JsonPrimitive(0)), web = true, expected = expected)
            "playmode/intelligence/list" -> return official("/api/playmode/intelligence/list", data("songId" to id, "type" to "fromPlayOne",
                "playlistId" to arg("pid"), "startMusicId" to arg("sid", id), "count" to arg("count", "20")), expected = expected, authenticated = true)
            "search/hot/detail" -> return official("/api/hotsearchlist/get", web = true, expected = expected)
            "style/list" -> return official("/api/tag/list/get", web = true, expected = expected)
            "style/detail" -> return official("/api/style-tag/home/head", data("tagId" to arg("tagId")), web = true, expected = expected)
            "style/song" -> return official("/api/style-tag/home/song", data("tagId" to arg("tagId"), "cursor" to arg("cursor", "0"),
                "size" to arg("size", "30"), "sort" to arg("sort", "0")), web = true, expected = expected)
            "artist" -> return coroutineScope {
                val info = async { official("/api/v1/artist/$id", web = true, expected = expected) }
                val tracks = async { official("/api/v1/artist/songs", data("id" to id, "private_cloud" to "true", "work_type" to "1", "order" to "hot",
                    "offset" to arg("offset", "0"), "limit" to arg("limit", "100")), expected = expected) }
                val albums = async { official("/api/artist/albums/$id", data("limit" to "6", "offset" to "0", "total" to "true"), web = true, expected = expected) }
                val raw = info.await(); val page = tracks.await(); val collection = albums.await()
                buildJsonObject {
                    artist(raw.obj("artist")).forEach { (key, value) -> put(key, value) }
                    put("songs", songs(page.array("songs"))); put("more", page["more"] ?: JsonPrimitive(false)); put("total", page["total"] ?: raw.obj("artist")["musicSize"] ?: JsonPrimitive(0))
                    put("albums", JsonArray(collection.array("hotAlbums").mapNotNull { it as? JsonObject }.map { album(it) }))
                }
            }
            "artist/desc" -> return official("/api/artist/introduction", data("id" to id), web = true, expected = expected)
            "artist/albums" -> {
                val raw = official("/api/artist/albums/$id", data("limit" to arg("limit", "30"), "offset" to arg("offset", "0"), "total" to "true"), web = true, expected = expected)
                return buildJsonObject { put("albums", JsonArray(raw.array("hotAlbums").mapNotNull { it as? JsonObject }.map { album(it) })); put("more", raw["more"] ?: JsonPrimitive(false)) }
            }
            "album" -> { val raw = official("/api/v1/album/$id", web = true, expected = expected); return album(raw.obj("album"), raw.array("songs")) }
            "mv/detail" -> return official("/api/v1/mv/detail", data("id" to arg("mvid")), web = true, expected = expected)
            "mv/url" -> return official("/api/song/enhance/play/mv/url", data("id" to id, "r" to arg("r", "1080")), web = true, expected = expected)
            "captcha/sent/v1" -> return official("/api/middle/captcha/sent/v1", data("cellphone" to arg("phone"), "ctcode" to arg("ctcode", "86"),
                "secrete" to "music_middleuser_pclogin", "scene" to "0"), expected = expected)
            "user/detail" -> return official("/api/v1/user/detail/${arg("uid")}", web = true, expected = expected)
            "user/subcount" -> return official("/api/subcount", web = true, expected = expected, authenticated = true)
            "playlist/create" -> return official("/api/playlist/create", data("name" to arg("name"), "privacy" to arg("privacy", "0"), "type" to "NORMAL"), web = true, expected = expected, authenticated = true)
            "playlist/name/update" -> return official("/api/playlist/update/name", data("id" to id, "name" to arg("name")), expected = expected, authenticated = true)
            "playlist/delete" -> return official("/api/playlist/remove", data("ids" to "[$id]"), web = true, expected = expected, authenticated = true)
        }
        // Unmapped routes fail immediately; music requests never fall back to CurrentMusic.
        throw ApiException(ErrorKind.NotFound)
    }
    suspend fun daily(expectedRevision: Long? = null): JsonObject {
        val expected = sessions.snapshot()
        if (expectedRevision != null && expectedRevision != expected.revision) throw ApiException(ErrorKind.NeteaseSessionChanged)
        val raw = if (sessions.state.value.loggedIn && !sessions.state.value.stale) official("/api/v3/discovery/recommend/songs", web = true, expected = expected, authenticated = true)
            else official("/api/personalized/newsong", data("type" to "recommend", "limit" to "30"), web = true, expected = expected)
        val tracks = raw.obj("data").array("dailySongs").takeIf { it.isNotEmpty() }
            ?: raw.array("recommend").takeIf { it.isNotEmpty() }
            ?: JsonArray(raw.array("result").mapNotNull { (it as? JsonObject)?.get("song") })
        val forYou = if (expected.credential.profile != null && !expected.credential.stale) {
            when (val fm = withTimeoutOrNull(3_000) {
                appResult { official("/api/v1/radio/get", web = true, expected = expected, authenticated = true) }
            } ?: AppResult.Failure(ErrorKind.Timeout)) {
                is AppResult.Success -> fm.value.array("data").takeIf { it.isNotEmpty() } ?: tracks
                is AppResult.Failure -> { sessions.check(expected); tracks }
            }
        } else tracks
        return buildJsonObject { put("daily", songs(tracks)); put("forYou", songs(forYou)); put("artists", JsonArray(emptyList())) }
    }
    suspend fun recent(limit: Int = 50, expectedRevision: Long? = null): JsonObject {
        val expected = sessions.snapshot()
        if (expectedRevision != null && expectedRevision != expected.revision) throw ApiException(ErrorKind.NeteaseSessionChanged)
        if (!sessions.state.value.loggedIn) return buildJsonObject { put("songs", JsonArray(emptyList())) }
        val raw = official("/api/play-record/song/list", params("limit" to JsonPrimitive(limit)), web = true, expected = expected, authenticated = true)
        return buildJsonObject { put("songs", songs(JsonArray(raw.obj("data").array("list").mapNotNull { (it as? JsonObject)?.get("data") }))) }
    }
    suspend fun recordPlay(id: Long, milliseconds: Long? = null, expectedRevision: Long? = null) = listeningMutex.withLock {
        val expected = sessions.snapshot()
        if (expectedRevision != null && expectedRevision != expected.revision) throw ApiException(ErrorKind.NeteaseSessionChanged)
        if (!sessions.state.value.loggedIn || sessions.state.value.stale) return@withLock
        val owner = expected.revision to id
        if (milliseconds == null || listeningOwner != owner) {
            listeningOwner = owner; listeningMs = 0; listeningReported = false
        }
        if (milliseconds != null) {
            listeningMs += milliseconds.coerceAtLeast(0)
            // MusicService supplies 15-second deltas. Submit one qualified listen per
            // playback instead of treating every delta as a completed song.
            if (listeningReported || listeningMs < 60_000) return@withLock
            listeningReported = true
        }
        val event = buildJsonObject {
            put("action", if (milliseconds == null) "startplay" else "play")
            put("json", buildJsonObject {
                put("id", id); put("type", "song"); put("mainsite", "1"); put("mainsiteWeb", "1"); put("source", "list"); put("sourceId", "0")
                put("content", "id=0")
                if (milliseconds != null) { put("time", listeningMs / 1000); put("end", "playend"); put("download", 0) }
            })
        }
        official("/api/feedback/weblog", data("logs" to JsonArray(listOf(event)).toString()), clientLog = true, expected = expected, authenticated = true)
    }
}
