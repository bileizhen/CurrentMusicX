package io.github.currencortex.music.data.song

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.binding.BindingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class NeteaseCommentUser(val nickname: String = "", val avatarUrl: String = "")
@Serializable data class NeteaseComment(val commentId: Long, val content: String = "", val time: Long = 0,
    val likedCount: Long = 0, val user: NeteaseCommentUser = NeteaseCommentUser())
@Serializable data class NeteaseCommentsPage(val total: Long? = null, val more: Boolean = false,
    val hotComments: List<NeteaseComment> = emptyList(), val comments: List<NeteaseComment> = emptyList())
data class NeteaseHeartList(val playlistId: Long, val songs: List<Song>)
@Serializable private data class NeteaseLikes(val ids: List<Long>? = null)

/** Metadata and account actions always go through CurrentMusic, independently of the audio source. */
class NeteaseSongActionsRepository(private val api: ApiClient, private val session: () -> RequestSession) {
    private val reads = SessionReadCache(session, { 0L })
    private suspend fun request(path: String, query: Map<String, String>, expected: RequestSession): JsonElement {
        val value = api.request("GET", "ncm/$path", query, authenticated = expected.token != null, expectedSession = expected)
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        val code = (value as? JsonObject)?.get("code")?.jsonPrimitive?.intOrNull
        if (code != null && code != 200) throw ApiException(if (code in listOf(301, 401)) ErrorKind.NeteaseBindingRequired else ErrorKind.Server, code)
        return value
    }
    private suspend fun binding(expected: RequestSession): BindingState {
        if (expected.token == null) throw ApiException(ErrorKind.Unauthorized)
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
    suspend fun isLiked(id: Long, fresh: Boolean = false): Boolean = withContext(Dispatchers.Default) {
        reads.read("liked", fresh) { expected ->
            val bound = binding(expected)
            api.decode<NeteaseLikes>(request("likelist", mapOf("uid" to "${bound.profile!!.uid}",
                "timestamp" to "${System.currentTimeMillis()}"), expected)).ids?.toSet()
                ?: throw ApiException(ErrorKind.Parse)
        }.contains(id)
    }
    suspend fun toggleLiked(id: Long): Boolean {
        val expected = session()
        val desired = !isLiked(id, fresh = true)
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        setLiked(id, desired, expected)
        return desired
    }
    suspend fun setLiked(id: Long, liked: Boolean, expected: RequestSession = session()) {
        binding(expected)
        request("like", mapOf("id" to "$id", "like" to "$liked", "timestamp" to "${System.currentTimeMillis()}"), expected)
        reads.clear()
    }
    suspend fun heartList(seedId: Long, playlistId: Long? = null): NeteaseHeartList = withContext(Dispatchers.Default) {
        val expected = session()
        val bound = binding(expected)
        var pid = bound.likedPlaylistId.takeIf { it > 0 }
        if (pid == null) {
            val lists = request("user/playlist", mapOf("uid" to "${bound.profile!!.uid}", "limit" to "100"), expected) as? JsonObject
            pid = (lists?.get("playlist") as? JsonArray)?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { it["specialType"]?.jsonPrimitive?.intOrNull == 5 }?.get("id")?.jsonPrimitive?.longOrNull
        }
        if (pid == null || pid <= 0) throw ApiException(ErrorKind.NotFound)
        // Restored recommendations may belong to an account that has since been replaced.
        if (playlistId != null && playlistId != pid) throw ApiException(ErrorKind.NeteaseBindingRequired)
        val raw = request("playmode/intelligence/list", mapOf("id" to "$seedId", "sid" to "$seedId", "pid" to "$pid", "count" to "20"), expected) as? JsonObject
        val songs = (raw?.get("data") as? JsonArray).orEmpty().mapNotNull { entry ->
            val info = (entry as? JsonObject)?.get("songInfo") as? JsonObject ?: return@mapNotNull null
            nativeSong(info)
        }.distinctBy { it.id }.filter { it.id != seedId }
        if (songs.isEmpty()) throw ApiException(ErrorKind.NotFound)
        NeteaseHeartList(pid, songs)
    }
    private data class Count(val value: Long?)
    companion object {
        fun songId(song: Song?): Long? = song?.takeIf { !it.video && it.musicSource == MusicSource.NETEASE }
            ?.let { it.externalIds.neteaseId?.toLongOrNull() ?: it.id }?.takeIf { it > 0 }
        internal fun nativeSong(info: JsonObject): Song? {
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
