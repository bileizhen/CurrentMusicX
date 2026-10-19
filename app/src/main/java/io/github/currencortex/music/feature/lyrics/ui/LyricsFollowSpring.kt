package io.github.currencortex.music.feature.lyrics.ui

import kotlin.math.exp

internal data class LyricsFollowStep(val distance: Float, val velocity: Float)

/** A critically damped follow spring, integrated analytically so frame rate cannot destabilize it. */
internal fun lyricsFollowStep(remaining: Float, velocity: Float, seconds: Float): LyricsFollowStep {
    val dt = seconds.coerceIn(0f, .064f)
    val frequency = 16f
    val error = -remaining
    val impulse = velocity + frequency * error
    val decay = exp(-frequency * dt)
    val nextError = (error + impulse * dt) * decay
    return LyricsFollowStep(nextError - error, (velocity - frequency * impulse * dt) * decay)
}
