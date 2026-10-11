package io.github.currencortex.music.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlin.math.ln
import kotlin.math.roundToInt

enum class EqualizerPreset(val label: String, val gains: List<Int>) {
    FLAT("平直", listOf(0, 0, 0, 0, 0)),
    BASS("低音", listOf(600, 400, 0, -100, -200)),
    VOCAL("人声", listOf(-200, -100, 300, 400, 100)),
    ROCK("摇滚", listOf(500, 200, -200, 300, 500)),
    POP("流行", listOf(-100, 200, 400, 200, -100)),
    JAZZ("爵士", listOf(300, 100, -100, 200, 400)),
    CUSTOM("自定义", emptyList());
}

data class EqualizerSettings(val enabled: Boolean = false, val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val gains: List<Int> = EqualizerPreset.FLAT.gains) {
    fun normalized() = copy(gains = FREQUENCIES.indices.map { index ->
        ((gains.getOrElse(index) { 0 }.coerceIn(-1200, 1200) / 100.0).roundToInt() * 100)
    })
    fun withBand(index: Int, gain: Int): EqualizerSettings {
        require(index in FREQUENCIES.indices)
        return normalized().copy(preset = EqualizerPreset.CUSTOM,
            gains = normalized().gains.toMutableList().also { it[index] = gain }).normalized()
    }
    /** Device band layouts vary; interpolate our stored curve on a logarithmic frequency axis. */
    fun gainAt(frequencyHz: Float): Int {
        val values = normalized().gains
        if (!frequencyHz.isFinite() || frequencyHz <= FREQUENCIES.first()) return values.first()
        if (frequencyHz >= FREQUENCIES.last()) return values.last()
        val upper = FREQUENCIES.indexOfFirst { it >= frequencyHz }
        val lower = upper - 1
        val fraction = ln(frequencyHz / FREQUENCIES[lower]) / ln(FREQUENCIES[upper].toFloat() / FREQUENCIES[lower])
        return (values[lower] + (values[upper] - values[lower]) * fraction).roundToInt()
    }
    companion object { val FREQUENCIES = listOf(60, 230, 910, 3600, 14000) }
}

class EqualizerSettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val enabled = booleanPreferencesKey("equalizer.enabled")
    private val preset = stringPreferencesKey("equalizer.preset")
    private val bands = EqualizerSettings.FREQUENCIES.map { intPreferencesKey("equalizer.band.$it") }
    private fun decode(p: Preferences) = EqualizerSettings(p[enabled] ?: false,
        EqualizerPreset.entries.firstOrNull { it.name == p[preset] } ?: EqualizerPreset.FLAT,
        bands.map { p[it] ?: 0 }).normalized()
    val state = store.data.map(::decode).stateIn(scope, SharingStarted.Eagerly, EqualizerSettings())
    suspend fun snapshot() = decode(store.data.first())
    private suspend fun edit(transform: (EqualizerSettings) -> EqualizerSettings) {
        store.edit { p ->
            val next = transform(decode(p)).normalized()
            p[enabled] = next.enabled; p[preset] = next.preset.name
            bands.forEachIndexed { index, key -> p[key] = next.gains[index] }
        }
    }
    suspend fun setEnabled(value: Boolean) = edit { it.copy(enabled = value) }
    suspend fun setBand(index: Int, gain: Int) = edit { it.withBand(index, gain) }
    suspend fun select(value: EqualizerPreset) {
        require(value != EqualizerPreset.CUSTOM)
        edit { it.copy(preset = value, gains = value.gains) }
    }
}
