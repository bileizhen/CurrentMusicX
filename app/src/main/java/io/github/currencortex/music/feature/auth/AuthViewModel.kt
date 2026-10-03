package io.github.currencortex.music.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AuthUiState(val loading: Boolean = false, val message: String? = null)
class AuthViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(AuthUiState())
    val account = container.accountRepository.state
    fun login(username: String, password: String) {
        if (state.value.loading) return
        viewModelScope.launch {
            container.ready.await()
            state.value = AuthUiState(loading = true)
            val result = container.authRepository.login(username.trim(), password)
            state.value = AuthUiState(message = when (result) {
                is AppResult.Success -> "登录成功"
                is AppResult.Failure -> result.kind.message
            })
        }
    }
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
