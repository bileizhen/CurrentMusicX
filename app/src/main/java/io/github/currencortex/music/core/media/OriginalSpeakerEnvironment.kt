package io.github.currencortex.music.core.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

data class OriginalSpeakerEnvironment(val speaker: Boolean, val landscape: Boolean, val volume: Float)

/** Observes media routing/orientation/volume without changing output devices or device volume. */
class OriginalSpeakerEnvironmentObserver(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    val state: Flow<OriginalSpeakerEnvironment> = callbackFlow {
        fun update() { trySend(snapshot()) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { update() }
        }
        val devices = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { update() }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { update() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction("android.media.STREAM_DEVICES_CHANGED_ACTION")
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        audio?.registerAudioDeviceCallback(devices, android.os.Handler(android.os.Looper.getMainLooper()))
        update()
        awaitClose {
            runCatching { app.unregisterReceiver(receiver) }
            audio?.unregisterAudioDeviceCallback(devices)
        }
    }.distinctUntilChanged()

    fun snapshot(): OriginalSpeakerEnvironment {
        val routed = if (Build.VERSION.SDK_INT >= 33 && audio != null) runCatching {
            audio.getAudioDevicesForAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).map { it.type }.takeIf { it.isNotEmpty() }
        }.getOrNull() else null
        val connected = runCatching { audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.map { it.type }.orEmpty() }
            .getOrDefault(emptyList())
        val volume = runCatching {
            val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
            if (max > 0) (audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / max else .5f
        }.getOrDefault(.5f)
        return OriginalSpeakerEnvironment(originalSpeakerRoute(routed, connected),
            app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE, volume)
    }
}

@android.annotation.SuppressLint("InlinedApi")
internal fun originalSpeakerRoute(routed: List<Int>?, connected: List<Int>): Boolean {
    val speakers = setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE)
    // A mixed policy route is not exclusively external-speaker playback.
    if (!routed.isNullOrEmpty()) return routed.all { it in speakers }
    val builtIn = speakers + AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
    return connected.any { it in speakers } && connected.none { it !in builtIn }
}
