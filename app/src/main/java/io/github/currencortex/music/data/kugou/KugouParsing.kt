package io.github.currencortex.music.data.kugou

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.library.Playlist
import io.github.currencortex.music.data.roaming.RoamingCatalog
import io.github.currencortex.music.data.roaming.RoamingChoice
import io.github.currencortex.music.data.song.KugouTrackRef
import io.github.currencortex.music.data.song.MusicSource
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.song.SongExternalIds
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * KuGou serves the same song through several response shapes:
 *  - complexsearch (`FileHash`, `SingerName`, `HQ`/`SQ`/`Res` objects),
 *  - the mobile catalog used by rank/album/singer/playlist pages (`hash`, `320hash`, `sqhash`, `filename`),
 *  - everyday recommendation (`hash_320`, `hash_flac`, `author_name`),
 *  - cloud playlists (`cloudlist.service/v4/get_list_all_file`: `name`, `mixsongid`, `timelen`, `fileid`),
 *  - KMR song detail (`kmr.service.kugou.com/v3/album_audio/audio`: `audio_info`, `album_info`).
 * One tolerant mapper keeps every list on the same [Song] model.
 */
internal object KugouParsing {
    private val HASH = Regex("[0-9A-F]{32}")

    fun songs(values: JsonArray?): List<Song> = values.orEmpty().mapNotNull { (it as? JsonObject)?.let(::song) }
        .distinctBy { it.id }

    fun song(item: JsonObject): Song? {
        val transParam = item.obj("trans_param")
        val hash = item.text("FileHash", "hash", "hash_128", "origin_hash").uppercase()
        if (!HASH.matches(hash)) return null
        val fileName = item.text("FileName", "filename").ifBlank { item.text("name") }
        val (fileArtist, fileTitle) = splitFileName(fileName)
        val authors = item.array("authors").mapNotNull { it as? JsonObject }
        val singers = item.array("Singers").mapNotNull { it as? JsonObject }
        val singerInfo = item.array("singerinfo").mapNotNull { it as? JsonObject }
        val title = item.text("OriSongName", "songname", "ori_audio_name").ifBlank { fileTitle }
            .ifBlank { fileName }.cleanText()
        if (title.isBlank()) return null
        val artists = item.text("SingerName", "author_name", "singername").ifBlank {
            authors.mapNotNull { it.text("author_name").takeIf(String::isNotBlank) }.joinToString("、")
        }.ifBlank {
            singers.mapNotNull { it.text("name").takeIf(String::isNotBlank) }.joinToString("、")
        }.ifBlank {
            singerInfo.mapNotNull { it.text("name").takeIf(String::isNotBlank) }.joinToString("、")
        }.ifBlank { fileArtist }.cleanText()
        val artistIds = (singers.map { it.long("id") } + authors.map { it.long("author_id") } +
            singerInfo.map { it.long("id") }).filter { it > 0 }.distinct()
        val albumId = item.text("AlbumID", "album_id")
        val albumAudioId = item.long("MixSongID", "mixsongid", "album_audio_id")
        val audioId = item.long("Audioid", "audio_id", "scid", "songid")
        val duration = item.long("Duration", "duration", "time_length", "timelength", "timelen")
        val relateGoods = item.array("relate_goods").mapNotNull { it as? JsonObject }
        fun relateHash(level: Long): String = relateGoods
            .firstOrNull { it.long("level") == level }
            ?.text("hash")?.uppercase()?.takeIf(HASH::matches).orEmpty()
        val hq = item.obj("HQ").text("Hash").ifBlank { item.text("320hash", "hash_320") }
            .uppercase().takeIf(HASH::matches).orEmpty().ifBlank { relateHash(4L) }
        val sq = item.obj("SQ").text("Hash").ifBlank { item.text("sqhash", "hash_flac") }
            .uppercase().takeIf(HASH::matches).orEmpty().ifBlank { relateHash(5L) }
        val res = item.obj("Res").text("Hash").ifBlank { item.text("hash_high") }
            .uppercase().takeIf(HASH::matches).orEmpty().ifBlank { relateHash(6L) }
        val superHash = item.obj("Super").text("Hash").ifBlank { item.text("hash_super", "super_hash", "hash_vinylrecord") }
            .uppercase().takeIf(HASH::matches).orEmpty()
        val dolbyHash = item.text("hash_dolby_448", "hash_dolby_640", "hash_dolby_768", "dolby_hash")
            .uppercase().takeIf(HASH::matches).orEmpty()
        val mvHash = item.text("mvhash", "mv_hash", "MVHash").ifBlank {
            (item.array("mvdata").firstOrNull() as? JsonObject).text("hash")
        }.uppercase().takeIf(HASH::matches).orEmpty()
        val fileId = item.long("fileid", "file_id").toInt()
        val id = albumAudioId.takeIf { it > 0 } ?: audioId.takeIf { it > 0 } ?: hash.take(15).toLong(16)
        val albumName = item.text("AlbumName", "album_name").ifBlank { item.obj("albuminfo").text("name") }.cleanText()
        return Song(
            id = id, name = title, artists = artists, album = albumName,
            cover = cover(transParam.text("union_cover"), item.text("Image", "sizable_cover", "album_sizable_cover", "imgurl", "cover")),
            durationMs = durationMs(duration), artistIds = artistIds, musicSource = MusicSource.KUGOU,
            externalIds = SongExternalIds(kugouId = hash),
            albumId = albumId.toLongOrNull() ?: 0L,
            kugou = KugouTrackRef(
                hash = hash, albumId = albumId.takeIf { it != "0" }.orEmpty(), albumAudioId = albumAudioId,
                audioId = audioId, hqHash = hq, sqHash = sq, resHash = res,
                superHash = superHash, dolbyHash = dolbyHash, mvHash = mvHash, fileId = fileId,
            ),
        )
    }

