package io.github.currencortex.music.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences

internal object SettingsPreferences {
    private val THEME = stringPreferencesKey("theme_mode")
    private val LAUNCH_STYLE = stringPreferencesKey("launch_style")
    private val MONET = booleanPreferencesKey("monet")
    private val UI_SCALE = floatPreferencesKey("ui_scale")
    private val BLUR = booleanPreferencesKey("blur")
    private val FLOATING = booleanPreferencesKey("floating_bar")
    private val GLASS = booleanPreferencesKey("liquid_glass")
    private val PREDICTIVE = booleanPreferencesKey("predictive_back")
    private val DEPTH_RIBBON = booleanPreferencesKey("playlist_depth_ribbon")
    private val NAVIGATION_GLOW = booleanPreferencesKey("navigation_glow")
    private val REFRESH_RATE = stringPreferencesKey("refresh_rate")
    private val PLAYER_BACKGROUND = stringPreferencesKey("player_background")
    private val PLAYER_BACKGROUND_BLUR = floatPreferencesKey("player_background_blur")

    fun read(p: Preferences) = AppearanceSettings(
        launchStyle = runCatching { LaunchStyle.valueOf(p[LAUNCH_STYLE].orEmpty()) }.getOrDefault(LaunchStyle.CLASSIC),
        themeMode = runCatching { ThemeMode.valueOf(p[THEME] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
        monet = p[MONET] ?: false,
        uiScale = p[UI_SCALE] ?: 1f,
        blur = p[BLUR] ?: true,
        floatingBar = p[FLOATING] ?: true,
        liquidGlass = p[GLASS] ?: true,
        predictiveBack = p[PREDICTIVE] ?: true,
        navigationGlow = p[NAVIGATION_GLOW] ?: true,
        playlistDepthRibbon = p[DEPTH_RIBBON] ?: false,
        refreshRate = runCatching { RefreshRatePreference.valueOf(p[REFRESH_RATE] ?: RefreshRatePreference.SYSTEM.name) }.getOrDefault(RefreshRatePreference.SYSTEM),
        playerBackground = p[PLAYER_BACKGROUND].orEmpty(),
        playerBackgroundBlur = p[PLAYER_BACKGROUND_BLUR] ?: 35f,
    ).normalized()

    fun write(p: MutablePreferences, s: AppearanceSettings) {
        val v = s.normalized()
        p[LAUNCH_STYLE] = v.launchStyle.name
        p[THEME] = v.themeMode.name
        p[MONET] = v.monet
        p[UI_SCALE] = v.uiScale
        p[BLUR] = v.blur
        p[FLOATING] = v.floatingBar
        p[GLASS] = v.liquidGlass
        p[PREDICTIVE] = v.predictiveBack
        p[NAVIGATION_GLOW] = v.navigationGlow
        p[DEPTH_RIBBON] = v.playlistDepthRibbon
        p[REFRESH_RATE] = v.refreshRate.name
        p[PLAYER_BACKGROUND] = v.playerBackground
        p[PLAYER_BACKGROUND_BLUR] = v.playerBackgroundBlur
    }
}
