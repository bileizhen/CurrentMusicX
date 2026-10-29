package io.github.currencortex.music.data.netease.playlistimport

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.Song
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PlaylistImportTask internal constructor(val id: Long, internal val owner: NeteaseSession)
data class PlaylistImportStatus(val finished: Boolean, val successful: Boolean,
    val playlistIds: List<Long> = emptyList(), val message: String = "")

/** Official import protocol reconstructed from the NetEase app and its linked public H5. */
class NeteasePlaylistImportRepository(private val transport: NeteaseTransport,
    uploadBaseUrl: String = "https://nosup-hz1.127.net/") {
    val accounts get() = transport.sessions.state
    private val imageUpload = PlaylistImportImageUpload(uploadBaseUrl)

    private suspend fun owner(): NeteaseSession = transport.sessions.snapshot().also {
        if (it.credential.profile == null || it.credential.stale || it.credential.cookies["MUSIC_U"].isNullOrBlank())
            throw ApiException(ErrorKind.NeteaseBindingRequired)
    }

    internal suspend fun request(path: String, owner: NeteaseSession, data: JsonObject = buildJsonObject {}): JsonObject {
        transport.sessions.check(owner)
        val raw = transport.request(path, data, session = owner, clientProfile = NeteaseClientProfile.ANDROID).body
        val code = (raw["code"] as? JsonPrimitive)?.intOrNull ?: throw ApiException(ErrorKind.Parse)
        if (code == 200) return raw
        if (code in setOf(301, 401)) transport.sessions.markStale(owner)
        throw ApiException(when (code) {
            301, 401 -> ErrorKind.NeteaseBindingRequired
            403 -> ErrorKind.Forbidden
            406, 429 -> ErrorKind.RateLimited
            else -> ErrorKind.Server
        }, code, listOf("message", "msg").firstNotNullOfOrNull { key ->
            (raw[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)?.take(180)
        })
    }

    suspend fun submitLinks(links: List<String>, importStar: Boolean): PlaylistImportTask {
        val clean = links.map(::extractImportLink).distinct()
        require(clean.size in 1..10) { "请填写 1–10 个歌单链接" }
        val owner = owner()
        val normalized = mutableListOf<String>()
        for (link in clean) {
            val checked = request("/api/playlist/import/url/check", owner, buildJsonObject { put("link", encodeImportUri(link)) })
            require(checked["data"] != null && checked["data"] != JsonNull && checked["data"] != JsonPrimitive(false)) {
                "网易云暂不支持这个链接，请检查后重试"
            }
            val canonical = ((checked["data"] as? JsonObject)?.get("link") as? JsonPrimitive)?.contentOrNull
            normalized += canonical?.takeIf(String::isNotBlank)?.let(::extractImportLink) ?: link
        }
        return submitUrls(owner, normalized.distinct(), importStar)
    }

    suspend fun submitText(text: String, importStar: Boolean): PlaylistImportTask {
        require(text.isNotBlank() && text.length <= 20_000) { "请输入歌曲文字，最多 20000 字" }
        return submitUrls(owner(), listOf("rpc://playlist/import?text=${text.trim()}"), importStar)
    }

    suspend fun submitLocal(songs: List<Song>, importStar: Boolean): PlaylistImportTask {
        require(songs.size in 1..10_000 && songs.all { it.name.isNotBlank() && !it.video }) { "请选择 1–10000 首有效歌曲" }
        val multiSongs = buildJsonArray { songs.forEach { song -> add(buildJsonObject {
            put("songName", song.name); put("artistName", song.artists); put("albumName", song.album)
        }) } }
        return create(owner(), buildJsonObject { put("multiSongs", multiSongs.toString()); put("importStarPlaylist", importStar) })
    }

    suspend fun targets(): List<PlaylistImportTarget> = targets(owner())

    private suspend fun targets(owner: NeteaseSession): List<PlaylistImportTarget> {
        val uid = requireNotNull(owner.credential.profile).uid
        val raw = request("/api/user/playlist", owner, buildJsonObject { put("uid", uid); put("limit", 1000); put("offset", 0) })
        return (raw["playlist"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { row ->
            val creator = (row["creator"] as? JsonObject)?.get("userId") ?: row["userId"]
            val id = (row["id"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
            if ((creator as? JsonPrimitive)?.longOrNull != uid || id == null) null
            else PlaylistImportTarget(id, (row["name"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "歌单 $id" })
        }.distinctBy { it.id }
    }

    suspend fun submitImages(images: List<PlaylistImportImage>, playlistId: Long): PlaylistImportTask {
        require(images.size in 1..9 && images.all { it.bytes.size in 1..(8 * 1024 * 1024) && it.mimeType.startsWith("image/") }) {
            "请选择 1–9 张图片，每张最多 8 MB"
        }
        val owner = owner()
        require(targets(owner).any { it.id == playlistId }) { "请选择当前网易云账号创建的目标歌单" }
        val ids = mutableListOf<String>()
        for (image in images) {
            val raw = request("/api/nos/token/whalealloc", owner, buildJsonObject {
                put("filename", image.fileName.take(200)); put("fileSize", image.bytes.size)
                put("type", "image"); put("bucket", "yyimgs"); put("bizKey", "64e97e4d"); put("encryptFlag", 1)
            })
            val result = (raw["data"] ?: raw["result"]) as? JsonObject ?: throw ApiException(ErrorKind.Parse)
            val key = (result["objectKey"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it.length <= 1024 }
                ?: throw ApiException(ErrorKind.Parse)
            val token = (result["token"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
                ?: throw ApiException(ErrorKind.Parse)
            imageUpload.upload(image, key, token) { transport.sessions.check(owner) }
            ids += "yyimgs/$key"
        }
        return task(request("/api/playlist/import/create", owner, buildJsonObject {
            put("playlistId", playlistId); put("picIds", JsonArray(ids.map(::JsonPrimitive)).toString()); put("rqRefer", "")
        }), owner)
    }

    private suspend fun submitUrls(owner: NeteaseSession, urls: List<String>, importStar: Boolean): PlaylistImportTask {
        val name = "导入音乐 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        return create(owner, buildJsonObject {
            put("playlistName", name); put("taskIdForLog", ""); put("rqRefer", "")
            put("songs", buildJsonArray { urls.forEach { url -> add(buildJsonObject {
                put("name", name); put("type", ""); put("url", encodeImportUri(url))
            }) } }.toString())
            put("importStarPlaylist", importStar)
        })
    }

    private suspend fun create(owner: NeteaseSession, data: JsonObject): PlaylistImportTask {
        val raw = request("/api/playlist/import/name/task/create", owner, data)
        return task(raw, owner)
    }

    private fun task(raw: JsonObject, owner: NeteaseSession): PlaylistImportTask {
        val result = raw["data"] as? JsonObject ?: raw
        val id = (result["taskId"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
            ?: throw ApiException(ErrorKind.Parse, userMessage = "网易云未返回导入任务编号，请先检查歌单再重试")
        return PlaylistImportTask(id, owner)
    }

    suspend fun status(task: PlaylistImportTask): PlaylistImportStatus {
        val raw = request("/api/playlist/import/task/status/v2", task.owner, buildJsonObject {
            put("taskIds", buildJsonArray { add(task.id) }.toString())
        })
        val data = raw["data"] as? JsonObject ?: throw ApiException(ErrorKind.Parse)
        val row = (data["tasks"] as? JsonArray)?.filterIsInstance<JsonObject>()
            ?.firstOrNull { (it["id"] as? JsonPrimitive)?.longOrNull == task.id }
            ?: return PlaylistImportStatus(false, false, message = "任务已提交，等待网易云处理")
        val state = when ((row["status"] as? JsonPrimitive)?.contentOrNull) {
            "IMPORTING" -> 1; "COMPLETE" -> 2; "FAILED" -> 3; "PLAYLIST_DELETE" -> 4
            "PLAYLIST_FULL_SOME_SUCCESS" -> 5; "PLAYLIST_FULL_NONE_SUCCESS" -> 6; "RECOGNIZE_NO_SONGS" -> 7
            else -> 0
        }
        val finished = state in 2..7
        val successful = state == 2 || state == 5
        val playlist = (row["playlistId"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
        val message = (row["msg"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)?.take(300)
            ?: when (state) {
                1 -> "网易云正在匹配歌曲并导入"
                2 -> "导入完成"
                3 -> "导入失败，请检查歌单内容"
                4 -> "目标歌单已删除"
                5 -> "歌单已满，仅部分歌曲导入成功"
                6 -> "歌单已满，没有歌曲导入成功"
                7 -> "没有识别到可导入的歌曲"
                else -> "网易云仍在处理任务"
            }
        return PlaylistImportStatus(finished, successful, listOfNotNull(playlist), message)
    }
}

internal fun extractImportLink(input: String): String {
    val link = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE).find(input)?.value
        ?.trimEnd('，', '。', '；', '、', '！', '？', '」', '』', '】', '）')
        ?: throw IllegalArgumentException("请粘贴包含 http 或 https 链接的分享内容")
    require(link.length <= 4096 && runCatching { java.net.URI(link).host?.isNotBlank() == true }.getOrDefault(false)) { "歌单链接无效" }
    return link
}

/** JavaScript encodeURI, including percent signs and UTF-8 text in the text-import RPC URL. */
internal fun encodeImportUri(value: String): String = buildString {
    val safe = ";,/?:@&=+$-_.!~*'()#"
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        val c = byte.toInt() and 255
        if (c in 65..90 || c in 97..122 || c in 48..57 || c.toChar() in safe) append(c.toChar())
        else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
    }
}