    /** Compute the ordered set of [AudioQuality] tiers backed by valid hashes on [ref]. */
    fun availableQualities(ref: KugouTrackRef): Set<AudioQuality> = linkedSetOf<AudioQuality>().apply {
        if (ref.viperMasterHash.isNotBlank()) add(AudioQuality.KUGOU_VIPER_MASTER)
        if (ref.viperClearHash.isNotBlank()) add(AudioQuality.KUGOU_VIPER_CLEAR)
        if (ref.viperAtmosHash.isNotBlank()) add(AudioQuality.KUGOU_VIPER_ATMOS)
        if (ref.dolbyHash.isNotBlank()) add(AudioQuality.DOLBY)
        if (ref.resHash.isNotBlank() || ref.superHash.isNotBlank()) add(AudioQuality.HIRES)
        if (ref.sqHash.isNotBlank()) add(AudioQuality.LOSSLESS)
        if (ref.hqHash.isNotBlank()) add(AudioQuality.EXHIGH)
        if (ref.hash.isNotBlank()) add(AudioQuality.STANDARD)
    }

    /** Enrich an existing [Song] with KMR `/v3/album_audio/audio` detail and compute available [AudioQuality] levels. */
    fun kmrSongDetail(song: Song, item: JsonObject): Pair<Song, Set<AudioQuality>> {
        val audioInfo = item.obj("audio_info") ?: item
        val albumInfo = item.obj("album_info")
        val existing = song.kugou ?: KugouTrackRef(hash = "")
        val hash128 = audioInfo.text("hash_128", "hash").uppercase().takeIf(HASH::matches) ?: existing.hash
        val hash320 = audioInfo.text("hash_320").uppercase().takeIf(HASH::matches) ?: existing.hqHash
        val hashFlac = audioInfo.text("hash_flac").uppercase().takeIf(HASH::matches) ?: existing.sqHash
        val hashHigh = audioInfo.text("hash_high").uppercase().takeIf(HASH::matches) ?: existing.resHash
        val hashSuper = audioInfo.text("hash_super", "hash_vinylrecord").uppercase().takeIf(HASH::matches) ?: existing.superHash
        val hashDolby = audioInfo.text("hash_dolby_448", "hash_dolby_640", "hash_dolby_768").uppercase().takeIf(HASH::matches)
            ?: existing.dolbyHash
        val mvHash = item.text("mv_hash", "mvhash").ifBlank { audioInfo.text("mv_hash", "mvhash") }
            .uppercase().takeIf(HASH::matches) ?: existing.mvHash
        val audioId = item.long("audio_id", "scid").takeIf { it > 0 }
            ?: audioInfo.long("audio_id").takeIf { it > 0 } ?: existing.audioId
        val albumAudioId = item.long("album_audio_id", "mixsongid").takeIf { it > 0 } ?: existing.albumAudioId
        val albumId = albumInfo.text("album_id").ifBlank { item.text("album_id") }
            .takeIf { it.isNotBlank() && it != "0" } ?: existing.albumId
        val albumName = albumInfo.text("album_name").ifBlank { item.text("album_name") }.cleanText()
            .ifBlank { song.album }
        val coverUrl = cover(
            audioInfo.obj("trans_param").text("union_cover"),
            item.obj("trans_param").text("union_cover"),
            albumInfo.text("sizable_cover", "cover"),
            item.text("sizable_cover"),
        ).ifBlank { song.cover }
        val duration = audioInfo.long("timelength", "timelength_128", "duration")
            .takeIf { it > 0 }?.let(::durationMs) ?: song.durationMs
        val updatedRef = existing.copy(
            hash = hash128.ifBlank { existing.hash },
            albumId = albumId,
            albumAudioId = albumAudioId,
            audioId = audioId,
            hqHash = hash320,
            sqHash = hashFlac,
            resHash = hashHigh,
            superHash = hashSuper,
            dolbyHash = hashDolby,
            mvHash = mvHash,
        )
        val enriched = song.copy(
            name = item.text("ori_audio_name", "songname").cleanText().ifBlank { song.name },
            artists = item.text("author_name").cleanText().ifBlank { song.artists },
            album = albumName,
            albumId = albumId.toLongOrNull() ?: song.albumId,
            cover = coverUrl,
            durationMs = duration,
            externalIds = song.externalIds.copy(kugouId = updatedRef.hash.ifBlank { song.externalIds.kugouId }),
            kugou = updatedRef,
        )
        return enriched to availableQualities(updatedRef)
    }

