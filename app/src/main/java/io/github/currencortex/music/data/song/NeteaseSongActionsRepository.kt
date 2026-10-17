package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.binding.BindingState
import io.github.currencortex.music.data.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class NeteaseCommentUser(val nickname: String = "", val avatarUrl: String = "")
@Serializable data class NeteaseCommentReply(val content: String? = null, val user: NeteaseCommentUser? = null)
@Serializable data class NeteaseComment(val commentId: Long, val content: String = "", val time: Long = 0,
    val likedCount: Long = 0, val user: NeteaseCommentUser = NeteaseCommentUser(), val beReplied: List<NeteaseCommentReply> = emptyList())
@Serializable data class NeteaseCommentsPage(val total: Long? = null, val more: Boolean = false,
    val hotComments: List<NeteaseComment> = emptyList(), val comments: List<NeteaseComment> = emptyList())
data class NeteaseHeartList(val playlistId: Long, val songs: List<Song>)
@Serializable private data class NeteaseLikes(val ids: List<Long>? = null)

/** Account actions use the music API transport, independently of the selected audio provider. */
class NeteaseSongActionsRepository(private val api: ApiClient, private val now: () -> Long = System::nanoTime,
    private val session: () -> RequestSession) {
    val revision = MutableStateFlow(0L)
    val likedState = MutableStateFlow<Set<Long>>(emptySet())
    private val reads = SessionReadCache(session, { 0L })
    private data class LikedSnapshot(val uid: Long, val ids: Set<Long>)
    private data class WriteKey(val session: RequestSession, val uid: Long, val id: Long)
    private data class ConfirmedLike(val liked: Boolean, val until: Long)
    private val confirmedLikes = java.util.concurrent.ConcurrentHashMap<WriteKey, ConfirmedLike>()
    private suspend fun request(path: String, query: Map<String, String>, expected: RequestSession): JsonElement {
        val value = api.request("GET", "ncm/$path", query, authenticated = expected.token != null, expectedSession = expected)
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        val code = (value as? JsonObject)?.get("code")?.jsonPrimitive?.intOrNull
        if (code != null && code != 200) throw ApiException(when {
            code in listOf(301, 401) -> ErrorKind.NeteaseBindingRequired
            path == "playmode/intelligence/list" && code == 400 &&
                (value as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull?.contains("歌单不存在") == true -> ErrorKind.NeteaseLikedPlaylistUnavailable
            else -> ErrorKind.Server
        }, code)
        return value
    }
    private suspend fun binding(expected: RequestSession): BindingState {
        if (expected.token == null && api.netease == null) throw ApiException(ErrorKind.Unauthorized)
        val value = api.decode<BindingState>(api.request("GET", "ncmbind", authenticated = true, expectedSession = expected))
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        if (!value.bound || value.stale || value.profile?.uid == null || value.profile.uid <= 0)
            throw ApiException(ErrorKind.NeteaseBindingRequired)
        return value
    }
    suspend fun likeCount(id: Long, fresh: Boolean = false): Long? = withContext(Dispatchers.Default) {
        reads.read("red/$id", fresh) { expected ->
            val data = request("song/red/count", mapOf("id" to "$id"), expected) as? JsonObject
            // Unknown counts remain unknown, never substitute popularity or CurrentMusic likes.
            ((data?.get("data") as? JsonObject)?.get("count") as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }?.let(::Count) ?: Count(null)
        }.value
    }
    suspend fun comments(id: Long, offset: Int = 0, limit: Int = 20, fresh: Boolean = false): NeteaseCommentsPage = withContext(Dispatchers.Default) {
        reads.read("comments/$id/$offset/$limit", fresh) { expected ->
            api.decode<NeteaseCommentsPage>(request("comment/music", mapOf("id" to "$id", "offset" to "$offset", "limit" to "$limit"), expected))
        }
    }
    suspend fun sendComment(id: Long, content: String, replyTo: Long? = null) {
        require(id > 0 && content.isNotBlank() && content.trim().length <= 1000 && (replyTo == null || replyTo > 0))
        val expected = session()
        binding(expected)
        val result = api.request("POST", "ncmbind/comment/$id", body = buildJsonObject {
            put("content", content.trim()); replyTo?.let { put("commentId", it) }
        }, authenticated = true, expectedSession = expected, retryConnection = false) as? JsonObject
            ?: throw ApiException(ErrorKind.Parse)
        if (expected != session()) throw ApiException(ErrorKind.NeteaseSessionChanged)
        val code = result["code"]?.jsonPrimitive?.intOrNull
        if (code != null && code != 200) throw ApiException(when (code) {
            301, 401 -> ErrorKind.NeteaseBindingRequired
            403 -> ErrorKind.Forbidden
            429 -> ErrorKind.RateLimited
            else -> ErrorKind.Server
        }, code)
        if (result["ok"]?.jsonPrimitive?.booleanOrNull == false) throw ApiException(ErrorKind.Server)
        reads.clear()
    }
    suspend fun likedIds(fresh: Boolean = false): Set<Long> = withContext(Dispatchers.Default) {
        val expected = session()
        val bound = binding(expected)
        val snapshot = reads.read("liked/${bound.profile!!.uid}", fresh) { expected ->
            val value = try {
                api.request("GET", "ncmbind/likelist", mapOf("timestamp" to "${System.currentTimeMillis()}"), authenticated = true, expectedSession = expected)
            } catch (failure: ApiException) {
                // Some cloud servers expose the binding route but return 502 while
                // their generic read route is healthy. Only retry this idempotent read.
                val cloudReadFailure = !api.hasLocalNeteaseLogin && failure.kind == ErrorKind.Server
                if (failure.kind != ErrorKind.NotFound && !cloudReadFailure) throw failure
                try {
                    request("likelist", mapOf("uid" to "${bound.profile!!.uid}", "timestamp" to "${System.currentTimeMillis()}"), expected)
                } catch (generic: ApiException) {
                    if (api.hasLocalNeteaseLogin || generic.kind !in setOf(ErrorKind.NotFound, ErrorKind.Server)) throw generic
                    cloudLikedPlaylistIds(bound.profile!!.uid, expected)
                }
            }
            LikedSnapshot(bound.profile!!.uid, api.decode<NeteaseLikes>(value).ids?.toSet() ?: throw ApiException(ErrorKind.Parse))
        }
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        val ids = snapshot.ids.toMutableSet()
        confirmedLikes.forEach { (key, write) ->
            if (key.session == expected && key.uid == snapshot.uid) {
                if (write.until <= now()) confirmedLikes.remove(key, write)
                else {
                    if (fresh && (key.id in snapshot.ids) == write.liked) confirmedLikes.remove(key, write)
                    if (write.liked) ids.add(key.id) else ids.remove(key.id)
                }
            }
        }
        ids.toSet().also { likedState.value = it }
    }
    /** The owner's heart playlist is a read-only fallback when both cloud list adapters fail. */
    private suspend fun cloudLikedPlaylistIds(uid: Long, expected: RequestSession): JsonObject {
        var offset = 0
        do {
            val response = request("user/playlist", mapOf("uid" to "$uid", "limit" to "100", "offset" to "$offset"), expected).jsonObject
            val page = response["playlist"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
            val liked = page.mapNotNull { it as? JsonObject }.firstOrNull {
                val creator = it["creator"] as? JsonObject
                val owner = creator?.get("userId")?.jsonPrimitive?.longOrNull ?: it["userId"]?.jsonPrimitive?.longOrNull
                owner == uid && it["specialType"]?.jsonPrimitive?.intOrNull == 5 &&
                    it["subscribed"]?.jsonPrimitive?.booleanOrNull != true
            }
            if (liked != null) {
                val id = liked["id"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: throw ApiException(ErrorKind.Parse)
                val detail = request("playlist/detail", mapOf("id" to "$id", "s" to "0"), expected).jsonObject["playlist"] as? JsonObject
                    ?: throw ApiException(ErrorKind.Parse)
                if (detail["id"]?.jsonPrimitive?.longOrNull != id) throw ApiException(ErrorKind.Parse)
                val tracks = detail["trackIds"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
                val ids = tracks.map { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull?.takeIf { value -> value > 0 }
                    ?: throw ApiException(ErrorKind.Parse) }
                return buildJsonObject { put("ids", JsonArray(ids.distinct().map(::JsonPrimitive))) }
            }
            offset += page.size
        } while (page.isNotEmpty() && response["more"]?.jsonPrimitive?.booleanOrNull == true)
        throw ApiException(ErrorKind.NeteaseLikedPlaylistUnavailable)
    }
    suspend fun isLiked(id: Long, fresh: Boolean = false): Boolean = id in likedIds(fresh)
    suspend fun toggleLiked(id: Long, song: Song? = null): Boolean {
        val expected = session()
        val desired = !isLiked(id, fresh = true)
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        setLiked(id, desired, expected, song)
        return desired
    }
    suspend fun setLiked(id: Long, liked: Boolean, expected: RequestSession = session(), song: Song? = null) {
        val bound = binding(expected)
        val response = try {
            api.request("POST", "ncmbind/like/$id", query = mapOf("timestamp" to "${System.currentTimeMillis()}"), body = buildJsonObject {
                LibraryRepository.metadata(song ?: Song(id, "")).forEach { (key, value) -> put(key, value) }
                put("ncm_id", id); put("like", liked)
            }, authenticated = true, expectedSession = expected)
        } catch (failure: ApiException) {
            if (failure.kind != ErrorKind.NotFound) throw failure
            request("like", mapOf("id" to "$id", "like" to "$liked", "timestamp" to "${System.currentTimeMillis()}"), expected)
        }
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        val code = (response as? JsonObject)?.get("code")?.jsonPrimitive?.intOrNull
        if (code != null && code != 200) throw ApiException(if (code in listOf(301, 401)) ErrorKind.NeteaseBindingRequired else ErrorKind.Server, code)
        val acknowledged = (response as? JsonObject)?.let { it["like"] ?: it["on"] }?.jsonPrimitive?.booleanOrNull
        if (acknowledged != null && acknowledged != liked) throw ApiException(ErrorKind.Server)
        confirmedLikes.entries.removeIf { it.key.session != expected || it.value.until <= now() }
        confirmedLikes[WriteKey(expected, bound.profile!!.uid, id)] = ConfirmedLike(liked, now() + 120_000_000_000L)
        likedState.update { if (liked) it + id else it - id }
        reads.clear()
        revision.update { it + 1 }
    }
    suspend fun heartList(seedId: Long, playlistId: Long? = null): NeteaseHeartList = withContext(Dispatchers.Default) {
        val expected = session()
        val bound = binding(expected)
        val uid = bound.profile!!.uid
        // Binding's ncmLikedPlId refers to a CurrentMusic import. Obtain the upstream ID separately.
        val pid = reads.read("liked-playlist/$uid") {
            val lists = request("user/playlist", mapOf("uid" to "$uid", "limit" to "100"), expected) as? JsonObject
            (lists?.get("playlist") as? JsonArray)?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { item ->
                    val owner = (item["creator"] as? JsonObject)?.get("userId")?.jsonPrimitive?.longOrNull
                    item["specialType"]?.jsonPrimitive?.intOrNull == 5 && (owner == null || owner == uid)
                }?.get("id")?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
                ?: throw ApiException(ErrorKind.NeteaseLikedPlaylistUnavailable)
        }
        // Restored recommendations may belong to an account that has since been replaced.
        if (playlistId != null && playlistId != pid) throw ApiException(ErrorKind.NeteaseBindingRequired)
        val raw = request("playmode/intelligence/list", mapOf("id" to "$seedId", "sid" to "$seedId", "pid" to "$pid", "count" to "20"), expected) as? JsonObject
        val songs = (raw?.get("data") as? JsonArray).orEmpty().mapNotNull { entry ->
            val info = (entry as? JsonObject)?.get("songInfo") as? JsonObject ?: return@mapNotNull null
            nativeSong(info)
        }.distinctBy { it.id }.filter { it.id != seedId }
        if (songs.isEmpty()) throw ApiException(ErrorKind.NeteaseHeartNoRecommendations)
        NeteaseHeartList(pid, songs)
    }
    private data class Count(val value: Long?)
    fun clearSession() { reads.clear(); confirmedLikes.clear(); likedState.value = emptySet(); revision.update { it + 1 } }
    companion object {
        fun songId(song: Song?): Long? = song?.takeIf { !it.video && it.musicSource == MusicSource.NETEASE }
            ?.let { it.externalIds.neteaseId?.toLongOrNull() ?: it.id }?.takeIf { it > 0 }
        internal fun nativeSong(info: JsonObject): Song? {
            // /ncm/song/detail normalizes metadata; other NCM endpoints return raw songs.
            if (info["ncm_id"] != null) return ApiJson.decodeFromJsonElement<SongDto>(info).toDomain()
                .takeIf { it.id > 0 && it.name.isNotBlank() }
            fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
            val id = info["id"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return null
            val name = info.string("name").takeIf { it.isNotBlank() } ?: return null
            val artists = ((info["ar"] ?: info["artists"]) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val album = (info["al"] ?: info["album"]) as? JsonObject
            return Song(id, name, artists.joinToString(" / ") { it.string("name") }, album?.string("name").orEmpty(),
                album?.string("picUrl").orEmpty().replace("http:", "https:"),
                (info["dt"] ?: info["duration"])?.jsonPrimitive?.longOrNull ?: 0,
                (info["mv"] ?: info["mvid"])?.jsonPrimitive?.longOrNull ?: 0,
                artists.mapNotNull { it["id"]?.jsonPrimitive?.longOrNull })
        }
    }
}

internal fun formatNeteaseCount(count: Long): String = when {
    count >= 100_000_000 -> "${count / 100_000_000}亿+"
    count >= 10_000 -> "${count / 10_000}w+"
    else -> count.coerceAtLeast(0).toString()
}
