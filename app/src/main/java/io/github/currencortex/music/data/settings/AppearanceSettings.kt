package io.github.currencortex.music.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val monet: Boolean = false,
    val uiScale: Float = 1f,
    val blur: Boolean = true,
    val floatingBar: Boolean = true,
    val liquidGlass: Boolean = true,
    val predictiveBack: Boolean = true,
) {
    fun normalized() = copy(uiScale = if (uiScale.isFinite()) uiScale.coerceIn(MIN_SCALE, MAX_SCALE) else 1f)
    companion object { const val MIN_SCALE = 0.8f; const val MAX_SCALE = 1.2f }
}