    /**
     * Enrich [song] with hashes from `v2/get_res_privilege/lite` (`relate_goods[]`), which is the authoritative
     * source for KuGou Viper tiers (`viper_tape`, `viper_clear`, `viper_atmos`) alongside standard/HQ/SQ/Hi-Res/Dolby.
     */
    fun applyPrivilege(song: Song, data: JsonArray): Pair<Song, Set<AudioQuality>> {
        val existing = song.kugou ?: KugouTrackRef(hash = "")
        val entry = data.firstOrNull() as? JsonObject ?: return song to availableQualities(existing)
        val goods = entry.array("relate_goods").mapNotNull { it as? JsonObject }
        if (goods.isEmpty()) return song to availableQualities(existing)
        val byQuality = linkedMapOf<String, String>()
        goods.forEach { rg ->
            val q = rg.text("quality").lowercase()
            val h = rg.text("hash").uppercase().takeIf(HASH::matches) ?: return@forEach
            if (q.isNotBlank() && q !in byQuality) byQuality[q] = h
        }
        val dolby = byQuality["dolby2_448k"] ?: byQuality["dolby2_1500k"] ?: byQuality["dolby"] ?: existing.dolbyHash
        val updatedRef = existing.copy(
            hash = byQuality["128"] ?: existing.hash,
            hqHash = byQuality["320"] ?: existing.hqHash,
            sqHash = byQuality["flac"] ?: existing.sqHash,
            resHash = byQuality["high"] ?: existing.resHash,
            superHash = byQuality["super"] ?: existing.superHash,
            dolbyHash = dolby,
            viperMasterHash = byQuality["viper_tape"] ?: existing.viperMasterHash,
            viperClearHash = byQuality["viper_clear"] ?: existing.viperClearHash,
            viperAtmosHash = byQuality["viper_atmos"] ?: existing.viperAtmosHash,
        )
        val enriched = song.copy(
            externalIds = song.externalIds.copy(kugouId = updatedRef.hash.ifBlank { song.externalIds.kugouId }),
            kugou = updatedRef,
        )
        return enriched to availableQualities(updatedRef)
    }

