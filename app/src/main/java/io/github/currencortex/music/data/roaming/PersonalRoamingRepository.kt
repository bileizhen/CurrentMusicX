package io.github.currencortex.music.data.roaming

import io.github.currencortex.music.core.netease.*
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.song.Song
import kotlinx.serialization.json.*

data class RoamingChoice(val code: String, val title: String, val subtitle: String = "",
    val scene: Boolean = false, val icon: String = "", val cascade: String = "") {
    val key get() = if (scene) "scene:$code" else code
    val mode get() = if (scene) "SCENE_RCMD" else code
    val subMode get() = if (scene) code else ""
}
data class RoamingCatalog(val modes: List<RoamingChoice>, val scenes: List<RoamingChoice>) {
    val choices get() = modes + scenes
    companion object {
        val fallback = RoamingCatalog(listOf(
            RoamingChoice("DEFAULT", "默认模式", "沿着目前喜好继续聆听"),
            RoamingChoice("FAMILIAR", "熟悉模式", "喜欢过的歌曲与相似推荐"),
            RoamingChoice("EXPLORE", "探索模式", "偏好曲风与潜力好歌"),
            RoamingChoice("PUZZLE_MODE_RCMD", "拼图模式", "边听歌边拼歌曲封面")),
            listOf("NIGHT_EMO" to "伤感", "EXERCISE" to "运动", "SLEEP_HELP" to "助眠",
                "RELAX" to "放松", "FOCUS" to "专注", "COMMUTE" to "出行").map { (code, title) ->
                RoamingChoice(code, title, scene = true) })
    }
}

/** Client-compatible native FM protocol. No CurrentMusic token or server fallback. */
class PersonalRoamingRepository(private val transport: NeteaseTransport) {
    internal suspend fun request(path: String, body: JsonObject, owner: NeteaseSession? = null): JsonObject {
        val expected = owner ?: transport.sessions.snapshot()
        val raw = transport.request(path, body, session = expected, clientProfile = NeteaseClientProfile.ANDROID).body
        val code = raw.int("code") ?: throw ApiException(ErrorKind.Parse)
        if (code != 200) {
            if (code in setOf(301, 401)) transport.sessions.markStale(expected)
            throw ApiException(when (code) { 301, 401 -> ErrorKind.NeteaseBindingRequired
                403 -> ErrorKind.Forbidden; 429, 406 -> ErrorKind.RateLimited; else -> ErrorKind.Server }, code,
                raw.text("message").ifBlank { null })
        }
        return raw
    }
    suspend fun catalog(): RoamingCatalog = parseCatalog(request("/api/link/position/show/resource", buildJsonObject {
        put("positionCode", "FMTopModeDialog")
        put("extJson", buildJsonObject { put("clientLibraAbTest", buildJsonObject {})
            put("isHomePageNewFramework", true); put("userSetFMMode", false); put("enableAutoPlay", false) }.toString())
    }))
    suspend fun songs(choice: RoamingChoice, excluded: List<Long> = emptyList()): List<Song> {
        val owner = transport.sessions.snapshot()
        if (owner.credential.profile == null || owner.credential.stale || owner.credential.cookies["MUSIC_U"].isNullOrBlank())
            throw ApiException(ErrorKind.NeteaseBindingRequired)
        val raw = request("/api/v1/radio/get", buildJsonObject {
            put("mode", choice.mode); put("subMode", choice.subMode); put("limit", 3)
            put("unplaySongIds", excluded.takeLast(100).joinToString(",")); put("entranceType", "")
            if (choice.cascade.isNotBlank()) put("fmCascadeModeStr", choice.cascade)
            put("openAidj", false); put("aidjReqTimes", 0)
        }, owner)
        return parseSongs(raw).filter { it.id !in excluded }.distinctBy { it.id }
    }
    companion object {
        internal fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
        private fun JsonObject.int(key: String) = (get(key) as? JsonPrimitive)?.intOrNull
        private fun JsonObject.obj(key: String) = get(key) as? JsonObject ?: buildJsonObject {}
        private fun JsonObject.array(key: String) = get(key) as? JsonArray ?: JsonArray(emptyList())
        fun parseCatalog(raw: JsonObject): RoamingCatalog {
            val root = raw.obj("data").obj("crossPlatformResource").takeIf { it.isNotEmpty() } ?: raw
            val dsl = root.obj("dslData").takeIf { it.isNotEmpty() } ?: root
            val labels = dsl.obj("privateFmModeData")
            fun parse(key: String, scene: Boolean): List<RoamingChoice> = dsl.array(key).mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                val code = row.text("code"); val title = row.text("title")
                if (code.isBlank() || title.isBlank() || code.length > 100) return@mapNotNull null
                val cascade = row.text("fmCascadeModeStr").ifBlank {
                    dsl.obj("privateFmModeExt").obj(if (scene) "SCENE_RCMD" else code).obj(if (scene) code else "").text("fmCascadeModeStr") }
                RoamingChoice(code, title, row.text("subTitle"), scene, row.text("icon"), cascade)
            }.distinctBy { it.key }
            val modes = parse("recommendModeList", false).ifEmpty {
                labels.mapNotNull { (code, title) -> (title as? JsonPrimitive)?.contentOrNull?.let { RoamingChoice(code, it) } }
            }.ifEmpty { RoamingCatalog.fallback.modes }
            val scenes = parse("currentSceneList", true).ifEmpty {
                labels.obj("SCENE_RCMD").mapNotNull { (code, title) -> (title as? JsonPrimitive)?.contentOrNull?.let { RoamingChoice(code, it, scene = true) } }
            }
            return RoamingCatalog(modes, scenes)
        }
        fun parseSongs(raw: JsonObject): List<Song> {
            val data = raw["data"]
            val songs = data as? JsonArray ?: (data as? JsonObject)?.array("songs") ?: JsonArray(emptyList())
            return songs.mapNotNull { item ->
                val song = (item as? JsonObject)?.let { (it["songInfo"] as? JsonObject) ?: it } ?: return@mapNotNull null
                val id = (song["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
                val name = song.text("name"); if (id <= 0 || name.isBlank()) return@mapNotNull null
                val artists = ((song["ar"] ?: song["artists"]) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                val album = (song["al"] ?: song["album"]) as? JsonObject ?: buildJsonObject {}
                Song(id, name, artists.joinToString(" / ") { it.text("name") }, album.text("name"),
                    album.text("picUrl").replace("http:", "https:"),
                    ((song["dt"] ?: song["duration"]) as? JsonPrimitive)?.longOrNull ?: 0,
                    (song["mv"] as? JsonPrimitive)?.longOrNull ?: 0,
                    artists.mapNotNull { (it["id"] as? JsonPrimitive)?.longOrNull })
            }.distinctBy { it.id }
        }
    }
}
