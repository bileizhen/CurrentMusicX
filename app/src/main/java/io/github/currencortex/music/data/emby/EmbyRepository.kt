package io.github.currencortex.music.data.emby

import io.github.currencortex.music.core.emby.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.core.security.TokenStore
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

data class EmbyAccount(val server: String = "", val username: String = "", val identity: String = "",
    val revision: String = "", val configured: Boolean = false, val download: Boolean = false)
data class EmbyEntry(val id: String, val name: String, val type: String, val cover: String = "", val subtitle: String = "")
data class EmbyPage(val entries: List<EmbyEntry> = emptyList(), val songs: List<Song> = emptyList(),
    val nextOffset: Int = 0, val more: Boolean = false)

class EmbyRepository(private val vault: TokenStore, scope: CoroutineScope) {
    @Volatile private var account: EmbyAccess? = null
    val client = EmbyClient(access = { account })
    val state = MutableStateFlow(EmbyAccount())
    val likes = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val ready = CompletableDeferred<Unit>()
    private val lock = Mutex()
    init { scope.launch(Dispatchers.IO) {
        try { vault.read()?.let { raw ->
            val saved = ApiJson.parseToJsonElement(raw).jsonObject
            require(saved.text("userId").isNotBlank() && saved.text("token").isNotBlank())
            val restored = EmbyAccess(normalizeEmbyServer(saved.text("server")), saved.text("username"),
                saved.text("userId"), saved.text("token"), saved.text("deviceId"), saved.text("revision"))
            require(restored.deviceId.matches(Regex("[A-Za-z0-9-]{1,128}")) && restored.revision.isNotBlank())
            account = restored; publish(restored, saved.flag("download"))
        } } catch (_: Exception) { account = null; publish(null) } finally { ready.complete(Unit) }
    } }
    private fun publish(access: EmbyAccess?, download: Boolean = false) {
        state.value = access?.let { EmbyAccount(it.server, it.username, it.identity, it.revision, true, download) } ?: EmbyAccount()
    }
    suspend fun connect(server: String, username: String, password: String) = withContext(Dispatchers.IO) {
        require(username.isNotBlank() && username.length <= 256 && password.length <= 1024)
        ready.await()
        lock.withLock {
            val candidate = EmbyAccess(normalizeEmbyServer(server), username.trim(), "", "", UUID.randomUUID().toString())
            val result = client.request(listOf("Users", "AuthenticateByName"), method = "POST", body = buildJsonObject {
                put("Username", candidate.username); put("Pw", password)
            }, expected = candidate, provisional = true)
            val user = result.obj("User")
            require(result.text("AccessToken").isNotBlank() && user.text("Id").isNotBlank()) { "Emby 登录响应无效" }
            if (user.obj("Policy")["EnableMediaPlayback"] == JsonPrimitive(false))
                throw ApiException(ErrorKind.Forbidden, userMessage = "此 Emby 账号未开放媒体播放")
            val next = EmbyAccess(candidate.server, candidate.username, user.text("Id"), result.text("AccessToken"), candidate.deviceId)
            val download = user.obj("Policy").flag("EnableContentDownloading")
            vault.write(buildJsonObject {
                put("server", next.server); put("username", next.username); put("userId", next.userId)
                put("token", next.token); put("deviceId", next.deviceId); put("revision", next.revision); put("download", download)
            }.toString())
            account = next; likes.value = emptyMap(); publish(next, download)
        }
    }
    suspend fun disconnect() = withContext(Dispatchers.IO) { ready.await(); lock.withLock {
        val previous = account
        vault.clear(); account = null; likes.value = emptyMap(); publish(null)
        if (previous != null) try {
            client.request(listOf("Sessions", "Logout"), method = "POST", expected = previous, provisional = true)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Local logout also works offline. */ }
    } }
    fun session() = state.value.let { RequestSession(it.server, it.revision.takeIf { _ -> it.configured }) }
    fun accepts(song: Song) = song.musicSource == MusicSource.EMBY && state.value.configured && song.emby?.identity == state.value.identity
    private fun track(song: Song): EmbyTrackRef {
        if (!accepts(song)) throw embyUnauthorized()
        return requireNotNull(song.emby)
    }
    private fun cover(raw: JsonObject, access: EmbyAccess): String {
        val own = raw.obj("ImageTags").text("Primary")
        val id = if (own.isNotBlank()) raw.text("Id") else raw.text("AlbumId")
        val tag = own.ifBlank { raw.text("AlbumPrimaryImageTag") }
        if (id.isBlank() || tag.isBlank()) return ""
        return client.media(listOf("Items", id, "Images", "Primary"), listOf("MaxWidth" to "600", "Tag" to tag), access)
    }
    private fun artists(raw: JsonObject) = (raw["Artists"] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(" / ")
    internal fun song(raw: JsonObject, access: EmbyAccess): Song {
        val remote = raw.text("Id"); require(remote.isNotEmpty())
        val local = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest("${access.identity}\n$remote".toByteArray())).long or Long.MIN_VALUE
        val source = raw.objects("MediaSources").firstOrNull() ?: JsonObject(emptyMap())
        val stream = (raw.objects("MediaStreams") + source.objects("MediaStreams")).firstOrNull { it.text("Type") == "Audio" }
        val format = raw.text("Container").ifBlank { source.text("Container") }
        likes.update { it + (local to raw.obj("UserData").flag("IsFavorite")) }
        return Song(local, raw.text("Name"), artists(raw), raw.text("Album"), cover(raw, access), raw.number("RunTimeTicks") / 10_000,
            musicSource = MusicSource.EMBY, emby = EmbyTrackRef(remote, access.identity, source.text("Id"), format,
                stream?.number("SampleRate")?.toInt() ?: 0, stream?.number("Channels")?.toInt() ?: 0))
    }
    /** Every collection is bounded, including albums, artists, playlists and favorites. */
    suspend fun browse(kind: String = "albums", parent: String = "", query: String = "", offset: Int = 0): EmbyPage {
        require(offset >= 0)
        val access = client.current()
        val type = when (kind) { "albums", "artist" -> "MusicAlbum"; "artists" -> "MusicArtist"; "playlists" -> "Playlist"; else -> "Audio" }
        val params = mutableListOf("Recursive" to "true", "IncludeItemTypes" to type, "StartIndex" to "$offset",
            "Limit" to "$PAGE_SIZE", "EnableTotalRecordCount" to "true", "EnableUserData" to "true",
            "Fields" to "MediaSources,MediaStreams", "SortBy" to if (kind == "album") "ParentIndexNumber,IndexNumber,SortName" else "SortName",
            "SortOrder" to "Ascending")
        if (kind == "artist") params += "ArtistIds" to parent
        else if (parent.isNotBlank() && kind != "playlist") params += "ParentId" to parent
        if (kind == "likes") params += "IsFavorite" to "true"
        if (query.isNotBlank()) params += "SearchTerm" to query.trim()
        val path = if (kind == "playlist") {
            params.removeAll { it.first in setOf("SortBy", "SortOrder") }; params += "UserId" to access.userId
            listOf("Playlists", parent, "Items")
        } else listOf("Users", access.userId, "Items")
        val result = client.request(path, params, expected = access)
        val items = result.objects("Items")
        val next = offset + items.size
        val total = (result["TotalRecordCount"] as? JsonPrimitive)?.intOrNull
        return EmbyPage(items.filter { it.text("Type") != "Audio" }.map {
            EmbyEntry(it.text("Id"), it.text("Name"), it.text("Type"), cover(it, access), artists(it))
        }, items.filter { it.text("Type") == "Audio" }.map { song(it, access) }, next,
            items.isNotEmpty() && (total?.takeIf { it > 0 }?.let { next < it } ?: (items.size == PAGE_SIZE)))
    }
    suspend fun detail(song: Song): Song {
        val ref = track(song); val access = client.current()
        return song(client.request(listOf("Users", access.userId, "Items", ref.id), expected = access), access)
    }
    suspend fun setLiked(song: Song, liked: Boolean, expected: EmbyAccess = client.current()): Boolean {
        client.ensure(expected)
        val ref = track(song); val access = expected
        client.request(listOf("Users", access.userId, "FavoriteItems", ref.id), method = if (liked) "POST" else "DELETE", expected = access)
        likes.update { it + (song.id to liked) }; return liked
    }
    suspend fun toggleLike(song: Song): Boolean {
        val expected = client.current()
        detail(song)
        return setLiked(song, likes.value[song.id] != true, expected)
    }
    fun source(song: Song, download: Boolean = false): AudioSource {
        val expected = client.current()
        val ref = track(song)
        client.ensure(expected)
        if (download && !state.value.download) throw ApiException(ErrorKind.Forbidden, userMessage = "此 Emby 账号不允许下载")
        val path = if (download) listOf("Items", ref.id, "Download") else listOf("Audio", ref.id, "stream")
        val params = if (download) emptyList() else listOf("Static" to "true") +
            ref.mediaSourceId.takeIf(String::isNotBlank)?.let { listOf("MediaSourceId" to it) }.orEmpty()
        return AudioSource(client.media(path, params, expected), "Emby 原音质", ref.sampleRate, ref.channels, ref.format, validForSeconds = 600)
    }
    companion object { const val PAGE_SIZE = 40 }
}
