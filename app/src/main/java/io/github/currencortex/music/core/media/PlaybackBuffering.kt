package io.github.currencortex.music.core.media

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

/** Audio needs a time budget even when a high bitrate reaches the byte target early. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun playbackLoadControl() = DefaultLoadControl.Builder()
    .setBufferDurationsMs(20_000, 60_000, 2_500, 5_000)
    .setTargetBufferBytes(32 * 1024 * 1024)
    .setPrioritizeTimeOverSizeThresholds(true).build()
