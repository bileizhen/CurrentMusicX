package io.github.currencortex.music.feature.lyrics.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class DesktopEffect(val label: String) { PRECISE("精致"), STAGE("舞台") }
enum class DesktopAppearance(val label: String) { CAPTION("透明字幕"), CAPSULE("半透明胶囊") }
internal const val DESKTOP_DEFAULT_GLOW_COLOR: Long = 0xFFE0E0E0L
internal const val DESKTOP_DEFAULT_FONT_SIZE: Float = 20f
data class DesktopLyricsSettings(val enabled: Boolean = false, val locked: Boolean = false,
    val effect: DesktopEffect = DesktopEffect.PRECISE, val appearance: DesktopAppearance = DesktopAppearance.CAPSULE,
    val translation: Boolean = true, val fontSize: Float = DESKTOP_DEFAULT_FONT_SIZE, val opacity: Float = .85f,
    val offsetMs: Long = 0, val x: Float = .5f, val y: Float = .2f, val snapEdges: Boolean = false,
    val glowEnabled: Boolean = true, val glowStrength: Float = 1f, val glowSpread: Float = 1f,
    val textColor: Long = 0xFFFFFFFFL, val glowColor: Long = DESKTOP_DEFAULT_GLOW_COLOR) {
    fun normalized() = copy(fontSize = fontSize.finite(DESKTOP_DEFAULT_FONT_SIZE).coerceIn(18f, 38f),
        opacity = opacity.finite(.85f).coerceIn(.35f, .95f), offsetMs = offsetMs.coerceIn(-60000, 60000),
        x = x.finite(.5f).coerceIn(0f, 1f), y = y.finite(.2f).coerceIn(0f, 1f),
        glowStrength = glowStrength.finite(1f).coerceIn(0f, 2f), glowSpread = glowSpread.finite(1f).coerceIn(.5f, 2f),
        textColor = desktopOpaqueColor(textColor), glowColor = desktopOpaqueColor(glowColor))
}
internal fun desktopOpaqueColor(value: Long): Long = 0xFF000000L or (value and 0xFFFFFFL)
internal fun desktopColorHex(value: Long): String = String.format(java.util.Locale.ROOT, "%06X", value and 0xFFFFFFL)
internal fun desktopParseColor(value: String): Long? = value.trim().removePrefix("#")
    .takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLongOrNull(16)?.let(::desktopOpaqueColor)
private fun Float.finite(fallback: Float) = takeIf(Float::isFinite) ?: fallback

/** Separate namespace; editing desktop effects cannot change application lyric preferences. */
class DesktopLyricsPreferences(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val payload = stringPreferencesKey("desktopLyrics.preferences.v1")
    val ready = CompletableDeferred<Unit>()
    private val mutable = MutableStateFlow(DesktopLyricsSettings())
    val state = mutable.asStateFlow()
    init { scope.launch { store.data.collect { mutable.value = decode(it[payload]); ready.complete(Unit) } } }
    suspend fun edit(change: (DesktopLyricsSettings) -> DesktopLyricsSettings) {
        store.edit { preferences -> preferences[payload] = encode(change(decode(preferences[payload])).normalized()) }
    }
    private fun encode(s: DesktopLyricsSettings) = listOf(s.enabled, s.locked, s.effect.name, s.appearance.name,
        s.translation, s.fontSize, s.opacity, s.offsetMs, s.x, s.y, s.snapEdges,
        s.glowEnabled, s.glowStrength, s.glowSpread, s.textColor, s.glowColor).joinToString("|")
    private fun decode(raw: String?): DesktopLyricsSettings {
        val fields = raw?.split('|').orEmpty()
        return DesktopLyricsSettings(fields.getOrNull(0)?.toBooleanStrictOrNull() ?: false,
            fields.getOrNull(1)?.toBooleanStrictOrNull() ?: false,
            DesktopEffect.entries.firstOrNull { it.name == fields.getOrNull(2) } ?: DesktopEffect.PRECISE,
            DesktopAppearance.entries.firstOrNull { it.name == fields.getOrNull(3) } ?: DesktopAppearance.CAPSULE,
            fields.getOrNull(4)?.toBooleanStrictOrNull() ?: true, fields.getOrNull(5)?.toFloatOrNull() ?: DESKTOP_DEFAULT_FONT_SIZE,
            fields.getOrNull(6)?.toFloatOrNull() ?: .85f, fields.getOrNull(7)?.toLongOrNull() ?: 0,
            fields.getOrNull(8)?.toFloatOrNull() ?: .5f, fields.getOrNull(9)?.toFloatOrNull() ?: .2f,
            fields.getOrNull(10)?.toBooleanStrictOrNull() ?: false,
            fields.getOrNull(11)?.toBooleanStrictOrNull() ?: true,
            fields.getOrNull(12)?.toFloatOrNull() ?: 1f, fields.getOrNull(13)?.toFloatOrNull() ?: 1f,
            fields.getOrNull(14)?.toLongOrNull() ?: 0xFFFFFFFFL, fields.getOrNull(15)?.toLongOrNull() ?: DESKTOP_DEFAULT_GLOW_COLOR).normalized()
    }
}

data class DesktopWindowStatus(val attached: Boolean = false, val visible: Boolean = false,
    val error: String? = null, val alpha: Float = 0f, val width: Int = 0, val height: Int = 0, val x: Int = 0, val y: Int = 0)
data class DesktopSafeArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = (right - left).coerceAtLeast(1)
    val height get() = (bottom - top).coerceAtLeast(1)
}
data class DesktopPlacement(val x: Int, val y: Int, val width: Int, val height: Int)
internal fun desktopPlacement(area: DesktopSafeArea, width: Int, height: Int, x: Float, y: Float): DesktopPlacement {
    val w = width.coerceIn(1, area.width); val h = height.coerceIn(1, area.height)
    return DesktopPlacement((area.left + area.width * x.finite(.5f) - w / 2).toInt().coerceIn(area.left, area.right - w),
        (area.top + area.height * y.finite(.2f) - h / 2).toInt().coerceIn(area.top, area.bottom - h), w, h)
}
internal fun desktopWindowAlpha(opacity: Float, locked: Boolean, maximum: Float): Float =
    opacity.finite(.85f).coerceIn(.35f, .95f).let { if (locked) minOf(it, (maximum - .01f).coerceIn(0f, 1f)) else it }
