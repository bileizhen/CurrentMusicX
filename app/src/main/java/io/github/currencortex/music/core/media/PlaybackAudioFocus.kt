package io.github.currencortex.music.core.media

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Apply the saved policy before preparing media, then change it on the live player. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackAudioFocus(private var player: ExoPlayer, allowConcurrent: Flow<Boolean>, scope: CoroutineScope) {
    private val initialized = CompletableDeferred<Unit>()
    val ready: Deferred<Unit> get() = initialized
    private var allow: Boolean? = null
    private val attributes = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()
    // An idle player must not take focus using a default while preferences are loading.
    init {
        player.setAudioAttributes(attributes, false)
        scope.launch {
            allowConcurrent.distinctUntilChanged().collect { value ->
                allow = value
                player.setAudioAttributes(attributes, !value)
                initialized.complete(Unit)
            }
        }.invokeOnCompletion { failure -> if (failure != null) initialized.completeExceptionally(failure) }
    }
    fun replace(value: ExoPlayer) {
        if (value === player) return
        player.setAudioAttributes(attributes, false)
        player = value
        player.setAudioAttributes(attributes, allow == false)
    }
}
