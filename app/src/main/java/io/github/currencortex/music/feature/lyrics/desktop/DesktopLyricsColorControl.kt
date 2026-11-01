package io.github.currencortex.music.feature.lyrics.desktop

import androidx.compose.runtime.Composable
import io.github.currencortex.music.ui.component.nativeui.SettingsColorPreference

private val desktopPalette = listOf("白色" to 0xFFFFFFFFL, "淡灰" to DESKTOP_DEFAULT_GLOW_COLOR, "冰蓝" to 0xFF80DEFFL, "薄荷" to 0xFF9DE6BAL,
    "暖金" to 0xFFFFD58AL, "樱粉" to 0xFFFFAACCL, "淡紫" to 0xFFC5B5FFL, "黑色" to 0xFF20232AL)

@Composable internal fun DesktopLyricsColorControl(title: String, value: Long, tag: String,
    defaultColor: Long = 0xFFFFFFFFL, save: (Long) -> Unit) {
    SettingsColorPreference(title, value, defaultColor, desktopPalette, tag, save)
}
