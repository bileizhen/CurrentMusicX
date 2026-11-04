package io.github.currencortex.music.data.song

import kotlinx.serialization.json.*

data class SongPreviewClip(val startMs: Long, val endMs: Long, val chorus: Boolean) {
    val durationMs get() = endMs - startMs
}

internal fun songPreviewClip(segments: List<ChorusSegment>, durationMs: Long): SongPreviewClip {
    val limit = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
    segments.sortedBy { it.startMs }.forEach { segment ->
        if (segment.startMs >= 0 && segment.endMs > segment.startMs && segment.startMs < limit) {
            // Subtract first so malformed extreme timestamps cannot overflow start + 20 seconds.
            val duration = minOf(segment.endMs - segment.startMs, limit - segment.startMs, 20_000L)
            if (duration >= 1000) return SongPreviewClip(segment.startMs, segment.startMs + duration, true)
        }
    }
    return songPreviewClip(null, 0, durationMs)
}

/** Chorus timestamps are milliseconds. Never substitute a different song's range. */
internal fun songPreviewClip(raw: JsonObject?, id: Long, durationMs: Long): SongPreviewClip {
    val entries = (raw?.get("chorus") as? JsonArray) ?: (raw?.get("data") as? JsonArray)
    val item = entries?.filterIsInstance<JsonObject>()?.firstOrNull { it["id"]?.jsonPrimitive?.longOrNull == id }
    val start = item?.get("startTime")?.jsonPrimitive?.longOrNull
    val end = item?.get("endTime")?.jsonPrimitive?.longOrNull
    val limit = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
    if (start != null && end != null && start >= 0 && end > start && start < limit) {
        val stop = minOf(end, limit, start + 20_000)
        if (stop - start >= 1000) return SongPreviewClip(start, stop, true)
    }
    // An unannotated track gets an explicitly labelled sample, not a fabricated chorus.
    val fallback = if (durationMs > 0) minOf((durationMs * .35).toLong(), (durationMs - 15_000).coerceAtLeast(0)) else 0L
    return SongPreviewClip(fallback, minOf(fallback + 20_000, limit), false)
}
