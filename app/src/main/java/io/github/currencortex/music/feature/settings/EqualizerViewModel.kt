package io.github.currencortex.music.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.data.settings.EqualizerPreset
import io.github.currencortex.music.data.settings.EqualizerSettings
import kotlinx.coroutines.launch

class EqualizerViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.equalizerSettings.state
    val runtime = container.equalizer.state
    val playback = container.playerController.state
    val soundPreferences = container.soundEffectsSettings.state
    private var editedManually = false
    private fun manual(desired: EqualizerSettings, action: (suspend () -> Unit)? = null) {
        container.originalAiMasterEffects.disable()
        container.originalSpeakerEffects.disable()
        container.originalSmartEffects.disable()
        container.originalMoodEffects.disable()
        container.originalSoundEffects.clear()
        editedManually = true
        container.smartTuning.previewManual(desired)
        container.equalizer.configure(desired)
        if (container.soundEffectsSettings.state.value.smartEnabled || action != null) viewModelScope.launch {
            if (container.soundEffectsSettings.state.value.smartEnabled)
                container.soundEffectsSettings.setSmartEnabled(false)
            action?.invoke()
        }
    }
    fun enabled(value: Boolean) {
        manual(settings.value.copy(enabled = value)) {
            container.equalizerSettings.setEnabled(value)
        }
    }
    fun preset(value: EqualizerPreset) {
        manual(settings.value.copy(preset = value, gains = value.gains)) {
            container.equalizerSettings.select(value)
        }
    }
    fun previewBand(index: Int, gain: Int) {
        manual(settings.value.withBand(index, gain))
    }
    fun saveBand(index: Int, gain: Int) {
        manual(settings.value.withBand(index, gain)) {
            container.equalizerSettings.setBand(index, gain)
        }
    }
    override fun onCleared() {
        // A cancelled drag/navigation must not leave an unsaved audio curve running.
        container.smartTuning.clearManualPreview()
        val restored = if (editedManually) settings.value else container.smartTuning.effective.value
        container.equalizer.configure(if (container.originalProcessingPreset.value == null && !container.originalSpeakerEffects.state.value.requestedEnabled) restored else restored.copy(enabled = false))
    }
}
