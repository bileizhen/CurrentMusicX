package io.github.currencortex.music.core.media

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import kotlin.math.pow

/** USB Host bypasses Android's mixer, so apply its media volume before submitting PCM. */
internal class UsbMediaVolume(private val readGain: () -> Float, private val nowMs: () -> Long) {
    constructor(context: Context) : this(mediaVolumeReader(context), SystemClock::elapsedRealtime)
    private var sampledAt = Long.MIN_VALUE
    private var gain = 0f

    // Read on the writer thread, including before its first buffer. Polling also works
    // in the background and on ROMs that don't send the hidden volume-change broadcast.
    fun gain(): Float {
        val now = nowMs()
        if (sampledAt != Long.MIN_VALUE && now - sampledAt < 50) return gain
        sampledAt = now
        gain = runCatching { usbVolumeGain(readGain()) }.getOrDefault(0f)
        return gain
    }
}

private fun mediaVolumeReader(context: Context): () -> Float {
    val audio = context.getSystemService(AudioManager::class.java)
    var previous: String? = null
    return read@{
        val manager = audio ?: return@read 0f
        val index = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val muted = manager.isStreamMute(AudioManager.STREAM_MUSIC)
        val decibels = if (Build.VERSION.SDK_INT >= 28) runCatching {
            manager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, AudioDeviceInfo.TYPE_USB_HEADSET)
        }.getOrNull() else null
        val gain = usbMediaVolumeGain(index, maximum, muted, decibels)
        val state = "index=$index max=$maximum muted=$muted curveDb=$decibels gain=$gain"
        if (state != previous) {
            previous = state
            CurrentMusicAudioDiagnostics.record("usbHost.mediaVolume $state")
        }
        gain
    } // An unreadable volume is muted by the caller, never replaced by full scale.
}

internal fun usbMediaVolumeGain(index: Int, maximum: Int, muted: Boolean, decibels: Float?): Float {
    if (muted || index <= 0 || maximum <= 0) return 0f
    if (decibels == Float.NEGATIVE_INFINITY) return 0f
    if (decibels != null && decibels.isFinite() && decibels <= 0f)
        return 10.0.pow(decibels / 20.0).toFloat().coerceIn(0f, 1f)
    // Android 8 and unavailable OEM curves: a perceptual curve, never amplification.
    val fraction = (index.toFloat() / maximum).coerceIn(0f, 1f)
    return fraction * fraction * fraction
}

internal fun usbVolumeGain(value: Float) = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
