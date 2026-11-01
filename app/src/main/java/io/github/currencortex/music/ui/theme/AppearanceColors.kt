// SPDX-License-Identifier: GPL-3.0-only
// upstream lib/ui/color.dart @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7,
// mapped from Material color roles to Miuix's native color roles.
package io.github.currencortex.music.ui.theme

import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeColorStyle
import top.yukonga.miuix.kmp.theme.*
import kotlin.math.min

@Composable internal fun rememberAppearanceColors(settings: AppearanceSettings, dark: Boolean): Colors {
    val dynamic = settings.monet && Build.VERSION.SDK_INT >= 31
    val style = when (settings.colorStyle) {
        ThemeColorStyle.VIVID, ThemeColorStyle.TINTED -> ThemePaletteStyle.Content
        ThemeColorStyle.SOFT -> ThemePaletteStyle.TonalSpot
        ThemeColorStyle.MONOCHROME -> ThemePaletteStyle.Monochrome
    }
    val controller = remember(dark, dynamic, settings.primaryColor, style) {
        ThemeController(colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = if (dynamic) null else Color(settings.primaryColor), paletteStyle = style)
    }
    val base = controller.currentColors()
    return remember(base, settings.primaryColor, settings.colorStyle, settings.pureBlack, dynamic, dark) {
        val seed = Hct.fromInt(if (dynamic) base.primary.toArgb() else settings.primaryColor.toInt())
        val tinted = settings.colorStyle == ThemeColorStyle.TINTED
        val colorful = settings.colorStyle in listOf(ThemeColorStyle.VIVID, ThemeColorStyle.TINTED)
        fun tone(hue: Double, chroma: Double, value: Double) = Color(Hct.from(hue, chroma, value.coerceIn(0.0, 100.0)).toInt())
        fun neutral(value: Double, paper: Boolean = false) = tone(seed.hue,
            if (tinted) min(seed.chroma / 6, 10.0) else if (paper && !dark) 1.5 else 3.0, value)
        val accentTone = if (dark) (seed.tone + 3).coerceIn(50.0, 75.0) else seed.tone.coerceIn(35.0, 60.0)
        val primary = if (colorful) tone(seed.hue, seed.chroma, accentTone) else base.primary
        val onPrimary = if (colorful) { if (accentTone <= 60) Color.White else tone(seed.hue, seed.chroma, 10.0) } else base.onPrimary
        val shift = if (dark && settings.pureBlack) 6.0 else 0.0
        val page = neutral(if (dark) 6.0 - shift else 98.0, true)
        val card = neutral(if (dark) 15.0 - shift else 100.0)
        val block = neutral(if (dark) 21.0 - shift else 96.0)
        val inset = neutral(if (dark) 27.0 - shift else 94.0)
        val container = tone(seed.hue, min(seed.chroma, 48.0), if (dark) 30.0 else 90.0)
        base.copy(primary = primary, onPrimary = onPrimary, primaryVariant = primary,
            primaryContainer = primary, onPrimaryContainer = onPrimary,
            onPrimaryVariant = container, tertiaryContainer = container,
            onTertiaryContainer = tone(seed.hue, min(seed.chroma, 48.0), if (dark) 90.0 else 30.0),
            disabledPrimary = primary.copy(alpha = .35f), disabledPrimaryButton = primary.copy(alpha = .35f),
            disabledPrimarySlider = primary.copy(alpha = .35f), sliderKeyPointForeground = primary,
            background = page, surface = page, surfaceVariant = card, surfaceContainer = card,
            surfaceContainerHigh = block, surfaceContainerHighest = inset,
            secondary = block, secondaryVariant = inset, secondaryContainer = block,
            onBackground = if (dark) neutral(97.0) else base.onBackground,
            onSurface = if (dark) neutral(97.0) else base.onSurface,
            onSurfaceContainer = if (dark) neutral(97.0) else base.onSurfaceContainer)
    }
}
