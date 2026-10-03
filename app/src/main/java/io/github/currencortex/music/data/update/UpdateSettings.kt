package io.github.currencortex.music.data.update

import io.github.currencortex.music.core.update.UpdateChannel

data class UpdateSettings(
    val autoCheckOnLaunch: Boolean = true,
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val ignoredVersions: Map<UpdateChannel, String> = emptyMap(),
)
