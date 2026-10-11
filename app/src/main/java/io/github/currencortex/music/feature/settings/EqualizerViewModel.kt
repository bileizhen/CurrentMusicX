package io.github.currencortex.music.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.settings.EqualizerPreset
import kotlinx.coroutines.launch

class EqualizerViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.equalizerSettings.state
    val runtime = container.equalizer.state
    val playback = container.playerController.state
    fun enabled(value: Boolean) {
        container.equalizer.configure(settings.value.copy(enabled = value))
        viewModelScope.launch { container.equalizerSettings.setEnabled(value) }
    }
    fun preset(value: EqualizerPreset) {
        container.equalizer.configure(settings.value.copy(preset = value, gains = value.gains))
        viewModelScope.launch { container.equalizerSettings.select(value) }
    }
    fun previewBand(index: Int, gain: Int) { container.equalizer.configure(settings.value.withBand(index, gain)) }
    fun saveBand(index: Int, gain: Int) {
        previewBand(index, gain)
        viewModelScope.launch { container.equalizerSettings.setBand(index, gain) }
    }
    override fun onCleared() {
        // A cancelled drag/navigation must not leave an unsaved audio curve running.
        container.equalizer.configure(settings.value)
    }
}
