package io.github.currencortex.music.data.lx

import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.data.song.*
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest

internal const val LX_MAX_SCRIPT_BYTES = 1024 * 1024
internal const val LX_MAX_RESPONSE_BYTES = 2 * 1024 * 1024
internal const val LX_CAPABILITY_VERSION = 2
internal val lxQualities = listOf(
    "128k", "320k", "flac", "flac24bit", "hires",
    "atmos", "atmos_plus", "master", "nac", "dtsx", "dolby",
)
internal enum class LxPlatform(val key: String, val label: String) {
    NETEASE("wy", "网易云"),
    QQ_MUSIC("tx", "QQ 音乐");

    companion object {
        fun from(song: Song) = if (song.musicSource == MusicSource.QQ_MUSIC) QQ_MUSIC else NETEASE
    }
}
internal val lxPlatforms = linkedMapOf("wy" to "网易云", "tx" to "QQ 音乐")

private fun lxCanonicalQuality(quality: String) = when (quality) {
    "dts", "qq_dts_x" -> "dtsx"
    "qq_nac" -> "nac"
    "qq_master" -> "master"
    "qq_studio" -> "atmos"
    "qq_surround" -> "atmos_plus"
    else -> quality
}

internal fun lxQualityLabel(platform: LxPlatform, quality: String) = when (platform) {
    LxPlatform.NETEASE -> when (quality) {
        "128k" -> "标准"
        "320k" -> "极高"
        "flac" -> "无损"
        "flac24bit" -> "24bit"
        "hires" -> "Hi-Res"
        "atmos" -> "高清臻音"
        "atmos_plus" -> "全景声"
        "master" -> "超清母带"
        "nac" -> "NAC"
        "dtsx" -> "DTS:X"
        "dolby" -> "杜比全景声"
        else -> quality
    }
    LxPlatform.QQ_MUSIC -> when (quality) {
        "128k" -> "标准"
        "320k" -> "HQ 高品质"
        "flac" -> "SQ 无损"
        "flac24bit" -> "24bit 无损"
        "hires" -> "Hi-Res 无损"
        "atmos" -> "臻品音质"
        "atmos_plus" -> "臻品全景声"
        "master" -> "臻品母带"
        "nac" -> "NAC"
        "dtsx" -> "DTS:X"
        "dolby" -> "杜比全景声"
        else -> quality
    }
}

internal fun lxQualityLevel(platform: LxPlatform, quality: String) = when (platform) {
    LxPlatform.NETEASE -> when (quality) {
        "master" -> AudioQuality.JYMASTER.value
        "atmos" -> AudioQuality.JYEFFECT.value
        "atmos_plus" -> AudioQuality.VIVID.value
        "dolby" -> AudioQuality.DOLBY.value
        "hires" -> AudioQuality.HIRES.value
        "flac24bit" -> "flac24bit"
        "flac" -> AudioQuality.LOSSLESS.value
        "320k" -> AudioQuality.EXHIGH.value
        else -> AudioQuality.STANDARD.value
    }
    LxPlatform.QQ_MUSIC -> when (quality) {
        "master" -> AudioQuality.QQ_MASTER.value
        "atmos" -> AudioQuality.QQ_STUDIO.value
        "atmos_plus" -> AudioQuality.QQ_SURROUND.value
        "nac" -> AudioQuality.QQ_NAC.value
        "dtsx" -> AudioQuality.QQ_DTS_X.value
        "dolby" -> AudioQuality.DOLBY.value
        "hires" -> AudioQuality.HIRES.value
        "flac24bit" -> "flac24bit"
        "flac" -> AudioQuality.LOSSLESS.value
        "320k" -> AudioQuality.EXHIGH.value
        else -> AudioQuality.STANDARD.value
    }
}

internal fun lxQualityFormat(quality: String) = when (quality) {
    "128k", "320k" -> "mp3"
    "nac" -> "nac"
    "dtsx", "dolby" -> "mp4"
    else -> "flac"
}

private fun LxPlatform.declaredQualities(sources: List<LxCapability>): List<String> =
    sources.asSequence().filter { it.platform == key }
        .flatMap { it.qualities.asSequence() }
        .filter { lxCanonicalQuality(it) in lxQualities }
        .distinct()
        .toList()

private fun LxPlatform.qualities(sources: List<LxCapability>): List<String> {
    val declared = declaredQualities(sources).map(::lxCanonicalQuality).toSet()
    return lxQualities.filter { it in declared }
}

