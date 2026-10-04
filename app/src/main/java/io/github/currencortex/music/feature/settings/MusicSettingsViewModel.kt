package io.github.currencortex.music.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.media.AudioQuality
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.auth.UserDto
import io.github.currencortex.music.data.settings.AudioProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class NetworkSettingsState(val busy: Boolean = false, val message: String? = null)
data class AudioCacheSettingsState(val busy: Boolean = false, val message: String? = null)
class MusicSettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.musicSettings.state
    val state = MutableStateFlow(NetworkSettingsState())
    val cacheBytes = container.audioCache.bytes
    val cacheState = MutableStateFlow(AudioCacheSettingsState())
    val audioProvider = container.audioSettings.state
    val audioState = MutableStateFlow(NetworkSettingsState())
    private fun audioEdit(change: suspend () -> Unit) = viewModelScope.launch {
        if (audioState.value.busy) return@launch
        val previous = container.audioSettings.access().identity
        audioState.value = NetworkSettingsState(busy = true)
        try {
            change()
            container.audioSources.invalidate()
            if (previous != container.audioSettings.access().identity &&
                container.playerController.state.value.mode == io.github.currencortex.music.core.media.PlayerMode.LOCAL &&
                container.playbackQueue.state.value.current?.video == false) {
                val playback = container.playerController.state.value
                val queue = container.playbackQueue.state.value
                container.playerController.load(playback.showPause,
                    if (playback.song?.id == queue.current?.id) playback.positionMs else queue.positionMs)
            }
            audioState.value = NetworkSettingsState(message = "音源设置已保存")
        } catch (e: CancellationException) { throw e }
        catch (_: IllegalArgumentException) { audioState.value = NetworkSettingsState(message = "请输入有效的 API Key") }
        catch (_: Exception) { audioState.value = NetworkSettingsState(message = "音源设置保存失败，请重试") }
    }
    fun audioProvider(value: AudioProvider) = audioEdit { container.audioSettings.select(value) }
    fun audioKey(value: String) = audioEdit { container.audioSettings.saveKey(value) }
    fun clearAudioKey() = audioEdit { container.audioSettings.clearKey() }
    fun refreshCache() = viewModelScope.launch(Dispatchers.IO) {
        runCatching { container.audioCache.refreshUsage() }.onFailure {
            cacheState.value = AudioCacheSettingsState(message = "歌曲缓存暂不可用")
        }
    }
    fun preload(value: Boolean) = viewModelScope.launch { container.musicSettings.setPreload(value) }
    fun preloadMetered(value: Boolean) = viewModelScope.launch { container.musicSettings.setPreloadMetered(value) }
    fun clearCache() = viewModelScope.launch {
        if (cacheState.value.busy) return@launch
        cacheState.value = AudioCacheSettingsState(busy = true)
        try {
            container.audioSources.invalidate()
            container.audioCache.clear()
            cacheState.value = AudioCacheSettingsState(message = "歌曲缓存已清理")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { cacheState.value = AudioCacheSettingsState(message = "缓存清理失败，请重试") }
    }
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
