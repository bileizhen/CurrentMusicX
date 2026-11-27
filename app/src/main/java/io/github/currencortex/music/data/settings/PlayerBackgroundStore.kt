package io.github.currencortex.music.data.settings

import android.content.Context

class PlayerBackgroundStore(
    context: Context,
    settings: SettingsRepository,
    suffix: String = "",
) : CustomBackgroundStore(
    context = context,
    settings = settings,
    directoryName = "player-background",
    readName = AppearanceSettings::playerBackground,
    writeName = { s, name -> s.copy(playerBackground = name) },
    suffix = suffix,
)
