package io.github.currencortex.music.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.longPreferencesKey

internal object SettingsPreferences {
    private val THEME = stringPreferencesKey("theme_mode")
    private val PURE_BLACK = booleanPreferencesKey("pure_black")
    private val PRIMARY_COLOR = longPreferencesKey("primary_color")
    private val PRIMARY_COLORS = stringPreferencesKey("primary_colors")
    private val COLOR_STYLE = stringPreferencesKey("theme_color_style")
    private val CUSTOM_TEXT_SCALE = booleanPreferencesKey("custom_text_scale")
    private val TEXT_SCALE = floatPreferencesKey("text_scale")
    private val TAB_ANIMATION = stringPreferencesKey("tab_animation")
    private val CUSTOM_FONT = stringPreferencesKey("custom_font")
    private val LAUNCH_STYLE = stringPreferencesKey("launch_style")
    private val MONET = booleanPreferencesKey("monet")
    private val UI_SCALE = floatPreferencesKey("ui_scale")
    private val BLUR = booleanPreferencesKey("blur")
    private val FLOATING = booleanPreferencesKey("floating_bar")
    private val GLASS = booleanPreferencesKey("liquid_glass")
    private val PREDICTIVE = booleanPreferencesKey("predictive_back")
    private val DEPTH_RIBBON = booleanPreferencesKey("playlist_depth_ribbon")
    private val RECORD_SHELF = booleanPreferencesKey("playlist_record_shelf")
    private val RECORD_PREVIEW_MODE = stringPreferencesKey("record_shelf_preview_mode")
    private val RECORD_FULL_PREVIEW = booleanPreferencesKey("record_shelf_full_preview")
    private val NAVIGATION_GLOW = booleanPreferencesKey("navigation_glow")
    private val REFRESH_RATE = stringPreferencesKey("refresh_rate")
    private val PLAYER_BACKGROUND = stringPreferencesKey("player_background")
    private val PLAYER_BACKGROUND_BLUR = floatPreferencesKey("player_background_blur")
    private val PLAYER_DYNAMIC_BACKGROUND = booleanPreferencesKey("player_dynamic_background")
    private val PLAYER_COVER_STYLE = stringPreferencesKey("player_cover_style")

    fun read(p: Preferences) = AppearanceSettings(
        customFont = p[CUSTOM_FONT].orEmpty(),
        pureBlack = p[PURE_BLACK] ?: false,
        primaryColor = p[PRIMARY_COLOR] ?: DefaultThemeColors.first(),
        primaryColors = p[PRIMARY_COLORS]?.split(',')?.mapNotNull(String::toLongOrNull) ?: DefaultThemeColors,
        colorStyle = runCatching { ThemeColorStyle.valueOf(p[COLOR_STYLE].orEmpty()) }.getOrDefault(ThemeColorStyle.VIVID),
        customTextScale = p[CUSTOM_TEXT_SCALE] ?: false,
        textScale = p[TEXT_SCALE] ?: 1f,
        tabAnimation = runCatching { RootTabAnimation.valueOf(p[TAB_ANIMATION].orEmpty()) }.getOrDefault(RootTabAnimation.SLIDE),
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
        playlistRecordShelf = p[RECORD_SHELF] ?: false,
        recordShelfPreviewMode = runCatching { RecordShelfPreviewMode.valueOf(p[RECORD_PREVIEW_MODE].orEmpty()) }.getOrDefault(RecordShelfPreviewMode.ONCE),
        recordShelfFullPreview = p[RECORD_FULL_PREVIEW] ?: false,
        refreshRate = runCatching { RefreshRatePreference.valueOf(p[REFRESH_RATE] ?: RefreshRatePreference.SYSTEM.name) }.getOrDefault(RefreshRatePreference.SYSTEM),
        playerBackground = p[PLAYER_BACKGROUND].orEmpty(),
        playerBackgroundBlur = p[PLAYER_BACKGROUND_BLUR] ?: 35f,
        playerDynamicBackground = p[PLAYER_DYNAMIC_BACKGROUND] ?: true,
        playerCoverStyle = runCatching { PlayerCoverStyle.valueOf(p[PLAYER_COVER_STYLE].orEmpty()) }.getOrDefault(PlayerCoverStyle.CIRCLE),
    ).normalized()

    fun write(p: MutablePreferences, s: AppearanceSettings) {
        val v = s.normalized()
        p[CUSTOM_FONT] = v.customFont
        p[PURE_BLACK] = v.pureBlack
        p[PRIMARY_COLOR] = v.primaryColor
        p[PRIMARY_COLORS] = v.primaryColors.joinToString(",")
        p[COLOR_STYLE] = v.colorStyle.name
        p[CUSTOM_TEXT_SCALE] = v.customTextScale
        p[TEXT_SCALE] = v.textScale
        p[TAB_ANIMATION] = v.tabAnimation.name
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
        p[RECORD_SHELF] = v.playlistRecordShelf
        p[RECORD_PREVIEW_MODE] = v.recordShelfPreviewMode.name
        p[RECORD_FULL_PREVIEW] = v.recordShelfFullPreview
        p[REFRESH_RATE] = v.refreshRate.name
        p[PLAYER_BACKGROUND] = v.playerBackground
        p[PLAYER_BACKGROUND_BLUR] = v.playerBackgroundBlur
        p[PLAYER_DYNAMIC_BACKGROUND] = v.playerDynamicBackground
        p[PLAYER_COVER_STYLE] = v.playerCoverStyle.name
    }
}