    fun comment(item: JsonObject): KugouComment? {
        val id = item.text("id", "comment_id").takeIf(String::isNotBlank) ?: return null
        val content = item.text("content").cleanText()
        if (content.isBlank()) return null
        val like = item.obj("like")
        val likes = like.long("likenum", "count").takeIf { it > 0 } ?: item.long("likenum", "like_num")
        val hasLike = like.long("haslike") == 1L || item.long("haslike") == 1L
        val quoted = item.text("pcontent").cleanText().takeIf(String::isNotBlank)
        val quotedUser = item.text("puser_name").cleanText()
        return KugouComment(
            id = id,
            nickname = item.text("user_name", "nickname").cleanText().ifBlank { "酷狗用户" },
            avatar = cover(item.text("user_pic", "avatar")),
            text = content,
            time = parseCommentTime(item.text("addtime", "time")),
            likes = likes,
            likedByMe = hasLike,
            replyCount = item.long("reply_num").toInt(),
            userId = item.long("user_id"),
            quotedNickname = quotedUser,
            quotedContent = quoted,
        )
    }

    fun category(item: JsonObject): KugouCategory? {
        val id = item.text("special_tag_id", "id").takeIf { it.isNotBlank() && it != "0" } ?: return null
        val name = item.text("name", "tagname").cleanText().ifBlank { return null }
        return KugouCategory(id = id, name = name)
    }

    fun tagPlaylist(item: JsonObject): Playlist? {
        val id = item.text("specialid").takeIf { it.isNotBlank() && it != "0" } ?: return null
        val name = item.text("specialname", "name").cleanText().ifBlank { return null }
        return Playlist(
            id = id.toLongOrNull() ?: id.hashCode().toLong(),
            name = name,
            description = item.text("intro").cleanText(),
            cover = cover(item.text("imgurl", "img")),
            source = "kugou",
            ownerId = 0,
            count = item.long("songcount", "song_count").toInt(),
            songs = emptyList(),
            remoteId = id,
        )
    }

    fun cloudPlaylist(item: JsonObject, ownerId: Long): Playlist? {
        if (item.long("is_del") == 1L || item.long("is_drop") == 1L) return null
        val listId = item.long("listid")
        if (listId <= 0) return null
        val name = item.text("name").cleanText().ifBlank { if (listId == KugouProtocol.LIKED_LIST_ID.toLong()) "我喜欢" else return null }
        val creatorUid = item.long("list_create_userid")
        val liked = listId == KugouProtocol.LIKED_LIST_ID.toLong()
        // `list_create_userid` names the source playlist's creator, so a different uid means a
        // collected copy. It is the only reliable marker; `type` is just a fallback.
        val owned = liked || (if (creatorUid > 0L) creatorUid == ownerId
            else "type" in item && item.long("type") == 0L)
        val rawCover = item.text("pic", "imgurl").ifBlank {
            item.array("pics").firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty()
        }
        val remote = listId.toString()
        return Playlist(
            id = listId,
            name = name,
            description = item.text("intro").cleanText(),
            cover = cover(rawCover),
            source = "kugou",
            ownerId = if (owned) ownerId else creatorUid,
            count = item.long("count").toInt(),
            songs = emptyList(),
            nativeOwned = owned,
            nativeLiked = liked,
            nativeSubscribed = !owned,
            remoteId = remote,
        )
    }

