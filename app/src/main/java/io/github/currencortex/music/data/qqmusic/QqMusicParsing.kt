package io.github.currencortex.music.data.qqmusic

import io.github.currencortex.music.data.song.*
import io.github.currencortex.music.data.library.Playlist
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.security.MessageDigest

/** MID remains the remote identity; the negative ID is only a local queue/database key. */
internal object QqMusicParsing {
    fun localId(mid: String): Long = (ByteBuffer.wrap(MessageDigest.getInstance("SHA-256")
        .digest(("qqmusic:" + mid).toByteArray(Charsets.UTF_8))).long and Long.MAX_VALUE).let { -it.coerceAtLeast(1) }

    fun loginCapabilities(value: JsonObject): QqLoginCapabilities {
        val channels = value.array("channels").mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull
                is JsonObject -> item.text("id", "channel", "name")
                else -> null
            }?.takeIf(String::isNotBlank)
        }.distinct().map(::QqLoginChannel)
        val configured = (value["configured"] as? JsonPrimitive)?.booleanOrNull ?: channels.isNotEmpty()
        return QqLoginCapabilities(
            configured = configured,
            sessionMode = value.text("sessionMode", "session_mode"),
            channels = channels,
            features = (value["features"] as? JsonObject)?.booleanFeatures().orEmpty(),
        )
    }

    fun song(value: JsonElement): Song? {
        val outer = value as? JsonObject ?: return null
        val o = (outer["track"] as? JsonObject) ?: (outer["song"] as? JsonObject) ?: (outer["songInfo"] as? JsonObject) ?: outer
        val mid = o.text("qq_mid", "mid", "songmid").ifBlank { o.text("id").takeIf { it.any(Char::isLetter) }.orEmpty() }
        if (!mid.matches(Regex("[a-zA-Z0-9]{1,64}"))) return null
        val album = o["album"] as? JsonObject
        val singers = o.array("singer", "singers")
        val singerObjects = singers.mapNotNull(::parseLegacySinger)
        val singerMids = singers.mapNotNull { (it as? JsonObject)?.text("mid", "singermid")?.takeIf(String::isNotBlank) }
            .ifEmpty { singerObjects.mapNotNull { it["mid"] } }
            .ifEmpty { o.array("artist_ids").mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }
        val albumMid = album?.text("mid", "albummid", "albumMid").orEmpty()
            .ifBlank { o.text("qq_album_mid", "album_mid", "albummid", "albumMid") }.ifBlank {
            Regex("T002R\\d+x\\d+M000([a-zA-Z0-9]+)\\.").find(o.text("pic", "cover"))?.groupValues?.get(1).orEmpty()
        }
        val mediaMid = (o["file"] as? JsonObject)?.text("media_mid", "mediaMid", "strMediaMid").orEmpty()
            .ifBlank { o.text("qq_media_mid", "media_mid", "mediaMid", "strMediaMid") }
        val cover = o.text("pic", "cover", "picurl").ifBlank { album?.text("pic", "cover").orEmpty() }
            .ifBlank { albumMid.takeIf(String::isNotBlank)?.let { "https://y.gtimg.cn/music/photo_new/T002R500x500M000$it.jpg" }.orEmpty() }.https()
        val numericId = o.number("qq_id", "songid").takeIf { it > 0 } ?: o.number("id")
        return Song(localId(mid), o.text("name", "title", "songname").decodeHtmlEntities(),
            artists = o.text("artists").ifBlank {
                singerObjects.mapNotNull { it["name"]?.decodeHtmlEntities()?.takeIf(String::isNotBlank) }
                    .joinToString(" / ")
            }.decodeHtmlEntities(),
            album = album?.text("name", "title").orEmpty().ifBlank { o.text("album", "albumname") }.decodeHtmlEntities(),
            cover = cover,
            durationMs = if (o["duration"] != null) o.number("duration") else o.number("interval") * 1000,
            musicSource = MusicSource.QQ_MUSIC, externalIds = SongExternalIds(qqMusicId = mid),
            qqMusic = QqMusicTrackRef(mid, numericId, albumMid, singerMids, mediaMid))
    }

    fun songs(o: JsonObject): List<Song> = o.array("songs", "songlist", "songList", "tracks", "list").mapNotNull(::song).distinctBy { it.id }

    private fun parseLegacySinger(value: JsonElement): Map<String, String>? = when (value) {
        is JsonObject -> mapOf(
            "name" to value.text("name", "title"),
            "mid" to value.text("mid", "singermid"),
        ).filterValues(String::isNotBlank).takeIf(Map<String, String>::isNotEmpty)
        is JsonPrimitive -> {
            val raw = value.contentOrNull.orEmpty()
            if (!raw.startsWith("@{") && !raw.startsWith("{")) null else {
                fun field(key: String): String =
                    Regex("""(?:^|[;{,\s])${Regex.escape(key)}=([^;}]+)""")
                        .find(raw)?.groupValues?.get(1)?.trim().orEmpty()
                mapOf("name" to field("name"), "mid" to field("mid"))
                    .filterValues(String::isNotBlank).takeIf(Map<String, String>::isNotEmpty)
            }
        }
        else -> null
    }

    fun playlist(value: JsonElement, owned: Boolean = false): Playlist? {
        val o = value as? JsonObject ?: return null
        val id = o.identifier("id", "tid", "dissid", "disstid")
        if (id.isBlank()) return null
        return Playlist(localId("playlist:$id"), o.text("name", "title", "dirName", "dissname").decodeHtmlEntities(),
            o.text("description", "desc", "intro").decodeHtmlEntities(),
            o.text("cover", "picurl", "picUrl", "bigpicUrl", "pic", "logo", "albumPicUrl", "diss_cover").https(), "qqmusic", 0,
            o.number("trackCount", "songnum", "songNum", "songCount", "song_cnt", "total").toInt(), songs(o),
            nativeOwned = owned || o.flag("owned", "isOwn"), remoteId = id,
            playCount = o.number("listennum", "play_cnt", "listenNum").takeIf { it > 0 } ?: 0)
    }

    fun entry(value: JsonElement, type: String): QqCatalogEntry? {
        val o = value as? JsonObject ?: return null
        val id = if (type == "mv") o.identifier("vid") else o.identifier("id", "mid", "tid", "albumMID", "albumMid", "singerMID", "singerMid", "dissid")
        if (id.isBlank()) return null
        return QqCatalogEntry(id, o.text("name", "title", "albumName", "singerName", "dissname").decodeHtmlEntities(),
            o.text("cover", "pic", "picurl", "picUrl", "logo").https(),
            o.text("subtitle", "artist", "creator", "intro", "desc", "alias").decodeHtmlEntities(), type)
    }

    fun categories(value: JsonObject): List<QqMusicCategory> = value.array("groups").flatMap { groupValue ->
        val group = groupValue as? JsonObject ?: return@flatMap emptyList()
        val groupName = group.text("name", "title").decodeHtmlEntities()
        group.array("categories").mapNotNull { category(it, groupName) }
    }

    /** Listening-rank rows wrap each normalized track and its play count in an item object. */
    fun listeningRankPage(value: JsonObject): QqListeningRankPage = QqListeningRankPage(
        entries = value.array("items").mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            val track = row["track"]?.let(::song) ?: return@mapNotNull null
            QqListeningRankEntry(track, row.number("listenCount", "playCount", "count"))
        }.distinctBy { it.song.id },
        from = value.text("from"),
        token = value.text("token"),
    )

    private fun category(value: JsonElement, group: String): QqMusicCategory? {
        val o = value as? JsonObject ?: return null
        val id = o.identifier("id", "categoryId")
        val name = o.text("name", "title").decodeHtmlEntities()
        if (id.isBlank() || name.isBlank()) return null
        val sorts = o.array("sorts").mapNotNull { sort ->
            val item = sort as? JsonObject ?: return@mapNotNull null
            val sortId = item.identifier("id", "sortId").toIntOrNull()?.takeIf { it > 0 }
                ?: return@mapNotNull null
            val sortName = item.text("name", "title").decodeHtmlEntities().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            QqMusicCategorySort(sortId, sortName)
        }.distinctBy { it.id }
        return QqMusicCategory(id, name, group, sorts)
    }
}

