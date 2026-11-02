package io.github.currencortex.music.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class RootTabAnimation { SLIDE, FADE }
enum class PlayerCoverStyle(val label: String) { CIRCLE("圆形封面"), IMMERSIVE("沉浸式封面") }
enum class ThemeColorStyle(val label: String) { VIVID("鲜明"), TINTED("染色"), SOFT("柔和"), MONOCHROME("单色") }
val DefaultThemeColors = listOf(0xFF007AFF, 0xFFCB30E0, 0xFFFF2D55, 0xFFFF383C, 0xFFFF8D28, 0xFFFFCC00, 0xFF34C759, 0xFF8E8E93)
enum class RecordShelfPreviewMode { ONCE, LOOP, NEXT, RANDOM }
enum class LaunchStyle(val label: String, val description: String) {
    CLASSIC("经典", "原有文字与头像开屏动画"),
    HALO("光环", "光环展开、星芒与蓝色粒子，展示当前用户名"),
    NONE("无开屏动画", "启动后直接进入应用"),
    SIGNATURE("签名", "当前用户名连笔写出，柔光定格后进入应用"),
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
    val playerDynamicBackground: Boolean = true,
    val pureBlack: Boolean = false,
    val primaryColor: Long = DefaultThemeColors.first(),
    val primaryColors: List<Long> = DefaultThemeColors,
    val colorStyle: ThemeColorStyle = ThemeColorStyle.VIVID,
    val customTextScale: Boolean = false,
    val textScale: Float = 1f,
    val tabAnimation: RootTabAnimation = RootTabAnimation.SLIDE,
    val playerCoverStyle: PlayerCoverStyle = PlayerCoverStyle.CIRCLE,
) {
    fun normalized() = copy(floatingBar = true,
        uiScale = if (uiScale.isFinite()) uiScale.coerceIn(MIN_SCALE, MAX_SCALE) else 1f,
        playerBackgroundBlur = if (playerBackgroundBlur.isFinite()) playerBackgroundBlur.coerceIn(0f, 100f) else 35f,
        textScale = if (textScale.isFinite()) textScale.coerceIn(.8f, 1.4f) else 1f,
        primaryColor = (primaryColor and 0xFFFFFF) or 0xFF000000,
        primaryColors = primaryColors.map { (it and 0xFFFFFF) or 0xFF000000 }.distinct().take(32).ifEmpty { DefaultThemeColors })
    companion object { const val MIN_SCALE = 0.8f; const val MAX_SCALE = 1.2f }
}
