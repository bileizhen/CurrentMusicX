package io.github.currencortex.music.data.netease.listening

import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

internal data class ListeningArtistCapabilities(val read: Boolean, val follow: Boolean)

/** Official monthly-artist actions. A bound instance belongs to exactly one captured account. */
internal class NeteaseListeningSocialRepository(
    private val checkSession: () -> Unit,
    private val request: suspend (String, JsonObject) -> JsonObject,
) {
    private val writes = ListeningActionGate(checkSession)

    suspend fun capabilities(): ListeningArtistCapabilities {
        val body = read("/api/middle/clientcfg/config/list", buildJsonObject {
            put("moduleName", "activityBench"); put("keys", "[\"listen-data-homepage\"]"); put("platform", "web")
        })
        val extra = ((body["data"] as? JsonObject)?.get("activityBench#listen-data-homepage") as? JsonObject)
            ?.get("extraConfig") as? JsonObject
        return ListeningArtistCapabilities(
            (extra?.get("canFetchFollowArtistStatus") as? JsonPrimitive)?.booleanOrNull == true,
            (extra?.get("canFetchFollowArtist") as? JsonPrimitive)?.booleanOrNull == true)
    }

    suspend fun followed(id: Long, capabilities: ListeningArtistCapabilities): Boolean {
        require(id > 0)
        if (!capabilities.read) throw ApiException(ErrorKind.Forbidden)
        val body = read("/api/user/sub/artist/exist", buildJsonObject { put("artistIds", JsonArray(listOf(JsonPrimitive(id)))) })
        val row = (body["data"] as? JsonArray)?.singleOrNull() as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        val returnedId = (row["artistId"] as? JsonPrimitive)?.longOrNull
        if (returnedId != null && returnedId != id) throw ApiException(ErrorKind.Parse)
        return (row["followed"] as? JsonPrimitive)?.booleanOrNull ?: throw ApiException(ErrorKind.Parse)
    }

    /** The report offers follow, never an unfollow toggle. No automatic retry of writes. */
    suspend fun follow(id: Long, capabilities: ListeningArtistCapabilities): Boolean {
        require(id > 0)
        if (!capabilities.read || !capabilities.follow) throw ApiException(ErrorKind.Forbidden)
        return writes.run("artist:$id") {
            read("/api/v1/artist/sub", buildJsonObject { put("artistId", id) })
        }
    }

    private suspend fun read(path: String, payload: JsonObject): JsonObject {
        currentCoroutineContext().ensureActive(); checkSession()
        val body = request(path, payload)
        currentCoroutineContext().ensureActive(); checkSession()
        val code = (body["code"] as? JsonPrimitive)?.intOrNull ?: throw ApiException(ErrorKind.Parse)
        if (code != 200) throw ApiException(when (code) {
            301, 401 -> ErrorKind.NeteaseBindingRequired
            403 -> ErrorKind.Forbidden
            429 -> ErrorKind.RateLimited
            else -> ErrorKind.Server
        }, code)
        return body
    }
}

/** Drop repeated taps, preserve cancellation and validate the account on both sides of a write. */
internal class ListeningActionGate(private val checkSession: () -> Unit) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    suspend fun run(key: String, action: suspend () -> Unit): Boolean {
        val lock = locks.getOrPut(key) { Mutex() }
        if (!lock.tryLock()) return false
        try {
            currentCoroutineContext().ensureActive(); checkSession()
            action()
            currentCoroutineContext().ensureActive(); checkSession()
            return true
        } finally { lock.unlock() }
    }
}
