package io.github.currencortex.music.data.update

import io.github.currencortex.music.core.update.UpdateChannel
import io.github.currencortex.music.core.update.UpdateSource

data class UpdateSettings(
    val autoCheckOnLaunch: Boolean = true,
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val ignoredVersions: Map<UpdateChannel, String> = emptyMap(),
    val downloadSource: UpdateSource = UpdateSource.default,
) {
    val effectiveDownloadSource: UpdateSource
        get() = downloadSource.takeIf { it in UpdateSource.forChannel(channel) } ?: UpdateSource.GITHUB
}
