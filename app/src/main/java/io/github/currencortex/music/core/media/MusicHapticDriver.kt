package io.github.currencortex.music.core.media

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import io.github.currencortex.music.data.settings.AdaptiveAudioSettings
import io.github.currencortex.music.data.settings.MusicHapticMode
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class MusicHapticDriver(context: Context) : AutoCloseable {
    @Suppress("DEPRECATION") private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private var active = false
    private var previousBass = 0f
    private var nextPulseAt = 0L
    val supported = vibrator?.hasVibrator() == true
    val amplitudeControl = vibrator?.hasAmplitudeControl() == true
    fun play(frame: MusicHapticTimeline.Frame, settings: AdaptiveAudioSettings, volume: Float): Boolean {
        if (!supported || !settings.enabled || !settings.haptics || settings.hapticStrength == 0 || volume <= 0f) { stop(); return false }
        val level = if (settings.hapticMode == MusicHapticMode.BASS)
            (frame.bass - previousBass * .65f).coerceAtLeast(0f) else frame.full
        previousBass = frame.bass
        val amplitude = (sqrt((level - .008f).coerceIn(0f, 1f)) * 255 * settings.hapticStrength / 100f * volume.coerceIn(0f, 1f))
            .roundToInt().coerceIn(0, 255)
        if (amplitude < 4) { cancelActive(); return false }
        val now = android.os.SystemClock.elapsedRealtime()
        // Some OEMs round even tiny one-shots up to a fixed, strong pulse. Bound their
        // cadence as well as duration, so low strength cannot become continuous buzzing.
        if (!amplitudeControl && now < nextPulseAt) return false
        return try {
            val effect = if (amplitudeControl) VibrationEffect.createOneShot(40, amplitude)
                else VibrationEffect.createOneShot((4 + 20 * amplitude / 255.0).roundToInt().toLong(), VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator?.vibrate(effect, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            active = true
            if (!amplitudeControl) nextPulseAt = now + 80 + (255 - amplitude) * 2L
            true
        } catch (_: RuntimeException) { stop(); false }
    }
    fun stop() {
        cancelActive()
        previousBass = 0f; nextPulseAt = 0L
    }
    private fun cancelActive() {
        if (active) runCatching { vibrator?.cancel() }
        active = false
    }
    override fun close() = stop()
}
