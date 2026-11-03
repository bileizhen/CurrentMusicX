package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

enum class DynamicEqCurve(val label: String, val tilt: List<Float>) {
    WARM("温暖", listOf(2f, 2f, 1.5f, 1f, .5f, 0f, -.5f, -1f, -1.5f, -2f)),
    V_SHAPE("低高增强", listOf(2f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 2f)),
    VOCAL("人声", listOf(-1f, -1f, 0f, .5f, 1f, 2f, 2f, 1f, 0f, -1f)),
    BRIGHT("明亮", listOf(-2f, -1.5f, -1f, -.5f, 0f, .5f, 1f, 1.5f, 2f, 2f))
}
enum class MusicHapticMode(val label: String) { BASS("低频悦动"), FULL("全频能量") }

data class AdaptiveAudioSettings(
    val enabled: Boolean = false,
    val gains: List<Float> = List(10) { 0f },
    val dynamicEq: Boolean = true,
    val curve: DynamicEqCurve = DynamicEqCurve.V_SHAPE,
    val strength: Int = 1,
    val speed: Int = 1,
    val virtualBass: Boolean = false,
    val bassStrength: Int = 50,
    val haptics: Boolean = false,
    val hapticStrength: Int = 50,
    val hapticMode: MusicHapticMode = MusicHapticMode.BASS,
) {
    fun normalized() = copy(gains = FREQUENCIES.indices.map { gains.getOrElse(it) { 0f }.let { v ->
        if (v.isFinite()) v.coerceIn(-6f, 6f) else 0f
    } }, strength = strength.coerceIn(0, 4), speed = speed.coerceIn(0, 4),
        bassStrength = bassStrength.coerceIn(0, 100), hapticStrength = hapticStrength.coerceIn(0, 100))
    companion object {
        val FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val SPEEDS = listOf(.5f, 1f, 1.5f, 2f, 3f)
    }
}

class AdaptiveAudioSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val enabled = booleanPreferencesKey("adaptive_audio.enabled")
    private val dynamic = booleanPreferencesKey("adaptive_audio.dynamic")
    private val curve = stringPreferencesKey("adaptive_audio.curve")
    private val strength = intPreferencesKey("adaptive_audio.strength")
    private val speed = intPreferencesKey("adaptive_audio.speed")
    private val bass = booleanPreferencesKey("adaptive_audio.bass")
    private val bassStrength = intPreferencesKey("adaptive_audio.bass_strength")
    private val haptics = booleanPreferencesKey("adaptive_audio.haptics")
    private val hapticStrength = intPreferencesKey("adaptive_audio.haptic_strength")
    private val mode = stringPreferencesKey("adaptive_audio.haptic_mode")
    private val bands = AdaptiveAudioSettings.FREQUENCIES.map { floatPreferencesKey("adaptive_audio.band.$it") }
    private fun decode(p: Preferences) = AdaptiveAudioSettings(p[enabled] ?: false, bands.map { p[it] ?: 0f },
        p[dynamic] ?: true, DynamicEqCurve.entries.firstOrNull { it.name == p[curve] } ?: DynamicEqCurve.V_SHAPE,
        p[strength] ?: 1, p[speed] ?: 1, p[bass] ?: false, p[bassStrength] ?: 50,
        p[haptics] ?: false, p[hapticStrength] ?: 50,
        MusicHapticMode.entries.firstOrNull { it.name == p[mode] } ?: MusicHapticMode.BASS).normalized()
    val state = store.data.map(::decode).stateIn(scope, SharingStarted.Eagerly, AdaptiveAudioSettings())
    private class Preview(val transform: (AdaptiveAudioSettings) -> AdaptiveAudioSettings)
    private val preview = MutableStateFlow<Preview?>(null)
    /** Drag changes reach the audio thread without writing preferences for every pointer event. */
    val effective = combine(state, preview) { saved, pending ->
        pending?.transform?.invoke(saved)?.normalized() ?: saved
    }.stateIn(scope, SharingStarted.Eagerly, AdaptiveAudioSettings())

    fun preview(transform: (AdaptiveAudioSettings) -> AdaptiveAudioSettings) {
        preview.value = if (transform(state.value).normalized() == state.value) null else Preview(transform)
    }
    fun clearPreview() { preview.value = null }
    suspend fun commitPreview(transform: (AdaptiveAudioSettings) -> AdaptiveAudioSettings) {
        val pending = preview.value
        try {
            edit(transform)
            // Keep the audible preview until DataStore's state catches up. An older save must
            // never clear a new drag (or wait forever if a newer gesture superseded it).
            combine(state, preview) { saved, current ->
                current !== pending || transform(saved).normalized() == saved
            }.first { it }
        } finally {
            preview.compareAndSet(pending, null)
        }
    }
    suspend fun snapshot() = decode(store.data.first())
    suspend fun edit(transform: (AdaptiveAudioSettings) -> AdaptiveAudioSettings) {
        store.edit { p ->
            val s = transform(decode(p)).normalized()
            p[enabled] = s.enabled; p[dynamic] = s.dynamicEq; p[curve] = s.curve.name
            p[strength] = s.strength; p[speed] = s.speed; p[bass] = s.virtualBass; p[bassStrength] = s.bassStrength
            p[haptics] = s.haptics; p[hapticStrength] = s.hapticStrength; p[mode] = s.hapticMode.name
            bands.forEachIndexed { i, key -> p[key] = s.gains[i] }
        }
    }
}
