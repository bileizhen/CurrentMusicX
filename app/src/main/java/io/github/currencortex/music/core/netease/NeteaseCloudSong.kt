package io.github.currencortex.music.core.netease

import io.github.currencortex.music.core.network.ApiException
import io.github.currencortex.music.core.network.ErrorKind
import kotlinx.serialization.json.*

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull ?: 0L
private fun JsonObject.objectOrEmpty(key: String) = get(key) as? JsonObject ?: buildJsonObject {}

private fun names(raw: JsonObject, vararg keys: String) = JsonArray(keys.flatMap { key ->
    when (val value = raw[key]) {
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(value.contentOrNull)
        else -> emptyList()
    }
}.map(String::trim).filter(String::isNotBlank).distinct().map(::JsonPrimitive))

/** Convert a raw SimpleSong to the normalized shape used by the rest of the app. */
private fun normalizedSong(raw: JsonObject): JsonObject = buildJsonObject {
    val artists = ((raw["ar"] ?: raw["artists"]) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val album = (raw["al"] ?: raw["album"]) as? JsonObject ?: buildJsonObject {}
    put("ncm_id", raw.long("id")); put("name", raw.text("name"))
    put("artists", artists.joinToString(" / ") { it.text("name") })
    put("artist_ids", JsonArray(artists.mapNotNull { it["id"] }))
    put("album", album.text("name")); put("album_id", album.long("id"))
    put("pic", album.text("picUrl").ifBlank { raw.text("picUrl") }.replace("http:", "https:"))
    put("duration", raw["dt"] ?: raw["duration"] ?: JsonPrimitive(0))
    put("mv", raw["mv"] ?: raw["mvid"] ?: JsonPrimitive(0))
    put("tns", names(raw, "tns", "transNames"))
    put("alia", names(raw, "alia", "alias"))
}

/** Cloud entries carry both a playable ID and optional SimpleSong metadata. */
internal fun mapCloudSong(entry: JsonElement): JsonObject? {
    val cloud = entry as? JsonObject ?: return null
    val info = cloud.objectOrEmpty("simpleSong").takeIf { it.isNotEmpty() } ?: cloud
    val songId = info.long("id").takeIf { it > 0 } ?: cloud.long("songId")
    if (songId <= 0) return null
    val normalized = normalizedSong(info)
    return JsonObject(normalized + buildJsonObject {
        put("ncm_id", songId)
        put("name", normalized.text("name").ifBlank { cloud.text("songName") })
        put("artists", normalized.text("artists").ifBlank { cloud.text("artist") })
        put("album", normalized.text("album").ifBlank { cloud.text("album") })
        // size/fileSize are bytes, not playback duration.
        put("duration", normalized.long("duration"))
        put("cloud", true)
    })
}

internal fun mapCloudPage(raw: JsonObject, offset: Int, limit: Int): JsonObject {
    val rows = raw["data"] as? JsonArray ?: throw ApiException(ErrorKind.Parse)
    val songs = rows.mapNotNull(::mapCloudSong)
    if (rows.isNotEmpty() && songs.isEmpty()) throw ApiException(ErrorKind.Parse)
    val count = (raw["count"] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 }
    val more = rows.isNotEmpty() && ((raw["hasMore"] as? JsonPrimitive)?.booleanOrNull
        ?: count?.let { offset + rows.size < it } ?: (rows.size >= limit))
    return buildJsonObject {
        put("code", 200)
        put("data", JsonArray(songs))
        put("count", maxOf(count ?: 0, offset + rows.size))
        put("nextOffset", offset + rows.size)
        put("hasMore", more)
    }
}
