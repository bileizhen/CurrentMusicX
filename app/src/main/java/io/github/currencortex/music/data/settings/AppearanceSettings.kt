package io.github.currencortex.music.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class RecordShelfPreviewMode { ONCE, LOOP, NEXT, RANDOM }
enum class LaunchStyle(val label: String, val description: String) {
    CLASSIC("经典", "原有文字与头像开屏动画"),
    HALO("光环", "光环展开、星芒与蓝色粒子，展示当前用户名"),
    NONE("无开屏动画", "启动后直接进入应用"),
}
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
    val playlistRecordShelf: Boolean = false,
    val recordShelfPreviewMode: RecordShelfPreviewMode = RecordShelfPreviewMode.ONCE,
    val recordShelfFullPreview: Boolean = false,
    val refreshRate: RefreshRatePreference = RefreshRatePreference.SYSTEM,
    val playerBackground: String = "",
    val playerBackgroundBlur: Float = 35f,
    val launchStyle: LaunchStyle = LaunchStyle.CLASSIC,
    val customFont: String = "",
) {
    fun normalized() = copy(uiScale = if (uiScale.isFinite()) uiScale.coerceIn(MIN_SCALE, MAX_SCALE) else 1f,
        playerBackgroundBlur = if (playerBackgroundBlur.isFinite()) playerBackgroundBlur.coerceIn(0f, 100f) else 35f)
    companion object { const val MIN_SCALE = 0.8f; const val MAX_SCALE = 1.2f }
}