    fun playlist(item: JsonObject): KugouCatalogEntry? {
        val id = item.text("specialid", "global_collection_id", "gid").takeIf(String::isNotBlank) ?: return null
        val name = item.text("specialname", "name").cleanText().ifBlank { return null }
        return KugouCatalogEntry(id, name, cover(item.text("img", "imgurl", "flexible_cover")),
            item.text("nickname", "username").cleanText(), "playlist", item.long("song_count", "songcount").toInt())
    }

    fun album(item: JsonObject): KugouCatalogEntry? {
        val id = item.text("albumid", "album_id").takeIf { it.isNotBlank() && it != "0" } ?: return null
        val name = item.text("albumname", "name").cleanText().ifBlank { return null }
        return KugouCatalogEntry(id, name, cover(item.text("img", "imgurl")), item.text("singer", "singername").cleanText(),
            "album", item.long("songcount", "song_count").toInt())
    }

    fun artist(item: JsonObject): KugouCatalogEntry? {
        val id = item.text("AuthorId", "singerid").takeIf { it.isNotBlank() && it != "0" } ?: return null
        val name = item.text("AuthorName", "singername").cleanText().ifBlank { return null }
        return KugouCatalogEntry(id, name, cover(item.text("Avatar", "imgurl")), "", "artist", item.long("AudioCount", "songcount").toInt())
    }

    fun rank(item: JsonObject): KugouRank? {
        val id = item.text("rankid").takeIf(String::isNotBlank) ?: return null
        val name = item.text("rankname").cleanText().ifBlank { return null }
        return KugouRank(id, name, cover(item.text("imgurl", "img_9", "banner_9")), item.text("intro").cleanText(),
            item.text("update_frequency"))
    }

    /** Built-in KuGou "猜你喜欢" modes and scenes (`SceneModeListProtocol$a` / `e$a.b()`). */
    fun defaultRoamingCatalog(): RoamingCatalog = RoamingCatalog(
        modes = listOf(
            RoamingChoice("0", "默认模式", "按你的实时口味连续推荐", poolId = "0"),
            RoamingChoice("1000002", "尝鲜模式", "为你开启尝鲜模式", poolId = "1000002"),
            RoamingChoice("1000001", "熟悉模式", "为你开启熟悉模式", poolId = "1000001"),
        ),
        scenes = listOf(
            RoamingChoice("88104", "热歌", "为你开启热歌", scene = true, poolId = "88104"),
            RoamingChoice("112816", "通勤必听", "为你开启通勤必听", scene = true, poolId = "112816"),
            RoamingChoice("87963", "驾车", "为你开启驾车", scene = true, poolId = "87963"),
            RoamingChoice("112815", "动感运动", "为你开启动感运动", scene = true, poolId = "112815"),
            RoamingChoice("112806", "助眠", "为你开启助眠模式", scene = true, poolId = "112806"),
            RoamingChoice("87495", "粤语", "为你开启粤语", scene = true, poolId = "87495"),
        ),
    )

