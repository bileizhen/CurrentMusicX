package io.github.currencortex.music.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import io.github.currencortex.music.data.auth.RegistrationInput
import android.os.SystemClock

data class AuthUiState(val loading: Boolean = false, val message: String? = null)
class AuthViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(AuthUiState())
    val account = container.accountRepository.state
    val codeUntil = MutableStateFlow(0L)
    fun begin() { state.value = AuthUiState() }
    /** Drops a stale outcome without touching an in-flight request. */
    fun clearMessage() { state.update { it.copy(message = null) } }
    private fun action(success: String, block: suspend () -> AppResult<*>) {
        if (state.value.loading) return
        state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            try {
                container.sessionRestored.await()
                when (val result = block()) {
                    is AppResult.Success -> state.value = AuthUiState(message = success)
                    is AppResult.Failure -> state.value = AuthUiState(message = result.kind.message)
                }
            } finally { state.update { it.copy(loading = false) } }
        }
    }
    fun login(username: String, password: String) {
        action("登录成功") { container.authRepository.login(username.trim(), password).also {
            if (it is AppResult.Success) container.playerController.pause()
        } }
    }
    fun register(input: RegistrationInput) = action("注册成功") { container.authRepository.register(input).also { if (it is AppResult.Success) container.playerController.pause() } }
    fun code(contact: String, phone: Boolean) {
        if (SystemClock.elapsedRealtime() < codeUntil.value) return
        action("验证码已发送") { container.authRepository.sendRegistrationCode(contact.trim(), phone).also {
            if (it is AppResult.Success) codeUntil.value = SystemClock.elapsedRealtime() + 60_000
        } }
    }
    fun switch(key: String) = action("账号已切换") {
        if (container.roomSession.active) container.roomSession.leave()
        container.dlnaController.stop()
        if (container.playerController.state.value.mode == io.github.currencortex.music.core.media.PlayerMode.LOCAL) container.playerController.pause()
        container.authRepository.switchAccount(key)
    }
    fun removeSaved(key: String) = action("已移除本机保存的账号") { appResult { container.accountRepository.removeSaved(key) } }
    fun retry() = viewModelScope.launch {
        state.value = AuthUiState(loading = true)
        val result = container.authRepository.restore(container.accountRepository.server)
        state.value = AuthUiState(message = (result as? AppResult.Failure)?.kind?.message)
    }
    fun logout() = viewModelScope.launch {
        state.value = AuthUiState(loading = true)
        try { container.authRepository.logout() } catch (_: Exception) { }
        state.value = AuthUiState()
    }
}