internal fun lxPlatformCapabilities(sources: List<LxCapability>, platform: LxPlatform): List<LxCapability> {
    val qualities = platform.qualities(sources)
    return if (qualities.isEmpty()) emptyList() else listOf(LxCapability(platform.key, qualities))
}

internal fun lxNeteaseCapabilities(sources: List<LxCapability>): List<LxCapability> =
    lxPlatformCapabilities(sources, LxPlatform.NETEASE)

internal fun lxNormalizeCapabilities(sources: List<LxCapability>): List<LxCapability> =
    LxPlatform.entries.mapNotNull { platform ->
        platform.declaredQualities(sources).takeIf { it.isNotEmpty() }?.let { LxCapability(platform.key, it) }
    }

@Serializable data class LxCapability(val platform: String, val qualities: List<String>)
@Serializable data class LxSourceInfo(val id: String, val name: String, val description: String = "",
    val version: String = "", val author: String = "", val homepage: String = "",
    val origin: String = "", val capabilities: List<LxCapability> = emptyList(), val capabilityVersion: Int = 0) {
    val summary get() = summary(LxPlatform.NETEASE)
    internal fun qualities(platform: LxPlatform): List<String> = platform.qualities(capabilities)
    internal fun summary(platform: LxPlatform) =
        (when {
            qualities(platform).isNotEmpty() -> platform.label
            capabilityVersion == 0 -> "待检测"
            else -> "未声明${platform.label}接口"
        }) +
        version.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
}
@Serializable internal data class LxStoredSource(val info: LxSourceInfo, val script: String,
    val performance: LxSourcePerformance = LxSourcePerformance(),
    val qqPerformance: LxSourcePerformance = LxSourcePerformance(),
    val capabilitiesChecked: Boolean = false) {
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
    val declared = LxPlatform.entries.mapNotNull { platform ->
        val source = sources[platform.key] as? JsonObject ?: return@mapNotNull null
        val actions = (source["actions"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        if ((source["type"] as? JsonPrimitive)?.contentOrNull != "music" || "musicUrl" !in actions) return@mapNotNull null
        val qualities = (source["qualitys"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        LxCapability(platform.key, qualities)
    }
    return lxNormalizeCapabilities(declared).also {
        if (it.isEmpty()) throw LxSourceException("此音源没有声明网易云或 QQ 音乐播放接口")
    }
}

internal data class LxTrackRequest(val platform: String, val quality: String, val data: JsonObject)
private val lxQqMid = Regex("[a-zA-Z0-9]{1,64}")

private data class LxSongTarget(val id: String, val platform: LxPlatform,
    val supported: List<String>, val declared: List<String>)

private fun lxSongTarget(song: Song, sources: List<LxCapability>): LxSongTarget {
    val platform = LxPlatform.from(song)
    val supported = lxPlatformCapabilities(sources, platform).firstOrNull()?.qualities
        ?: throw LxSourceException("此音源没有声明${platform.label}播放接口，请切换音源")
    val declared = platform.declaredQualities(sources)
    val id = when (platform) {
        LxPlatform.NETEASE -> song.externalIds.neteaseId?.takeIf { (it.toLongOrNull() ?: 0) > 0 }
            ?: song.id.takeIf { song.musicSource == MusicSource.NETEASE && it > 0 }?.toString()
        LxPlatform.QQ_MUSIC -> song.qqMusic?.mid?.takeIf(lxQqMid::matches)
            ?: song.externalIds.qqMusicId?.takeIf(lxQqMid::matches)
    } ?: throw LxSourceException("歌曲缺少有效${platform.label} ID")
    return LxSongTarget(id, platform, supported, declared)
}

private val lxQualityPreferences: Map<AudioQuality, List<String>> = mapOf(
    AudioQuality.STANDARD to listOf("128k"),
    AudioQuality.HIGHER to listOf("128k"),
    AudioQuality.EXHIGH to listOf("320k", "128k"),
    AudioQuality.LOSSLESS to listOf("flac", "flac24bit", "hires"),
    AudioQuality.HIRES to listOf("hires", "flac24bit", "flac"),
    AudioQuality.JYEFFECT to listOf("atmos", "hires", "flac24bit", "flac"),
    AudioQuality.VIVID to listOf("atmos_plus", "atmos", "hires", "flac24bit", "flac"),
    AudioQuality.DOLBY to listOf("dolby", "atmos_plus", "atmos", "hires", "flac24bit", "flac"),
    AudioQuality.SKY to listOf("dolby", "atmos_plus", "atmos", "hires", "flac24bit", "flac"),
    AudioQuality.JYMASTER to listOf("master", "atmos_plus", "hires", "flac24bit", "flac"),
    AudioQuality.QQ_MASTER to listOf("master", "atmos_plus", "hires", "flac24bit", "flac"),
    AudioQuality.QQ_STUDIO to listOf("atmos", "hires", "flac24bit", "flac"),
    AudioQuality.QQ_SURROUND to listOf("atmos_plus", "atmos", "hires", "flac24bit", "flac"),
    AudioQuality.QQ_DTS_X to listOf("dtsx", "master", "atmos_plus", "atmos", "hires", "flac24bit", "flac"),
    AudioQuality.QQ_NAC to listOf("nac", "master", "atmos_plus", "atmos", "hires", "flac24bit", "flac"),
    AudioQuality.AUTO to listOf("master", "atmos", "hires", "flac24bit", "flac", "atmos_plus", "dolby", "dtsx", "nac", "320k", "128k"),
)

private val lxLossyFallbacks = listOf("320k", "128k")

private fun lxSelectedQuality(quality: AudioQuality, supported: List<String>): String =
    (lxQualityPreferences[quality].orEmpty() + lxLossyFallbacks).firstOrNull { it in supported }
        ?: supported.first()

private fun lxTrackRequest(song: Song, target: LxSongTarget, selected: String): LxTrackRequest {
    val pluginQualities = target.declared.ifEmpty { target.supported }
    val pluginQuality = pluginQualities.firstOrNull { it == selected }
        ?: pluginQualities.firstOrNull { lxCanonicalQuality(it) == selected }
        ?: selected
    val types = JsonArray(pluginQualities.map { buildJsonObject { put("type", it) } })
    val typeMap = buildJsonObject { pluginQualities.forEach { put(it, buildJsonObject { put("type", it) }) } }
    val isQq = target.platform == LxPlatform.QQ_MUSIC
    val mediaMid = song.qqMusic?.mediaMid?.takeIf(lxQqMid::matches) ?: target.id
    val albumMid = song.qqMusic?.albumMid?.takeIf(lxQqMid::matches)
    val songId = song.qqMusic?.songId?.takeIf { it > 0 }
    return LxTrackRequest(target.platform.key, selected, buildJsonObject {
        put("source", target.platform.key); put("action", "musicUrl")
        put("info", buildJsonObject {
            put("type", pluginQuality)
            put("musicInfo", buildJsonObject {
                put("source", target.platform.key); put("songmid", target.id); put("name", song.name)
                put("singer", song.artists); put("albumName", song.album); put("img", song.cover)
                put("interval", "%02d:%02d".format(java.util.Locale.ROOT, song.durationMs / 60000, song.durationMs / 1000 % 60))
                put("_types", typeMap)
                put("types", types)
                if (isQq) {
                    put("strMediaMid", mediaMid)
                    songId?.let { put("songId", it) }
                    albumMid?.let {
                        put("albumId", it)
                        put("albumMid", it)
                    }
                }
                put("meta", buildJsonObject {
                    put("songId", target.id)
                    put("albumName", song.album)
                    if (isQq) {
                        put("strMediaMid", mediaMid)
                        put("qualitys", types)
                        put("_qualitys", typeMap)
                        put("picUrl", song.cover)
                        songId?.let { put("id", it) }
                        albumMid?.let {
                            put("albumId", it)
                            put("albumMid", it)
                        }
                    }
                })
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
    val requests = if (quality == AudioQuality.AUTO) {
        val fallback = lxQualityPreferences.getValue(AudioQuality.AUTO).filter { it in target.supported }
            .ifEmpty { target.supported }
        fallback.map { level -> lxTrackRequest(song, target, level) }
    } else listOf(lxTrackRequest(song, target, lxSelectedQuality(quality, target.supported)))
    for ((index, request) in requests.withIndex()) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        try { return request to resolve(request.data) }
        catch (error: LxSourceException) {
            if (!error.qualityUnavailable || index == requests.lastIndex) throw error
        }
    }
    error("No declared LX quality")
}
