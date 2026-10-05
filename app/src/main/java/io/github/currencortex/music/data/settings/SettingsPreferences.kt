package io.github.currencortex.music.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences

internal object SettingsPreferences {
    private val THEME = stringPreferencesKey("theme_mode")
    private val MONET = booleanPreferencesKey("monet")
    private val UI_SCALE = floatPreferencesKey("ui_scale")
    private val BLUR = booleanPreferencesKey("blur")
    private val FLOATING = booleanPreferencesKey("floating_bar")
    private val GLASS = booleanPreferencesKey("liquid_glass")
    private val PREDICTIVE = booleanPreferencesKey("predictive_back")

    fun read(p: Preferences) = AppearanceSettings(
        themeMode = runCatching { ThemeMode.valueOf(p[THEME] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
        monet = p[MONET] ?: false,
        uiScale = p[UI_SCALE] ?: 1f,
        blur = p[BLUR] ?: true,
        floatingBar = p[FLOATING] ?: true,
        liquidGlass = p[GLASS] ?: true,
        predictiveBack = p[PREDICTIVE] ?: true,
    ).normalized()

    fun write(p: MutablePreferences, s: AppearanceSettings) {
        val v = s.normalized()
        p[THEME] = v.themeMode.name
        p[MONET] = v.monet
        p[UI_SCALE] = v.uiScale
        p[BLUR] = v.blur
        p[FLOATING] = v.floatingBar
        p[GLASS] = v.liquidGlass
        p[PREDICTIVE] = v.predictiveBack
    }
}
