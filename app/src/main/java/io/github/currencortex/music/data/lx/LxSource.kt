package io.github.currencortex.music.data.lx

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest

internal const val LX_MAX_SCRIPT_BYTES = 1024 * 1024
internal const val LX_MAX_RESPONSE_BYTES = 2 * 1024 * 1024
internal const val LX_CAPABILITY_VERSION = 1
internal val lxQualities = listOf("128k", "320k", "flac", "flac24bit", "hires", "atmos", "master")
internal val lxPlatforms = linkedMapOf("wy" to "网易云")

internal fun lxQualityLabel(quality: String) = when (quality) {
    "128k" -> "标准"
    "320k" -> "极高"
    "flac" -> "无损"
    "flac24bit" -> "24bit"
    "hires" -> "Hi-Res"
    "atmos" -> "高清臻音"
    "master" -> "超清母带"
    else -> quality
}

internal fun lxQualityLevel(quality: String) = when (quality) {
    "master" -> AudioQuality.JYMASTER.value
    "atmos" -> AudioQuality.JYEFFECT.value
    "hires" -> AudioQuality.HIRES.value
    "flac24bit" -> "flac24bit"
    "flac" -> AudioQuality.LOSSLESS.value
    "320k" -> AudioQuality.EXHIGH.value
    else -> AudioQuality.STANDARD.value
}

internal fun lxQualityFormat(quality: String) = if (quality in setOf("128k", "320k")) "mp3" else "flac"

/** Also applied to saved declarations from versions which exposed other platforms. */
internal fun lxNeteaseCapabilities(sources: List<LxCapability>): List<LxCapability> {
    val qualities = lxQualities.filter { quality -> sources.any { it.platform == "wy" && quality in it.qualities } }
    return if (qualities.isEmpty()) emptyList() else listOf(LxCapability("wy", qualities))
}

@Serializable data class LxCapability(val platform: String, val qualities: List<String>)
@Serializable data class LxSourceInfo(val id: String, val name: String, val description: String = "",
    val version: String = "", val author: String = "", val homepage: String = "",
    val origin: String = "", val capabilities: List<LxCapability> = emptyList(), val capabilityVersion: Int = 0) {
    val summary get() = (if (lxNeteaseCapabilities(capabilities).isEmpty()) "未声明网易云接口" else "网易云") +
        version.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
}
@Serializable internal class LxStoredSource(val info: LxSourceInfo, val script: String,
    val performance: LxSourcePerformance = LxSourcePerformance()) {
    override fun toString() = "LxStoredSource(${info.id}, script=[private])"
}
class LxSourceException(message: String, val qualityUnavailable: Boolean = false) : java.io.IOException(message)

internal fun lxScriptInfo(script: String, origin: String = ""): LxSourceInfo {
    require(script.toByteArray(Charsets.UTF_8).size in 1..LX_MAX_SCRIPT_BYTES) { "音源脚本为空或超过 1 MB" }
    val header = Regex("^\\s*/\\*[\\s\\S]*?\\*/").find(script.removePrefix("\uFEFF"))?.value
        ?: throw IllegalArgumentException("不是有效的落雪音源：缺少脚本信息注释")
    val fields = Regex("(?m)^\\s*\\*?\\s*@(name|description|version|author|homepage)\\s+([^\\r\\n]+)")
        .findAll(header).associate { it.groupValues[1] to it.groupValues[2].trim() }
    val name = fields["name"].orEmpty()
    require(name.isNotBlank() && name.length <= 256) { "音源脚本缺少有效名称" }
    require(fields.values.all { it.length <= 8192 }) { "音源脚本信息过长" }
    val id = MessageDigest.getInstance("SHA-256").digest(script.toByteArray()).joinToString("") { "%02x".format(it) }
    return LxSourceInfo(id, name, fields["description"].orEmpty(), fields["version"].orEmpty(),
        fields["author"].orEmpty(), fields["homepage"].orEmpty(), origin)
}

