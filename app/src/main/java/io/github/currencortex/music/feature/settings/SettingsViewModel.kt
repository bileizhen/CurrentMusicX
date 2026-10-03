package io.github.currencortex.music.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.data.settings.AppearanceSettings
import io.github.currencortex.music.data.settings.SettingsRepository
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val settings = repository.state
    fun edit(change: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch { repository.edit(change) }
    }
}
