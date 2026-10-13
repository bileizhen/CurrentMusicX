package io.github.currencortex.music.core.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.currencortex.music.data.settings.MusicTransitionMode
import io.github.currencortex.music.data.song.Song
import kotlinx.coroutines.*

internal data class TransitionOffer(val from: Song, val next: Song, val request: AudioRequest)

/** Main-thread owner. Only decoded numeric features leave each player's audio thread. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackTransitions(initial: ExoPlayer, initialProbe: TrackEnergyProbe,
    private val scope: CoroutineScope,
    private val createPlayer: (TrackEnergyProbe) -> ExoPlayer,
    private val offer: () -> TransitionOffer?,
    private val resolve: suspend (TransitionOffer) -> MediaItem?,
    private val promote: (Song, ExoPlayer, ExoPlayer) -> Unit,
    private val discard: (ExoPlayer) -> Unit = {},
    private val currentSong: () -> Song? = { offer()?.from },
) : AutoCloseable {
    var player: ExoPlayer = initial
        private set
    var probe: TrackEnergyProbe = initialProbe
        private set
    private data class Staged(val offer: TransitionOffer, val player: ExoPlayer, val probe: TrackEnergyProbe,
        var ready: Boolean = false, var intro: MusicEnergyProfile? = null)
    private data class Fade(val staged: Staged, val outgoing: ExoPlayer, val plan: MusicTransitionPlan,
        val baseVolume: Float, var elapsed: Long = 0, var lastPosition: Long, var promoted: Boolean = false,
        var waitingMs: Long = 0)
    private var staged: Staged? = null
    private var preparing: Job? = null
    private var fade: Fade? = null
    private var failed: TransitionOffer? = null
    private var lastMode = MusicTransitionMode.OFF
    private var lastSeconds = 5
    val active get() = fade != null

    fun tick(mode: MusicTransitionMode, seconds: Int) {
        if (mode != lastMode || seconds != lastSeconds) {
            cancel(); failed = null; probe.reset(); lastMode = mode; lastSeconds = seconds
        }
        probe.enabled = mode == MusicTransitionMode.SMART
        if (mode == MusicTransitionMode.OFF) return
        val currentOffer = offer()
        val running = fade
        if (running != null) {
            // The business queue changes exactly once, at the equal-power midpoint.
            val valid = if (running.promoted) currentSong()?.let { it.id == running.staged.offer.next.id &&
                it.musicSource == running.staged.offer.next.musicSource } == true else currentOffer == running.staged.offer
            if (!valid) { cancel(); return }
            if (!player.isPlaying) {
                if (running.outgoing !== player) running.outgoing.pause()
                if (running.staged.player !== player) running.staged.player.pause()
                running.lastPosition = player.currentPosition
                return
            }
            if (running.outgoing !== player && !running.outgoing.playWhenReady) running.outgoing.play()
            if (running.staged.player !== player && !running.staged.player.playWhenReady) running.staged.player.play()
            // A stalled incoming stream must not fade the audible outgoing track to silence.
            if (!running.staged.player.isPlaying) {
                running.waitingMs += (player.currentPosition - running.lastPosition).coerceAtLeast(0)
                running.lastPosition = player.currentPosition
                if (running.waitingMs > 500) { failed = running.staged.offer; cancel() }
                return
            }
            running.waitingMs = 0
            val now = player.currentPosition
            val delta = now - running.lastPosition
            if (delta < 0 || delta > 1000) { cancel(); return }
            running.elapsed += delta; running.lastPosition = now
            val progress = (running.elapsed.toFloat() / running.plan.durationMs).coerceIn(0f, 1f)
            val gains = musicTransitionGains(progress, running.plan.incomingGain)
            running.outgoing.volume = gains.first * running.baseVolume
            running.staged.player.volume = gains.second * running.baseVolume
            if (progress >= .5f && !running.promoted) handoff(running)
            if (progress >= 1f || running.outgoing.playbackState == Player.STATE_ENDED) finish(running)
            return
        }
        if (currentOffer == null) { cancel(); return }
        if (staged?.offer != currentOffer) cancel()
        if (!player.isPlaying) {
            if (preparing?.isActive == true) cancel()
            return
        }
        val remaining = player.duration - player.currentPosition
        if (player.duration <= 2000 || remaining <= 250 || remaining > maxOf(15000L, seconds * 1000L + 5000)) return
        if (staged == null && failed != currentOffer) stage(currentOffer, mode)
        val next = staged?.takeIf { it.ready && it.player.playbackState == Player.STATE_READY && it.player.duration > 2000 } ?: return
        val requested = if (mode == MusicTransitionMode.SMART) smartMusicTransition(probe.profile(), next.intro)
            else MusicTransitionPlan(seconds * 1000L)
        val duration = minOf(requested.durationMs, player.duration / 3, next.player.duration / 3).coerceAtLeast(250)
        if (remaining > duration + 60) return
        val plan = requested.copy(durationMs = minOf(duration, remaining - 40).coerceAtLeast(200))
        val newFade = Fade(next, player, plan, player.volume, lastPosition = player.currentPosition)
        fade = newFade
        next.player.volume = 0f
        next.player.play()
    }

    private fun stage(current: TransitionOffer, mode: MusicTransitionMode) {
        val nextProbe = TrackEnergyProbe().apply { enabled = mode == MusicTransitionMode.SMART }
        val next = Staged(current, createPlayer(nextProbe), nextProbe)
        next.player.playbackParameters = player.playbackParameters
        staged = next
        preparing = scope.launch {
            try {
                val media = resolve(current) ?: run { failed = current; this@PlaybackTransitions.cancel(); return@launch }
                ensureActive()
                if (offer() != current || staged !== next) return@launch
                next.player.volume = 0f
                next.player.setMediaItem(media); next.player.prepare()
                withTimeout(8000) { while (next.player.playbackState != Player.STATE_READY) {
                    next.player.playerError?.let { throw it }; delay(25)
                } }
                if (mode == MusicTransitionMode.SMART && next.player.duration > 3000) {
                    // Preview only the first two seconds, muted and without an audio-focus request.
                    next.player.play()
                    withTimeout(5000) { while (next.player.currentPosition < 2000 && next.player.playbackState != Player.STATE_ENDED) delay(25) }
                    next.intro = nextProbe.profile()
                    next.player.pause()
                    val start = next.intro?.leadingSilenceMs?.coerceIn(0, 1500) ?: 0
                    next.player.seekTo(start)
                    withTimeout(4000) { while (next.player.playbackState != Player.STATE_READY) delay(25) }
                }
                if (offer() == current && staged === next) next.ready = true
            } catch (_: TimeoutCancellationException) { failed = current; if (staged === next) this@PlaybackTransitions.cancel() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = current; if (staged === next) this@PlaybackTransitions.cancel() }
        }
    }

    private fun handoff(running: Fade) {
        if (running.promoted) return
        running.promoted = true
        val old = player
        player = running.staged.player; probe = running.staged.probe
        running.lastPosition = player.currentPosition
        promote(running.staged.offer.next, old, player)
    }
    private fun finish(running: Fade) {
        handoff(running)
        // Remove ownership before release: player listeners may be invoked synchronously.
        fade = null; staged = null; preparing?.cancel(); preparing = null
        player.volume = running.baseVolume
        dispose(running.outgoing)
    }
    /** The service suppresses ordinary end-of-track advancement while an overlap owns the boundary. */
    fun ended(source: ExoPlayer): Boolean {
        val running = fade ?: return false
        if (source === running.outgoing) { finish(running); return true }
        return false
    }
    fun failed(source: ExoPlayer): Boolean {
        if (source === player) { cancel(); return false }
        if (source === staged?.player) { failed = staged?.offer; cancel(); return true }
        return true
    }
    fun pauseCompanion() {
        val running = fade ?: return
        if (running.outgoing !== player) running.outgoing.pause()
        if (running.staged.player !== player) running.staged.player.pause()
    }
    fun cancel() {
        val running = fade
        val next = staged
        fade = null; staged = null
        preparing?.cancel(); preparing = null
        if (running != null) {
            player.volume = running.baseVolume
            dispose(if (running.promoted) running.outgoing else running.staged.player)
        } else if (next != null) dispose(next.player)
    }
    private fun dispose(value: ExoPlayer) { discard(value); value.release() }
    override fun close() { cancel() }
}
