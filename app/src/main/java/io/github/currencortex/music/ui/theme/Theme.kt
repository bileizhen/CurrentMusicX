package io.github.currencortex.music.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import io.github.currencortex.music.ui.component.nativeui.LocalLiquidGlassEnabled
import io.github.currencortex.music.ui.component.nativeui.LocalGlassBlurEnabled
import io.github.currencortex.music.ui.component.nativeui.ThemeCrossFade
import io.github.suqi8.coui.kmp.theme.COUITheme

val LocalDarkTheme = staticCompositionLocalOf { false }
@Composable fun isInDarkTheme() = LocalDarkTheme.current

@Composable
fun LeiTheme(settings: AppearanceSettings, darkSystemBars: Boolean? = null, darkStatusBars: Boolean? = null,
    enforceNavigationBarContrast: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = rememberAppearanceColors(settings, dark)
    val context = LocalContext.current
    val darkBars = darkSystemBars ?: dark
    LaunchedEffect(darkBars, darkStatusBars, enforceNavigationBarContrast) {
        (context as? ComponentActivity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !(darkStatusBars ?: darkBars)
                isAppearanceLightNavigationBars = !darkBars
            }
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = enforceNavigationBarContrast
            }
        }
    }
    val density = LocalDensity.current
    val typeface = rememberCustomTypeface(settings.customFont)
    val font = remember(typeface) { typeface?.let { androidx.compose.ui.text.font.FontFamily(it) } }
    val baseStyles = COUITheme.textStyles
    val fontStyles = remember(baseStyles, font) {
        if (font == null) baseStyles else baseStyles.copy(
            displayL = baseStyles.displayL.copy(fontFamily = font), display = baseStyles.display.copy(fontFamily = font),
            displayM = baseStyles.displayM.copy(fontFamily = font), displayS = baseStyles.displayS.copy(fontFamily = font),
            displayXS = baseStyles.displayXS.copy(fontFamily = font), headlineL = baseStyles.headlineL.copy(fontFamily = font),
            headlineM = baseStyles.headlineM.copy(fontFamily = font), headlineS = baseStyles.headlineS.copy(fontFamily = font),
            headlineXS = baseStyles.headlineXS.copy(fontFamily = font), bodyXL = baseStyles.bodyXL.copy(fontFamily = font),
            bodyL = baseStyles.bodyL.copy(fontFamily = font), bodyM = baseStyles.bodyM.copy(fontFamily = font),
            bodyS = baseStyles.bodyS.copy(fontFamily = font), bodyXS = baseStyles.bodyXS.copy(fontFamily = font),
            buttonL = baseStyles.buttonL.copy(fontFamily = font), buttonM = baseStyles.buttonM.copy(fontFamily = font),
            buttonS = baseStyles.buttonS.copy(fontFamily = font), description = baseStyles.description.copy(fontFamily = font),
            caption = baseStyles.caption.copy(fontFamily = font), tag = baseStyles.tag.copy(fontFamily = font))
    }
    val scaled = remember(density.density, density.fontScale, settings.uiScale, settings.customTextScale, settings.textScale) {
        Density(density.density * settings.uiScale, if (settings.customTextScale) settings.textScale else density.fontScale)
    }
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalDensity provides scaled, LocalCustomFont provides font, LocalCustomTypeface provides typeface,
        LocalLiquidGlassEnabled provides (settings.liquidGlass && Build.VERSION.SDK_INT >= 33),
        LocalGlassBlurEnabled provides settings.blur) {
        COUITheme(colors = colors, textStyles = fontStyles) {
            ThemeCrossFade(listOf(dark, settings.monet, settings.primaryColor, settings.colorStyle, settings.pureBlack)) {
                content()
            }
        }
    }
}
