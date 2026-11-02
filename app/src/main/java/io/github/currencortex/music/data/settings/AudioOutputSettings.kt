package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

enum class AudioOutputMode(val label: String) {
    AUDIO_TRACK("AudioTrack"), OPENSL_ES("OpenSL ES"), AAUDIO("AAudio"), DIRECT("Direct (beta)"), OBOE("Oboe"), BIT_PERFECT("Bit-perfect"), NETEASE_USB("USB 独占（网易云）");
    val rawPcm get() = this == BIT_PERFECT || this == NETEASE_USB
    val bypassProcessing get() = this == DIRECT || rawPcm
}
enum class AudioBufferPolicy(val label: String) { BALANCED("均衡"), RESPONSIVE("快速响应"), RESILIENT("弱网优先") }

data class AudioOutputSettings(val sampleRate: Int = 0, val deviceKey: String = "", val mode: AudioOutputMode = AudioOutputMode.AUDIO_TRACK,
    val usbDeviceKey: String = "", val bufferPolicy: AudioBufferPolicy = AudioBufferPolicy.BALANCED) {
    companion object { val rates = listOf(0, 44100, 48000, 88200, 96000, 176400, 192000) }
}

class AudioOutputSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val rate = intPreferencesKey("audio.output.sampleRate")
    private val device = stringPreferencesKey("audio.output.device")
    private val mode = stringPreferencesKey("audio.output.mode")
    private val usbDevice = stringPreferencesKey("audio.output.usbHostDevice")
    private val buffer = stringPreferencesKey("audio.output.bufferPolicy")
    val status = MutableStateFlow("尚未创建音频输出")
    val state = store.data.map { AudioOutputSettings(it[rate]?.takeIf(AudioOutputSettings.rates::contains) ?: 0, it[device].orEmpty(),
        AudioOutputMode.entries.firstOrNull { value -> value.name == it[mode] } ?: AudioOutputMode.AUDIO_TRACK, it[usbDevice].orEmpty(),
        AudioBufferPolicy.entries.firstOrNull { value -> value.name == it[buffer] } ?: AudioBufferPolicy.BALANCED) }
        .stateIn(scope, SharingStarted.Eagerly, AudioOutputSettings())
    suspend fun setSampleRate(value: Int) {
        require(value in AudioOutputSettings.rates)
        store.edit { it[rate] = value }
    }
    suspend fun setDevice(value: String) { store.edit { it[device] = value } }
    suspend fun setUsbDevice(value: String) { store.edit { it[usbDevice] = value } }
    suspend fun setMode(value: AudioOutputMode) { store.edit { it[mode] = value.name } }
    suspend fun setBufferPolicy(value: AudioBufferPolicy) { store.edit { it[buffer] = value.name } }
}
