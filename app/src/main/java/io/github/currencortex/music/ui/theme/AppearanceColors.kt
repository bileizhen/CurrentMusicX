// SPDX-License-Identifier: GPL-3.0-only
// upstream lib/ui/color.dart @ 7fd417fb9a9a277c6a9339a8ac87f907df7c25f7,
// mapped from Material color roles to COUI 17 color roles.
package io.github.currencortex.music.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeColorStyle
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.ThemePaletteStyle
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import kotlin.math.min

@Composable internal fun rememberAppearanceColors(settings: AppearanceSettings, dark: Boolean): Colors {
    val dynamic = settings.monet && Build.VERSION.SDK_INT >= 31
    val style = when (settings.colorStyle) {
        ThemeColorStyle.VIVID, ThemeColorStyle.TINTED -> ThemePaletteStyle.Content
        ThemeColorStyle.SOFT -> ThemePaletteStyle.TonalSpot
        ThemeColorStyle.MONOCHROME -> ThemePaletteStyle.Monochrome
    }
    val controller = remember(dark, dynamic, settings.primaryColor, style) {
        ThemeController(
            colorSchemeMode = when {
                dynamic && dark -> ColorSchemeMode.MonetDark
                dynamic -> ColorSchemeMode.MonetLight
                dark -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.Light
            },
            lightColors = lightColorScheme(),
            darkColors = darkColorScheme(),
            keyColor = if (dynamic) null else Color(settings.primaryColor),
            paletteStyle = style,
        )
    }
    val base = controller.currentColors()
    return remember(base, settings.primaryColor, settings.colorStyle, settings.pureBlack, dynamic, dark) {
        val seed = Hct.fromInt(if (dynamic) base.primary.toArgb() else settings.primaryColor.toInt())
        val colorful = settings.colorStyle in listOf(ThemeColorStyle.VIVID, ThemeColorStyle.TINTED)
        fun tone(hue: Double, chroma: Double, value: Double) =
            Color(Hct.from(hue, chroma, value.coerceIn(0.0, 100.0)).toInt())
        val accentTone = if (dark) (seed.tone + 3).coerceIn(50.0, 75.0) else seed.tone.coerceIn(35.0, 60.0)
        val primary = if (colorful) tone(seed.hue, seed.chroma, accentTone) else base.primary
        val onPrimary = if (colorful) {
            if (accentTone <= 60) Color.White else tone(seed.hue, seed.chroma, 10.0)
        } else base.onPrimary
        val page = when {
            dark && settings.pureBlack -> Color.Black
            dark -> Color(0xFF0F1011)
            else -> Color(0xFFF2F5F7)
        }
        val card = if (dark) Color(0xFF1B1C1D) else Color.White
        val block = if (dark) Color(0xFF242526) else Color(0xFFF7F9FA)
        val inset = if (dark) Color(0xFF2B2C2D) else Color(0xFFE8EDF0)
        val container = tone(seed.hue, min(seed.chroma, 48.0), if (dark) 30.0 else 90.0)
        val label = if (dark) Color(0xFFF5F5F5) else Color(0xFF191B1C)
        val tint = container.copy(alpha = if (dark) .25f else .15f)
        base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryText = tone(seed.hue, min(seed.chroma, 48.0), if (dark) 90.0 else 30.0),
            labelTheme = primary,
            link = primary,
            disable = primary.copy(alpha = .35f),
            containerTheme = container,
            containerThemeHalftone = tint,
            textColorHighlight = tint,
            secondary = tint,
            secondaryVariant = tint,
            secondaryContainer = container.copy(alpha = if (dark) .30f else .20f),
            disabledSliderThumb = primary.copy(alpha = .35f),
            labelOnColor = onPrimary,
            sliderBackground = block,
            background = page,
            backgroundElevated = page,
            backgroundElevatedWithCard = page,
            backgroundWithCard = page,
            surface = card,
            surfaceTop = block,
            surfaceWithCard = card,
            bar = card,
            bottomBar = card,
            card = card,
            cardBackground = card,
            cardPressed = block,
            divider = if (dark) Color(0x1FFFFFFF) else Color(0x14000000),
            outline = if (dark) Color(0x33FFFFFF) else Color(0x1F000000),
            surfaceContainerHighest = inset,
            labelPrimary = label,
            labelPrimaryVariant = label.copy(alpha = base.labelPrimaryVariant.alpha),
            labelSecondary = label.copy(alpha = base.labelSecondary.alpha),
            labelSecondaryVariant = label.copy(alpha = base.labelSecondaryVariant.alpha),
            labelTertiary = label.copy(alpha = base.labelTertiary.alpha),
            labelQuaternary = label.copy(alpha = base.labelQuaternary.alpha),
        )
    }
}
