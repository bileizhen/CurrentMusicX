package io.github.currencortex.music.data.library

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.binding.BindingRepository
import io.github.currencortex.music.data.settings.MusicSettingsRepository
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Upstream IDs stay separate from CurrentMusic's imported playlist IDs. */
class NeteaseLibraryRepository(private val api: ApiClient, private val binding: BindingRepository,
    private val actions: NeteaseSongActionsRepository, private val session: () -> RequestSession) {
    val revision = MutableStateFlow(0L)
    private val reads = SessionReadCache(session, { revision.value + actions.revision.value })
    private val writeMutex = Mutex()
    fun invalidate() { reads.clear(); revision.update { it + 1 } }
    private suspend fun owner(fresh: Boolean = false): Long {
        val bound = if (fresh) binding.status() else binding.cachedStatus()
        return bound.profile?.uid?.takeIf { bound.bound && !bound.stale && it > 0 }
            ?: throw ApiException(ErrorKind.NeteaseBindingRequired)
    }
    private suspend fun request(path: String, query: Map<String, String>, expected: RequestSession): JsonObject {
        val response = api.request("GET", "ncm/$path", query + ("timestamp" to "${System.currentTimeMillis()}"),
            authenticated = true, expectedSession = expected, retryConnection = path != "playlist/tracks") as? JsonObject
            ?: throw ApiException(ErrorKind.Parse)
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        val code = response["code"]?.jsonPrimitive?.intOrNull
        if (code != null && code != 200) throw ApiException(if (code in listOf(301, 401)) ErrorKind.NeteaseBindingRequired else ErrorKind.Server, code)
        return response
    }
    suspend fun playlists(fresh: Boolean = false): List<Playlist> = withContext(Dispatchers.Default) {
        val expectedOwner = session()
        val uid = owner(fresh)
        if (expectedOwner != session()) throw ApiException(ErrorKind.Unauthorized)
        reads.read("playlists/$uid", fresh) { expected ->
            if (expected != expectedOwner) throw ApiException(ErrorKind.Unauthorized)
            val result = mutableListOf<Playlist>()
            var offset = 0
            do {
                val response = request("user/playlist", mapOf("uid" to "$uid", "limit" to "100", "offset" to "$offset"), expected)
                val page = response["playlist"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
                result += page.mapNotNull { (it as? JsonObject)?.toPlaylist(uid) }
                offset += page.size
                val more = response["more"]?.jsonPrimitive?.booleanOrNull == true
                if (!more || page.isEmpty()) break
            } while (true)
            result.distinctBy { it.id }
        }
    }
    private suspend fun songs(ids: List<Long>, expected: RequestSession, known: List<Song> = emptyList()): List<Song> {
        val found = known.associateByTo(mutableMapOf()) { it.id }
        // CurrentMusic's song-detail gateway accepts at most 100 IDs per request.
        ids.distinct().filterNot(found::containsKey).chunked(100).forEach { chunk ->
            val response = request("song/detail", mapOf("ids" to chunk.joinToString(",")), expected)
            val page = response["songs"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
            page.mapNotNull { (it as? JsonObject)?.let(NeteaseSongActionsRepository::nativeSong) }.forEach { found[it.id] = it }
        }
        return ids.mapNotNull(found::get)
    }
    suspend fun playlist(id: Long, fresh: Boolean = false): Playlist = withContext(Dispatchers.Default) {
        val expectedOwner = session()
        val uid = owner(fresh)
        if (expectedOwner != session()) throw ApiException(ErrorKind.Unauthorized)
        reads.read("playlist/$uid/$id", fresh) { expected ->
            if (expected != expectedOwner) throw ApiException(ErrorKind.Unauthorized)
            val response = request("playlist/detail", mapOf("id" to "$id", "s" to "0"), expected)
            val raw = response["playlist"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
            val info = raw.toPlaylist(uid) ?: throw ApiException(ErrorKind.Parse)
            if (info.id != id) throw ApiException(ErrorKind.Parse)
            val ids = (raw["trackIds"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull }
            val embedded = (raw["tracks"] as? JsonArray)?.mapNotNull {
                (it as? JsonObject)?.let(NeteaseSongActionsRepository::nativeSong)
            }
            val tracks = if (ids != null) songs(ids, expected, embedded.orEmpty()) else {
                (embedded ?: throw ApiException(ErrorKind.Parse)).also {
                    if (it.size < info.count) throw ApiException(ErrorKind.Parse)
                }
            }
            info.copy(songs = tracks)
        }
    }
    suspend fun likedPlaylist(fresh: Boolean = false): Playlist = withContext(Dispatchers.Default) {
        val expected = session()
        val info = playlists(fresh).firstOrNull { it.nativeLiked && it.nativeOwned }
            ?: throw ApiException(ErrorKind.NeteaseLikedPlaylistUnavailable)
        // Membership comes from the user's red-heart list, including acknowledged writes
        // while upstream playlist/detail is still catching up.
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        reads.read("liked/${info.ownerId}/${info.id}", fresh) { owner ->
            if (owner != expected) throw ApiException(ErrorKind.Unauthorized)
            val ids = actions.likedIds(fresh).toList()
            val detail = request("playlist/detail", mapOf("id" to "${info.id}", "s" to "0"), expected)
            val raw = detail["playlist"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
            if (raw["id"]?.jsonPrimitive?.longOrNull != info.id) throw ApiException(ErrorKind.Parse)
            val embedded = (raw["tracks"] as? JsonArray).orEmpty().mapNotNull {
                (it as? JsonObject)?.let(NeteaseSongActionsRepository::nativeSong)
            }
            val tracks = songs(ids, expected, embedded)
            if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
            info.copy(name = "我喜欢的音乐", songs = tracks, count = tracks.size)
        }
    }
    suspend fun add(id: Long, song: Song) = writeMutex.withLock {
        val songId = NeteaseSongActionsRepository.songId(song) ?: throw ApiException(ErrorKind.NotFound)
        val expected = session()
        val uid = owner(fresh = true)
        val target = playlists(fresh = true).firstOrNull { it.id == id && it.ownerId == uid && it.nativeOwned }
            ?: throw ApiException(ErrorKind.Forbidden)
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        if (target.nativeLiked) actions.setLiked(songId, true, expected, song)
        else request("playlist/tracks", mapOf("pid" to "$id", "tracks" to "$songId", "op" to "add", "confirm" to "1"), expected)
        invalidate()
    }
    private fun JsonObject.toPlaylist(uid: Long): Playlist? {
        val id = this["id"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return null
        val creator = this["creator"] as? JsonObject
        val owner = creator?.get("userId")?.jsonPrimitive?.longOrNull ?: this["userId"]?.jsonPrimitive?.longOrNull ?: 0
        return Playlist(id, this["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            this["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            this["coverImgUrl"]?.jsonPrimitive?.contentOrNull.orEmpty().replace("http:", "https:"),
            "netease", owner, this["trackCount"]?.jsonPrimitive?.intOrNull ?: 0, emptyList(),
            nativeOwned = owner == uid, nativeLiked = this["specialType"]?.jsonPrimitive?.intOrNull == 5)
    }
}

/** Bound accounts can use the live NetEase library without importing CurrentMusic snapshots. */
class PrimaryMusicLibrary(private val current: LibraryRepository, private val netease: NeteaseLibraryRepository,
    private val binding: BindingRepository, private val settings: MusicSettingsRepository, scope: CoroutineScope) {
    val identity = combine(binding.state, settings.state.map { it.neteaseMainLibrary }.distinctUntilChanged()) { bound, preferred ->
        Triple(preferred && bound?.bound == true, bound?.profile?.uid ?: 0, bound?.stale == true)
    }.stateIn(scope, SharingStarted.Eagerly, Triple(false, 0L, false))
    val usesNetease = identity.map { it.first }.stateIn(scope, SharingStarted.Eagerly, false)
    suspend fun resolveNetease(): Boolean = settings.snapshot().neteaseMainLibrary && binding.cachedStatus().bound
    suspend fun playlists(fresh: Boolean = false): List<Playlist> = if (resolveNetease()) netease.playlists(fresh) else {
        if (fresh) current.clearReads()
        current.playlists()
    }
    suspend fun likedPlaylist(fresh: Boolean = false): Playlist? = if (resolveNetease()) netease.likedPlaylist(fresh) else null
}
