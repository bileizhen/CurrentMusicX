package io.github.currencortex.music.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.storage.StorageUsage
import io.github.currencortex.music.data.settings.AudioCacheLimit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageState(val usage: StorageUsage = StorageUsage(), val busy: Boolean = false, val message: String? = null)

class StorageViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(StorageState())
    val audioBytes = container.audioCache.bytes
    val audioTracks = container.audioCache.entries
    val cacheLimit = container.storageSettings.state

    fun refresh() = viewModelScope.launch {
        val usage = container.storage.measure()
        state.update { it.copy(usage = usage) }
    }

    fun clearData() = action("已清理数据缓存") { container.storage.clearDataCache() }

    fun clearAudio() = action("已清理音乐缓存") { container.storage.clearAudioCache() }

    fun setCacheLimit(value: AudioCacheLimit) = action("缓存上限已设为 ${value.label}") {
        container.storageSettings.set(value)
        container.audioCache.setMaxBytes(value.effectiveBytes)
    }

    private fun action(done: String, block: suspend () -> Unit) = viewModelScope.launch {
        if (state.value.busy) return@launch
        state.update { it.copy(busy = true, message = null) }
        val failure = runCatching { block() }.exceptionOrNull()
        val usage = runCatching { container.storage.measure() }.getOrNull()
        state.update {
            it.copy(busy = false, message = failure?.message ?: done, usage = usage ?: it.usage)
        }
    }
}
