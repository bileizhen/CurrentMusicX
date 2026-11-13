package io.github.currencortex.music.feature.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.currencortex.music.core.network.*
import io.github.currencortex.music.data.support.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DonationState(val signedIn: Boolean = false, val loading: Boolean = false,
    val busy: Boolean = false, val info: DonationInfo? = null, val step: Int = 0,
    val qr: ByteArray? = null, val qrLoading: Boolean = false, val qrError: String? = null,
    val input: DonationInput = DonationInput(), val error: String? = null, val message: String? = null)

class DonationViewModel(private val repository: DonationApi, owners: Flow<RequestSession>) : ViewModel() {
    val state = MutableStateFlow(DonationState())
    private var owner: RequestSession? = null
    private var revision = 0L
    private var action: Job? = null
    private var imageAction: Job? = null

    init {
        viewModelScope.launch {
            owners.distinctUntilChanged().collect { next ->
                action?.cancel(); imageAction?.cancel(); revision++; owner = next
                state.value = DonationState(signedIn = !next.token.isNullOrBlank())
                if (state.value.signedIn) refresh()
            }
        }
    }
    private fun current(expected: RequestSession, epoch: Long) = owner == expected && revision == epoch

    fun refresh() {
        val expected = owner?.takeIf { !it.token.isNullOrBlank() } ?: return
        if (state.value.loading || state.value.busy) return
        action?.cancel(); imageAction?.cancel()
        val epoch = ++revision
        state.update { it.copy(loading = true, info = null, step = 0, qr = null, qrLoading = false,
            qrError = null, error = null, message = null) }
        action = viewModelScope.launch {
            val result = appResult { repository.info(expected) }
            if (!current(expected, epoch)) return@launch
            when (result) {
                is AppResult.Success -> state.update { it.copy(loading = false, info = result.value) }
                is AppResult.Failure -> state.update { it.copy(loading = false, error = failureMessage(result)) }
            }
        }
    }

    fun confirmAccount() {
        if (state.value.info == null || state.value.loading || state.value.busy) return
        state.update { it.copy(step = 1, error = null, message = null) }
        if (state.value.qr == null) loadQr()
    }
    fun loadQr() {
        val expected = owner?.takeIf { !it.token.isNullOrBlank() } ?: return
        if (state.value.info == null || state.value.qrLoading) return
        val epoch = revision
        state.update { it.copy(qrLoading = true, qr = null, qrError = null) }
        imageAction = viewModelScope.launch {
            val result = appResult { repository.qr(expected) }
            if (!current(expected, epoch)) return@launch
            when (result) {
                is AppResult.Success -> state.update { it.copy(qrLoading = false, qr = result.value) }
                is AppResult.Failure -> state.update { it.copy(qrLoading = false, qrError = failureMessage(result)) }
            }
        }
    }
    fun showForm() {
        if (state.value.step == 1 && state.value.qr != null) state.update { it.copy(step = 2, error = null) }
    }
    fun previous() {
        if (!state.value.busy) state.update { it.copy(step = (it.step - 1).coerceAtLeast(0), error = null) }
    }
    fun edit(input: DonationInput) {
        if (!state.value.busy) state.update { it.copy(input = input, error = null, message = null) }
    }
    fun submit() {
        val expected = owner?.takeIf { !it.token.isNullOrBlank() } ?: return
        val value = state.value
        val info = value.info ?: return
        if (value.step != 2 || value.busy || value.loading) return
        value.input.validation(info)?.let { message -> state.update { it.copy(error = message) }; return }
        val epoch = revision
        state.update { it.copy(busy = true, error = null, message = null) }
        action = viewModelScope.launch {
            val result = appResult { repository.submit(expected, value.input, info) }
            if (!current(expected, epoch)) return@launch
            when (result) {
                is AppResult.Success -> state.update { it.copy(busy = false, info = info.copy(mine = result.value),
                    input = DonationInput(), step = 0, message = "捐赠记录已提交，供站长核对。感谢你的支持！") }
                is AppResult.Failure -> state.update { it.copy(busy = false, error = failureMessage(result)) }
            }
        }
    }
    private fun failureMessage(error: AppResult.Failure): String = when (error.status) {
        400 -> "金额或交易单号格式不正确，请核对后重试"
        401 -> "CurrentMusic 登录已过期，请重新登录"
        404 -> "当前服务器暂不支持官方捐赠"
        409 -> "该交易单号已提交，请核对单号或刷新本人捐赠记录"
        else -> error.message
    }
}
