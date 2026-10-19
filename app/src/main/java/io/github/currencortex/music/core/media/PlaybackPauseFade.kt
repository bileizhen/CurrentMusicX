// SPDX-License-Identifier: AGPL-3.0-only
// Native transport adapter inspired by Folia playbackFade.ts / usePlaybackTransportController.ts,
// commit 26c57f9b. Copyright Folia contributors. See THIRD_PARTY_NOTICES.md.
package io.github.currencortex.music.core.media

import kotlinx.coroutines.*

/** Main-thread transport envelope. The gain multiplies, rather than replaces, deck/user volume. */
internal class PlaybackPauseFade(private val scope: CoroutineScope,
    private val applyGain: (Float) -> Unit, private val pausePlayer: () -> Unit,
    private val changed: () -> Unit = {}, private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    var pausedIntent = false
        private set
    var gain = 1f
        private set
    private var settled = true
    private var revision = 0L
    private var ramp: Job? = null
    val fadingOut get() = pausedIntent && !settled

    fun pause(audible: Boolean) {
        if (pausedIntent) return
        pausedIntent = true; settled = false; changed()
        if (!audible) { finishPause(); return }
        animate(0f) { delay(15); finishPause() }
    }

    fun resume() {
        if (!pausedIntent) return
        pausedIntent = false; settled = true; changed()
        animate(1f)
    }

    /** End-of-track, focus loss or a seek cannot let an old pause start another song. */
    fun finishPause() {
        if (!fadingOut) return
        revision++; ramp?.cancel(); ramp = null
        settled = true
        setGain(0f)
        pausePlayer()
        changed()
        // Keep the paused pipeline muted; only an explicit resume/new source restores its gain.
    }

    /** Call after stopping the old source, before loading a different source or releasing players. */
    fun reset() {
        revision++; ramp?.cancel(); ramp = null
        pausedIntent = false; settled = true
        setGain(1f); changed()
    }

    private fun setGain(value: Float) { gain = value; applyGain(value) }
    private fun animate(target: Float, complete: suspend () -> Unit = {}) {
        val token = ++revision
        ramp?.cancel()
        val from = gain
        val start = nowMs()
        ramp = scope.launch {
            while (token == revision) {
                val progress = ((nowMs() - start).toFloat() / DURATION_MS).coerceIn(0f, 1f)
                setGain(from + (target - from) * progress)
                if (progress >= 1f) break
                delay(minOf(16L, (DURATION_MS - (nowMs() - start)).coerceAtLeast(1)))
            }
            if (token == revision) complete()
        }
    }

    companion object { const val DURATION_MS = 200L }
}
