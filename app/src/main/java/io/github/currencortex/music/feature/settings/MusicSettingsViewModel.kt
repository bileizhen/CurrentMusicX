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
    val lxSources = container.lxSources.state
    val lxHealth = container.lxSources.health
    suspend fun checkLxSources() {
        val selected = audioProvider.value.lxSourceId
        lxSources.value.sortedBy { if (it.id == selected) 0 else 1 }.forEach { container.lxSources.checkAvailability(it.id) }
    }
    fun lxSupported() = container.lxSources.supported()
    val serverHealth = container.serverRouter.health
    val automaticServer = container.serverRouter.automatic
    val serverProbeBusy = MutableStateFlow(false)
    fun refreshServers() = viewModelScope.launch {
        if (serverProbeBusy.value) return@launch
        serverProbeBusy.value = true
        try {
            container.ready.await()
            container.serverRouter.refresh(container.musicSettings.snapshot().customServer)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            state.value = state.value.copy(message = "线路检测失败，请重试")
        } finally { serverProbeBusy.value = false }
    }
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
        catch (e: io.github.currencortex.music.data.lx.LxSourceException) { audioState.value = NetworkSettingsState(message = e.message) }
        catch (e: IllegalArgumentException) { audioState.value = NetworkSettingsState(message = e.message?.take(160) ?: "设置无效，请检查后重试") }
        catch (_: Exception) { audioState.value = NetworkSettingsState(message = "音源设置保存失败，请重试") }
    }
    fun audioProvider(value: AudioProvider) = audioEdit { container.audioSettings.select(value) }
    fun audioKey(value: String) = audioEdit { container.audioSettings.saveKey(value) }
    fun clearAudioKey() = audioEdit { container.audioSettings.clearKey() }
    fun selectLxSource(id: String) = audioEdit { container.audioSettings.selectLxSource(id) }
    fun importLxFile(uri: android.net.Uri) = audioEdit {
        container.audioSettings.selectLxSource(container.lxSources.importFile(uri).id)
    }
    fun importLxUrl(url: String) = audioEdit {
        container.audioSettings.selectLxSource(container.lxSources.importUrl(url).id)
    }
    fun removeLxSource(id: String) = audioEdit {
        if (container.audioSettings.access().lxSourceId == id) {
            container.audioSettings.selectLxSource("")
        }
        container.lxSources.remove(id)
    }
    fun refreshCache() = viewModelScope.launch(Dispatchers.IO) {
        runCatching { container.audioCache.refreshUsage() }.onFailure {
            cacheState.value = AudioCacheSettingsState(message = "歌曲缓存暂不可用")
        }
    }
    fun preload(value: Boolean) = viewModelScope.launch { container.musicSettings.setPreload(value) }
    fun lyricsSource(value: io.github.currencortex.music.data.settings.LyricsSource) = viewModelScope.launch {
        try { container.musicSettings.setLyricsSource(value) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { container.errorNotices.show("歌词来源保存失败，请重试") }
    }
    fun likeMode(value: io.github.currencortex.music.data.settings.LikeMode) = viewModelScope.launch { container.musicSettings.setLikeMode(value) }
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
    fun server(value: String) = selectServer(CurrentMusicServer.CUSTOM, value)
    fun selectServer(mode: CurrentMusicServer, custom: String = "") = viewModelScope.launch {
        if (state.value.busy) return@launch
        state.value = NetworkSettingsState(busy = true)
        try {
            container.ready.await()
            val previous = container.musicSettings.snapshot()
            // Presets share an account. Keep its logical server stable when changing only transport.
            val normalized = if (mode == CurrentMusicServer.CUSTOM) ServerUrl.normalize(custom)
                else previous.server.takeIf(CurrentMusicServer::builtIn) ?: mode.url ?: io.github.currencortex.music.core.config.ServerDefaults.URL
            val changedAccountServer = normalized != container.accountRepository.server
            if (changedAccountServer) {
                container.playerController.clear()
                container.database.music().clearLyrics()
                container.authRepository.resetServer(normalized)
            }
            container.musicSettings.setServerSelection(mode, normalized)
            container.serverRouter.configure(mode, normalized)
            container.accountRepository.server = normalized
            state.value = NetworkSettingsState(message = if (changedAccountServer)
                "服务器已更新，请在新服务器重新登录" else "服务器线路已更新")
            if (mode == CurrentMusicServer.AUTO || mode == CurrentMusicServer.CUSTOM) refreshServers()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (failure: IllegalArgumentException) {
            state.value = NetworkSettingsState(message = failure.message)
        } catch (_: Exception) {
            state.value = NetworkSettingsState(message = "服务器设置保存失败，请重试")
        }
    }
    fun test() = viewModelScope.launch {
        if (state.value.busy) return@launch
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
    fun restore(value: Boolean) = viewModelScope.launch { container.musicSettings.setRestore(value) }
    fun concurrentPlayback(value: Boolean) = viewModelScope.launch { container.musicSettings.setConcurrentPlayback(value) }
    fun transition(value: io.github.currencortex.music.data.settings.MusicTransitionMode) = viewModelScope.launch { container.musicSettings.setTransitionMode(value) }
    fun crossfadeSeconds(value: Int) = viewModelScope.launch { container.musicSettings.setCrossfadeSeconds(value) }
}
