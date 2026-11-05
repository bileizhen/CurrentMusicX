package io.github.currencortex.music.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.AppContainer
import io.github.currencortex.music.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import io.github.currencortex.music.data.auth.RegistrationInput
import io.github.currencortex.music.data.auth.LoginOutcome
import io.github.currencortex.music.data.auth.LoginChallenge
import android.os.SystemClock

data class AuthUiState(val loading: Boolean = false, val message: String? = null, val failed: Boolean = false)
class AuthViewModel(private val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(AuthUiState())
    val account = container.accountRepository.state
    val errorNotices = container.errorNotices
    val codeUntil = MutableStateFlow(0L)
    val resetCodeUntil = MutableStateFlow(0L)
    val loginEmailCodeUntil = MutableStateFlow(0L)
    val loginChallenge = MutableStateFlow<LoginChallenge?>(null)
    private var loginJob: Job? = null
    private var loginAttempt = 0L
    private var loginOwnerRevision: Long? = null
    fun begin() { dismissLogin(); state.value = AuthUiState() }
    fun dismissLogin() {
        loginChallenge.value?.let(container.authRepository::discardLoginChallenge)
        loginChallenge.value = null
        // Account emission can dispose the form before the accepted login finishes its
        // metadata write. Only cancel a request that has not established a new session.
        if (loginOwnerRevision == null || loginOwnerRevision == container.accountRepository.sessionRevision.value) {
            loginAttempt++; loginJob?.cancel(); loginJob = null
            state.value = AuthUiState()
        }
    }
    private fun loginAction(block: suspend () -> AppResult<LoginOutcome>) {
        if (state.value.loading) return
        val attempt = ++loginAttempt
        loginOwnerRevision = null
        state.value = AuthUiState(loading = true)
        loginJob = viewModelScope.launch {
            try {
                container.sessionRestored.await()
                loginOwnerRevision = container.accountRepository.sessionRevision.value
                val result = block()
                ensureActive()
                if (attempt != loginAttempt) return@launch
                when (result) {
                    is AppResult.Success -> when (val outcome = result.value) {
                        is LoginOutcome.VerificationRequired -> {
                            loginChallenge.value = outcome.challenge
                            state.value = AuthUiState()
                        }
                        is LoginOutcome.SignedIn -> {
                            loginChallenge.value = null
                            container.playerController.pause()
                            state.value = AuthUiState(message = "登录成功")
                        }
                    }
                    is AppResult.Failure -> state.value = AuthUiState(message = result.message, failed = true)
                }
            } finally {
                if (attempt == loginAttempt) state.update { it.copy(loading = false) }
            }
        }
    }
    /** Drops a stale outcome without touching an in-flight request. */
    fun clearMessage() { state.update { it.copy(message = null, failed = false) } }
    private fun action(success: String, block: suspend () -> AppResult<*>) {
        if (state.value.loading) return
        state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            try {
                container.sessionRestored.await()
                when (val result = block()) {
                    is AppResult.Success -> state.value = AuthUiState(message = success)
                    is AppResult.Failure -> state.value = AuthUiState(message = result.message, failed = true)
                }
            } finally { state.update { it.copy(loading = false) } }
        }
    }
    fun login(username: String, password: String) {
        if (state.value.loading) return
        dismissLogin()
        loginAction { container.authRepository.login(username.trim(), password) }
    }
    fun verifyLogin(code: String) {
        val challenge = loginChallenge.value ?: return
        loginAction { container.authRepository.verifyLogin(challenge, code) }
    }
    fun loginWithEmail(email: String, code: String) {
        if (state.value.loading) return
        dismissLogin()
        loginAction { container.authRepository.loginWithEmail(email, code) }
    }
    fun loginWithPasskey(context: android.content.Context) {
        if (state.value.loading) return
        dismissLogin()
        loginAction {
            when (val origin = appResult { AndroidPasskeyLogin.origin(context) }) {
                is AppResult.Failure -> origin
                is AppResult.Success -> container.authRepository.loginWithPasskey(origin.value) {
                    AndroidPasskeyLogin.credential(context, it)
                }
            }
        }
    }
    fun loginEmailCode(email: String) {
        if (SystemClock.elapsedRealtime() < loginEmailCodeUntil.value) return
        action("登录验证码已发送") {
            container.authRepository.sendLoginEmailCode(email).also {
                if (it is AppResult.Success) loginEmailCodeUntil.value = SystemClock.elapsedRealtime() + 60_000
            }
        }
    }
    override fun onCleared() {
        loginChallenge.value?.let(container.authRepository::discardLoginChallenge)
        super.onCleared()
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
    fun passwordCode(email: String) {
        if (SystemClock.elapsedRealtime() < resetCodeUntil.value) return
        action("验证码已发送，5 分钟内有效") { container.authRepository.sendPasswordResetCode(email).also {
            if (it is AppResult.Success) resetCodeUntil.value = SystemClock.elapsedRealtime() + 60_000
        } }
    }
    fun resetPassword(email: String, code: String, newPassword: String) = action("密码已重置，请使用新密码登录") {
        container.authRepository.resetPassword(email, code, newPassword)
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
