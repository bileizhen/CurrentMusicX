package io.github.currencortex.music.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.auth.UserDto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class NetworkSettingsState(val busy: Boolean = false, val message: String? = null)
class MusicSettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.musicSettings.state
    val state = MutableStateFlow(NetworkSettingsState())
    fun server(value: String) = viewModelScope.launch {
        if (state.value.busy) return@launch
        val normalized = try { ServerUrl.normalize(value) } catch (e: IllegalArgumentException) {
            state.value = NetworkSettingsState(message = e.message); return@launch
        }
        container.ready.await()
        state.value = NetworkSettingsState(busy = true)
        // Credentials are scoped to one server. Never send the previous server's token to a new host.
        container.playerController.clear()
        container.database.music().clearLyrics()
        container.authRepository.resetServer(normalized)
        container.musicSettings.setServer(normalized)
        container.accountRepository.server = normalized
        val validation = appResult { container.apiClient.get<UserDto>("auth/me", authenticated = true) }
        state.value = NetworkSettingsState(message = if (validation is AppResult.Failure && validation.kind != ErrorKind.Unauthorized)
            validation.kind.message else "服务器已更新，请在新服务器重新登录")
    }
    fun test() = viewModelScope.launch {
        state.value = NetworkSettingsState(busy = true)
        val result = appResult { container.apiClient.get<UserDto>("auth/me", authenticated = true) }
        state.value = NetworkSettingsState(message = when (result) {
            is AppResult.Success -> "连接成功"
            is AppResult.Failure -> if (result.kind == ErrorKind.Unauthorized) "服务器可达，请登录" else result.kind.message
        })
    }
    fun quality(value: AudioQuality) = viewModelScope.launch {
        container.musicSettings.setQuality(value)
        if (container.playbackQueue.state.value.current != null) container.playerController.qualityChanged()
    }
    fun warning(value: Boolean) = viewModelScope.launch { container.musicSettings.setWarning(value) }
    fun restore(value: Boolean) = viewModelScope.launch { container.musicSettings.setRestore(value) }
}
