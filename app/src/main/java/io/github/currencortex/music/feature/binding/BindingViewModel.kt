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
    val qrUrl: String? = null, val qrMessage: String = "", val codeUntil: Long = 0,
    val codeFailed: Boolean = false, val phoneBoundRevision: Int = 0)
class BindingViewModel(val container: AppContainer) : ViewModel() {
    val state = MutableStateFlow(BindingUiState())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private var task: Job? = null
    private var actionTask: Job? = null
    private var qrKey: String? = null
    private var qrOwner: RequestSession? = null
    private var qrGeneration = 0L
    private var qrTerminal = false
    private fun session() = RequestSession(container.accountRepository.server, container.accountRepository.token)
    init {
        viewModelScope.launch {
            container.sessionRestored.await()
            combine(container.accountRepository.sessionRevision,
                container.musicSettings.state.map { it.server }.distinctUntilChanged()) { revision, server -> revision to server }
                .collect {
                    actionTask?.cancel(); busy.value = false; message.value = null
                    clearQr(); state.value = BindingUiState(); reload()
                }
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
    private fun failureMessage(failure: AppResult.Failure, stage: String): String {
        val status = if (failure.status > 0) "（HTTP ${failure.status}）" else ""
        if (stage == "备用验证码发送" && failure.kind in listOf(ErrorKind.Forbidden, ErrorKind.NotFound))
            return "当前服务器未开放备用验证码接口，请使用扫码登录。"
        if (stage.endsWith("验证码发送") && failure.kind in listOf(ErrorKind.Server, ErrorKind.Timeout, ErrorKind.Network))
            return "网易云验证码发送结果未确认$status。若已收到短信，可直接填写；未收到可稍后重试或改用扫码登录。"
        if (failure.kind == ErrorKind.Server) return "网易云${stage}暂时失败$status，请稍后重试" +
            (if (stage == "手机验证码登录") "，或改用扫码登录。" else "。")
        return "网易云$stage$status：${failure.kind.message}"
    }
    private fun action(stage: String = "账号操作", block: suspend (RequestSession) -> String) {
        if (busy.value) return
        busy.value = true
        message.value = null
        val expected = session()
        actionTask = viewModelScope.launch {
            try { when (val r = appResult { block(expected) }) {
                is AppResult.Success -> if (expected == session()) { message.value = r.value; reload() }
                is AppResult.Failure -> if (expected == session()) { message.value = failureMessage(r, stage); reload() }
            } } finally { if (expected == session()) busy.value = false }
        }
    }
    fun live() = action {
        val status = container.bindingRepository.live()
        if (status.ok) "网易云登录态正常" else if (!status.bound) "尚未绑定网易云" else "网易云登录态已失效，请重新登录"
    }
    fun refresh() = action { expected -> container.bindingRepository.refresh(expected); "网易云登录态已刷新" }
    fun sync(expected: RequestSession = session()) = action { container.bindingRepository.sync(expected).message() }
    fun mainLibrary(value: Boolean) = viewModelScope.launch { container.musicSettings.setNeteaseMainLibrary(value) }
    fun reloadMusic() = action {
        container.bindingRepository.status()
        container.neteaseLibrary.invalidate()
        container.libraryRepository.invalidate()
        "音乐库已更新"
    }
    private suspend fun afterBinding(expected: RequestSession): String {
        if (container.musicSettings.snapshot().neteaseMainLibrary) {
            val status = appResult { container.bindingRepository.status() }
            container.neteaseLibrary.invalidate()
            container.libraryRepository.invalidate()
            return if (status is AppResult.Success) "绑定成功，已使用网易云音乐库"
                else "绑定成功；暂时无法读取音乐库，请刷新重试"
        }
        return when (val result = appResult { container.bindingRepository.sync(expected) }) {
            is AppResult.Success -> "绑定成功；${result.value.message()}"
            is AppResult.Failure -> "绑定成功；${failureMessage(result, "歌单同步")}，可再次同步"
        }
    }
    fun unbind() = action { expected -> container.bindingRepository.unbind(expected); clearQr(); "网易云已解绑，已导入歌单保留为快照" }
    fun code(phone: String, country: String, alternate: Boolean = false) {
        if (SystemClock.elapsedRealtime() < state.value.codeUntil) return
        action(if (alternate) "备用验证码发送" else "验证码发送") { expected ->
            // A gateway timeout does not prove that the SMS was not delivered.
            state.update { it.copy(codeUntil = SystemClock.elapsedRealtime() + 60_000, codeFailed = false) }
            when (val result = appResult { container.bindingRepository.sendCode(phone, country, alternate, expected) }) {
                is AppResult.Success -> "验证码已发送，请查看短信"
                is AppResult.Failure -> {
                    if (expected == session()) state.update { it.copy(codeFailed = true) }
                    throw ApiException(result.kind, result.status)
                }
            }
        }
    }
    fun phone(phone: String, captcha: String, country: String) = action("手机验证码登录") { expected ->
        container.bindingRepository.bindPhone(phone, captcha, country, expected)
        if (expected != session()) throw ApiException(ErrorKind.Unauthorized)
        state.update { it.copy(phoneBoundRevision = it.phoneBoundRevision + 1) }
        afterBinding(expected)
    }
    suspend fun qrSession() {
        val expected = session()
        if (qrOwner != expected) clearQr()
        qrOwner = expected
        if (qrTerminal) return
        val generation = qrGeneration
        fun valid() = expected == session() && generation == qrGeneration
        if (qrKey == null) {
            state.update { it.copy(qrUrl = null, qrMessage = "正在生成二维码…") }
            when (val key = appResult { container.bindingRepository.qrKey(expected) }) {
                is AppResult.Failure -> {
                    if (valid()) state.update { it.copy(qrMessage = failureMessage(key, "二维码生成")) }
                    return
                }
                is AppResult.Success -> {
                    if (!valid()) return
                    qrKey = key.value
                    state.update { it.copy(qrUrl = container.bindingRepository.qrUrl(key.value), qrMessage = "等待扫码…") }
                }
            }
        }
        val key = qrKey ?: return
        var retryDelay = 2000L
        // Resume checks the existing key immediately, including confirmation made in the NCM app.
        while (currentCoroutineContext().isActive && valid()) {
            when (val status = appResult { container.bindingRepository.qrStatus(key, expected) }) {
                is AppResult.Failure -> {
                    if (!valid()) return
                    if (status.kind in listOf(ErrorKind.Unauthorized, ErrorKind.Forbidden, ErrorKind.NotFound)) {
                        qrTerminal = true
                        state.update { it.copy(qrUrl = null, qrMessage = failureMessage(status, "扫码状态查询")) }
                        return
                    }
                    state.update { it.copy(qrMessage = failureMessage(status, "扫码状态查询") + " 正在重试，二维码保持不变。") }
                    retryDelay = (retryDelay * 2).coerceAtMost(15000L)
                }
                is AppResult.Success -> {
                    if (!valid()) return
                    retryDelay = 2000L
                    when (status.value.code) {
                        800 -> {
                            qrTerminal = true
                            state.update { it.copy(qrMessage = "二维码已过期，请刷新", qrUrl = null) }
                            return
                        }
                        802 -> state.update { it.copy(qrMessage = "已扫码，请在网易云音乐确认授权…") }
                        803 -> {
                            qrTerminal = true
                            state.update { it.copy(qrUrl = null, qrMessage = "绑定成功") }
                            action("音乐库更新") { afterBinding(expected) }
                            return
                        }
                        801 -> state.update { it.copy(qrMessage = "等待扫码…") }
                        else -> state.update { it.copy(qrMessage = "扫码状态暂时不可用，正在重试，二维码保持不变。") }
                    }
                }
            }
            delay(retryDelay)
        }
    }
    fun clearQr() {
        qrGeneration++; qrKey = null; qrOwner = null; qrTerminal = false
        state.update { it.copy(qrUrl = null, qrMessage = "") }
    }
    override fun onCleared() { task?.cancel() }
}
