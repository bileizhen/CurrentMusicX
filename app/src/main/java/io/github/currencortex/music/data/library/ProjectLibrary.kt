package io.github.currencortex.music.data.library

import io.github.currencortex.music.core.network.ApiClient
import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import io.github.currencortex.music.core.network.RequestSession
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.SongExternalIds
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class ProjectLibrarySource(val mode: String, val source: String,
    val binding: String, val writable: Boolean)
@Serializable data class ProjectLibraryPreferences(val providers: Map<String, ProjectLibrarySource>)
@Serializable internal data class ProjectPlaylistItem(val id: String, val name: String, val description: String = "",
    val artwork: String = "", val source: String, val trackCount: Int = 0, val version: Long? = null,
    val editable: Boolean = false, val liked: Boolean = false) {
    fun domain(owner: Long, songs: List<Song> = emptyList()) = Playlist(
        id.substringAfter(':').toLong(), name, description, artwork, source, if (editable) owner else 0,
        trackCount, songs, nativeOwned = editable, nativeLiked = liked, remoteId = id, revision = version)
}
@Serializable internal data class ProjectTrack(val id: String, val provider: String, val name: String,
    val artists: String = "", val album: String = "", val artwork: String = "", val durationMs: Long = 0, val videoId: String = "") {
    fun domain(): Song? = when (provider) {
        "netease" -> id.toLongOrNull()?.let { Song(it, name, artists, album, artwork, durationMs, videoId.toLongOrNull() ?: 0) }
        "kugou" -> if (id.matches(KUGOU_ID)) Song(kugouLocalId(id), name, artists, album, artwork, durationMs,
            musicSource = MusicSource.KUGOU, externalIds = SongExternalIds(kugouId = id)) else null
        else -> null
    }
}
@Serializable internal data class ProjectPlaylistPage(val items: List<ProjectPlaylistItem>, val nextOffset: Int? = null)
@Serializable internal data class ProjectPlaylistDetail(val resource: ProjectPlaylistItem, val tracks: List<ProjectTrack> = emptyList(),
    val nextOffset: Int? = null)
@Serializable internal data class ProjectFavoritePage(val items: List<ProjectTrack>, val nextOffset: Int? = null)
@Serializable internal data class ProjectTrackRef(val provider: String, val id: String) {
    fun json() = buildJsonObject { put("provider", provider); put("id", id) }
}
@Serializable internal data class ProjectFavoriteStatus(val selected: List<ProjectTrackRef> = emptyList())
@Serializable internal data class ProjectFavoriteResult(val selected: Boolean)

internal val KUGOU_ID = Regex("[a-f0-9]{32}-[1-9][0-9]{0,14}")
private fun kugouLocalId(id: String): Long = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(id.toByteArray())).long or Long.MIN_VALUE

internal fun projectTrackRef(song: Song): ProjectTrackRef = when (song.musicSource) {
    MusicSource.KUGOU -> ProjectTrackRef("kugou", song.externalIds.kugouId?.takeIf(KUGOU_ID::matches)
        ?: throw ApiException(ErrorKind.NotFound))
    MusicSource.NETEASE -> ProjectTrackRef("netease", song.id.takeIf { it > 0 }?.toString() ?: throw ApiException(ErrorKind.NotFound))
    else -> throw ApiException(ErrorKind.NotFound)
}

internal class ProjectLibrary(private val api: ApiClient, private val owner: () -> Long) {
    private suspend inline fun <reified T> request(method: String, path: String, session: RequestSession,
        query: Map<String, String> = emptyMap(), body: JsonElement? = null): T = api.decode(api.request(method,
        "library/v2/$path", query, body, authenticated = true, expectedSession = session, projectOnly = true))

    suspend fun preferences(session: RequestSession): ProjectLibraryPreferences? = try {
        request("GET", "preferences", session)
    } catch (error: ApiException) {
        if (error.status == 404) null else throw error
    }

    suspend fun setMode(session: RequestSession, platform: String, mode: String): ProjectLibraryPreferences = request(
        "PUT", "preferences", session, body = buildJsonObject { putJsonObject("providers") { put(platform, mode) } })

