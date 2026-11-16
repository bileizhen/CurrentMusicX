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
        val outer = value as? JsonObject ?: return null
        val o = when (type) {
            "artist" -> (outer["artist"] as? JsonObject) ?: (outer["singer_info"] as? JsonObject) ?: outer
            "album" -> albumObject(outer)
            else -> outer
        }
        val id = when (type) {
            "mv" -> o.identifier("vid")
            "artist" -> o.identifier("mid", "singerMid", "singerMID", "singermid", "id")
            "album" -> o.identifier("albumMid", "albumMID", "mid", "albumID", "id")
            else -> o.identifier("id", "mid", "tid", "albumMID", "albumMid", "singerMID", "singerMid", "dissid")
        }
        if (id.isBlank()) return null
        val cover = o.text("cover", "pic", "albumPic", "albumCover", "picurl", "picUrl", "logo", "avatar")
            .ifBlank {
                when (type) {
                    "album" -> "https://y.gtimg.cn/music/photo_new/T002R500x500M000$id.jpg"
                    "artist" -> "https://y.gtimg.cn/music/photo_new/T001R500x500M000$id.jpg"
                    else -> ""
                }
            }
        val subtitle = when (type) {
            "album" -> listOf(
                singers(o).ifEmpty {
                    o.text("singerName").split('/').map(String::trim).filter(String::isNotBlank)
                }.joinToString(" / "),
                o.text("albumType", "typeName"),
                o.text("publishDate", "time_public", "releaseDate"),
            ).filter(String::isNotBlank).joinToString(" · ")
            else -> o.text("subtitle", "artist", "creator", "intro", "desc", "alias")
        }
        return QqCatalogEntry(id, o.text("name", "title", "albumName", "singerName", "dissname").decodeHtmlEntities(),
            cover.https(), subtitle.decodeHtmlEntities(), type)
    }

    fun artistDetail(value: JsonObject): QqArtistDetail {
        val artist = (value["artist"] as? JsonObject)
            ?: (value["singer_info"] as? JsonObject)
            ?: (value["singer"] as? JsonObject)
            ?: JsonObject(emptyMap())
        val mid = artist.identifier("mid", "singerMid", "singermid").ifBlank {
            artist.identifier("id")
        }
        val songs = songs(value)
        val albums = value.array("albums").mapNotNull { entry(it, "album") }
        val totalSong = value.number("totalSong", "total_song").takeIf { it > 0 }?.toInt()
            ?: artist.number("songTotal", "song_total", "songNum", "songnum").toInt()
        val totalAlbum = value.number("totalAlbum", "total_album").takeIf { it > 0 }?.toInt()
            ?: artist.number("albumTotal", "album_total", "albumNum", "albumnum").toInt()
        val totalMv = value.number("totalMv", "total_mv").takeIf { it > 0 }?.toInt()
            ?: artist.number("mvTotal", "mv_total", "mvNum", "mvnum").toInt()
        return QqArtistDetail(
            mid = mid,
            name = artist.text("name", "title", "singerName").decodeHtmlEntities(),
            cover = artist.text("cover", "pic", "avatar", "avatarUrl").ifBlank {
                mid.takeIf(String::isNotBlank)?.let {
                    "https://y.gtimg.cn/music/photo_new/T001R500x500M000$it.jpg"
                }.orEmpty()
            }.https(),
            alias = artist.text("alias", "other_name", "otherName").decodeHtmlEntities(),
            brief = value.text("brief", "singer_brief").ifBlank {
                artist.text("brief", "briefDesc", "desc")
            }.decodeHtmlEntities(),
            fans = value.number("fans", "fansNum", "fans_num").takeIf { it > 0 }
                ?: artist.number("fans", "fansNum", "fans_num"),
            totalSong = totalSong,
            totalAlbum = totalAlbum,
            totalMv = totalMv,
            songs = songs,
            albums = albums,
            hasMoreSongs = value.flag("moreSongs", "more") ||
                (totalSong > 0 && songs.size < totalSong),
            hasMoreAlbums = value.flag("albumsFailed", "moreAlbums") ||
                (totalAlbum > 0 && albums.size < totalAlbum),
            albumsFailed = value.flag("albumsFailed"),
        )
    }

    fun albumDetail(value: JsonObject): QqAlbumDetail {
        val album = albumObject(value)
        val basic = (album["basicInfo"] as? JsonObject) ?: album
        val singerRows = singerObjects(album, value)
        val mid = basic.identifier("albumMid", "albumMID", "mid")
            .ifBlank { value.identifier("albumMid", "albumMID", "mid") }
        val songs = songs(value)
        val singerNames = singerRows.mapNotNull { it["name"] }.filter(String::isNotBlank)
            .ifEmpty { songs.firstOrNull()?.artists?.split(" / ").orEmpty().map(String::trim) }
        val singerMids = singerRows.mapNotNull { it["mid"] }.filter(String::isNotBlank)
            .ifEmpty { songs.firstOrNull()?.qqMusic?.singerMids.orEmpty() }
        val company = when (val raw = album["company"] ?: value["company"]) {
            is JsonObject -> raw.text("name", "title", "companyName")
            is JsonPrimitive -> raw.contentOrNull.orEmpty()
            else -> ""
        }
        val totalSongs = value.number("totalSongs", "totalNum", "total_song_num", "total_num", "total", "song_num")
            .takeIf { it > 0 }?.toInt() ?: songs.size
        return QqAlbumDetail(
            mid = mid,
            title = basic.text("albumName", "name", "title").decodeHtmlEntities(),
            singerNames = singerNames.joinToString(" / ").decodeHtmlEntities(),
            singerMids = singerMids.distinct(),
            cover = basic.text("cover", "pic", "picurl", "picUrl", "albumPic", "albumCover")
                .ifBlank {
                    mid.takeIf(String::isNotBlank)?.let {
                        "https://y.gtimg.cn/music/photo_new/T002R500x500M000$it.jpg"
                    }.orEmpty()
                }.https(),
            description = basic.text("desc", "description", "intro").decodeHtmlEntities(),
            publishDate = basic.text("publishDate", "time_public", "releaseDate"),
            company = company.decodeHtmlEntities(),
            language = basic.text("language", "lan"),
            albumType = basic.text("albumType", "typeName"),
            songs = songs,
            totalSongs = totalSongs,
            hasMoreSongs = value.flag("hasMoreSongs", "moreSongs", "more") || songs.size < totalSongs,
        )
    }

    fun artistSongPage(value: JsonObject): QqSongPage {
        val songs = songs(value)
        val total = value.number("total", "totalSong", "total_song")
        return QqSongPage(songs,
            hasMore = value.flag("more", "hasMore", "moreSongs") ||
                (total > 0 && songs.size < total))
    }

    fun albumSongPage(value: JsonObject): QqSongPage {
        val songs = songs(value)
        val total = value.number("total", "totalSongs", "totalNum", "total_song_num", "total_num")
        return QqSongPage(songs,
            hasMore = value.flag("more", "hasMore", "hasMoreSongs") ||
                (total > 0 && songs.size < total))
    }

    private fun albumObject(value: JsonObject): JsonObject =
        (value["album"] as? JsonObject)
            ?: (value["basicInfo"] as? JsonObject)
            ?: value

    private fun singerObjects(vararg roots: JsonObject): List<Map<String, String>> {
        val rows = roots.flatMap { root ->
            when (val raw = root["singer"]) {
                is JsonArray -> raw.toList()
                is JsonObject -> raw.array("singerList", "singers", "list", "singer").toList()
                else -> root.array("singerList", "singers", "singersList")
            }
        }
        return rows.mapNotNull { row ->
            parseLegacySinger(row)?.takeIf(Map<String, String>::isNotEmpty)
        }
    }

    private fun singers(value: JsonObject): List<String> =
        singerObjects(value).mapNotNull { it["name"] }.filter(String::isNotBlank)
            .ifEmpty {
                value.array("singer").mapNotNull { row ->
                    (row as? JsonObject)?.text("name", "title")?.takeIf(String::isNotBlank)
                }
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
