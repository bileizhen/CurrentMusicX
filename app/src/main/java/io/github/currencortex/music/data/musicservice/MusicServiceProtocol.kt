package io.github.currencortex.music.data.musicservice

import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.nio.ByteBuffer
import java.security.MessageDigest

internal const val CONNECTION_HOST = "currentmusicx.bileizhen.top"
internal const val CONNECTION_PATH = "/connect"
internal const val RETURN_PATH = "/connect/callback"
internal const val REDIRECT_URI = "https://$CONNECTION_HOST$RETURN_PATH"
private val identifier = Regex("[a-z0-9][a-z0-9_.-]{0,79}")
internal val writeOperations = setOf("library.favorite.set", "library.playlist.create", "library.playlist.edit",
    "library.playlist.delete", "library.playlist.tracks", "track.comment.add", "history.record")
internal val knownOperations = writeOperations + setOf(
    "catalog.search", "catalog.track", "catalog.artist", "catalog.album", "catalog.playlist",
    "discover.hot", "discover.suggest", "discover.charts", "discover.recommendations",
    "track.lyrics", "track.comments", "playback.qualities", "playback.resolve", "playback.mv", "playback.download",
    "library.favorites", "library.playlists", "library.playlist", "library.cloud", "history.recent", "roaming.songs",
)

internal fun httpsUrl(value: String, fragment: Boolean = false): HttpUrl {
    require(value.length <= 4096)
    val url = requireNotNull(value.toHttpUrlOrNull()) { "需要有效的 HTTPS 地址" }
    require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && (fragment || url.fragment == null)) {
        "需要有效的 HTTPS 地址"
    }
    return url
}

internal fun serviceMediaUrl(value: String): String = httpsUrl(value).also {
    require(it.queryParameterNames.none { name -> name.startsWith("_cm_") }) { "无效的外接媒体地址" }
}.toString()

private fun HttpUrl.sameOrigin(other: HttpUrl): Boolean = scheme == other.scheme && host == other.host && port == other.port

@Serializable data class MusicServiceCapability(val id: String, val label: String, val scope: String? = null, val write: Boolean = false)

@Serializable data class MusicServiceAuthorization(
    val issuer: String, val clientId: String, val authorizeUrl: String, val tokenUrl: String, val revokeUrl: String,
)

@Serializable data class MusicServiceDescriptor(
    val format: String, val version: Int, val id: String, val name: String, val apiBase: String,
    val platforms: Map<String, List<MusicServiceCapability>>, val authorization: MusicServiceAuthorization? = null,
) {
    internal fun validate(descriptorUrl: String): MusicServiceDescriptor {
        require(format == "currentmusic.music-service" && version == 1) { "此服务使用的协议版本暂不支持" }
        val origin = httpsUrl(descriptorUrl)
        val api = httpsUrl(apiBase)
        require(origin.query == null && api.query == null && api.sameOrigin(origin)) { "音频服务的 API 必须与服务描述同源" }
        require(identifier.matches(id) && name.isNotBlank() && name.length <= 80 && name.none(Char::isISOControl))
        require(platforms.isNotEmpty() && platforms.size <= 16 && platforms.keys.all(identifier::matches))
        authorization?.let {
            require(it.clientId.matches(identifier))
            val endpoints = listOf(httpsUrl(it.issuer), httpsUrl(it.authorizeUrl, fragment = true),
                httpsUrl(it.tokenUrl), httpsUrl(it.revokeUrl))
            require(endpoints.all { endpoint -> endpoint.sameOrigin(origin) && endpoint.query == null })
        }
        val usable = platforms.mapValues { (_, caps) ->
            require(caps.size <= 64 && caps.distinctBy(MusicServiceCapability::id).size == caps.size)
            caps.filter { cap ->
                cap.id in knownOperations && cap.write == (cap.id in writeOperations) &&
                    cap.label.isNotBlank() && cap.label.length <= 80 && cap.label.none(Char::isISOControl) &&
                    (cap.scope == null || authorization != null && cap.scope.matches(identifier)) &&
                    (!cap.write || cap.scope != null)
            }
        }
        require(usable.values.any { it.isNotEmpty() }) { "此服务没有可连接的能力" }
        return copy(platforms = usable.filterValues { it.isNotEmpty() })
    }

    fun capability(platform: String, operation: String): MusicServiceCapability? =
        platforms[platform]?.firstOrNull { it.id == operation }
}

@Serializable data class MusicServiceResource(
    val id: String, val kind: String, val name: String, val artists: String = "", val album: String = "",
    val artwork: String = "", val durationMs: Long = 0, val videoId: String = "",
) {
    fun song(connection: String, platform: String): Song {
        val ref = ServiceTrack(connection, platform, id)
        val trackId = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256")
            .digest("${ref.connection}\n${ref.platform}\n${ref.id}".toByteArray())).long and Long.MAX_VALUE
        val cover = runCatching { serviceMediaUrl(artwork) }.getOrDefault("")
        return Song(trackId, name, artists, album, cover, durationMs.coerceAtLeast(0),
            musicSource = MusicSource.SERVICE, serviceTrack = ref)
    }
}

@Serializable data class ServiceTrack(val connection: String, val platform: String, val id: String)

@Serializable data class MusicServicePage(
    val items: List<MusicServiceResource> = emptyList(), val resource: MusicServiceResource? = null,
    val tracks: List<MusicServiceResource> = emptyList(), val albums: List<MusicServiceResource> = emptyList(),
    val nextOffset: Int? = null,
)
