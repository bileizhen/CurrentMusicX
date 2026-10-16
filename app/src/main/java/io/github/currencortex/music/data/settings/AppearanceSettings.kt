package io.github.currencortex.music.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class RefreshRatePreference(val hz: Int, val label: String) {
    SYSTEM(0, "跟随系统"), HZ60(60, "60 Hz"), HZ90(90, "90 Hz"), HZ120(120, "120 Hz")
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val monet: Boolean = false,
    val uiScale: Float = 1f,
    val blur: Boolean = true,
    val floatingBar: Boolean = true,
    val liquidGlass: Boolean = true,
    val predictiveBack: Boolean = true,
    val navigationGlow: Boolean = true,
    val playlistDepthRibbon: Boolean = false,
    val refreshRate: RefreshRatePreference = RefreshRatePreference.SYSTEM,
) {
    fun normalized() = copy(uiScale = if (uiScale.isFinite()) uiScale.coerceIn(MIN_SCALE, MAX_SCALE) else 1f)
    companion object { const val MIN_SCALE = 0.8f; const val MAX_SCALE = 1.2f }
}
