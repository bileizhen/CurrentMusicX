package io.github.currencortex.music.feature.library

import android.content.Context
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.*
import io.github.currencortex.music.data.song.Song
import io.github.currencortex.music.data.settings.RecordShelfPreviewMode
import kotlinx.coroutines.*

/** The audition never replaces the user's queue or seek position. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class RecordShelfPreview(context: Context, private val container: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = container.playerController
    private val owner = container.musicSession()
    private var pauseRevision: Long? = null
    private var resumeMain = false
    private var blocked = false
    private var active = true
    private var audioOwner: AudioRequest? = null
    private val player = ExoPlayer.Builder(context.applicationContext, DockRenderersFactory(context.applicationContext, DockAudioLevel(),
        outputSampleRate = { container.audioOutput.state.value.sampleRate },
        outputSettings = { container.audioOutput.state.value },
        outputStatus = { container.audioOutput.status.value = it }))
        .setMediaSourceFactory(DefaultMediaSourceFactory(container.audioCache.dataSourceFactory)).build().apply {
            volume = 0f
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), !container.musicSettings.state.value.allowConcurrentPlayback)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady && reason in setOf(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
                            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) resumeMain = false
                }
            })
        }
    private val engine = RecordPreviewController(scope, object : RecordPreviewAudio {
        override var gain: Float
            get() = player.volume
            set(value) { player.volume = if (container.audioOutput.state.value.mode ==
                io.github.currencortex.music.data.settings.AudioOutputMode.DIRECT && value > 0f) 1f else value }
        override val positionMs get() = player.currentPosition
        override val playing get() = player.isPlaying
        override val ended get() = player.playbackState == Player.STATE_ENDED
        override val failed get() = player.playerError != null
        override val durationMs get() = player.duration
        override val buffering get() = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady &&
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
        override suspend fun prepare(source: RecordPreviewSource) {
            player.setPreferredAudioDevice(context.getSystemService(android.media.AudioManager::class.java)
                .preferredOutput(container.audioOutput.state.value.deviceKey))
            val item = MediaItem.Builder().setUri(source.uri).setCustomCacheKey(source.cacheKey)
            if (!source.fullLength) item.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(source.clip.startMs).setEndPositionMs(source.clip.endMs).build())
            player.setMediaItem(item.build())
            player.prepare()
            while (player.playbackState != Player.STATE_READY) {
                player.playerError?.let { throw it }
                delay(16)
            }
        }
        override fun play() { player.play() }
        override fun stop() { player.stop(); player.clearMediaItems() }
        override fun release() { player.release() }
    }, resolve = { song ->
        check(container.audioOutput.state.value.mode != io.github.currencortex.music.data.settings.AudioOutputMode.BIT_PERFECT) {
            "Bit-perfect 使用独占 USB 输出，请使用主播放器播放歌曲"
        }
        coroutineScope {
            val request = container.audioRequest(song, container.musicSettings.state.value.quality)
            audioOwner = request
            val clip = async { container.musicRepository.previewClip(song) }
            val resolved = container.audioSources.resolve(request)
            val range = clip.await()
            check(request == container.audioRequest(song, request.quality))
            RecordPreviewSource(resolved.source.url, resolved.key, range)
        }
    }, beforePlay = {
        if (pauseRevision == null) {
            resumeMain = main.state.value.showPause
            if (resumeMain) main.pause()
            pauseRevision = main.commandRevision
            if (resumeMain) delay(240)
        }
    }, stillAllowed = {
        active && !blocked && owner == container.musicSession() && main.state.value.mode == PlayerMode.LOCAL &&
            (pauseRevision == null || pauseRevision == main.commandRevision) && audioOwner?.let { request ->
                request.song?.let { request == container.audioRequest(it, request.quality) } ?: true
            } != false
    })
    val state get() = engine.state
    val nextRequests get() = engine.nextRequests
    fun setMode(mode: RecordShelfPreviewMode) = engine.setMode(mode)
    fun setFullLength(value: Boolean) = engine.setFullLength(value)
    internal val auditionPlayer get() = player
    internal var released = false
        private set

    fun select(song: Song?) {
        if (song == null) blocked = false
        if (song != null && pauseRevision != null && pauseRevision != main.commandRevision) {
            pauseRevision = null; resumeMain = false
        }
        if (active && !blocked) engine.select(song?.takeUnless { it.video })
    }
    fun setActive(value: Boolean) {
        active = value
        if (!value) { resumeMain = false; engine.select(null) }
    }
    fun commit(play: () -> Unit) {
        blocked = true; resumeMain = false
        engine.finish { pauseRevision = null; play() }
    }
    fun close() {
        active = false
        engine.finish(release = true) {
            released = true
            if (resumeMain && pauseRevision == main.commandRevision && owner == container.musicSession() &&
                main.state.value.mode == PlayerMode.LOCAL && !main.state.value.showPause) main.toggle()
            scope.cancel()
        }
    }
}
