package io.github.currencortex.music.data.library

import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Server data is authoritative. Like toggles are serialized and rollback on failure. */
class LibraryRepository(private val api: ApiClient, private val accountId: () -> Long,
    private val session: () -> RequestSession) {
    val statuses = MutableStateFlow<Map<Long, SongStatus>>(emptyMap())
    val revision = MutableStateFlow(0L)
    private val mutationMutex = Mutex()
    private val reads = SessionReadCache(session, { revision.value })
    fun clearReads() = reads.clear()
    fun invalidate() { reads.clear(); revision.update { it + 1 } }
    fun clearSession() { reads.clear(); statuses.value = emptyMap(); revision.update { it + 1 } }
    private suspend inline fun <reified T> read(path: String, expected: RequestSession, query: Map<String, String> = emptyMap()): T =
        api.decode(api.request("GET", path, query, authenticated = true, expectedSession = expected))
    private suspend fun <T : Any> backgroundRead(key: String, fresh: Boolean = false, load: suspend (RequestSession) -> T): T =
        withContext(Dispatchers.Default) { reads.read(key, fresh, load) }
    suspend fun daily(): Daily = backgroundRead("daily") { expected ->
        val d = read<DailyDto>("daily", expected)
        Daily(d.daily.map(SongDto::toDomain), d.forYou.map(SongDto::toDomain), d.artists)
    }
    suspend fun recent(): List<Song> = backgroundRead("recent") { read<SongListDto>("plays/recent", it, mapOf("limit" to "50")).songs.map(SongDto::toDomain) }
    suspend fun likedSongs(): List<Song> = backgroundRead("likes") { read<SongListDto>("likes/mine", it).songs.map(SongDto::toDomain) }
    suspend fun playlists(): List<Playlist> = backgroundRead("playlists") { read<PlaylistsDto>("playlists", it).playlists.map(PlaylistDto::domain) }
    suspend fun playlist(id: Long, fresh: Boolean = false): Playlist = backgroundRead("playlist/$id", fresh) { read<PlaylistDto>("playlists/$id", it).domain() }
    suspend fun refreshStatus(ids: List<Long>) {
        if (accountId() == 0L) return
        val expected = session()
        ids.distinct().chunked(100).forEach { chunk ->
            if (expected != session()) return
            val d = api.get<SongStatusDto>("songs/status", mapOf("ids" to chunk.joinToString(",")), true)
            if (expected == session()) statuses.update { old -> old + chunk.associateWith { id ->
                old[id]?.takeIf { it.pending } ?: SongStatus(id in d.liked, id in d.faved)
            } }
        }
    }
    suspend fun toggleLike(song: Song): AppResult<Unit> = mutationMutex.withLock {
        if (accountId() == 0L) return@withLock AppResult.Failure(ErrorKind.Unauthorized)
        val expected = session()
        // Read before toggling: a default false must not invert an existing server true.
        val loaded = appResult { refreshStatus(listOf(song.id)) }
        if (loaded is AppResult.Failure) return@withLock loaded
        if (expected != session()) return@withLock AppResult.Failure(ErrorKind.Unauthorized)
        val before = statuses.value[song.id] ?: SongStatus()
        statuses.update { it + (song.id to before.copy(liked = !before.liked, pending = true)) }
        val result = try { appResult {
            api.request("POST", "likes/${song.id}", body = metadata(song), authenticated = true, expectedSession = expected)
        } } catch (cancelled: kotlinx.coroutines.CancellationException) {
            if (expected == session()) statuses.update { it + (song.id to before) }
            throw cancelled
        }
        if (expected == session()) {
            statuses.update { it + (song.id to if (result is AppResult.Success) (it[song.id] ?: before).copy(
                liked = (result.value as? JsonObject)?.get("on")?.jsonPrimitive?.booleanOrNull ?: !before.liked, pending = false) else before) }
            if (result is AppResult.Success) revision.update { it + 1 }
        }
        when (result) { is AppResult.Success -> AppResult.Success(Unit); is AppResult.Failure -> result }
    }
    suspend fun create(name: String, description: String) {
        require(name.trim().isNotEmpty())
        val expected = session()
        api.request("POST", "playlists", body = buildJsonObject { put("name", name.trim()); put("description", description) }, authenticated = true, expectedSession = expected)
        revision.update { it + 1 }
    }
    private suspend fun editable(id: Long, expected: RequestSession) {
        if (expected != session() || !playlist(id, fresh = true).editable(accountId()) || expected != session()) throw ApiException(ErrorKind.Forbidden)
    }
    suspend fun rename(id: Long, name: String) {
        require(name.trim().isNotEmpty()); val expected = session(); editable(id, expected)
        api.request("PUT", "playlists/$id", body = buildJsonObject { put("name", name.trim()) }, authenticated = true, expectedSession = expected)
        revision.update { it + 1 }
    }
    suspend fun delete(id: Long) {
        val expected = session(); editable(id, expected); api.request("DELETE", "playlists/$id", authenticated = true, expectedSession = expected); revision.update { it + 1 }
    }
    suspend fun add(id: Long, songs: List<Song>) {
        val expected = session(); editable(id, expected)
        api.request("POST", "playlists/$id/tracks", body = buildJsonObject { put("songs", JsonArray(songs.map(::metadata))) }, authenticated = true, expectedSession = expected)
        revision.update { it + 1 }
    }
    suspend fun remove(id: Long, song: Song) {
        val expected = session(); editable(id, expected)
        api.request("DELETE", "playlists/$id/tracks", body = buildJsonObject { put("songs", JsonArray(listOf(metadata(song)))) }, authenticated = true, expectedSession = expected)
        revision.update { it + 1 }
    }
    suspend fun recordPlay(song: Song, expected: RequestSession = session()) {
        if (accountId() != 0L && !song.video) {
            api.request("POST", "plays/${song.id}", body = metadata(song), authenticated = true, expectedSession = expected)
            revision.update { it + 1 }
        }
    }
    suspend fun listen(song: Song, milliseconds: Long, expected: RequestSession = session()) {
        if (accountId() != 0L && !song.video && milliseconds > 0) api.request("POST", "plays/${song.id}",
            body = buildJsonObject { put("ms", milliseconds) }, authenticated = true, expectedSession = expected)
    }
    suspend fun artist(id: Long, offset: Int = 0): Artist {
        val d = api.get<ArtistDto>("ncm/artist", mapOf("id" to "$id", "offset" to "$offset", "limit" to "100"))
        return Artist(id, d.name, d.pic.replace("http:", "https:"), d.alias, d.songs.map(SongDto::toDomain), d.more,
            d.albums.map(AlbumDto::domain), d.albumTotal, d.total)
    }
    suspend fun artistBiography(id: Long, fresh: Boolean = false): ArtistBiography = backgroundRead("artist-bio/$id", fresh) {
        api.get<ArtistBiography>("ncm/artist/desc", mapOf("id" to "$id")).also {
            if (it.code != 200) throw ApiException(if (it.code == 404) ErrorKind.NotFound else ErrorKind.Server, it.code)
        }
    }
    suspend fun artistAlbums(id: Long, offset: Int): Pair<List<Album>, Boolean> {
        val d = api.get<ArtistAlbumsDto>("ncm/artist/albums", mapOf("id" to "$id", "offset" to "$offset", "limit" to "30"))
        return d.albums.map(AlbumDto::domain) to d.more
    }
    suspend fun album(id: Long) = api.get<AlbumDto>("ncm/album", mapOf("id" to "$id")).domain().copy(id = id)
    suspend fun catalog(query: String, albums: Boolean, offset: Int = 0): CatalogPage {
        val d = api.get<CatalogSearchDto>("ncm/search", mapOf("keywords" to query, "type" to if (albums) "album" else "artist", "limit" to "30", "offset" to "$offset"))
        return if (albums) CatalogPage(d.albums.map { CatalogEntry(it.id, it.name, it.pic.replace("http:", "https:"), it.artist) }, d.hasMore.album, d.totals.album)
        else CatalogPage(d.artists.map { CatalogEntry(it.id, it.name, it.pic.replace("http:", "https:"), it.alias) }, d.hasMore.artist, d.totals.artist)
    }
    suspend fun mv(id: Long) = api.get<MvDetailDto>("ncm/mv/detail", mapOf("mvid" to "$id")).data
    suspend fun mvSource(id: Long): String {
        val url = api.get<MvUrlDto>("ncm/mv/url", mapOf("id" to "$id", "r" to "1080")).data.url
        return url?.toHttpUrlOrNull()?.toString() ?: throw ApiException(ErrorKind.NotFound)
    }
    companion object {
        fun metadata(song: Song) = buildJsonObject {
            put("ncm_id", song.id); put("name", song.name); put("artists", song.artists)
            put("album", song.album); put("pic", song.cover); put("duration", song.durationMs); put("mv", song.mv)
            put("artist_ids", JsonArray(song.artistIds.map(::JsonPrimitive)))
        }
    }
}
