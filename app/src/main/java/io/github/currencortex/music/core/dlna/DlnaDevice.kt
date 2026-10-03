package io.github.currencortex.music.core.dlna

data class DlnaService(val type: String, val controlUrl: String)
data class DlnaDevice(val id: String, val name: String, val location: String, val transport: DlnaService,
    val rendering: DlnaService? = null)
data class DlnaPosition(val positionMs: Long, val durationMs: Long, val uri: String = "")
fun dlnaTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
}
fun dlnaMillis(text: String): Long {
    val parts = text.split(':'); if (parts.size != 3) return 0
    return (((parts[0].toLongOrNull() ?: 0) * 3600 + (parts[1].toLongOrNull() ?: 0) * 60) * 1000 +
        ((parts[2].toDoubleOrNull() ?: 0.0) * 1000).toLong()).coerceAtLeast(0)
}
