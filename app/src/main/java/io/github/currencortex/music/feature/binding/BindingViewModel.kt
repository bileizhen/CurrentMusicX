package io.github.currencortex.music.feature.binding

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.binding.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BindingUiState(val loading: Boolean = false, val binding: BindingState? = null, val error: String? = null,
    val qrUrl: String? = null, val qrMessage: String = "", val codeUntil: Long = 0)
class BindingViewModel(val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(BindingUiState())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private var task: Job? = null
    private fun session() = RequestSession(container.accountRepository.server, container.accountRepository.token)
    init {
        viewModelScope.launch {
            container.sessionRestored.await()
            combine(container.accountRepository.state.map { it.account?.id }.distinctUntilChanged(),
                container.musicSettings.state.map { it.server }.distinctUntilChanged()) { id, server -> id to server }
                .collect { state.value = BindingUiState(); reload() }
        }
    }
    fun reload() {
        task?.cancel(); task = viewModelScope.launch {
            state.update { it.copy(loading = true, error = null) }
            when (val r = appResult { container.bindingRepository.status() }) {
                is AppResult.Success -> state.update { it.copy(loading = false, binding = r.value) }
                is AppResult.Failure -> state.update { it.copy(loading = false, error = r.kind.message) }
            }
        }
    }
    fun action(block: suspend () -> String) {
        if (busy.value) return
        busy.value = true
        val expected = session()
        viewModelScope.launch {
            try { when (val r = appResult { block() }) {
                is AppResult.Success -> if (expected == session()) { message.value = r.value; reload() }
                is AppResult.Failure -> if (expected == session()) { message.value = r.kind.message; reload() }
            } } finally { busy.value = false }
        }
    }
    fun live() = action {
        val status = container.bindingRepository.live()
        if (status.ok) "网易云登录态正常" else if (!status.bound) "尚未绑定网易云" else "网易云登录态已失效，请重新登录"
    }
    fun refresh() = action { container.bindingRepository.refresh(); "网易云登录态已刷新" }
    fun sync(expected: RequestSession = session()) = action { container.bindingRepository.sync(expected).message() }
    fun unbind() = action { container.bindingRepository.unbind(); "网易云已解绑，已导入歌单保留为快照" }
    fun code(phone: String, country: String) {
        if (SystemClock.elapsedRealtime() < state.value.codeUntil) return
        action { container.bindingRepository.sendCode(phone, country); state.update { it.copy(codeUntil = SystemClock.elapsedRealtime() + 60_000) }; "验证码已发送" }
    }
    fun phone(phone: String, captcha: String, country: String) = action {
        val expected = session()
        container.bindingRepository.bindPhone(phone, captcha, country)
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        when (val result = appResult { container.bindingRepository.sync(expected) }) {
            is AppResult.Success -> "绑定成功；${result.value.message()}"
            is AppResult.Failure -> "绑定成功；歌单同步失败：${result.kind.message}，可再次同步"
        }
    }
    suspend fun qrSession() {
        val expected = session()
        state.update { it.copy(qrUrl = null, qrMessage = "正在生成二维码…") }
        when (val key = appResult { container.bindingRepository.qrKey(expected) }) {
            is AppResult.Failure -> if (expected == session()) state.update { it.copy(qrMessage = key.kind.message) }
            is AppResult.Success -> {
                if (expected != session()) return
                state.update { it.copy(qrUrl = container.bindingRepository.qrUrl(key.value), qrMessage = "等待扫码…") }
                while (currentCoroutineContext().isActive && expected == session()) {
                    delay(2000)
                    when (val status = appResult { container.bindingRepository.qrStatus(key.value, expected) }) {
                        is AppResult.Failure -> if (expected == session()) state.update { it.copy(qrMessage = status.kind.message) }
                        is AppResult.Success -> {
                            if (expected != session()) return
                            when (status.value.code) {
                                800 -> { state.update { it.copy(qrMessage = "二维码已过期，请刷新", qrUrl = null) }; return }
                                802 -> state.update { it.copy(qrMessage = "已扫码，请在网易云音乐确认授权…") }
                                803 -> {
                                    state.update { it.copy(qrUrl = null, qrMessage = "绑定成功") }; reload()
                                    sync(expected); return
                                }
                                else -> state.update { it.copy(qrMessage = "等待扫码…") }
                            }
                        }
                    }
                }
            }
        }
    }
    fun clearQr() { state.update { it.copy(qrUrl = null, qrMessage = "") } }
    override fun onCleared() { task?.cancel() }
}
