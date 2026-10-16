package io.github.currencortex.music.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }
/** Device-reported rates are not limited to a fixed list of 60/90/120 Hz. */
data class RefreshRatePreference(val hz: Int) {
    init { require(hz in 0..1000) }
    val label: String get() = if (hz == 0) "跟随系统" else "$hz Hz"
    // Preserve the existing preference payload, including SYSTEM/HZ60/HZ90/HZ120.
    val name: String get() = if (hz == 0) "SYSTEM" else "HZ$hz"
    companion object {
        val SYSTEM = RefreshRatePreference(0)
        val HZ60 = RefreshRatePreference(60)
        val HZ90 = RefreshRatePreference(90)
        val HZ120 = RefreshRatePreference(120)
        fun valueOf(name: String): RefreshRatePreference {
            if (name == "SYSTEM") return SYSTEM
            require(name.startsWith("HZ"))
            val hz = name.removePrefix("HZ").toInt()
            require(hz in 1..1000)
            return RefreshRatePreference(hz)
        }
    }
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
