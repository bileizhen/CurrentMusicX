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
    fun setFont(context: android.content.Context, name: String, onFailure: () -> Unit = {}) {
        viewModelScope.launch {
            val old = try {
                val old = repository.snapshot().customFont
                repository.edit { it.copy(customFont = name) }
                old
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                io.github.currencortex.music.ui.theme.deleteCustomFont(context, name); onFailure(); return@launch
            }
            if (old != name) runCatching { io.github.currencortex.music.ui.theme.deleteCustomFont(context, old) }
        }
    }
}
