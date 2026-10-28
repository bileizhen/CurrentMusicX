package io.github.currencortex.music.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

val LocalDarkTheme = staticCompositionLocalOf { false }
private val clearClickIndication = MiuixIndication(color = Color.Transparent)
@Composable fun isInDarkTheme() = LocalDarkTheme.current

@Composable
fun LeiTheme(settings: AppearanceSettings, darkSystemBars: Boolean? = null, darkStatusBars: Boolean? = null, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val monet = settings.monet && Build.VERSION.SDK_INT >= 31
    val mode = when (settings.themeMode) {
        ThemeMode.SYSTEM -> if (monet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        ThemeMode.LIGHT -> if (monet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        ThemeMode.DARK -> if (monet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
    }
    val controller = remember(mode, dark) {
        ThemeController(
            colorSchemeMode = mode,
            isDark = dark,
            lightColors = lightColorScheme(
                background = Color(0xFFF5F6F8),
                surface = Color(0xFFF5F6F8),
                surfaceContainer = Color.White,
            ),
            darkColors = darkColorScheme(
                background = Color(0xFF111214),
                surface = Color(0xFF111214),
                surfaceContainer = Color(0xFF1D1F22),
            ),
        )
    }
    val context = LocalContext.current
    val darkBars = darkSystemBars ?: dark
    LaunchedEffect(darkBars, darkStatusBars) {
        (context as? ComponentActivity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !(darkStatusBars ?: darkBars)
                isAppearanceLightNavigationBars = !darkBars
            }
        }
    }
    val density = LocalDensity.current
    val typeface = rememberCustomTypeface(settings.customFont)
    val font = remember(typeface) { typeface?.let { androidx.compose.ui.text.font.FontFamily(it) } }
    val baseStyles = MiuixTheme.textStyles
    val fontStyles = remember(baseStyles, font) {
        if (font == null) baseStyles else baseStyles.copy(
            main = baseStyles.main.copy(fontFamily = font), paragraph = baseStyles.paragraph.copy(fontFamily = font),
            body1 = baseStyles.body1.copy(fontFamily = font), body2 = baseStyles.body2.copy(fontFamily = font),
            button = baseStyles.button.copy(fontFamily = font), footnote1 = baseStyles.footnote1.copy(fontFamily = font),
            footnote2 = baseStyles.footnote2.copy(fontFamily = font), headline1 = baseStyles.headline1.copy(fontFamily = font),
            headline2 = baseStyles.headline2.copy(fontFamily = font), subtitle = baseStyles.subtitle.copy(fontFamily = font),
            title1 = baseStyles.title1.copy(fontFamily = font), title2 = baseStyles.title2.copy(fontFamily = font),
            title3 = baseStyles.title3.copy(fontFamily = font), title4 = baseStyles.title4.copy(fontFamily = font))
    }
    val scaled = remember(density.density, density.fontScale, settings.uiScale) {
        Density(density.density * settings.uiScale, density.fontScale)
    }
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalDensity provides scaled, LocalCustomFont provides font, LocalCustomTypeface provides typeface) {
        MiuixTheme(controller = controller, textStyles = fontStyles) {
            // Override here because MiuixTheme installs its own default press overlay.
            CompositionLocalProvider(LocalIndication provides clearClickIndication, content = content)
        }
    }
}