    /** Parse `kyproxyrec/v2/client/pfm_scene_list_new` into modes (`music_model`) and scenes (`item_list`). */
    fun parseRoamingCatalog(root: JsonObject): RoamingCatalog {
        val data = root.obj("data") ?: return defaultRoamingCatalog()
        fun firstField(obj: JsonObject, extra: JsonObject?, vararg keys: String): String =
            obj.text(*keys).ifBlank { extra.text(*keys) }.cleanText()

        fun parseChoice(item: JsonObject, isScene: Boolean): RoamingChoice? {
            val rawId = item.text("id", "itemId", "module_id").toIntOrNull() ?: return null
            val extra = item.obj("ky_extra")
            val navName = firstField(item, extra, "nv", "navigation_name")
            val optionName = firstField(item, extra, "an", "option_name")
            val title = optionName.ifBlank { navName }.ifBlank { if (rawId == 0) "默认模式" else rawId.toString() }
            val subtitle = firstField(item, extra, "tt", "switch_tip").ifBlank { "为你开启$title" }
            val icon = cover(firstField(item, extra, "optionIcon", "option_icon_url", "popupIcon", "popup_icon_url"))
            return RoamingChoice(
                code = rawId.toString(),
                title = title,
                subtitle = subtitle,
                scene = isScene,
                icon = icon,
                poolId = rawId.toString(),
            )
        }

        val modes = mutableListOf<RoamingChoice>()
        val scenes = mutableListOf<RoamingChoice>()
        val groups = data.array("groups").mapNotNull { it as? JsonObject }
        if (groups.isNotEmpty()) {
            for (group in groups) {
                val groupId = group.text("group_id", "id")
                val groupName = group.text("group_name")
                val showType = group.text("show_type")
                val isModeGroup = groupId == "music_model" || showType == "1" || groupName == "切换音乐模式"
                for (entry in group.array("items")) {
                    val obj = entry as? JsonObject ?: continue
                    parseChoice(obj, isScene = !isModeGroup)?.let { if (isModeGroup) modes += it else scenes += it }
                }
            }
        } else {
            for (entry in data.array("music_model")) {
                val obj = entry as? JsonObject ?: continue
                parseChoice(obj, isScene = false)?.let(modes::add)
            }
            for (entry in data.array("item_list")) {
                val obj = entry as? JsonObject ?: continue
                parseChoice(obj, isScene = true)?.let(scenes::add)
            }
        }
        val defaultMode = RoamingChoice("0", "默认模式", "按你的实时口味连续推荐", poolId = "0")
        val dedupModes = (if (modes.any { it.code == "0" }) modes else listOf(defaultMode) + modes).distinctBy { it.code }
        val dedupScenes = scenes.distinctBy { it.code }
        if (dedupModes.size <= 1 && dedupScenes.isEmpty()) return defaultRoamingCatalog()
        return RoamingCatalog(modes = dedupModes, scenes = dedupScenes)
    }

    fun playlistModel(entry: KugouCatalogEntry, songs: List<Song> = emptyList(), description: String = "") = Playlist(
        id = entry.id.toLongOrNull() ?: entry.id.hashCode().toLong(), name = entry.name, description = description,
        cover = entry.cover, source = "kugou", ownerId = 0, count = maxOf(entry.count, songs.size), songs = songs,
        remoteId = entry.id,
    )

    fun splitFileName(fileName: String): Pair<String, String> {
        val index = fileName.indexOf(" - ")
        return if (index > 0) fileName.substring(0, index).trim() to fileName.substring(index + 3).trim() else "" to fileName.trim()
    }

    /** Search and catalog durations are seconds; a few gateways answer in milliseconds. */
    fun durationMs(raw: Long): Long = if (raw <= 0) 0 else if (raw >= 36_000) raw else raw * 1000

    fun cover(vararg values: String): String = values.firstOrNull { it.startsWith("http") }.orEmpty()
        .replace("{size}", "480").replace("http://", "https://")

    private fun parseCommentTime(raw: String): Long {
        val numeric = raw.toLongOrNull()
        if (numeric != null) return if (numeric > 10_000_000_000L) numeric else numeric * 1000L
        if (raw.isBlank()) return 0L
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }.parse(raw)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun String.cleanText() = replace(Regex("<[^>]{1,40}>"), "").trim().take(512)
    private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())

    fun JsonObject?.text(vararg keys: String): String {
        if (this == null) return ""
        for (key in keys) {
            val value = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
            if (value != null) return value
        }
        return ""
    }
    fun JsonObject?.long(vararg keys: String): Long {
        if (this == null) return 0
        for (key in keys) {
            val value = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
            if (value != null) return value
        }
        return 0
    }
    fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject
    fun JsonObject?.array(key: String): JsonArray = (this?.get(key) as? JsonArray) ?: JsonArray(emptyList())
    fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
}
