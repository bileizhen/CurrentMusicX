package io.github.currencortex.music.data.navidrome

import io.github.currencortex.music.core.navidrome.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.feature.lyrics.model.*
import io.github.currencortex.music.feature.lyrics.parser.LyricsParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.security.MessageDigest

data class NavidromeAccount(val server: String = "", val username: String = "", val identity: String = "",
    val revision: String = "", val configured: Boolean = false, val home: Boolean = false,
    val download: Boolean = false, val version: String = "")
data class NavidromeEntry(val id: String, val name: String, val subtitle: String = "", val cover: String = "",
    val count: Int = 0, val owned: Boolean = false)
data class NavidromeSearch(val songs: List<Song>, val albums: List<NavidromeEntry>, val artists: List<NavidromeEntry>, val more: Boolean)

class NavidromeRepository(private val vault: TokenStore, scope: CoroutineScope) {
    @Volatile private var account: NavidromeAccess? = null
    val client = NavidromeClient { account }
    val state = MutableStateFlow(NavidromeAccount())
    val likes = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val revision = MutableStateFlow(0L)
    val ready = CompletableDeferred<Unit>()
    private val lock = Mutex()
    init { scope.launch(Dispatchers.IO) {
        try { vault.read()?.let { raw ->
            val saved = ApiJson.parseToJsonElement(raw).jsonObject
            val access = NavidromeAccess(normalizeNavidromeServer(saved.string("server")), saved.string("username"),
                saved.string("password"), saved.string("revision"), saved.bool("home"))
            account = access; publish(access, saved.bool("download"), saved.string("version"))
        } } catch (_: Exception) { account = null; publish(null) } finally { ready.complete(Unit) }
    } }
    private fun publish(access: NavidromeAccess?, download: Boolean = false, version: String = "") {
        state.value = if (access == null) NavidromeAccount() else NavidromeAccount(access.server, access.username,
            access.identity, access.revision, true, access.home, download, version)
    }
    private fun write(access: NavidromeAccess, download: Boolean, version: String) {
        vault.write(buildJsonObject {
            put("server", access.server); put("username", access.username); put("password", access.password)
            put("revision", access.revision); put("home", access.home); put("download", download); put("version", version)
        }.toString())
    }
    suspend fun connect(server: String, username: String, password: String) = withContext(Dispatchers.IO) {
        require(username.isNotBlank() && password.isNotEmpty() && username.length <= 256 && password.length <= 1024)
        ready.await()
        lock.withLock {
            val candidate = NavidromeAccess(normalizeNavidromeServer(server), username.trim(), password)
            val ping = client.request("ping", expected = candidate, provisional = true)
            val user = client.request("getUser", listOf("username" to candidate.username), candidate, provisional = true)["user"]?.jsonObject
            val download = (user?.get("downloadRole") as? JsonPrimitive)?.booleanOrNull ?: false
            write(candidate, download, ping.string("serverVersion"))
            account = candidate; likes.value = emptyMap(); publish(candidate, download, ping.string("serverVersion")); changed()
        }
    }
    suspend fun home(enabled: Boolean) = withContext(Dispatchers.IO) { ready.await(); lock.withLock {
        val old = client.current()
        val next = NavidromeAccess(old.server, old.username, old.password, old.revision, enabled)
        write(next, state.value.download, state.value.version); account = next; publish(next, state.value.download, state.value.version)
    } }
    suspend fun disconnect() = withContext(Dispatchers.IO) { ready.await(); lock.withLock {
        vault.clear(); account = null; likes.value = emptyMap(); publish(null); changed()
    } }
    fun session(): RequestSession = state.value.let { RequestSession(it.server, it.revision.takeIf { _ -> it.configured }) }
    fun accepts(song: Song) = song.navidrome?.identity == state.value.identity && state.value.configured
    private fun track(song: Song): NavidromeTrackRef {
        if (!accepts(song)) throw ApiException(ErrorKind.Unauthorized)
        return song.navidrome ?: throw ApiException(ErrorKind.NotFound)
    }
    private fun changed() { revision.update { it + 1 } }
    private fun id(remote: String, access: NavidromeAccess): Long {
        val bytes = MessageDigest.getInstance("SHA-256").digest("${access.identity}\n$remote".toByteArray())
        return ByteBuffer.wrap(bytes).long or Long.MIN_VALUE
    }
    private fun cover(raw: JsonObject, access: NavidromeAccess): String = raw.string("coverArt").takeIf { it.isNotBlank() }
        ?.let { client.media("getCoverArt", listOf("id" to it, "size" to "600"), access) }.orEmpty()
    private fun entry(raw: JsonObject, access: NavidromeAccess) = NavidromeEntry(raw.string("id"), raw.string("name"),
        raw.string("artist").ifBlank { raw.string("owner") }, cover(raw, access), raw.number("songCount").toInt(), raw.string("owner") == access.username)
    private fun song(raw: JsonObject, access: NavidromeAccess): Song {
        val remote = raw.string("id"); require(remote.isNotEmpty())
        val local = id(remote, access)
        likes.update { it + (local to raw.string("starred").isNotBlank()) }
        return Song(local, raw.string("title").ifBlank { raw.string("name") }, raw.string("artist"), raw.string("album"),
            cover(raw, access), (((raw["duration"] as? JsonPrimitive)?.doubleOrNull ?: 0.0) * 1000).toLong(), musicSource = MusicSource.NAVIDROME,
            navidrome = NavidromeTrackRef(remote, access.identity, raw.string("suffix"), raw.number("samplingRate").toInt(), raw.number("channelCount").toInt()))
    }
    suspend fun albums(offset: Int = 0, type: String = "newest"): List<NavidromeEntry> {
        val access = client.current()
        val result = client.request("getAlbumList2", listOf("type" to type, "size" to "40", "offset" to "$offset"), access)
        return result["albumList2"]?.jsonObject?.objects("album").orEmpty().map { entry(it, access) }
    }
    suspend fun artists(): List<NavidromeEntry> {
        val access = client.current()
        return client.request("getArtists", expected = access)["artists"]?.jsonObject?.objects("index").orEmpty()
            .flatMap { it.objects("artist") }.map { entry(it, access) }
    }
    suspend fun artistAlbums(id: String): List<NavidromeEntry> {
        val access = client.current()
        return client.request("getArtist", listOf("id" to id), access)["artist"]?.jsonObject?.objects("album").orEmpty().map { entry(it, access) }
    }
    suspend fun album(id: String): List<Song> {
        val access = client.current()
        return client.request("getAlbum", listOf("id" to id), access)["album"]?.jsonObject?.objects("song").orEmpty().map { song(it, access) }
    }
    suspend fun playlists(): List<NavidromeEntry> {
        val access = client.current()
        return client.request("getPlaylists", expected = access)["playlists"]?.jsonObject?.objects("playlist").orEmpty().map { entry(it, access) }
    }
    suspend fun playlist(id: String): List<Song> {
        val access = client.current()
        return client.request("getPlaylist", listOf("id" to id), access)["playlist"]?.jsonObject?.objects("entry").orEmpty().map { song(it, access) }
    }
    suspend fun starred(): List<Song> {
        val access = client.current()
        return client.request("getStarred2", expected = access)["starred2"]?.jsonObject?.objects("song").orEmpty().map { song(it, access) }
    }
    suspend fun search(query: String, offset: Int = 0): NavidromeSearch {
        val access = client.current()
        val result = client.request("search3", listOf("query" to query, "songCount" to "40", "songOffset" to "$offset",
            "albumCount" to if (offset == 0) "20" else "0", "artistCount" to if (offset == 0) "20" else "0"), access)["searchResult3"]?.jsonObject ?: JsonObject(emptyMap())
        val songs = result.objects("song").map { song(it, access) }
        return NavidromeSearch(songs, result.objects("album").map { entry(it, access) }, result.objects("artist").map { entry(it, access) }, songs.size == 40)
    }
    suspend fun detail(song: Song): Song {
        val ref = track(song); val access = client.current()
        return client.request("getSong", listOf("id" to ref.id), access)["song"]?.jsonObject?.let { song(it, access) } ?: song
    }
    suspend fun toggleLike(song: Song): Boolean {
        val ref = track(song); val access = client.current()
        val current = client.request("getSong", listOf("id" to ref.id), access)["song"]?.jsonObject ?: throw ApiException(ErrorKind.NotFound)
        val next = current.string("starred").isBlank()
        client.request(if (next) "star" else "unstar", listOf("id" to ref.id), access)
        likes.update { it + (song.id to next) }; changed(); return next
    }
    suspend fun add(playlist: String, song: Song) { val ref = track(song)
        client.request("updatePlaylist", listOf("playlistId" to playlist, "songIdToAdd" to ref.id)); changed()
    }
    suspend fun remove(playlist: String, index: Int) {
        client.request("updatePlaylist", listOf("playlistId" to playlist, "songIndexToRemove" to "$index")); changed()
    }
    suspend fun create(name: String) { require(name.trim().isNotEmpty())
        client.request("createPlaylist", listOf("name" to name.trim())); changed()
    }
    suspend fun rename(playlist: String, name: String) { require(name.trim().isNotEmpty())
        client.request("updatePlaylist", listOf("playlistId" to playlist, "name" to name.trim())); changed()
    }
    suspend fun delete(playlist: String) { client.request("deletePlaylist", listOf("id" to playlist)); changed() }
    fun source(song: Song, quality: AudioQuality, download: Boolean = false): AudioSource {
        val ref = track(song)
        if (download && !state.value.download) throw ApiException(ErrorKind.Forbidden)
        // Preserve original audio; Navidrome/server transcoding policy controls codec compatibility.
        val url = client.media(if (download) "download" else "stream", listOf("id" to ref.id, "format" to "raw"))
        return AudioSource(url, "Navidrome 原音质", ref.sampleRate, ref.channels, ref.format, validForSeconds = 600)
    }
    suspend fun scrobble(song: Song, submission: Boolean) {
        val ref = track(song)
        client.request("scrobble", listOf("id" to ref.id, "submission" to "$submission", "time" to "${System.currentTimeMillis()}"))
    }
    suspend fun lyrics(song: Song): LyricsDocument {
        val ref = track(song)
        val structured = try { client.request("getLyricsBySongId", listOf("id" to ref.id))["lyricsList"]?.jsonObject?.objects("structuredLyrics").orEmpty() }
            catch (e: CancellationException) { throw e } catch (e: ApiException) { if (e.kind == ErrorKind.Unauthorized) throw e; emptyList() }
        val main = structured.firstOrNull { it.string("kind") !in setOf("translation", "pronunciation") && it.bool("synced") }
        if (main != null) {
            val offset = main.number("offset")
            val lines = main.objects("line").filter { it.string("value").isNotBlank() }.sortedBy { it.number("start") }
            val translation = structured.firstOrNull { it.string("kind") == "translation" }?.objects("line").orEmpty().associate { it.number("start") to it.string("value") }
            return LyricsDocument(lines.mapIndexed { index, line ->
                io.github.currencortex.music.feature.lyrics.model.LyricLine((line.number("start") + offset).coerceAtLeast(0),
                    (lines.getOrNull(index + 1)?.number("start")?.plus(offset) ?: song.durationMs).coerceAtLeast(line.number("start") + offset),
                    line.string("value"), translation = translation[line.number("start")].orEmpty())
            }, LyricsMetadata(source = "Navidrome"))
        }
        val text = client.request("getLyrics", listOf("artist" to song.artists, "title" to song.name))["lyrics"]?.jsonObject?.string("value").orEmpty()
        if (text.isBlank()) return LyricsDocument(metadata = LyricsMetadata(source = "Navidrome"))
        return LyricsParser.parse(buildJsonObject { put("lrc", buildJsonObject { put("lyric", text) }) }).let {
            it.copy(metadata = it.metadata.copy(source = "Navidrome"))
        }
    }
}