private fun JsonObject.booleanFeatures(prefix: String = ""): Map<String, Boolean> = buildMap {
    for ((key, value) in this@booleanFeatures) {
        val path = prefix.takeIf(String::isNotBlank)?.let { "$it.$key" } ?: key
        when (value) {
            is JsonObject -> putAll(value.booleanFeatures(path))
            is JsonPrimitive -> value.booleanOrNull?.let { put(path, it) }
            else -> Unit
        }
    }
}

internal fun JsonObject.text(vararg keys: String): String = keys.firstNotNullOfOrNull {
    (get(it) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}.orEmpty()
internal fun JsonObject.identifier(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
    (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { value ->
        value.contentOrNull?.takeIf(String::isNotBlank)
            ?: value.content.takeIf { !value.isString && it.isNotBlank() }
    }
}.orEmpty()
internal fun JsonObject.number(vararg keys: String): Long = keys.firstNotNullOfOrNull { (get(it) as? JsonPrimitive)?.longOrNull } ?: 0
internal fun JsonObject.flag(vararg keys: String): Boolean = keys.firstNotNullOfOrNull {
    (get(it) as? JsonPrimitive)?.let { p -> p.booleanOrNull ?: p.intOrNull?.let { n -> n != 0 } }
} ?: false
internal fun JsonObject.array(vararg keys: String): JsonArray = keys.firstNotNullOfOrNull { get(it) as? JsonArray } ?: JsonArray(emptyList())
internal fun String.https() = if (startsWith("http://")) replaceFirst("http://", "https://") else this
private val htmlEntity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]+);")
internal fun String.decodeHtmlEntities(): String = htmlEntity.replace(this) { match ->
    val entity = match.groupValues[1]
    when {
        entity.startsWith("#x", ignoreCase = true) -> entity.substring(2).toIntOrNull(16)?.codePointString()
            ?: match.value
        entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.codePointString()
            ?: match.value
        else -> when (entity.lowercase()) {
            "amp" -> "&"
            "apos" -> "'"
            "gt" -> ">"
            "lt" -> "<"
            "nbsp" -> "\u00a0"
            "quot" -> "\""
            else -> match.value
        }
    }
}
private fun Int.codePointString(): String? = takeIf { Character.isValidCodePoint(it) }?.let {
    StringBuilder().appendCodePoint(it).toString()
}