    suspend fun playlists(session: RequestSession): List<Playlist> {
        var offset: Int? = 0
        val all = mutableListOf<Playlist>()
        do {
            val page = request<ProjectPlaylistPage>("GET", "playlists", session,
                mapOf("platform" to "netease", "source" to "aggregate", "offset" to offset.toString()))
            all += page.items.map { it.domain(owner()) }
            offset = page.nextOffset
        } while (offset != null)
        return all
    }

    suspend fun detail(session: RequestSession, id: Long): Playlist {
        val songs = mutableListOf<Song>()
        var offset: Int? = 0
        var item: ProjectPlaylistItem? = null
        do {
            val page = request<ProjectPlaylistDetail>("GET", "playlists/aggregate:$id", session,
                mapOf("offset" to offset.toString()))
            if (item != null && item.version != page.resource.version) throw ApiException(ErrorKind.Server,
                userMessage = "歌单已更新，请刷新")
            item = page.resource
            songs += page.tracks.mapNotNull { it.domain() }
            offset = page.nextOffset
        } while (offset != null)
        return requireNotNull(item).domain(owner(), songs)
    }

    suspend fun favorites(session: RequestSession): List<Song> {
        val songs = mutableListOf<Song>()
        var offset: Int? = 0
        do {
            val page = request<ProjectFavoritePage>("GET", "favorites", session,
                mapOf("platform" to "netease", "source" to "aggregate", "offset" to offset.toString()))
            songs += page.items.mapNotNull { it.domain() }
            offset = page.nextOffset
        } while (offset != null)
        return songs
    }

    suspend fun create(session: RequestSession, name: String, description: String) {
        request<JsonObject>("POST", "playlists", session, mapOf("platform" to "netease", "source" to "aggregate"),
            buildJsonObject { put("name", name); put("description", description) })
    }

    suspend fun edit(session: RequestSession, playlist: Playlist, method: String, suffix: String = "",
        values: JsonObject = buildJsonObject {}): Long? {
        val version = requireNotNull(playlist.revision)
        val result = request<JsonObject>(method, "playlists/${playlist.remoteId}$suffix", session,
            body = JsonObject(values + ("version" to JsonPrimitive(version))))
        return result["resource"]?.jsonObject?.get("version")?.jsonPrimitive?.longOrNull
    }

    suspend fun statuses(session: RequestSession, songs: List<Song>): Map<Long, SongStatus> {
        val groups = songs.filter { it.musicSource in setOf(MusicSource.NETEASE, MusicSource.KUGOU) && !it.video }
            .groupBy { projectTrackRef(it).provider }
        val values = mutableMapOf<Long, SongStatus>()
        for ((platform, tracks) in groups) {
            try {
                for (chunk in tracks.chunked(100)) {
                    val refs = chunk.map(::projectTrackRef)
                    val selected = request<ProjectFavoriteStatus>("POST", "favorites/status", session,
                        mapOf("platform" to platform), buildJsonObject { put("tracks", JsonArray(refs.map { it.json() })) }).selected.toSet()
                    chunk.zip(refs).forEach { (song, ref) -> values[song.id] = SongStatus(liked = ref in selected) }
                }
            } catch (error: ApiException) {
                if (error.status !in setOf(403, 503)) throw error
            }
        }
        return values
    }

    suspend fun changeLike(session: RequestSession, song: Song, desired: Boolean?, source: String?): Boolean {
        val track = projectTrackRef(song)
        val target = source ?: preferences(session)?.providers?.get(track.provider)?.source ?: throw ApiException(ErrorKind.NotFound)
        val query = mapOf("platform" to track.provider, "source" to target)
        val selected = desired ?: (request<ProjectFavoriteStatus>("POST", "favorites/status", session, query,
            buildJsonObject { put("tracks", JsonArray(listOf(track.json()))) }).selected.isEmpty())
        return request<ProjectFavoriteResult>("PUT", "favorites", session, query,
            buildJsonObject { put("track", track.json()); put("selected", selected) }).selected
    }
}
