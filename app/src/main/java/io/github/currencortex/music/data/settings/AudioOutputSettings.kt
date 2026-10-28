package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

enum class AudioOutputMode(val label: String) {
    AUDIO_TRACK("AudioTrack"), OPENSL_ES("OpenSL ES"), AAUDIO("AAudio"), DIRECT("Direct (beta)"), OBOE("Oboe"), BIT_PERFECT("Bit-perfect");
    val rawPcm get() = this == BIT_PERFECT
    val bypassProcessing get() = this == DIRECT || rawPcm
}
data class AudioOutputSettings(val sampleRate: Int = 0, val deviceKey: String = "", val mode: AudioOutputMode = AudioOutputMode.AUDIO_TRACK) {
    companion object { val rates = listOf(0, 44100, 48000, 88200, 96000, 176400, 192000) }
}

class AudioOutputSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val rate = intPreferencesKey("audio.output.sampleRate")
    private val device = stringPreferencesKey("audio.output.device")
    private val mode = stringPreferencesKey("audio.output.mode")
    val status = MutableStateFlow("尚未创建音频输出")
    val state = store.data.map { AudioOutputSettings(it[rate]?.takeIf(AudioOutputSettings.rates::contains) ?: 0, it[device].orEmpty(),
        AudioOutputMode.entries.firstOrNull { value -> value.name == it[mode] } ?: AudioOutputMode.AUDIO_TRACK) }
        .stateIn(scope, SharingStarted.Eagerly, AudioOutputSettings())
    suspend fun setSampleRate(value: Int) {
        require(value in AudioOutputSettings.rates)
        store.edit { it[rate] = value }
    }
    suspend fun setDevice(value: String) { store.edit { it[device] = value } }
    suspend fun setMode(value: AudioOutputMode) { store.edit { it[mode] = value.name } }
}