internal fun lxCapabilities(value: JsonObject): List<LxCapability> {
    val sources = value["sources"] as? JsonObject ?: throw LxSourceException("音源未声明支持的平台")
    return lxPlatforms.keys.mapNotNull { key ->
        val source = sources[key] as? JsonObject ?: return@mapNotNull null
        val actions = (source["actions"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        if (source["type"]?.jsonPrimitive?.contentOrNull != "music" || "musicUrl" !in actions) return@mapNotNull null
        val declared = (source["qualitys"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val qualities = lxQualities.filter { it in declared }
        qualities.takeIf { it.isNotEmpty() }?.let { LxCapability(key, it) }
    }.also { if (it.isEmpty()) throw LxSourceException("此音源没有声明网易云播放接口，目前仅支持网易云") }
}

internal data class LxTrackRequest(val platform: String, val quality: String, val data: JsonObject)
private data class LxSongTarget(val id: String, val platform: String, val supported: List<String>)

private fun lxSongTarget(song: Song, sources: List<LxCapability>): LxSongTarget {
    val wy = song.externalIds.neteaseId?.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true }
        ?: song.id.takeIf { song.musicSource == MusicSource.NETEASE && it > 0 }?.toString()
    val id = wy ?: throw LxSourceException("落雪音源目前仅支持带有效网易云 ID 的歌曲")
    val platform = "wy"
    val supported = lxNeteaseCapabilities(sources).firstOrNull()?.qualities
        ?: throw LxSourceException("此音源没有声明网易云播放接口，请切换音源")
    return LxSongTarget(id, platform, supported)
}

private fun lxSelectedQuality(quality: AudioQuality, supported: List<String>): String {
    val desired = when (quality) {
        AudioQuality.STANDARD, AudioQuality.HIGHER -> 0
        AudioQuality.EXHIGH -> 1
        AudioQuality.LOSSLESS -> 3
        AudioQuality.HIRES -> 4
        AudioQuality.JYEFFECT, AudioQuality.VIVID, AudioQuality.DOLBY, AudioQuality.SKY -> 5
        AudioQuality.JYMASTER -> 6
        AudioQuality.AUTO -> lxQualities.lastIndex
    }
    return lxQualities.take(desired + 1).lastOrNull { it in supported } ?: supported.first()
}

private fun lxTrackRequest(song: Song, target: LxSongTarget, selected: String): LxTrackRequest {
    return LxTrackRequest(target.platform, selected, buildJsonObject {
        put("source", target.platform); put("action", "musicUrl")
        put("info", buildJsonObject {
            put("type", selected)
            put("musicInfo", buildJsonObject {
                put("source", target.platform); put("songmid", target.id); put("name", song.name)
                put("singer", song.artists); put("albumName", song.album); put("img", song.cover)
                put("interval", "%02d:%02d".format(java.util.Locale.ROOT, song.durationMs / 60000, song.durationMs / 1000 % 60))
                put("_types", buildJsonObject { target.supported.forEach { put(it, buildJsonObject { put("type", it) }) } })
                put("types", JsonArray(target.supported.map { buildJsonObject { put("type", it) } }))
                put("meta", buildJsonObject { put("songId", target.id); put("albumName", song.album) })
            })
        })
    })
}

internal fun lxTrackRequest(song: Song, quality: AudioQuality, sources: List<LxCapability>): LxTrackRequest {
    val target = lxSongTarget(song, sources)
    return lxTrackRequest(song, target, lxSelectedQuality(quality, target.supported))
}

/** Only an explicit per-track rejection permits AUTO to try the next declared quality. */
internal suspend fun lxResolveTrack(song: Song, quality: AudioQuality, sources: List<LxCapability>,
    resolve: suspend (JsonObject) -> String): Pair<LxTrackRequest, String> {
    val target = lxSongTarget(song, sources)
    val requests = if (quality == AudioQuality.AUTO) target.supported.asReversed().map { level -> lxTrackRequest(song, target, level) }
        else listOf(lxTrackRequest(song, target, lxSelectedQuality(quality, target.supported)))
    for ((index, request) in requests.withIndex()) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        try { return request to resolve(request.data) }
        catch (error: LxSourceException) {
            if (!error.qualityUnavailable || index == requests.lastIndex) throw error
        }
    }
    error("No declared LX quality")
}
