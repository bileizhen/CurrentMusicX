package io.github.currencortex.music.data.style

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.NeteaseSongActionsRepository
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class MusicStyle(val tagId: Long, val tagName: String, val enName: String = "",
    val childrenTags: List<MusicStyle>? = null) {
    fun find(id: Long): MusicStyle? = if (tagId == id) this else childrenTags.orEmpty().firstNotNullOfOrNull { it.find(id) }
}
@Serializable private data class StyleListResponse(val data: List<MusicStyle>)
@Serializable data class StyleDescription(val tagId: Long, val name: String, val enName: String = "",
    val desc: String = "", val cover: List<String> = emptyList())
@Serializable private data class StyleDetailResponse(val data: StyleDescription)
data class StyleSongsPage(val songs: List<Song>, val nextCursor: Int, val more: Boolean, val total: Int)

/** Public NetEase catalog; independent of the selected playback source. */
class MusicStyleRepository(private val api: ApiClient, private val session: () -> RequestSession) {
    private val reads = SessionReadCache(session, { 0L })
    private suspend fun request(path: String, expected: RequestSession, query: Map<String, String> = emptyMap()): JsonElement {
        val result = api.request("GET", "ncm/style/$path", query, expectedSession = expected)
        val code = (result as? JsonObject)?.get("code")?.jsonPrimitive?.intOrNull
        if (code != null && code != 200) throw ApiException(if (code == 404) ErrorKind.NotFound else ErrorKind.Server, code)
        return result
    }
    suspend fun list(fresh: Boolean = false): List<MusicStyle> = withContext(Dispatchers.Default) {
        reads.read("styles", fresh) { expected ->
            api.decode<StyleListResponse>(request("list", expected)).data.filter { it.tagId > 0 && it.tagName.isNotBlank() }.distinctBy { it.tagId }
        }
    }
    suspend fun detail(id: Long, fresh: Boolean = false): StyleDescription = withContext(Dispatchers.Default) {
        require(id > 0)
        reads.read("style/$id", fresh) { expected ->
            api.decode<StyleDetailResponse>(request("detail", expected, mapOf("tagId" to "$id"))).data.also {
                if (it.tagId != id) throw ApiException(ErrorKind.Parse)
            }
        }
    }
    suspend fun songs(id: Long, sort: Int, cursor: Int = 0, size: Int = 30, fresh: Boolean = false): StyleSongsPage = withContext(Dispatchers.Default) {
        require(id > 0 && sort in 0..1 && cursor >= 0 && size in 1..100)
        reads.read("style/$id/$sort/$cursor/$size", fresh) { expected ->
            val raw = request("song", expected, mapOf("tagId" to "$id", "sort" to "$sort", "cursor" to "$cursor", "size" to "$size")) as? JsonObject
                ?: throw ApiException(ErrorKind.Parse)
            val data = raw["data"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
            val records = data["songs"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
            val page = data["page"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
            // The deployed API echoes this page's offset, rather than a next-page token.
            val offset = page["cursor"]?.jsonPrimitive?.intOrNull ?: cursor
            val count = page["size"]?.jsonPrimitive?.intOrNull ?: records.size
            val next = maxOf(cursor, offset).toLong() + count.coerceAtLeast(records.size)
            if (next > Int.MAX_VALUE) throw ApiException(ErrorKind.Parse)
            StyleSongsPage(records.mapNotNull { (it as? JsonObject)?.let(NeteaseSongActionsRepository::nativeSong) }.distinctBy { it.id },
                next.toInt(), page["more"]?.jsonPrimitive?.booleanOrNull == true && records.isNotEmpty() && next > cursor,
                page["total"]?.jsonPrimitive?.intOrNull ?: 0)
        }
    }
}
